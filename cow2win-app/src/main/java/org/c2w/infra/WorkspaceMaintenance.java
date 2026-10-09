package org.c2w.infra;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Restoring the workspace from a backup ZIP (see {@link BackupService}) and moving it to
 * another folder. Both are scheduled in the settings dialog ({@link Config#setPendingRestoreZip},
 * {@link Config#setPendingWorkspaceMove}) and run on the next start by
 * {@link #runPendingOperation} - before any journal database is opened (locked on Windows
 * while open) and before the context is built from the workspace.
 *
 * <p><b>Restore</b> replaces the workspace completely with the ZIP's content, after saving the
 * current state as {@value #BEFORE_RESTORE_FILE_NAME} in the backup directory. The technical log
 * ({@value Logger#LOG_FILE_NAME} and its rotated file) is neither restored nor deleted - it
 * describes the program runs, not the data; a backup directory inside the workspace is kept too.
 *
 * <p><b>Move</b> copies the whole workspace into an empty target (copying, since
 * {@link Files#move} does not work across drives), checks the copy, switches the configured
 * workspace and only then deletes the old folder.
 *
 * <p>Nothing here ever throws: a failure leaves the old workspace in use and is returned as an
 * {@link Outcome} - starting the application must never fail because of it. The package-private
 * overloads take explicit paths and a log sink, so tests touch neither {@link Config} nor the
 * real workspace.
 */
public final class WorkspaceMaintenance {

    /** The state before a restore, saved in the backup directory (overwrites an older one). */
    public static final String BEFORE_RESTORE_FILE_NAME = "workspace-before-restore.zip";

    /** Name of the dedicated folder a workspace always lives in (see {@link #normalizeWorkspaceDir}). */
    public static final String WORKSPACE_DIR_NAME = ".cow2Win";

    /** The technical log files directly in the workspace - never restored, never deleted by a restore. */
    static final Set<String> LOG_FILE_NAMES = Set.of(Logger.LOG_FILE_NAME, Logger.LOG_FILE_NAME + ".1");

    private WorkspaceMaintenance() {
    }

    /** What a pending operation run at startup did - see {@link #runPendingOperation}. */
    public sealed interface Outcome permits Restored, RestoreFailed, Moved, MoveFailed {
    }

    /** The workspace was replaced by the content of {@code zip} (a backup written at {@code backupTime}). */
    public record Restored(Path zip, FileTime backupTime, int files, Path beforeRestoreZip) implements Outcome {
    }

    /** Restoring from {@code zip} failed - the previous state stays (or was put back). */
    public record RestoreFailed(Path zip, String reason) implements Outcome {
    }

    /** The workspace now lives in {@code to}; {@code oldWorkspaceRemoved} is false if parts of {@code from} are left. */
    public record Moved(Path from, Path to, int files, boolean oldWorkspaceRemoved) implements Outcome {
    }

    /** Moving failed - the workspace stays unchanged in {@code from}. {@code problem} is set if the target was rejected. */
    public record MoveFailed(Path from, Path to, MoveProblem problem, String reason) implements Outcome {
    }

    /** Why a folder cannot take the workspace - see {@link #checkMoveTarget}. */
    public enum MoveProblem {
        /** There is no workspace folder to move. */
        NO_WORKSPACE,
        /** The target is the workspace itself. */
        SAME_AS_WORKSPACE,
        /** The target lies inside the workspace. */
        INSIDE_WORKSPACE,
        /** The workspace lies inside the target. */
        CONTAINS_WORKSPACE,
        /** The target exists and is not an empty folder - workspaces are never merged. */
        TARGET_NOT_EMPTY,
        /** The target's parent folder does not exist or cannot be written. */
        PARENT_NOT_WRITABLE
    }

    /** Daily or weekly backup. */
    public enum BackupKind {
        DAILY(BackupService.DAILY_BACKUP_FILE_NAME),
        WEEKLY(BackupService.WEEKLY_BACKUP_FILE_NAME);

        private final String fileName;

        BackupKind(String fileName) {
            this.fileName = fileName;
        }
    }

    /** A backup ZIP that can be restored, with the time it was written. */
    public record BackupChoice(BackupKind kind, Path zip, FileTime time) {
    }

    /** Copies one file - replaceable in tests to simulate a failing copy. */
    @FunctionalInterface
    interface FileCopier {
        void copy(Path source, Path target) throws IOException;
    }

    private static final FileCopier DEFAULT_COPIER =
            (source, target) -> Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);

    // --- choices and checks for the settings dialog ---

    /** The daily and weekly backups that exist in {@code backupDir} (daily first). */
    public static List<BackupChoice> availableBackups(Path backupDir) {
        List<BackupChoice> choices = new ArrayList<>();
        for (BackupKind kind : BackupKind.values()) {
            Path zip = backupDir.resolve(kind.fileName);
            if (Files.isRegularFile(zip)) {
                try {
                    choices.add(new BackupChoice(kind, zip.toAbsolutePath().normalize(), Files.getLastModifiedTime(zip)));
                } catch (IOException e) {
                    Logger.logException("Could not read the time of backup " + zip, e);
                }
            }
        }
        return choices;
    }

    /**
     * The workspace folder for a folder the user chose: the folder itself if it is named
     * {@value #WORKSPACE_DIR_NAME} (ignoring case), otherwise a {@value #WORKSPACE_DIR_NAME}
     * folder inside it. Creates nothing.
     */
    public static Path normalizeWorkspaceDir(Path chosen) {
        Path folderName = chosen.getFileName();
        boolean alreadyWorkspaceDir = folderName != null && folderName.toString().equalsIgnoreCase(WORKSPACE_DIR_NAME);
        return alreadyWorkspaceDir ? chosen : chosen.resolve(WORKSPACE_DIR_NAME);
    }

    /** Why {@code target} cannot take the workspace {@code workspace}, or {@code null} if it can. */
    public static MoveProblem checkMoveTarget(Path workspace, Path target) {
        Path from = absolute(workspace);
        Path to = absolute(target);
        if (!Files.isDirectory(from)) {
            return MoveProblem.NO_WORKSPACE;
        }
        if (to.equals(from)) {
            return MoveProblem.SAME_AS_WORKSPACE;
        }
        if (to.startsWith(from)) {
            return MoveProblem.INSIDE_WORKSPACE;
        }
        if (from.startsWith(to)) {
            return MoveProblem.CONTAINS_WORKSPACE;
        }
        if (Files.exists(to) && (!Files.isDirectory(to) || !isEmptyDirectory(to))) {
            return MoveProblem.TARGET_NOT_EMPTY;
        }
        Path parent = to.getParent();
        if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent)) {
            return MoveProblem.PARENT_NOT_WRITABLE;
        }
        return null;
    }

    // --- at startup ---

    /**
     * Runs the scheduled restore or move, if any, and removes the schedule first - so it runs
     * exactly once, even if it fails. If both are scheduled the restore wins and the move is
     * discarded. Call right after {@link Config#load()}, before backups and journal databases.
     *
     * @param progress receives a short description for the splash screen
     * @return what was done, empty if nothing was scheduled
     */
    public static Optional<Outcome> runPendingOperation(Consumer<String> progress) {
        return runPendingOperation(progress, Config::save);
    }

    /** Like {@link #runPendingOperation(Consumer)}, with {@code saveConfig} instead of {@link Config#save()} - for tests. */
    static Optional<Outcome> runPendingOperation(Consumer<String> progress, Runnable saveConfig) {
        String restoreZip = Config.getPendingRestoreZip();
        String moveTarget = Config.getPendingWorkspaceMove();
        if (restoreZip.isBlank() && moveTarget.isBlank()) {
            return Optional.empty();
        }
        Config.clearPendingOperations();
        saveConfig.run();
        Path workspace = Config.getWorkspaceDir();
        try {
            if (!restoreZip.isBlank()) {
                if (!moveTarget.isBlank()) {
                    Logger.log("Pending workspace move to " + moveTarget + " discarded - a restore is pending too and wins");
                }
                progress.accept("Restoring workspace ...");
                return Optional.of(restoreFromBackup(Paths.get(restoreZip), workspace, Config.getBackupDirPath(), Logger::log));
            }
            progress.accept("Moving workspace ...");
            Path target = Paths.get(moveTarget);
            Path backupDir = Config.getBackupDirPath();
            return Optional.of(moveWorkspace(workspace, target, () -> switchWorkspace(workspace, target, backupDir, saveConfig), Logger::log));
        } catch (RuntimeException e) {
            Logger.logException("Pending workspace operation failed", e);
            return Optional.of(restoreZip.isBlank()
                    ? new MoveFailed(absolute(workspace), absolute(Paths.get(moveTarget)), null, e.toString())
                    : new RestoreFailed(Paths.get(restoreZip), e.toString()));
        }
    }

    /** Makes {@code target} the configured workspace - with a backup directory that lived inside the old one. */
    private static void switchWorkspace(Path workspace, Path target, Path backupDir, Runnable saveConfig) {
        Path from = absolute(workspace);
        Path to = absolute(target);
        Config.setWorkspacePath(to.toString());
        Path backup = absolute(backupDir);
        if (backup.startsWith(from)) {
            Config.setBackupDir(to.resolve(from.relativize(backup).toString()).toString());
        }
        saveConfig.run();
    }

    // --- restore ---

    /** Replaces the content of {@code workspaceDir} by the content of {@code zip} - see the class Javadoc. */
    static Outcome restoreFromBackup(Path zip, Path workspaceDir, Path backupDir, Consumer<String> log) {
        Path workspace = absolute(workspaceDir);
        log.accept("Restoring workspace " + workspace + " from backup " + zip);
        FileTime backupTime;
        try {
            backupTime = Files.getLastModifiedTime(zip);
            int entries = checkZip(zip);
            log.accept("Backup " + zip + " of " + backupTime + " is valid: " + entries + " entries");
        } catch (IOException e) {
            log.accept("Restore failed - the backup " + zip + " is not valid, the workspace is unchanged: " + e);
            return new RestoreFailed(zip, e.toString());
        }

        Path beforeRestoreZip = absolute(backupDir).resolve(BEFORE_RESTORE_FILE_NAME);
        try {
            Files.createDirectories(workspace);
            BackupService.createBackup(workspace, beforeRestoreZip);
            log.accept("Current workspace saved as " + beforeRestoreZip);
        } catch (IOException e) {
            log.accept("Restore failed - could not save the current workspace as " + beforeRestoreZip
                    + ", the workspace is unchanged: " + e);
            return new RestoreFailed(zip, e.toString());
        }

        Set<Path> keep = keptOnRestore(workspace, backupDir);
        try {
            int files = replaceWith(zip, workspace, keep);
            log.accept("Workspace restored from " + zip + " (backup of " + backupTime + "): " + files + " files");
            return new Restored(zip, backupTime, files, beforeRestoreZip);
        } catch (IOException e) {
            log.accept("Restore failed while replacing the workspace: " + e
                    + " - putting back the previous state from " + beforeRestoreZip);
            try {
                replaceWith(beforeRestoreZip, workspace, keep);
                log.accept("Previous state put back from " + beforeRestoreZip);
            } catch (IOException rollbackError) {
                log.accept("Could not put back the previous state - it is still in " + beforeRestoreZip + ": " + rollbackError);
            }
            return new RestoreFailed(zip, e.toString());
        }
    }

    /** What a restore never deletes: the technical log, the backup ZIPs and a backup directory inside the workspace. */
    private static Set<Path> keptOnRestore(Path workspace, Path backupDir) {
        Set<Path> keep = new HashSet<>();
        LOG_FILE_NAMES.forEach(name -> keep.add(workspace.resolve(name)));
        Path backup = absolute(backupDir);
        if (backup.startsWith(workspace) && !backup.equals(workspace)) {
            keep.add(backup);
        }
        for (String name : List.of(BackupService.DAILY_BACKUP_FILE_NAME, BackupService.WEEKLY_BACKUP_FILE_NAME,
                BEFORE_RESTORE_FILE_NAME)) {
            keep.add(backup.resolve(name));
        }
        return keep;
    }

    /**
     * Unpacks {@code zip} into a temporary folder next to the workspace, then deletes the
     * workspace's content except {@code keep} and moves the unpacked content in.
     *
     * @return the number of files restored
     */
    private static int replaceWith(Path zip, Path workspace, Set<Path> keep) throws IOException {
        Path temp = Files.createTempDirectory(workspace.getParent(), workspace.getFileName() + "-restore-");
        try {
            int files = unpack(zip, temp);
            deleteContentExcept(workspace, keep);
            moveContentInto(temp, workspace);
            return files;
        } finally {
            deleteRecursively(temp);
        }
    }

    /** Reads every entry of {@code zip} (checking names and CRCs) - at least one entry, no absolute or ".." names. */
    static int checkZip(Path zip) throws IOException {
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                checkEntryName(entry.getName());
                in.transferTo(OutputStream.nullOutputStream());
                entries++;
            }
        }
        if (entries == 0) {
            throw new IOException("The backup " + zip + " has no entries (or is no ZIP file)");
        }
        return entries;
    }

    /** Protection against "Zip Slip": an entry must stay inside the folder it is unpacked into. */
    private static void checkEntryName(String name) throws IOException {
        boolean absoluteName = name.startsWith("/") || name.startsWith("\\") || (name.length() > 1 && name.charAt(1) == ':');
        if (name.isBlank() || absoluteName) {
            throw new IOException("Invalid entry name in backup: \"" + name + "\"");
        }
        for (String part : name.split("[/\\\\]")) {
            if (part.equals("..")) {
                throw new IOException("Invalid entry name in backup: \"" + name + "\"");
            }
        }
    }

    /** Unpacks {@code zip} into {@code target}, skipping the technical log; returns the number of files. */
    private static int unpack(Path zip, Path target) throws IOException {
        int files = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                checkEntryName(name);
                if (LOG_FILE_NAMES.contains(name)) {
                    continue;
                }
                Path out = target.resolve(name).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Invalid entry name in backup: \"" + name + "\"");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                files++;
            }
        }
        return files;
    }

    /** Deletes everything in {@code dir} except the paths in {@code keep} (and the folders leading to them). */
    private static void deleteContentExcept(Path dir, Set<Path> keep) throws IOException {
        for (Path child : list(dir)) {
            if (keep.contains(child)) {
                continue;
            }
            boolean leadsToKept = keep.stream().anyMatch(kept -> kept.startsWith(child));
            if (leadsToKept && Files.isDirectory(child)) {
                deleteContentExcept(child, keep);
            } else {
                deleteRecursively(child);
            }
        }
    }

    /** Moves the content of {@code source} into {@code target}, merging folders that exist in both. */
    private static void moveContentInto(Path source, Path target) throws IOException {
        for (Path child : list(source)) {
            Path destination = target.resolve(child.getFileName().toString());
            if (Files.isDirectory(child) && Files.isDirectory(destination)) {
                moveContentInto(child, destination);
            } else {
                Files.move(child, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // --- move ---

    /** Moves {@code workspaceDir} to {@code targetDir} - see the class Javadoc. */
    static Outcome moveWorkspace(Path workspaceDir, Path targetDir, Runnable switchToTarget, Consumer<String> log) {
        return moveWorkspace(workspaceDir, targetDir, switchToTarget, log, DEFAULT_COPIER);
    }

    /**
     * Like {@link #moveWorkspace(Path, Path, Runnable, Consumer)} with the given way to copy a file.
     *
     * @param switchToTarget makes the target the configured workspace - runs after the copy was
     *                       checked and before the old folder is deleted
     */
    static Outcome moveWorkspace(Path workspaceDir, Path targetDir, Runnable switchToTarget, Consumer<String> log,
                                 FileCopier copier) {
        Path from = absolute(workspaceDir);
        Path to = absolute(targetDir);
        log.accept("Moving workspace from " + from + " to " + to);
        MoveProblem problem = checkMoveTarget(from, to);
        if (problem != null) {
            log.accept("Workspace move rejected (" + problem + ") - the workspace stays in " + from);
            return new MoveFailed(from, to, problem, problem.name());
        }

        boolean targetExisted = Files.isDirectory(to);
        int files;
        try {
            files = copyTree(from, to, copier);
            checkCopy(from, to);
        } catch (IOException e) {
            log.accept("Workspace move failed while copying: " + e + " - the workspace stays in " + from);
            try {
                if (targetExisted) {
                    deleteContentExcept(to, Set.of());
                } else {
                    deleteRecursively(to);
                }
            } catch (IOException cleanupError) {
                log.accept("Could not remove the partial copy in " + to + ": " + cleanupError);
            }
            return new MoveFailed(from, to, null, e.toString());
        }

        switchToTarget.run();
        log.accept("Workspace copied to " + to + " (" + files + " files) - it is the workspace from now on");
        List<Path> leftOver = deleteRecursivelyBestEffort(from);
        boolean removed = !Files.exists(from);
        if (removed) {
            log.accept("Old workspace " + from + " deleted");
        } else {
            log.accept("The old workspace " + from + " could not be deleted completely - "
                    + leftOver.size() + " entries left, e.g. " + (leftOver.isEmpty() ? from : leftOver.get(0)));
        }
        return new Moved(from, to, files, removed);
    }

    /** Copies every folder and file of {@code source} into {@code target}; returns the number of files. */
    private static int copyTree(Path source, Path target, FileCopier copier) throws IOException {
        int files = 0;
        Files.createDirectories(target);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    copier.copy(path, destination);
                    files++;
                }
            }
        }
        return files;
    }

    /** Fails unless {@code copy} has the same files with the same sizes as {@code original}. */
    private static void checkCopy(Path original, Path copy) throws IOException {
        Map<String, Long> expected = fileSizes(original);
        Map<String, Long> actual = fileSizes(copy);
        if (!expected.equals(actual)) {
            throw new IOException("The copy differs from the workspace: " + expected.size() + " files expected, "
                    + actual.size() + " copied (or different sizes)");
        }
    }

    private static Map<String, Long> fileSizes(Path dir) throws IOException {
        Map<String, Long> sizes = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isRegularFile(path)) {
                    sizes.put(dir.relativize(path).toString(), Files.size(path));
                }
            }
        }
        return sizes;
    }

    // --- file helpers ---

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static boolean isEmptyDirectory(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static List<Path> list(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.toList();
        }
    }

    /** Deletes {@code path} with everything in it; nothing if it does not exist. */
    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    /** Deletes as much of {@code path} as possible; returns what could not be deleted. */
    private static List<Path> deleteRecursivelyBestEffort(Path path) {
        List<Path> leftOver = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    leftOver.add(p);
                }
            }
        } catch (IOException e) {
            leftOver.add(path);
        }
        return leftOver;
    }
}
