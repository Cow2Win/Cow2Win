package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.CatalogManifest;
import org.c2w.infra.CatalogSource;
import org.c2w.infra.CatalogStore;
import org.c2w.infra.JsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Checks a whole game catalog ({@link CatalogSource}: folder or built-in, with its manifest)
 * before it is used: at startup for a downloaded catalog, before a downloaded catalog replaces
 * the active one, and in the data tool before saving. Swing-free; the findings are English
 * texts for the log (and the first of them for a dialog).
 *
 * <p>Errors reject the catalog: paths outside the known layout, files too large, wrong size or
 * SHA-256, missing or unreadable data files and names (read with the same parsers as at
 * startup), duplicate ids, ids dropped compared with the previously active catalog, images
 * not in the manifest and prerequisites naming no fortification. Warnings only go to the log:
 * whatever the parsers skip (e.g. an unknown role or element), a name missing in a language, an
 * image nothing refers to.
 */
public final class CatalogValidator {

    /** At most this many bytes per catalog file. */
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    /** At most this many bytes for all catalog files together. */
    public static final long MAX_TOTAL_SIZE = 50L * 1024 * 1024;

    public static final String NAMES = "names.json";
    public static final String HEROES = "data/heroes.json";
    public static final String TITANS = "data/titans.json";
    public static final String PETS = "data/pets.json";
    public static final String WAR_FLAGS = "data/warFlags.json";
    public static final String FORTIFICATIONS = "data/fortifications.json";

    /** The five data files, each with the image folder of its entries (null: no images). */
    private static final Map<String, String> DATA_FILES = dataFiles();

    private static final Pattern PATH = Pattern.compile(
            "names\\.json|data/[A-Za-z0-9._-]+\\.json|images/(heroes|titans|pets|flags)/[A-Za-z0-9._-]+\\.png");

    /** One finding: an error rejects the catalog, a warning does not. */
    public record Finding(boolean error, String message) {
        @Override
        public String toString() {
            return (error ? "error: " : "warning: ") + message;
        }
    }

    /** All findings of one check. */
    public record Result(List<Finding> findings) {

        public Result {
            findings = List.copyOf(findings);
        }

        public List<Finding> errors() {
            return findings.stream().filter(Finding::error).toList();
        }

        public List<Finding> warnings() {
            return findings.stream().filter(finding -> !finding.error()).toList();
        }

        /** True if there is no error (warnings allowed). */
        public boolean isValid() {
            return errors().isEmpty();
        }
    }

    private final List<Finding> findings = new ArrayList<>();

    private CatalogValidator() {
    }

    /**
     * Checks {@code catalog}.
     *
     * @param previousIds the ids per data file of the previously active catalog (see {@link #idsOf}),
     *                    none of which may be missing in {@code catalog}; null: no comparison
     */
    public static Result validate(CatalogSource catalog, Map<String, Set<String>> previousIds) {
        CatalogValidator validator = new CatalogValidator();
        validator.check(catalog, previousIds);
        return new Result(validator.findings);
    }

    /**
     * The ids per data file ({@code data/heroes.json} etc.) of {@code catalog} - what a later catalog
     * must still contain.
     *
     * @throws IOException if a data file cannot be read
     */
    public static Map<String, Set<String>> idsOf(CatalogSource catalog) throws IOException {
        Map<String, Set<String>> ids = new LinkedHashMap<>();
        for (String file : DATA_FILES.keySet()) {
            ids.put(file, new TreeSet<>(rawIds(JsonParser.parseString(catalog.readText(file)).getAsJsonArray())));
        }
        return ids;
    }

    // --- the checks ---

