package org.c2w.infra;

import org.c2w.data.model.FortificationType;
import org.c2w.domain.CowScoreBonuses;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * The application-wide settings in {@code config.properties} (outside the workspace): language,
 * workspace and backup folders, default algorithms, the data status's stale check, the adjustable
 * CowScore bonus percentages (see {@link #getCowScoreBonuses()}), scheduled workspace operations and
 * the recently used guild/lineup. Every setter logs an actual change; {@link #save()} writes the file.
 */
public final class Config {

    /**
     * {@code <user.home>/.cow2Win} - umbrella folder holding both the
     * default workspace folder ({@link #DEFAULT_WORKSPACE_DIR}) and the
     * default backup folder ({@link #DEFAULT_BACKUP_DIR}). Anchored to the
     * user's home directory (rather than the app's working directory) so
     * every install/update on this machine automatically finds the same
     * data, with nothing to recreate or copy by hand.
     */
    private static final Path ROOT = Paths.get(System.getProperty("user.home"), ".cow2Win");

    /**
     * {@code <user.home>/.cow2Win/workspace} - guilds, lineups and {@link
     * Logger}'s log file, unless {@link #KEY_WORKSPACE_PATH} points
     * elsewhere (see {@link #getWorkspaceDir()}). Also used by {@link
     * BackupService} (what gets backed up) and, as a fallback, {@code
     * org.c2w.gui.ActionBar}.
     */
    private static final Path DEFAULT_WORKSPACE_DIR = ROOT.resolve("workspace");

    /**
     * This class's own config.properties - deliberately NOT under {@link
     * #getWorkspaceDir()}: {@link #KEY_WORKSPACE_PATH} is exactly what
     * points at the workspace to use, and a config file living inside the
     * workspace it names could never point at a *different* one (there
     * would be nowhere to read {@link #KEY_WORKSPACE_PATH} from before
     * knowing which workspace's config file to read it from). Living at
     * the app level instead is what makes several Cow2Win
     * installations/copies on the same machine able to use different
     * workspace folders.
     *
     * <p>Resolved the same way as {@link JsonSupport#resolveDataFile} -
     * the packaged app's on-disk "resources" folder (shipped right next to
     * {@code Cow2Win.exe} via jpackage's {@code appContentPaths}) when
     * running the installed app-image, falling back to the project root
     * when run from the IDE/source checkout (see {@link
     * #resolveConfigFilePath()}).
     */
    private static final Path CONFIG_FILE_PATH = resolveConfigFilePath();

    private static final String KEY_LAST_GUILD_PATH = "lastGuildPath";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_LAST_LINEUP_PATH = "lastLineUpPath";
    /** Legacy single default algorithm (from before the hero/titan split) for both sides - only read as a fallback, see {@link #getDefaultHeroAlgorithm()}. */
    private static final String KEY_DEFAULT_ALGORITHM = "defaultAlgorithm";
    private static final String KEY_DEFAULT_HERO_ALGORITHM = "defaultHeroAlgorithm";
    private static final String KEY_DEFAULT_TITAN_ALGORITHM = "defaultTitanAlgorithm";
    private static final String KEY_BACKUP_DIR = "backupDir";
    private static final String KEY_WORKSPACE_PATH = "workspacePath";
    /** Folder the last battle logs were imported from (Weltenschlacht journal). */
    private static final String KEY_LAST_JOURNAL_IMPORT_DIR = "lastJournalImportDir";
    /** Fortification type (HERO/TITAN) selected last in the context bar, restored on the next start. */
    private static final String KEY_LAST_FORTIFICATION_TYPE = "lastFortificationType";
    /** Whether members are marked as outdated by the age of their teams (data status, "outdated after X days"). */
    private static final String KEY_STALE_CHECK_ENABLED = "staleCheckEnabled";
    /** Days after which teams count as outdated with the stale check on. */
    private static final String KEY_STALE_AFTER_DAYS = "staleAfterDays";
    /** Whether the user has operated the stale check in the settings - then it is never switched on automatically. */
    private static final String KEY_STALE_CHECK_USER_SET = "staleCheckUserSet";
    /** Backup ZIP the workspace is restored from on the next start (see {@link WorkspaceMaintenance}). */
    private static final String KEY_PENDING_RESTORE_ZIP = "pendingRestoreZip";
    /** Folder the workspace is moved to on the next start (see {@link WorkspaceMaintenance}). */
    private static final String KEY_PENDING_WORKSPACE_MOVE = "pendingWorkspaceMove";
    /** The adjustable CowScore bonus percentages (see {@link CowScoreBonuses}) - one key per value. */
    private static final String KEY_COW_SCORE_ROLE = "cowScore.roleMatchPercent";
    private static final String KEY_COW_SCORE_ELEMENT = "cowScore.elementMatchPercent";
    private static final String KEY_COW_SCORE_RELATION = "cowScore.relationPercent";
    private static final String KEY_COW_SCORE_PET = "cowScore.petMarkedPercent";
    private static final String KEY_COW_SCORE_WAR_FLAG = "cowScore.warFlagMarkedPercent";
    private static final String KEY_COW_SCORE_COMBO = "cowScore.comboPercent";
    private static final String KEY_COW_SCORE_TOTEM = "cowScore.totemPercent";
    /** When the CowScore bonuses were last actually changed (ISO instant) - lineups saved before need recalculating. */
    private static final String KEY_COW_SCORE_BONUSES_CHANGED_AT = "cowScore.bonusesChangedAt";
    /** Default and valid range of {@link #KEY_STALE_AFTER_DAYS}. */
    public static final int DEFAULT_STALE_AFTER_DAYS = 30;
    public static final int MIN_STALE_AFTER_DAYS = 1;
    public static final int MAX_STALE_AFTER_DAYS = 365;
    /** Default backup directory: a "backup" folder next to {@link #DEFAULT_WORKSPACE_DIR}, under the same {@link #ROOT} umbrella. */
    private static final String DEFAULT_BACKUP_DIR = ROOT.resolve("backup").toString();

    private static final Properties properties = new Properties();

    private Config() {
    }

    /**
     * Where {@link #CONFIG_FILE_PATH} lives: the packaged app-image's
     * on-disk {@code resources} folder (a sibling of {@code Cow2Win.exe},
     * present whenever running as the installed app rather than from
     * source - see {@code JsonSupport}'s class Javadoc) if that folder
     * exists, otherwise the current working directory, which is the
     * project root when run from the IDE/source checkout.
     */
    private static Path resolveConfigFilePath() {
        Path packagedResources = Paths.get("resources");
        Path base = Files.isDirectory(packagedResources) ? packagedResources : Paths.get("");
        return base.resolve("config.properties");
    }

    public static boolean exists() {
        return Files.isRegularFile(CONFIG_FILE_PATH);
    }

    public static void load() {
        properties.clear();
        if (Files.isRegularFile(CONFIG_FILE_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE_PATH)) {
                properties.load(in);
            } catch (IOException e) {
                Logger.logException("Could not read " + CONFIG_FILE_PATH, e);
            }
        }
    }

    public static void save() {
        try {
            if (CONFIG_FILE_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_FILE_PATH.getParent());
            }
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE_PATH)) {
                properties.store(out, "Cow2Win - recently used settings (auto-generated)");
            }
        } catch (IOException e) {
            Logger.logException("Could not write " + CONFIG_FILE_PATH, e);
        }
    }

    /**
     * Sets one property and, if this actually changes its previous value,
     * logs that via {@link Logger#log} (e.g. "Config changed: language =
     * \"deutsch\" (previously \"english\")") - this is how every
     * {@code setXxx} method below reports a "technical change" to the log
     * file, without each of them having to do it individually.
     * Never logs anything during {@link #load()}, since that only replays
     * values already on disk rather than changing them.
     */
    private static void setProperty(String key, String value) {
        String previous = properties.getProperty(key, "");
        if (!previous.equals(value)) {
            String previousNote = previous.isEmpty() ? "" : " (previously \"" + previous + "\")";
            Logger.log("Config changed: " + key + " = \"" + value + "\"" + previousNote);
        }
        properties.setProperty(key, value);
    }

    // --- lastJournalImportDir ---

    /** Folder the last battle logs were imported from, {@code ""} if none yet. */
    public static String getLastJournalImportDir() {
        return properties.getProperty(KEY_LAST_JOURNAL_IMPORT_DIR, "");
    }

    public static void setLastJournalImportDir(String lastJournalImportDir) {
        setProperty(KEY_LAST_JOURNAL_IMPORT_DIR, lastJournalImportDir == null ? "" : lastJournalImportDir);
    }

    // --- lastFortificationType ---

    /** The fortification type selected last, {@link FortificationType#HERO} if none (or an unknown value) is stored. */
    public static FortificationType getLastFortificationType() {
        String value = properties.getProperty(KEY_LAST_FORTIFICATION_TYPE, "");
        for (FortificationType type : FortificationType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return FortificationType.HERO;
    }

    public static void setLastFortificationType(FortificationType fortificationType) {
        setProperty(KEY_LAST_FORTIFICATION_TYPE, fortificationType == null ? "" : fortificationType.name());
    }

    // --- lastGuildPath ---

    public static String getLastGuildPath() {
        return properties.getProperty(KEY_LAST_GUILD_PATH, "");
    }

    public static void setLastGuildPath(String lastGuildPath) {
        setProperty(KEY_LAST_GUILD_PATH, lastGuildPath == null ? "" : lastGuildPath);
    }

    // --- language ---

    /**
     * Raw configured language, e.g. {@code "deutsch"} - the name of the
     * subdirectory under {@code resources/language} (see {@link
     * LanguageService#availableLanguages()}), not a file name. May also
     * still hold a legacy value like {@code "deutsch.txt"} for a
     * config saved by an older Cow2Win version; {@link
     * LanguageService#configuredLanguage()} is what normalizes that,
     * callers that just need "the language to use" should go through it
     * rather than this raw getter.
     */
    public static String getLanguage() {
        return properties.getProperty(KEY_LANGUAGE, "");
    }

    public static void setLanguage(String language) {
        setProperty(KEY_LANGUAGE, language == null ? "" : language);
    }

    // --- lastLineUpPath ---

    public static String getLastLineUpPath() {
        return properties.getProperty(KEY_LAST_LINEUP_PATH, "");
    }

    public static void setLastLineUpPath(String lastLineUpPath) {
        setProperty(KEY_LAST_LINEUP_PATH, lastLineUpPath == null ? "" : lastLineUpPath);
    }

    // --- defaultHeroAlgorithm / defaultTitanAlgorithm ---

    /**
     * Display name of the default HERO lineup algorithm (see {@code
     * LineupAlgorithms#HERO}), or {@code ""} if none is configured. Falls back
     * to the legacy single {@code defaultAlgorithm} value saved before the
     * algorithms were split into hero and titan variants, since
     * every strategy kept its display name in that split.
     */
    public static String getDefaultHeroAlgorithm() {
        return properties.getProperty(KEY_DEFAULT_HERO_ALGORITHM, properties.getProperty(KEY_DEFAULT_ALGORITHM, ""));
    }

    public static void setDefaultHeroAlgorithm(String defaultHeroAlgorithm) {
        setProperty(KEY_DEFAULT_HERO_ALGORITHM, defaultHeroAlgorithm == null ? "" : defaultHeroAlgorithm);
    }

    /** Display name of the default TITAN lineup algorithm - same fallback as {@link #getDefaultHeroAlgorithm()}. */
    public static String getDefaultTitanAlgorithm() {
        return properties.getProperty(KEY_DEFAULT_TITAN_ALGORITHM, properties.getProperty(KEY_DEFAULT_ALGORITHM, ""));
    }

    public static void setDefaultTitanAlgorithm(String defaultTitanAlgorithm) {
        setProperty(KEY_DEFAULT_TITAN_ALGORITHM, defaultTitanAlgorithm == null ? "" : defaultTitanAlgorithm);
    }

    // --- backupDir ---

    /** Configured backup directory, or {@link #DEFAULT_BACKUP_DIR} if none was ever saved. See {@link #getBackupDirPath()} for the resolved {@link Path}. */
    public static String getBackupDir() {
        return properties.getProperty(KEY_BACKUP_DIR, DEFAULT_BACKUP_DIR);
    }

    public static void setBackupDir(String backupDir) {
        setProperty(KEY_BACKUP_DIR, (backupDir == null || backupDir.isBlank()) ? DEFAULT_BACKUP_DIR : backupDir);
    }

    /** {@link #getBackupDir()} as a {@link Path}, for callers (e.g. {@link BackupService}) that need to use it directly. */
    public static Path getBackupDirPath() {
        return Paths.get(getBackupDir());
    }

    // --- workspacePath ---

    /**
     * Configured workspace directory (guilds, lineups, {@link Logger}'s log
     * file), or {@link #DEFAULT_WORKSPACE_DIR} if none was ever saved. See
     * {@link #getWorkspaceDir()} for the resolved {@link Path} - callers
     * throughout the app (e.g. {@code C2WApp}, {@code ActionBar}, {@link
     * BackupService}, {@link Logger}) should go through that rather than
     * this raw getter, so a configured workspace actually takes effect
     * everywhere.
     */
    public static String getWorkspacePath() {
        return properties.getProperty(KEY_WORKSPACE_PATH, DEFAULT_WORKSPACE_DIR.toString());
    }

    public static void setWorkspacePath(String workspacePath) {
        setProperty(KEY_WORKSPACE_PATH, (workspacePath == null || workspacePath.isBlank())
                ? DEFAULT_WORKSPACE_DIR.toString() : workspacePath);
    }

    /**
     * {@link #getWorkspacePath()} as a {@link Path}. It reflects
     * {@link #KEY_WORKSPACE_PATH} and can therefore only be trusted after {@link #load()} has run.
     */
    public static Path getWorkspaceDir() {
        return Paths.get(getWorkspacePath());
    }

    // --- CowScore bonuses ---

    /**
     * The CowScore bonus percentages: a missing or invalid value is its default, every value is
     * limited to its range, and a buff smaller than a small bonus of its side is raised to it
     * (logged, the file is not written) - see {@link CowScoreBonuses#normalized()}.
     */
    public static CowScoreBonuses getCowScoreBonuses() {
        CowScoreBonuses d = CowScoreBonuses.DEFAULTS;
        CowScoreBonuses clamped = new CowScoreBonuses(
                percent(KEY_COW_SCORE_ROLE, d.rolePercent()), percent(KEY_COW_SCORE_ELEMENT, d.elementPercent()),
                percent(KEY_COW_SCORE_RELATION, d.relationPercent()), percent(KEY_COW_SCORE_PET, d.petPercent()),
                percent(KEY_COW_SCORE_WAR_FLAG, d.warFlagPercent()), percent(KEY_COW_SCORE_COMBO, d.comboPercent()),
                percent(KEY_COW_SCORE_TOTEM, d.totemPercent())).clamped();
        CowScoreBonuses normalized = clamped.normalized();
        if (!normalized.equals(clamped)) {
            Logger.log("Config: CowScore buff raised to the biggest bonus of its side (role "
                    + clamped.rolePercent() + " -> " + normalized.rolePercent() + ", element "
                    + clamped.elementPercent() + " -> " + normalized.elementPercent() + ") - in memory only");
        }
        return normalized;
    }

    /**
     * Stores {@code bonuses} (normalized, see {@link CowScoreBonuses#normalized()}); if at least
     * one value actually changes, also the time of the change (see {@link #getCowScoreBonusesChangedAt()}).
     * Does not save; the caller does.
     *
     * @return true if a value changed
     */
    public static boolean setCowScoreBonuses(CowScoreBonuses bonuses) {
        CowScoreBonuses normalized = bonuses.normalized();
        boolean changed = !normalized.equals(getCowScoreBonuses());
        setProperty(KEY_COW_SCORE_ROLE, Double.toString(normalized.rolePercent()));
        setProperty(KEY_COW_SCORE_ELEMENT, Double.toString(normalized.elementPercent()));
        setProperty(KEY_COW_SCORE_RELATION, Double.toString(normalized.relationPercent()));
        setProperty(KEY_COW_SCORE_PET, Double.toString(normalized.petPercent()));
        setProperty(KEY_COW_SCORE_WAR_FLAG, Double.toString(normalized.warFlagPercent()));
        setProperty(KEY_COW_SCORE_COMBO, Double.toString(normalized.comboPercent()));
        setProperty(KEY_COW_SCORE_TOTEM, Double.toString(normalized.totemPercent()));
        if (changed) {
            setProperty(KEY_COW_SCORE_BONUSES_CHANGED_AT, Instant.now().toString());
        }
        return changed;
    }

    /** When the CowScore bonuses were last actually changed, null if never (or unreadable). */
    public static Instant getCowScoreBonusesChangedAt() {
        String value = properties.getProperty(KEY_COW_SCORE_BONUSES_CHANGED_AT, "").trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The double stored under {@code key}, {@code fallback} if it is missing or not a number. */
    private static double percent(String key, double fallback) {
        try {
            double value = Double.parseDouble(properties.getProperty(key, "").trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // --- staleCheckEnabled / staleAfterDays / staleCheckUserSet ---

    /** Whether teams are marked as outdated by age; off unless switched on (by the user or after the first journal import). */
    public static boolean isStaleCheckEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_STALE_CHECK_ENABLED, "false"));
    }

    public static void setStaleCheckEnabled(boolean enabled) {
        setProperty(KEY_STALE_CHECK_ENABLED, Boolean.toString(enabled));
    }

    /** Days after which teams count as outdated, {@link #DEFAULT_STALE_AFTER_DAYS} if none or an invalid value is stored; clamped to 1-365. */
    public static int getStaleAfterDays() {
        try {
            return clampStaleAfterDays(Integer.parseInt(properties.getProperty(KEY_STALE_AFTER_DAYS, "").trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_STALE_AFTER_DAYS;
        }
    }

    /** Stores {@code days}, clamped to {@link #MIN_STALE_AFTER_DAYS}-{@link #MAX_STALE_AFTER_DAYS}. */
    public static void setStaleAfterDays(int days) {
        setProperty(KEY_STALE_AFTER_DAYS, Integer.toString(clampStaleAfterDays(days)));
    }

    private static int clampStaleAfterDays(int days) {
        return Math.max(MIN_STALE_AFTER_DAYS, Math.min(MAX_STALE_AFTER_DAYS, days));
    }

    /** Whether the user has operated the stale check in the settings dialog. */
    public static boolean isStaleCheckUserSet() {
        return Boolean.parseBoolean(properties.getProperty(KEY_STALE_CHECK_USER_SET, "false"));
    }

    public static void setStaleCheckUserSet(boolean userSet) {
        setProperty(KEY_STALE_CHECK_USER_SET, Boolean.toString(userSet));
    }

    /**
     * Called after a successful journal import: switches the stale check on with
     * {@link #DEFAULT_STALE_AFTER_DAYS} days - once, and only if the user has never operated it
     * ({@link #isStaleCheckUserSet()}). Does not save; the caller does.
     *
     * @return true if this changed the settings
     */
    public static boolean enableStaleCheckAfterFirstImport() {
        if (isStaleCheckUserSet() || isStaleCheckEnabled()) {
            return false;
        }
        setStaleCheckEnabled(true);
        setStaleAfterDays(DEFAULT_STALE_AFTER_DAYS);
        return true;
    }

    // --- pendingRestoreZip / pendingWorkspaceMove ---

    /** Backup ZIP to restore the workspace from on the next start, {@code ""} if none. */
    public static String getPendingRestoreZip() {
        return properties.getProperty(KEY_PENDING_RESTORE_ZIP, "");
    }

    /**
     * Schedules restoring the workspace from {@code zip} on the next start - replaces a scheduled
     * move, at most one operation is pending. Does not save; the caller does.
     */
    public static void setPendingRestoreZip(String zip) {
        properties.remove(KEY_PENDING_WORKSPACE_MOVE);
        setProperty(KEY_PENDING_RESTORE_ZIP, zip);
    }

    /** Folder to move the workspace to on the next start, {@code ""} if none. */
    public static String getPendingWorkspaceMove() {
        return properties.getProperty(KEY_PENDING_WORKSPACE_MOVE, "");
    }

    /**
     * Schedules moving the workspace to {@code target} on the next start - replaces a scheduled
     * restore, at most one operation is pending. Does not save; the caller does.
     */
    public static void setPendingWorkspaceMove(String target) {
        properties.remove(KEY_PENDING_RESTORE_ZIP);
        setProperty(KEY_PENDING_WORKSPACE_MOVE, target);
    }

    /** Removes both scheduled operations. Does not save; the caller does. */
    public static void clearPendingOperations() {
        properties.remove(KEY_PENDING_RESTORE_ZIP);
        properties.remove(KEY_PENDING_WORKSPACE_MOVE);
    }

    /** A copy of all properties - for tests that change settings in memory and restore them afterwards. */
    static Properties snapshot() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    /** Replaces all properties by {@code snapshot} (see {@link #snapshot()}); never saves. */
    static void restore(Properties snapshot) {
        properties.clear();
        properties.putAll(snapshot);
    }
}
