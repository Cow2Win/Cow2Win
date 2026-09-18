package org.c2w.util;

import org.c2w.C2WApp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class LanguageService {

    /**
     * Classpath folder (no leading/trailing slash) holding one subdirectory
     * per language, e.g. {@code language/deutsch/deutsch.properties} - see
     * {@link #availableLanguages()} and {@link #loadLanguageFile(String)}.
     * Restructured 2026-09-16 from a flat {@code language/deutsch.txt} layout
     * so that a language can also carry longer, non-properties content
     * later (HTML/XML help texts etc.) alongside its {@code .properties}
     * file, without cluttering a single shared folder.
     */
    private static final String LANGUAGE_RESOURCE_ROOT = "language";
    private static final String DEFAULT_LANGUAGE = "english";

    private static volatile Properties displayNames;
    private static volatile String loadedLanguage;

    private LanguageService() {
    }

    /**
     * Every language currently available under {@code resources/language},
     * i.e. one entry per subdirectory - discovered at runtime (works both
     * when running from an IDE/exploded {@code target/classes} folder and
     * from the packaged, shaded jar) rather than hardcoded, so adding a
     * language is just adding a new {@code <name>/<name>.properties} folder,
     * no Java change required. The directory name IS what every UI spot
     * that lets the user pick a language (see {@link org.c2w.gui.InitialSetupDialog},
     * {@link org.c2w.gui.SettingsDialog}) shows in its combo box - there is
     * no separate "nice" display name mapping anymore. Sorted
     * alphabetically (case-insensitive) for a stable, predictable order.
     * Never empty: falls back to a single {@link #DEFAULT_LANGUAGE} entry
     * if discovery finds nothing (e.g. an unexpected classpath layout).
     */
    public static List<String> availableLanguages() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            Enumeration<URL> roots = LanguageService.class.getClassLoader().getResources(LANGUAGE_RESOURCE_ROOT);
            while (roots.hasMoreElements()) {
                names.addAll(listLanguageDirectories(roots.nextElement()));
            }
        } catch (IOException e) {
            Logger.logException("Could not enumerate language directories under " + LANGUAGE_RESOURCE_ROOT, e);
        }
        if (names.isEmpty()) {
            Logger.log("No language directories found under " + LANGUAGE_RESOURCE_ROOT + " - falling back to " + DEFAULT_LANGUAGE);
            names.add(DEFAULT_LANGUAGE);
        }
        return new ArrayList<>(names);
    }

    /**
     * The language {@link #availableLanguages()} name that is actually
     * active right now: whatever {@link Config#getLanguage()} has saved, or
     * {@link #DEFAULT_LANGUAGE} if nothing/blank is configured yet (e.g.
     * first start before {@link C2WApp}'s initial setup runs). Also
     * transparently upgrades a language file name saved by a version of
     * Cow2Win from before the 2026-09-16 per-language-folder restructuring
     * (e.g. a stored {@code "deutsch.txt"}) to the new plain name
     * ({@code "deutsch"}), so an existing {@code workspace/config.properties}
     * keeps working without the user having to touch it.
     */
    public static String configuredLanguage() {
        String configured = Config.getLanguage();
        if (configured == null || configured.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        if (configured.endsWith(".txt")) {
            return configured.substring(0, configured.length() - ".txt".length());
        }
        return configured;
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
            loadedLanguage = null;
        }
    }

    // --- private ---

    private static void ensureLoaded() {
        String active = configuredLanguage();
        if (displayNames != null && active.equals(loadedLanguage)) {
            return;
        }
        synchronized (LanguageService.class) {
            if (displayNames == null || !active.equals(loadedLanguage)) {
                displayNames = loadLanguageFile(active);
                loadedLanguage = active;
            }
        }
    }

    /** Loads {@code language/<name>/<name>.properties} from the classpath. */
    private static Properties loadLanguageFile(String name) {
        Properties properties = new Properties();
        String resourcePath = "/" + LANGUAGE_RESOURCE_ROOT + "/" + name + "/" + name + ".properties";
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

    /** Dispatches to the file-system or jar listing below depending on how {@code root} was resolved on the classpath. */
    private static List<String> listLanguageDirectories(URL root) {
        try {
            switch (root.getProtocol()) {
                case "file":
                    return listLanguageDirectoriesFromFileSystem(root);
                case "jar":
                    return listLanguageDirectoriesFromJar(root);
                default:
                    Logger.log("Unsupported classpath URL protocol for language directory listing: " + root);
                    return List.of();
            }
        } catch (IOException | URISyntaxException e) {
            Logger.logException("Could not list language directories under " + root, e);
            return List.of();
        }
    }

    /** IDE/exploded-classes case: {@code root} points at a real directory on disk - list its immediate subdirectories. */
    private static List<String> listLanguageDirectoriesFromFileSystem(URL root) throws IOException, URISyntaxException {
        Path dir = Paths.get(root.toURI());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var entries = Files.list(dir)) {
            return entries.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Packaged case: {@code root} points inside the shaded application jar
     * (see the maven-shade-plugin execution in {@code pom.xml}) - list the
     * immediate subdirectories of the {@code language/} entry by scanning
     * every entry of that jar. Opens its own, non-cached {@link JarFile}
     * (via {@code setUseCaches(false)}) rather than the one
     * {@link JarURLConnection#getJarFile()} would otherwise return from the
     * JVM-wide jar cache, so closing it here can never invalidate a jar
     * handle some other, unrelated classpath lookup is still relying on.
     */
    private static List<String> listLanguageDirectoriesFromJar(URL root) throws IOException {
        URLConnection connection = root.openConnection();
        if (!(connection instanceof JarURLConnection jarConnection)) {
            return List.of();
        }
        jarConnection.setUseCaches(false);
        String prefix = jarConnection.getEntryName();
        if (prefix == null) {
            prefix = LANGUAGE_RESOURCE_ROOT;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        Set<String> names = new LinkedHashSet<>();
        try (JarFile jarFile = jarConnection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                if (!entryName.startsWith(prefix) || entryName.length() <= prefix.length()) {
                    continue;
                }
                String rest = entryName.substring(prefix.length());
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    names.add(rest.substring(0, slash));
                }
            }
        }
        return new ArrayList<>(names);
    }

}