    private void check(CatalogSource catalog, Map<String, Set<String>> previousIds) {
        CatalogManifest manifest;
        try {
            manifest = catalog.manifest();
        } catch (IOException | RuntimeException e) {
            error(CatalogManifest.FILE_NAME + " cannot be read: " + e.getMessage());
            return;
        }
        if (manifest.formatVersion() < 1 || manifest.formatVersion() > CatalogStore.SUPPORTED_CATALOG_FORMAT) {
            error("formatVersion " + manifest.formatVersion() + " is not supported by this program (up to "
                    + CatalogStore.SUPPORTED_CATALOG_FORMAT + ")");
            return;
        }
        if (!checkPathsAndSizes(manifest)) {
            return;
        }
        Map<String, String> texts = checkContent(catalog, manifest);
        if (!errors()) {
            checkData(manifest, texts, previousIds);
        }
    }

    /** Path layout, duplicates and size limits (from the manifest, before reading anything). */
    private boolean checkPathsAndSizes(CatalogManifest manifest) {
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (CatalogManifest.FileEntry file : manifest.files()) {
            String path = file.path();
            if (path == null || path.contains("..") || !PATH.matcher(path).matches()) {
                error("Path not allowed in a catalog: " + path);
            } else if (!seen.add(path)) {
                error("Path listed twice: " + path);
            }
            if (file.size() < 0 || file.size() > MAX_FILE_SIZE) {
                error(path + " is too large (" + file.size() + " bytes, at most " + MAX_FILE_SIZE + ")");
            }
            total += Math.max(0, file.size());
        }
        if (total > MAX_TOTAL_SIZE) {
            error("The catalog is too large (" + total + " bytes, at most " + MAX_TOTAL_SIZE + ")");
        }
        return !errors();
    }

    /** Every listed file exists with the listed size and SHA-256; returns the texts of the JSON files. */
    private Map<String, String> checkContent(CatalogSource catalog, CatalogManifest manifest) {
        Map<String, String> texts = new HashMap<>();
        for (CatalogManifest.FileEntry file : manifest.files()) {
            byte[] content;
            try {
                content = catalog.readBytes(file.path());
            } catch (IOException | RuntimeException e) {
                error("Missing file: " + file.path());
                continue;
            }
            if (content.length != file.size() || !CatalogManifest.sha256(content).equals(file.sha256())) {
                error("Size or SHA-256 does not match the manifest: " + file.path());
            } else if (file.path().endsWith(".json")) {
                texts.put(file.path(), new String(content, StandardCharsets.UTF_8));
            }
        }
        return texts;
    }

    private void checkData(CatalogManifest manifest, Map<String, String> texts, Map<String, Set<String>> previousIds) {
        Set<String> listed = new HashSet<>();
        manifest.files().forEach(file -> listed.add(file.path()));
        Set<String> referencedImages = new HashSet<>();
        Map<String, Set<String>> idsByFile = new LinkedHashMap<>();

        for (Map.Entry<String, String> dataFile : DATA_FILES.entrySet()) {
            String file = dataFile.getKey();
            String text = texts.get(file);
            if (text == null) {
                error("Missing file: " + file);
                continue;
            }
            JsonArray entries;
            try {
                parse(file, text, this::warning);
                entries = JsonParser.parseString(text).getAsJsonArray();
            } catch (RuntimeException e) {
                error(file + " cannot be read: " + e.getMessage());
                continue;
            }
            idsByFile.put(file, checkUniqueIds(file, entries));
            if (dataFile.getValue() != null) {
                checkImages(file, dataFile.getValue(), entries, listed, referencedImages);
            }
        }
        checkPrerequisites(texts.get(FORTIFICATIONS), idsByFile.get(FORTIFICATIONS));
        checkNames(texts.get(NAMES), idsByFile);
        if (previousIds != null) {
            checkNoIdDropped(previousIds, idsByFile);
        }
        for (String path : listed) {
            if (path.startsWith("images/") && !referencedImages.contains(path)) {
                warning("Image not referred to by any entry: " + path);
            }
        }
    }

