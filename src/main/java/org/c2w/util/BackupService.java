package org.c2w.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a daily and a weekly backup (ZIP) of the workspace folder. Checked
 * once at program start (see {@code C2WApp#main}) - never during normal
 * operation, and never in a background thread, since the workspace folder
 * only holds small JSON/properties files.
 *
 * <p>Backup files always use the same fixed names ({@link #DAILY_BACKUP_FILE_NAME},
 * {@link #WEEKLY_BACKUP_FILE_NAME}), so a new backup simply overwrites the
 * previous one under that name and no old-file cleanup is ever needed.
 *
 * <p>Whether a backup is "due" is decided purely from that backup file's own
 * lastModified timestamp (no content/hash comparison against the workspace):
 * the daily backup is (re)created when it wasn't last written today; the
 * weekly backup is (re)created when it wasn't last written in the current
 * ISO-8601 week (Monday-Sunday, see {@link IsoFields#WEEK_OF_WEEK_BASED_YEAR}).
 *
 * <p>Any failure (missing workspace folder, unwritable backup directory,
 * ...) is only logged to stderr, the same way {@link Config#load()}/
 * {@link Config#save()} handle I/O problems elsewhere in this class - a
 * failed backup must never prevent the application from starting.
 */
public class BackupService {

    public static final String DAILY_BACKUP_FILE_NAME = "workspace-daily.zip";
    public static final String WEEKLY_BACKUP_FILE_NAME = "workspace-weekly.zip";

    private static final Path WORKSPACE_DIR = Config.DIR;

    private BackupService() {
    }

    /** Checks/creates both backups for the real "workspace" folder and the configured backup directory (see {@link Config#getBackupDirPath()}). */
    public static void checkAndCreateBackups() {
        checkAndCreateBackups(WORKSPACE_DIR, Config.getBackupDirPath());
    }

    /** Package-private overload taking explicit paths so tests don't have to touch the real "workspace" folder or {@link Config}. */
    static void checkAndCreateBackups(Path workspaceDir, Path backupDir) {
        if (!Files.isDirectory(workspaceDir)) {
            // Nothing to back up yet - e.g. the very first start, before the
            // initial setup has created the workspace folder (see C2WApp#runInitialSetup).
            return;
        }
        checkAndCreateBackup(workspaceDir, backupDir.resolve(DAILY_BACKUP_FILE_NAME), BackupService::isDueDaily);
        checkAndCreateBackup(workspaceDir, backupDir.resolve(WEEKLY_BACKUP_FILE_NAME), BackupService::isDueWeekly);
    }

    private static void checkAndCreateBackup(Path workspaceDir, Path backupFile, Predicate<FileTime> dueCheck) {
        try {
            boolean due = !Files.isRegularFile(backupFile) || dueCheck.test(Files.getLastModifiedTime(backupFile));
            if (due) {
                createBackup(workspaceDir, backupFile);
                Logger.log("Backup created: " + backupFile);
            }
        } catch (IOException e) {
            Logger.logException("Could not check/create backup " + backupFile, e);
        }
    }

    private static boolean isDueDaily(FileTime lastModified) {
        return !toLocalDate(lastModified).isEqual(LocalDate.now());
    }

    private static boolean isDueWeekly(FileTime lastModified) {
        LocalDate last = toLocalDate(lastModified);
        LocalDate today = LocalDate.now();
        return last.get(IsoFields.WEEK_BASED_YEAR) != today.get(IsoFields.WEEK_BASED_YEAR)
                || last.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private static LocalDate toLocalDate(FileTime fileTime) {
        return fileTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Zips every regular file under {@code workspaceDir} into {@code backupFile}
     * (entry names stay relative to {@code workspaceDir}), creating the backup
     * directory as needed. Files that already live under the backup file's own
     * parent directory are skipped, so a backup directory nested inside the
     * workspace folder doesn't try to include itself. Written via a temp file
     * plus a move into place, so an interrupted run never leaves a half-written
     * ZIP behind under the fixed backup name.
     */
    private static void createBackup(Path workspaceDir, Path backupFile) throws IOException {
        Path backupDir = backupFile.toAbsolutePath().normalize().getParent();
        if (backupDir != null) {
            Files.createDirectories(backupDir);
        }
        Path tempFile = Files.createTempFile(backupDir, "backup-", ".tmp");
        try {
            try (OutputStream fileOut = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING);
                 ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
                try (Stream<Path> paths = Files.walk(workspaceDir)) {
                    Iterable<Path> files = paths.filter(Files::isRegularFile)::iterator;
                    for (Path file : files) {
                        Path absoluteFile = file.toAbsolutePath().normalize();
                        if (backupDir != null && absoluteFile.startsWith(backupDir)) {
                            continue;
                        }
                        String entryName = workspaceDir.relativize(file).toString().replace('\\', '/');
                        zipOut.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zipOut);
                        zipOut.closeEntry();
                    }
                }
            }
            Files.move(tempFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
