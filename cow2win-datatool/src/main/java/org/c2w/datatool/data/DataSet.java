package org.c2w.datatool.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.ComboSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * All system data of one resources folder ({@code cow2win-app/src/main/resources}): the
 * eleven data files, the display names from the three language files and the catalog
 * version. Every change goes through this class, which marks the affected table as changed;
 * {@link #save} writes only files whose content actually changed.
 */
public final class DataSet {

    public static final Path CATALOG_VERSION = Path.of("data", "catalog-version.json");

    private final Path root;
    private final Map<TableKind, DataTable> tables = new EnumMap<>(TableKind.class);
    private final Map<TableKind, TableCodec> codecs = new EnumMap<>(TableKind.class);
    private final Map<Language, LanguageFile> languages = new EnumMap<>(Language.class);
    private final String catalogVersionText;
    /** Avatars chosen in this session: target path (relative to the root) to source file - copied on save. */
    private final Map<Path, Path> pendingImages = new LinkedHashMap<>();

    private DataSet(Path root) throws IOException {
        this.root = root;
        for (Language language : Language.values()) {
            languages.put(language, new LanguageFile(read(language.relativePath())));
        }
        for (TableKind kind : TableKind.values()) {
            TableCodec codec = TableCodec.of(kind);
            String text = read(kind.relativePath());
            DataTable table = new DataTable(kind, codec.read(text), text);
            if (kind.isCatalog()) {
                for (Row row : table.rows()) {
                    for (Language language : Language.values()) {
                        row.set(language.field(), languages.get(language).get(row.getString(Fields.ID)));
                    }
                }
            }
            codecs.put(kind, codec);
            tables.put(kind, table);
        }
        this.catalogVersionText = read(CATALOG_VERSION);
    }

    /** Loads everything below {@code resourcesRoot} (a folder checked by {@code ResourceFolder}). */
    public static DataSet load(Path resourcesRoot) throws IOException {
        return new DataSet(resourcesRoot);
    }

    private String read(Path relative) throws IOException {
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }

    public Path root() {
        return root;
    }

    public DataTable table(TableKind kind) {
        return tables.get(kind);
    }

    public LanguageFile language(Language language) {
        return languages.get(language);
    }

    /** English display name of a catalog entry, falling back to its id. */
    public String displayName(TableKind catalog, String id) {
        Row row = table(catalog).findById(id);
        String name = row == null ? null : row.getString(Language.EN.field());
        return name == null ? id : name;
    }

    public boolean isDirty() {
        return tables.values().stream().anyMatch(DataTable::isDirty);
    }

    // --- changes ---

    /** Appends a new row (new rows get an editable id). */
    public Row addRow(TableKind kind) {
        Row row = new Row(true);
        if (kind == TableKind.HERO_COMBOS) {
            row.set(Fields.SOURCE, ComboSource.C2W.name());
        }
        table(kind).rows().add(row);
        table(kind).markDirty();
        return row;
    }

    /**
     * Removes a row. For a saved catalog entry, its display names are removed from the language
     * files on save; references in other files stay and are reported by the check.
     */
    public void deleteRow(TableKind kind, Row row) {
        DataTable table = table(kind);
        if (table.rows().remove(row)) {
            if (!row.isNew() && row.getString(Fields.ID) != null) {
                table.deletedIds().add(row.getString(Fields.ID));
            }
            table.markDirty();
        }
    }

    /**
     * Sets a cell. Editing the English name of a new catalog entry also sets its id to the
     * suggestion from that name, as long as the id is empty or still the previous suggestion.
     */
    public void setValue(TableKind kind, Row row, String field, Object value) {
        if (value instanceof String s) {
            value = s.strip();
        }
        if (kind.isCatalog() && row.isNew() && field.equals(Language.EN.field())) {
            String id = row.getString(Fields.ID);
            if (id == null || id.equals(suggestId(kind, row.getString(field)))) {
                row.set(Fields.ID, suggestId(kind, (String) value));
            }
        }
        if (!Objects.equals(row.get(field), value instanceof String s && s.isEmpty() ? null : value)) {
            row.set(field, value);
            table(kind).markDirty();
        }
    }

    /**
     * Id suggested for an English name: lower case without accents and apostrophes, every
     * other run of non-alphanumerics as {@code -}; war flags without a leading "War Flag of"
     * and with the {@code flag-} prefix.
     */
    public static String suggestId(TableKind kind, String englishName) {
        if (englishName == null) {
            return null;
        }
        String id = Normalizer.normalize(englishName, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip();
        if (kind == TableKind.WAR_FLAGS) {
            id = id.replaceFirst("^war flag of (the )?", "");
        }
        id = id.replaceAll("['’`]", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (id.isEmpty()) {
            return null;
        }
        return id.startsWith(kind.idPrefix()) ? id : kind.idPrefix() + id;
    }

    // --- avatars ---

    /** File to show for an avatar: the chosen source until saved, else the file in the image folder. */
    public Path imageFile(TableKind kind, String fileName) {
        Path target = imageTarget(kind, fileName);
        return pendingImages.getOrDefault(target, root.resolve(target));
    }

    public boolean imageExistsInFolder(TableKind kind, String fileName) {
        return Files.isRegularFile(root.resolve(imageTarget(kind, fileName)));
    }

    /** True if the avatar exists in the folder or is chosen to be copied there on save. */
    public boolean imageAvailable(TableKind kind, String fileName) {
        return pendingImages.containsKey(imageTarget(kind, fileName)) || imageExistsInFolder(kind, fileName);
    }

    /**
     * Sets a row's avatar to the file name of {@code source}. With {@code copy}, the file is
     * copied into the kind's image folder on save (replacing a file of the same name).
     */
    public void setImage(TableKind kind, Row row, Path source, boolean copy) throws IOException {
        String fileName = source.getFileName().toString();
        Path target = imageTarget(kind, fileName);
        boolean sameFile = Files.exists(root.resolve(target)) && Files.isSameFile(source, root.resolve(target));
        if (copy && !sameFile) {
            pendingImages.put(target, source);
        }
        setValue(kind, row, Fields.IMAGE, fileName);
    }

    private static Path imageTarget(TableKind kind, String fileName) {
        return Path.of("images", kind.imageFolder(), fileName);
    }

    // --- check and save ---

    public List<Problem> validate() {
        return new Validator(this).validate();
    }

    /** True if saving would rewrite one of the five catalog files (then the data version may be updated). */
    public boolean catalogFilesChanged() {
        return TableKind.CATALOGS.stream().anyMatch(this::fileChanged);
    }

    /** Ids of new entries of {@code catalog} that have no entry in its CowScore file yet. */
    public List<String> newEntriesWithoutCowScore(TableKind catalog) {
        List<String> ids = new ArrayList<>();
        TableKind cowScore = catalog.cowScoreOfCatalog();
        if (cowScore == null) {
            return ids;
        }
        for (Row row : table(catalog).rows()) {
            String id = row.getString(Fields.ID);
            if (row.isNew() && id != null && table(cowScore).findById(id) == null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Text the file of {@code kind} would be written with. */
    String render(TableKind kind) {
        DataTable table = table(kind);
        return codecs.get(kind).write(table.rows(), table.trailingNewline());
    }

    private boolean fileChanged(TableKind kind) {
        DataTable table = table(kind);
        // A file with CRLF line endings is not rewritten just for those (only with a real change).
        return table.isDirty() && !render(kind).equals(table.fileText().replace("\r\n", "\n"));
    }

    /**
     * Checks and, without errors, writes every changed file (temporary file + rename): data
     * files, catalog version, new avatars and language files. Nothing is written if the check
     * finds an error.
     */
    public SaveResult save(SaveOptions options) throws IOException {
        List<Problem> errors = validate().stream().filter(Problem::isError).toList();
        if (!errors.isEmpty()) {
            return new SaveResult(List.of(), errors);
        }
        for (TableKind catalog : options.createCowScoreFor()) {
            TableKind cowScore = catalog.cowScoreOfCatalog();
            for (String id : newEntriesWithoutCowScore(catalog)) {
                setValue(cowScore, addRow(cowScore), Fields.ID, id);
            }
        }
        updateLanguageFiles();

        Map<Path, String> texts = new LinkedHashMap<>();
        for (TableKind kind : TableKind.values()) {
            if (fileChanged(kind)) {
                texts.put(kind.relativePath(), render(kind));
            }
        }
        if (options.setDataVersion() && catalogFilesChanged()) {
            texts.put(CATALOG_VERSION, withDataVersion(catalogVersionText, options.today()));
        }
        for (Language language : Language.values()) {
            if (languages.get(language).isModified()) {
                texts.put(language.relativePath(), languages.get(language).text());
            }
        }

        List<String> written = new ArrayList<>();
        for (Map.Entry<Path, String> entry : texts.entrySet()) {
            writeAtomically(root.resolve(entry.getKey()), entry.getValue().getBytes(StandardCharsets.UTF_8));
            written.add(slashes(entry.getKey()));
        }
        for (Map.Entry<Path, Path> image : pendingImages.entrySet()) {
            if (imageStillUsed(image.getKey())) {
                writeAtomically(root.resolve(image.getKey()), Files.readAllBytes(image.getValue()));
                written.add(slashes(image.getKey()));
            }
        }
        return new SaveResult(written, List.of());
    }

    private boolean imageStillUsed(Path target) {
        for (TableKind kind : TableKind.CATALOGS) {
            if (kind.imageFolder() != null && target.getName(1).toString().equals(kind.imageFolder())) {
                String fileName = target.getFileName().toString();
                return table(kind).rows().stream().anyMatch(row -> fileName.equals(row.getString(Fields.IMAGE)));
            }
        }
        return false;
    }

    /**
     * Brings the display names into the language files: removes the keys of deleted entries
     * (unless the id is in use again), changes changed values and adds the keys of new
     * entries at the end of their catalog's section.
     */
    private void updateLanguageFiles() {
        Set<String> allIds = new HashSet<>();
        for (TableKind kind : TableKind.CATALOGS) {
            allIds.addAll(table(kind).ids());
        }
        for (TableKind kind : TableKind.CATALOGS) {
            for (String deleted : table(kind).deletedIds()) {
                if (!allIds.contains(deleted)) {
                    languages.values().forEach(file -> file.remove(deleted));
                }
            }
        }
        for (TableKind kind : TableKind.CATALOGS) {
            List<String> sectionKeys = table(kind).ids();
            sectionKeys.addAll(table(kind).deletedIds());
            for (Row row : table(kind).rows()) {
                String id = row.getString(Fields.ID);
                for (Language language : Language.values()) {
                    LanguageFile file = languages.get(language);
                    String name = row.getString(language.field());
                    if (file.containsKey(id)) {
                        if (!Objects.equals(file.get(id), name)) {
                            file.set(id, name);
                        }
                    } else {
                        file.add(id, name, sectionKeys, sectionHeaders(kind), kind == TableKind.FORTIFICATIONS);
                    }
                }
            }
        }
    }

    /** Header comments of a catalog's section in the three language files. */
    private static List<String> sectionHeaders(TableKind kind) {
        return switch (kind) {
            case HEROES -> List.of("Helden", "Heroes", "Héros");
            case TITANS -> List.of("Titanen", "Titans");
            case PETS -> List.of("Pets", "Familiers");
            case WAR_FLAGS -> List.of("Kriegsflaggen", "War flags", "Drapeaux de guerre");
            default -> List.of("Festungen", "Fortification", "Fortifications", "Forteresses");
        };
    }

    /** {@code catalog-version.json} with {@code dataVersion} set to {@code date}, everything else unchanged. */
    static String withDataVersion(String catalogVersionText, LocalDate date) {
        JsonObject object = JsonParser.parseString(catalogVersionText).getAsJsonObject();
        object.addProperty("dataVersion", date.toString());
        return JsonText.write(object, Set.of(), catalogVersionText.endsWith("\n"));
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }
}
