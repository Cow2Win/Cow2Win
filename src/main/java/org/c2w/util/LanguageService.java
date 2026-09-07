package org.c2w.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class LanguageService {
    private static final String LANGUAGE_RESOURCE_FOLDER = "/language/";
    private static final String DEFAULT_LANGUAGE_FILE = "english.txt";

    private static volatile Properties displayNames;
    private static volatile String loadedLanguageFile;

    private LanguageService() {
    }

    /**
     * Returns the display name for the given catalog id in the currently
     * configured language, or the id itself if no entry exists for it
     * (unknown id, or the language file could not be read) - so this method
     * always returns something usable, never null.
     */
    public static String displayName(String id) {
        ensureLoaded();
        return displayNames.getProperty(id, id);
    }

    /** Forces the language file to be reloaded on the next call to {@link #displayName}. */
    public static void resetCache() {
        synchronized (LanguageService.class) {
            displayNames = null;
            loadedLanguageFile = null;
        }
    }

    // --- private ---

    private static void ensureLoaded() {
        String activeFile = activeLanguageFile();
        if (displayNames != null && activeFile.equals(loadedLanguageFile)) {
            return;
        }
        synchronized (LanguageService.class) {
            if (displayNames == null || !activeFile.equals(loadedLanguageFile)) {
                displayNames = loadLanguageFile(activeFile);
                loadedLanguageFile = activeFile;
            }
        }
    }

    private static String activeLanguageFile() {
        String configured = Config.getLanguage();
        return (configured == null || configured.isBlank()) ? DEFAULT_LANGUAGE_FILE : configured;
    }

    private static Properties loadLanguageFile(String fileName) {
        Properties properties = new Properties();
        String resourcePath = LANGUAGE_RESOURCE_FOLDER + fileName;
        try (InputStream in = LanguageService.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.err.println("Language file not found on classpath: " + resourcePath);
                return properties;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            System.err.println("Could not read language file " + resourcePath + ": " + e.getMessage());
        }
        return properties;
    }

}
