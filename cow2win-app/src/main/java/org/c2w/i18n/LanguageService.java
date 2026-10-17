package org.c2w.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.c2w.infra.CatalogStore;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class LanguageService {

    /**
     * Classpath folder (no leading/trailing slash) holding one subdirectory
     * per language, e.g. {@code language/deutsch/deutsch.properties} - see
     * {@link #availableLanguages()} and {@link #loadLanguageFile(String)}.
     * One folder per language so that a language can also carry longer, non-properties content
     * later (HTML/XML help texts etc.) alongside its {@code .properties}
     * file, without cluttering a single shared folder.
     */
    private static final String LANGUAGE_RESOURCE_ROOT = "language";
    private static final String DEFAULT_LANGUAGE = "english";

    /**
     * The display names of every catalog entry (fortifications, heroes, titans, pets, war flags)
     * in the active game catalog ({@link CatalogStore}) -
     * {@code {"adam": {"deutsch": "Adam", "english": "Adam", ...}, ...}}, keyed by catalog id and
     * language folder name. Looked up before the language files.
     */
    private static final String CATALOG_NAMES_PATH = "names.json";

    /** Catalog id to language to name, read once from {@link #CATALOG_NAMES_PATH}; null until loaded. */
    private static volatile Map<String, Map<String, String>> catalogNames;

    private static volatile Properties displayNames;
    private static volatile String loadedLanguage;

    /** Language files read by {@link #textIn}, by language name. */
    private static final Map<String, Properties> languageFiles = new ConcurrentHashMap<>();

    private LanguageService() {
    }

    /**
     * Every language currently available under {@code resources/language},
     * i.e. one entry per subdirectory - discovered at runtime (works both
     * when running from an IDE/exploded {@code target/classes} folder and
     * from the packaged, shaded jar) rather than hardcoded, so adding a
     * language is just adding a new {@code <name>/<name>.properties} folder,
     * no Java change required. The directory name IS what every UI spot
     * that lets the user pick a language (see {@link org.c2w.gui.SettingsDialog})
     * shows in its combo box - there is
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
     * first start before {@code WorkspaceBootstrap}'s initial setup runs). Also
     * transparently upgrades a language file name saved by a version of
     * Cow2Win from before the per-language-folder layout
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

    /** The locale of the configured language - for dates, numbers and upper case. */
    public static Locale locale() {
        return switch (configuredLanguage()) {
            case "deutsch" -> Locale.GERMANY;
            case "francais" -> Locale.FRANCE;
            default -> Locale.UK;
        };
    }

    /**
     * Returns the text for {@code id} in the currently configured language - always something
     * usable, never null:
     * <ul>
     *     <li>a catalog id (see {@link #CATALOG_NAMES_PATH}): its name in that language, else its
     *     English name, else the id itself;</li>
     *     <li>any other key (UI texts): the entry of the language file, or the id itself if there
     *     is none (unknown key, or the language file could not be read).</li>
     * </ul>
     */
    public static String displayName(String id) {
        Map<String, String> names = catalogNames().get(id);
        if (names != null) {
            String name = names.getOrDefault(configuredLanguage(), names.get(DEFAULT_LANGUAGE));
            return name != null ? name : id;
        }
        ensureLoaded();
        return displayNames.getProperty(id, id);
    }

    /**
     * Like {@link #displayName(String)} but formats the resolved text as a
     * {@link MessageFormat} pattern with the given arguments (e.g. a text
     * {@code "Move {0} from {1} to {2}"}). Returns the raw pattern unchanged
     * when no arguments are given or when the pattern is not a valid
     * {@link MessageFormat} (so a malformed translation never throws). Note
     * that, per {@link MessageFormat}, a literal apostrophe inside a pattern
     * that carries arguments must be written doubled ({@code ''}) in the
     * language file.
     */
    public static String displayName(String id, Object... args) {
        String pattern = displayName(id);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }


    /** Forces the language files and the catalog names to be reloaded on next use. */
    public static void resetCache() {
        synchronized (LanguageService.class) {
            displayNames = null;
            loadedLanguage = null;
            catalogNames = null;
            languageFiles.clear();
        }
    }

    /**
     * The text for {@code id} in the given language - independent of the
     * configured one, e.g. to search all languages (see
     * {@link TotemTexts#fromGameName} and the battle log's {@code NameResolver}) - or
     * {@code null} if that language has no entry for it. A catalog id is looked up in the
     * catalog names first, without the English fallback of {@link #displayName(String)}: a
     * caller searching all languages must only see the names of each language. Each language
     * file is read once and then kept.
     */
    public static String textIn(String language, String id) {
        Map<String, String> names = catalogNames().get(id);
        if (names != null && names.containsKey(language)) {
            return names.get(language);
        }
        return languageFiles.computeIfAbsent(language, LanguageService::loadLanguageFile).getProperty(id);
    }

    // --- private ---

    /** The catalog names, read from the active catalog on first use; empty (logged) if they cannot be read. */
    private static Map<String, Map<String, String>> catalogNames() {
        Map<String, Map<String, String>> names = catalogNames;
        if (names == null) {
            synchronized (LanguageService.class) {
                names = catalogNames;
                if (names == null) {
                    names = loadCatalogNames();
                    catalogNames = names;
                }
            }
        }
        return names;
    }

    private static Map<String, Map<String, String>> loadCatalogNames() {
        try {
            Map<String, Map<String, String>> result = new HashMap<>();
            var root = JsonParser.parseString(CatalogStore.readText(CATALOG_NAMES_PATH)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                Map<String, String> byLanguage = new HashMap<>();
                for (Map.Entry<String, JsonElement> name : entry.getValue().getAsJsonObject().entrySet()) {
                    if (!name.getValue().isJsonNull() && !name.getValue().getAsString().isBlank()) {
                        byLanguage.put(name.getKey(), name.getValue().getAsString());
                    }
                }
                result.put(entry.getKey(), Map.copyOf(byLanguage));
            }
            return Map.copyOf(result);
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not read the catalog names (" + CATALOG_NAMES_PATH + ")", e);
            return Map.of();
        }
    }

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

    /**
     * Classpath path of a file in the folder of {@code language}, e.g.
     * {@code /language/deutsch/battleLogVocabulary.json} - the one place that
     * knows the per-language folder layout.
     */
    public static String languageResourcePath(String language, String fileName) {
        return "/" + LANGUAGE_RESOURCE_ROOT + "/" + language + "/" + fileName;
    }

    /**
     * Opens a file in the folder of {@code language} (see
     * {@link #languageResourcePath}), or returns {@code null} if that language
     * has no such file. The caller closes the stream.
     */
    public static InputStream openLanguageResource(String language, String fileName) {
        return LanguageService.class.getResourceAsStream(languageResourcePath(language, fileName));
    }

    /** Loads {@code language/<name>/<name>.properties} from the classpath. */
    private static Properties loadLanguageFile(String name) {
        Properties properties = new Properties();
        String resourcePath = languageResourcePath(name, name + ".properties");
        try (InputStream in = openLanguageResource(name, name + ".properties")) {
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
