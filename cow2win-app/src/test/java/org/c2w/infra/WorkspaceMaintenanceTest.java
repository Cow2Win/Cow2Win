package org.c2w.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkspaceMaintenance}: restore from a backup and move of the workspace - with the
 * package-private overloads on {@code @TempDir} folders, never the real workspace or config file.
 */
class WorkspaceMaintenanceTest {

    @TempDir
    Path tempDir;

    private final List<String> log = new ArrayList<>();

    // --- restore ---

    @Test
    @DisplayName("Restore replaces the workspace exactly by the ZIP content - the technical log stays, guild.log is restored")
    void restoreReplacesWorkspace() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "current guild");
        write(workspace.resolve("Demo/guild.log"), "current guild log");
        write(workspace.resolve("Newer/guild.json"), "created after the backup");
        write(workspace.resolve("cow2win.log"), "current technical log");
        write(workspace.resolve("cow2win.log.1"), "rotated technical log");
        Path backupDir = tempDir.resolve("backup");
        Path zip = backupOf(Map.of(
                "Demo/guild.json", "backed up guild",
                "Demo/guild.log", "backed up guild log",
                "cow2win.log", "old technical log",
                "cow2win.log.1", "old rotated log"), backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME));

        WorkspaceMaintenance.Outcome outcome = WorkspaceMaintenance.restoreFromBackup(zip, workspace, backupDir, log::add);

        WorkspaceMaintenance.Restored restored = assertInstanceOf(WorkspaceMaintenance.Restored.class, outcome);
        assertEquals(2, restored.files());
        assertEquals(Set.of("Demo/guild.json", "Demo/guild.log", "cow2win.log", "cow2win.log.1"), files(workspace));
        assertEquals("backed up guild", Files.readString(workspace.resolve("Demo/guild.json")));
        assertEquals("backed up guild log", Files.readString(workspace.resolve("Demo/guild.log")));
        assertEquals("current technical log", Files.readString(workspace.resolve("cow2win.log")));
        assertEquals("rotated technical log", Files.readString(workspace.resolve("cow2win.log.1")));
        assertFalse(Files.exists(workspace.resolve("Newer")));
        // No temporary folder is left next to the workspace.
        assertEquals(Set.of("backup", ".cow2Win"), children(tempDir));
    }

    @Test
    @DisplayName("Restore first saves the current state as workspace-before-restore.zip")
    void restoreSavesBeforeRestoreZip() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "current guild");
        Path backupDir = tempDir.resolve("backup");
        Path zip = backupOf(Map.of("Demo/guild.json", "backed up guild"), backupDir.resolve(BackupService.WEEKLY_BACKUP_FILE_NAME));

        WorkspaceMaintenance.Restored restored = assertInstanceOf(WorkspaceMaintenance.Restored.class,
                WorkspaceMaintenance.restoreFromBackup(zip, workspace, backupDir, log::add));

        Path before = backupDir.resolve(WorkspaceMaintenance.BEFORE_RESTORE_FILE_NAME);
        assertEquals(before, restored.beforeRestoreZip());
        try (ZipFile zipFile = new ZipFile(before.toFile())) {
            assertEquals("current guild", new String(zipFile.getInputStream(zipFile.getEntry("Demo/guild.json")).readAllBytes()));
        }
    }

    @Test
    @DisplayName("A backup directory inside the workspace is not deleted by a restore")
    void restoreKeepsBackupDirInsideWorkspace() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "current guild");
        Path backupDir = workspace.resolve("backup");
        Path zip = backupOf(Map.of("Demo/guild.json", "backed up guild"), backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME));
        write(backupDir.resolve("note.txt"), "kept");

        assertInstanceOf(WorkspaceMaintenance.Restored.class,
                WorkspaceMaintenance.restoreFromBackup(zip, workspace, backupDir, log::add));

        assertTrue(Files.isRegularFile(zip));
        assertTrue(Files.isRegularFile(backupDir.resolve(WorkspaceMaintenance.BEFORE_RESTORE_FILE_NAME)));
        assertEquals("kept", Files.readString(backupDir.resolve("note.txt")));
        assertEquals("backed up guild", Files.readString(workspace.resolve("Demo/guild.json")));
    }

    @Test
    @DisplayName("A broken ZIP leaves the workspace unchanged")
    void brokenZipChangesNothing() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "current guild");
        Path backupDir = tempDir.resolve("backup");
        Path zip = write(backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME), "this is no zip file");

        assertInstanceOf(WorkspaceMaintenance.RestoreFailed.class,
                WorkspaceMaintenance.restoreFromBackup(zip, workspace, backupDir, log::add));

        assertEquals(Set.of("Demo/guild.json"), files(workspace));
        assertEquals("current guild", Files.readString(workspace.resolve("Demo/guild.json")));
        assertFalse(Files.exists(backupDir.resolve(WorkspaceMaintenance.BEFORE_RESTORE_FILE_NAME)));
    }

    @Test
    @DisplayName("An entry with \"..\" (Zip Slip) leaves the workspace unchanged and writes nothing outside")
    void zipSlipIsRejected() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "current guild");
        Path backupDir = tempDir.resolve("backup");
        Path zip = backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME);
        Files.createDirectories(backupDir);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("Demo/guild.json"));
            out.write("backed up guild".getBytes());
            out.putNextEntry(new ZipEntry("../evil.txt"));
            out.write("evil".getBytes());
        }

        assertInstanceOf(WorkspaceMaintenance.RestoreFailed.class,
                WorkspaceMaintenance.restoreFromBackup(zip, workspace, backupDir, log::add));

        assertEquals("current guild", Files.readString(workspace.resolve("Demo/guild.json")));
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    // --- move ---

    @Test
    @DisplayName("Move: every file is in the target afterwards, the old folder is gone, the switch ran in between")
    void moveCopiesEverythingAndDeletesOld() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "guild");
        write(workspace.resolve("Demo/guild.log"), "guild log");
        write(workspace.resolve("cow2win.log"), "technical log");
        Path target = Files.createDirectories(tempDir.resolve("elsewhere")).resolve(".cow2Win");
        AtomicBoolean switched = new AtomicBoolean();

        WorkspaceMaintenance.Outcome outcome = WorkspaceMaintenance.moveWorkspace(workspace, target,
                () -> switched.set(Files.isRegularFile(target.resolve("Demo/guild.json")) && Files.exists(workspace)), log::add);

        WorkspaceMaintenance.Moved moved = assertInstanceOf(WorkspaceMaintenance.Moved.class, outcome);
        assertEquals(3, moved.files());
        assertTrue(moved.oldWorkspaceRemoved());
        assertTrue(switched.get(), "switched after the copy, before the old folder was deleted");
        assertEquals(Set.of("Demo/guild.json", "Demo/guild.log", "cow2win.log"), files(target));
        assertEquals("guild", Files.readString(target.resolve("Demo/guild.json")));
        assertFalse(Files.exists(workspace));
    }

    @Test
    @DisplayName("Move is rejected for a non-empty target, a target in the workspace and a target holding the workspace")
    void moveRejectsBadTargets() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "guild");
        Path nonEmpty = tempDir.resolve("other/.cow2Win");
        write(nonEmpty.resolve("guild.json"), "another workspace");

        assertRejected(workspace, nonEmpty, WorkspaceMaintenance.MoveProblem.TARGET_NOT_EMPTY);
        assertRejected(workspace, workspace.resolve("inner/.cow2Win"), WorkspaceMaintenance.MoveProblem.INSIDE_WORKSPACE);
        assertRejected(workspace, tempDir, WorkspaceMaintenance.MoveProblem.CONTAINS_WORKSPACE);
        assertRejected(workspace, workspace, WorkspaceMaintenance.MoveProblem.SAME_AS_WORKSPACE);

        assertEquals(Set.of("Demo/guild.json"), files(workspace));
        assertEquals(Set.of("guild.json"), files(nonEmpty));
        assertFalse(Files.exists(workspace.resolve("inner")));
    }

    @Test
    @DisplayName("Move into an existing empty folder is allowed")
    void moveIntoEmptyFolder() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("Demo/guild.json"), "guild");
        Path target = Files.createDirectories(tempDir.resolve("empty/.cow2Win"));

        assertInstanceOf(WorkspaceMaintenance.Moved.class,
                WorkspaceMaintenance.moveWorkspace(workspace, target, () -> { }, log::add));
        assertEquals(Set.of("Demo/guild.json"), files(target));
    }

    @Test
    @DisplayName("A failing copy removes the partial copy and leaves the workspace unchanged")
    void failingCopyCleansUp() throws IOException {
        Path workspace = workspace();
        write(workspace.resolve("a.txt"), "a");
        write(workspace.resolve("b.txt"), "b");
        write(workspace.resolve("Demo/guild.json"), "guild");
        Path target = Files.createDirectories(tempDir.resolve("elsewhere")).resolve(".cow2Win");
        AtomicInteger copies = new AtomicInteger();
        AtomicBoolean switched = new AtomicBoolean();
        WorkspaceMaintenance.FileCopier failingOnSecond = (source, destination) -> {
            if (copies.incrementAndGet() == 2) {
                throw new IOException("simulated copy failure");
            }
            Files.copy(source, destination);
        };

        WorkspaceMaintenance.MoveFailed failed = assertInstanceOf(WorkspaceMaintenance.MoveFailed.class,
                WorkspaceMaintenance.moveWorkspace(workspace, target, () -> switched.set(true), log::add, failingOnSecond));

        assertNull(failed.problem());
        assertFalse(switched.get());
        assertFalse(Files.exists(target));
        assertEquals(Set.of("a.txt", "b.txt", "Demo/guild.json"), files(workspace));
    }

    // --- pending operation ---

    @Test
    @DisplayName("A scheduled restore runs exactly once and is removed from the config")
    void pendingOperationRunsOnce() throws IOException {
        Properties snapshot = Config.snapshot();
        Logger.holdEntries(); // keeps "Config changed" entries out of the real log
        try {
            Path workspace = workspace();
            write(workspace.resolve("Demo/guild.json"), "current guild");
            Path backupDir = tempDir.resolve("backup");
            Path zip = backupOf(Map.of("Demo/guild.json", "backed up guild"), backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME));
            Config.setWorkspacePath(workspace.toString());
            Config.setBackupDir(backupDir.toString());
            Config.setPendingWorkspaceMove(tempDir.resolve("elsewhere").toString());
            Config.setPendingRestoreZip(zip.toString());
            assertEquals("", Config.getPendingWorkspaceMove(), "a new schedule replaces the old one");
            AtomicInteger saves = new AtomicInteger();

            Optional<WorkspaceMaintenance.Outcome> first = WorkspaceMaintenance.runPendingOperation(status -> { }, saves::incrementAndGet);
            Optional<WorkspaceMaintenance.Outcome> second = WorkspaceMaintenance.runPendingOperation(status -> { }, saves::incrementAndGet);

            assertInstanceOf(WorkspaceMaintenance.Restored.class, first.orElseThrow());
            assertTrue(second.isEmpty());
            assertEquals("", Config.getPendingRestoreZip());
            assertEquals(1, saves.get());
            assertEquals("backed up guild", Files.readString(workspace.resolve("Demo/guild.json")));
        } finally {
            Logger.releaseHeldEntries();
            Config.restore(snapshot);
        }
    }

    // --- helpers ---

    private void assertRejected(Path workspace, Path target, WorkspaceMaintenance.MoveProblem expected) {
        AtomicBoolean switched = new AtomicBoolean();
        WorkspaceMaintenance.MoveFailed failed = assertInstanceOf(WorkspaceMaintenance.MoveFailed.class,
                WorkspaceMaintenance.moveWorkspace(workspace, target, () -> switched.set(true), log::add));
        assertEquals(expected, failed.problem(), target.toString());
        assertFalse(switched.get());
    }

    private Path workspace() throws IOException {
        return Files.createDirectories(tempDir.resolve(".cow2Win"));
    }

    /** Writes {@code files} (name -> content) into a fresh folder and zips it like {@link BackupService}. */
    private Path backupOf(Map<String, String> files, Path zip) throws IOException {
        Path source = Files.createTempDirectory(tempDir, "source");
        for (var file : files.entrySet()) {
            write(source.resolve(file.getKey()), file.getValue());
        }
        BackupService.createBackup(source, zip);
        deleteTree(source);
        return zip;
    }

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    /** The files below {@code dir}, as relative names with "/". */
    private static Set<String> files(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            Set<String> names = new TreeSet<>();
            paths.filter(Files::isRegularFile).forEach(p -> names.add(dir.relativize(p).toString().replace('\\', '/')));
            return names;
        }
    }

    private static Set<String> children(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            Set<String> names = new TreeSet<>();
            paths.forEach(p -> names.add(p.getFileName().toString()));
            return names;
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Collections.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
