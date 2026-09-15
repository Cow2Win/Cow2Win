package org.c2w.util;

import org.c2w.C2WApp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class LanguageService {
    private static final String LANGUAGE_RESOURCE_FOLDER = "/language/";
    private static final String DEFAULT_LANGUAGE_FILE = "english.txt";

    /**
     * Display name -> file name of the properties file under
     * resources/language, in the order they should be offered to the user.
     * Shared by every UI spot that lets the user pick a language (see
     * InitialSetupDialog and SettingsDialog) so the list only needs updating
     * in one place when a language is added or removed.
     */
    public static final String[][] AVAILABLE_LANGUAGES = {
            {"Deutsch", "deutsch.txt"},
            {"English", "english.txt"},
            {"Français", "francais.txt"}
    };

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


    public static String displayTitle(String id){
        ensureLoaded();
        return C2WApp.BASE_TITLE + " - " + displayNames.getProperty(id, id);
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
                Logger.log("Language file not found on classpath: " + resourcePath);
                return properties;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            Logger.logException("Could not read language file " + resourcePath, e);
        }
        return properties;
    }

}
