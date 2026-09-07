package org.c2w.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Properties;

public class Config {
    public static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.GERMANY);

    private static final Path CONFIG_FILE_PATH = Paths.get("workspace", "config.properties");
    private static final String KEY_LAST_GUILD_PATH = "lastGuildPath";
    private static final String KEY_LANGUAGE_FILE = "language";
    private static final String KEY_LAST_LINEUP_PATH = "lastLineUpPath";
    private static final String KEY_DEFAULT_ALGORITHM = "defaultAlgorithm";

    private static final Properties properties = new Properties();

    public static boolean editedGuild = false;
    public static boolean editedLineup = false;

    private Config() {
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
                System.err.println("Could not read " + CONFIG_FILE_PATH + ": " + e.getMessage());
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
            System.err.println("Could not write " + CONFIG_FILE_PATH + ": " + e.getMessage());
        }
    }

    // --- lastGuildPath ---

    public static String getLastGuildPath() {
        return properties.getProperty(KEY_LAST_GUILD_PATH, "");
    }

    public static void setLastGuildPath(String lastGuildPath) {
        properties.setProperty(KEY_LAST_GUILD_PATH, lastGuildPath == null ? "" : lastGuildPath);
    }

    // --- language ---

    public static String getLanguage() {
        return properties.getProperty(KEY_LANGUAGE_FILE, "");
    }

    public static void setLanguage(String languageFile) {
        properties.setProperty(KEY_LANGUAGE_FILE, languageFile == null ? "" : languageFile);
    }

    // --- lastLineUpPath ---

    public static String getLastLineUpPath() {
        return properties.getProperty(KEY_LAST_LINEUP_PATH, "");
    }

    public static void setLastLineUpPath(String lastLineUpPath) {
        properties.setProperty(KEY_LAST_LINEUP_PATH, lastLineUpPath == null ? "" : lastLineUpPath);
    }

    // --- defaultAlgorithm ---

    public static String getDefaultAlgorithm() {
        return properties.getProperty(KEY_DEFAULT_ALGORITHM, "");
    }

    public static void setDefaultAlgorithm(String defaultAlgorithm) {
        properties.setProperty(KEY_DEFAULT_ALGORITHM, defaultAlgorithm == null ? "" : defaultAlgorithm);
    }
}