    /** Reads a data file with the parser the app uses at startup; its warnings go to {@code log}. */
    private static void parse(String file, String text, Consumer<String> log) {
        switch (file) {
            case HEROES -> HeroRepository.parseHeroesJson(text, Map.of(), log);
            case TITANS -> TitanRepository.parseTitansJson(text, log);
            case PETS -> PetRepository.parsePetsJson(text, log);
            case WAR_FLAGS -> WarFlagRepository.parseWarFlagsJson(text, log);
            case FORTIFICATIONS -> FortificationRepository.parseFortificationsJson(text, log);
            default -> throw new IllegalArgumentException(file);
        }
    }

    private Set<String> checkUniqueIds(String file, JsonArray entries) {
        Set<String> ids = new LinkedHashSet<>();
        for (String id : rawIds(entries)) {
            if (!ids.add(id)) {
                error(file + ": id " + id + " is used more than once");
            }
        }
        return ids;
    }

    private void checkImages(String file, String folder, JsonArray entries, Set<String> listed,
                             Set<String> referencedImages) {
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String image = JsonSupport.getStringOrNull(entry, "image");
            if (image == null) {
                continue;
            }
            String path = "images/" + folder + "/" + image;
            referencedImages.add(path);
            if (!listed.contains(path)) {
                error(file + ": image " + image + " of " + JsonSupport.getStringOrNull(entry, "id")
                        + " is not in the catalog (" + path + ")");
            }
        }
    }

    private void checkPrerequisites(String fortificationsText, Set<String> fortificationIds) {
        if (fortificationsText == null || fortificationIds == null) {
            return;
        }
        for (JsonElement element : JsonParser.parseString(fortificationsText).getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject fortification = element.getAsJsonObject();
            for (String prerequisite : JsonSupport.getStringList(fortification, "prerequisites")) {
                if (!fortificationIds.contains(prerequisite)) {
                    error(FORTIFICATIONS + ": fortification " + JsonSupport.getStringOrNull(fortification, "id")
                            + " has the unknown prerequisite " + prerequisite);
                }
            }
        }
    }

    private void checkNames(String namesText, Map<String, Set<String>> idsByFile) {
        if (namesText == null) {
            error("Missing file: " + NAMES);
            return;
        }
        Map<String, Map<String, String>> names;
        try {
            names = LanguageService.parseCatalogNames(namesText);
        } catch (RuntimeException e) {
            error(NAMES + " cannot be read: " + e.getMessage());
            return;
        }
        List<String> languages = LanguageService.availableLanguages();
        for (Set<String> ids : idsByFile.values()) {
            for (String id : ids) {
                Map<String, String> byLanguage = names.getOrDefault(id, Map.of());
                for (String language : languages) {
                    if (!byLanguage.containsKey(language)) {
                        warning(NAMES + ": " + id + " has no " + language + " name");
                    }
                }
            }
        }
    }

    private void checkNoIdDropped(Map<String, Set<String>> previousIds, Map<String, Set<String>> idsByFile) {
        for (Map.Entry<String, Set<String>> previous : previousIds.entrySet()) {
            Set<String> now = idsByFile.getOrDefault(previous.getKey(), Set.of());
            for (String id : previous.getValue()) {
                if (!now.contains(id)) {
                    error(previous.getKey() + ": id " + id + " is missing - ids are never removed (guilds,"
                            + " lineups and journals refer to them)");
                }
            }
        }
    }

    // --- helpers ---

    private static List<String> rawIds(JsonArray entries) {
        List<String> ids = new ArrayList<>();
        for (JsonElement element : entries) {
            if (element.isJsonObject()) {
                String id = JsonSupport.getStringOrNull(element.getAsJsonObject(), "id");
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static Map<String, String> dataFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(FORTIFICATIONS, null);
        files.put(HEROES, "heroes");
        files.put(TITANS, "titans");
        files.put(PETS, "pets");
        files.put(WAR_FLAGS, "flags");
        return files;
    }

    private boolean errors() {
        return findings.stream().anyMatch(Finding::error);
    }

    private void error(String message) {
        findings.add(new Finding(true, message));
    }

    private void warning(String message) {
        findings.add(new Finding(false, message));
    }
}
