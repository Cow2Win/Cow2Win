package org.c2w.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses {@link BackupService}'s package-private
 * {@code checkAndCreateBackups(Path, Path)} overload so these tests work
 * against a {@code @TempDir} instead of the real "workspace"/"backup"
 * folders (see that method's javadoc).
 */
class BackupServiceTest {

    @Test
    void doesNotCreateBackupsWhenWorkspaceFolderIsMissing(@TempDir Path tempDir) {
        Path workspaceDir = tempDir.resolve("workspace"); // deliberately not created
        Path backupDir = tempDir.resolve("backup");

        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        assertFalse(Files.exists(backupDir));
    }

    @Test
    void createsBothBackupsOnFirstRun(@TempDir Path tempDir) throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Files.createDirectories(workspaceDir.resolve("Demo"));
        Files.writeString(workspaceDir.resolve("config.properties"), "language=english.txt");
        Files.writeString(workspaceDir.resolve("Demo").resolve("guild.json"), "{}");
        Path backupDir = tempDir.resolve("backup");

        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        Path dailyBackup = backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME);
        Path weeklyBackup = backupDir.resolve(BackupService.WEEKLY_BACKUP_FILE_NAME);
        assertTrue(Files.isRegularFile(dailyBackup));
        assertTrue(Files.isRegularFile(weeklyBackup));
        assertEquals(zipEntryNames(dailyBackup), zipEntryNames(weeklyBackup));
        assertTrue(zipEntryNames(dailyBackup).contains("config.properties"));
        assertTrue(zipEntryNames(dailyBackup).contains("Demo/guild.json"));
    }

    @Test
    void doesNotRecreateDailyBackupOnTheSameDay(@TempDir Path tempDir) throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Files.createDirectories(workspaceDir);
        Files.writeString(workspaceDir.resolve("config.properties"), "language=english.txt");
        Path backupDir = tempDir.resolve("backup");
        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        // Workspace changes, but the daily backup was already taken today -
        // a second check on the same day must leave it untouched.
        Files.writeString(workspaceDir.resolve("config.properties"), "language=deutsch.txt");
        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        Path dailyBackup = backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME);
        assertTrue(zipEntryContent(dailyBackup, "config.properties").contains("english.txt"));
    }

    @Test
    void recreatesDailyBackupOnceItIsFromAPreviousDay(@TempDir Path tempDir) throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Files.createDirectories(workspaceDir);
        Files.writeString(workspaceDir.resolve("config.properties"), "language=english.txt");
        Path backupDir = tempDir.resolve("backup");
        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        Path dailyBackup = backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME);
        setLastModifiedDate(dailyBackup, LocalDate.now().minusDays(1));
        Files.writeString(workspaceDir.resolve("config.properties"), "language=deutsch.txt");

        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        assertTrue(zipEntryContent(dailyBackup, "config.properties").contains("deutsch.txt"));
    }

    @Test
    void recreatesWeeklyBackupOnceItIsFromAPreviousIsoWeek(@TempDir Path tempDir) throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Files.createDirectories(workspaceDir);
        Files.writeString(workspaceDir.resolve("config.properties"), "language=english.txt");
        Path backupDir = tempDir.resolve("backup");
        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        Path weeklyBackup = backupDir.resolve(BackupService.WEEKLY_BACKUP_FILE_NAME);
        setLastModifiedDate(weeklyBackup, LocalDate.now().minusWeeks(1));
        Files.writeString(workspaceDir.resolve("config.properties"), "language=francais.txt");

        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        assertTrue(zipEntryContent(weeklyBackup, "config.properties").contains("francais.txt"));
    }

    @Test
    void skipsFilesInsideABackupDirectoryNestedInTheWorkspace(@TempDir Path tempDir) throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path backupDir = workspaceDir.resolve("backup"); // nested inside workspace, on purpose
        Files.createDirectories(workspaceDir);
        Files.writeString(workspaceDir.resolve("config.properties"), "language=english.txt");

        BackupService.checkAndCreateBackups(workspaceDir, backupDir);

        Path dailyBackup = backupDir.resolve(BackupService.DAILY_BACKUP_FILE_NAME);
        assertTrue(zipEntryNames(dailyBackup).contains("config.properties"));
        assertFalse(zipEntryNames(dailyBackup).stream().anyMatch(name -> name.startsWith("backup/")));
    }

    private static void setLastModifiedDate(Path file, LocalDate date) throws IOException {
        FileTime fileTime = FileTime.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Files.setLastModifiedTime(file, fileTime);
    }

    private static java.util.Set<String> zipEntryNames(Path zipFile) throws IOException {
        java.util.Set<String> names = new java.util.HashSet<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

    private static String zipEntryContent(Path zipFile, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            return new String(zip.getInputStream(entry).readAllBytes());
        }
    }
}
