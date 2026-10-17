package org.c2w.datatool.data;

import org.c2w.data.model.ComboSource;
import org.c2w.datatool.RepositoryFolder;
import org.c2w.infra.CatalogDataVersion;
import org.c2w.infra.CatalogManifest;

import java.io.IOException;
import java.io.StringReader;
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
import java.util.Properties;
import java.util.Set;

/**
 * All system data of one repository: the twelve data files (the five catalog files in {@code
 * catalog/}, the others in {@code cow2win-app/src/main/resources}), the display names from
 * {@code catalog/names.json} and the catalog manifest. Every change goes through this class, which
 * marks the affected table as changed; {@link #save} writes only files whose content actually
 * changed, and then brings {@code catalog/manifest.json} up to date.
 */
public final class DataSet {

    /** The manifest of the game catalog, relative to the repository root. */
    public static final Path MANIFEST = RepositoryFolder.CATALOG.resolve(CatalogManifest.FILE_NAME);
    /** The display names of all catalog entries, relative to the repository root. */
    public static final Path NAMES = RepositoryFolder.CATALOG.resolve("names.json");

    private final Path root;
    private final Map<TableKind, DataTable> tables = new EnumMap<>(TableKind.class);
    private final Map<TableKind, TableCodec> codecs = new EnumMap<>(TableKind.class);
    /** Keys of the three language files (UI texts) - a new catalog id must not be one of them. */
    private final Map<Language, Set<String>> languageFileKeys = new EnumMap<>(Language.class);
    private final NamesFile names;
    private final CatalogManifest manifest;
    /** Avatars chosen in this session: target path (relative to the root) to source file - copied on save. */
    private final Map<Path, Path> pendingImages = new LinkedHashMap<>();

    private DataSet(Path root) throws IOException {
        this.root = root;
        for (Language language : Language.values()) {
            Properties properties = new Properties();
            properties.load(new StringReader(read(language.relativePath())));
            languageFileKeys.put(language, Set.copyOf(properties.stringPropertyNames()));
        }
        this.names = new NamesFile(read(NAMES));
        for (TableKind kind : TableKind.values()) {
            TableCodec codec = TableCodec.of(kind);
            String text = read(kind.relativePath());
            DataTable table = new DataTable(kind, codec.read(text), text);
            if (kind.isCatalog()) {
                for (Row row : table.rows()) {
                    for (Language language : Language.values()) {
                        row.set(language.field(), names.get(row.getString(Fields.ID), language));
                    }
                }
            }
            codecs.put(kind, codec);
            tables.put(kind, table);
        }
        this.manifest = CatalogManifest.parse(read(MANIFEST));
    }

    /** Loads everything below {@code repositoryRoot} (a folder checked by {@link RepositoryFolder}). */
    public static DataSet load(Path repositoryRoot) throws IOException {
        return new DataSet(repositoryRoot);
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

    /** The keys (UI texts) of the language file of {@code language}. */
    public Set<String> languageFileKeys(Language language) {
        return languageFileKeys.get(language);
    }

    /** The catalog manifest as loaded (not yet updated by a save). */
    public CatalogManifest manifest() {
        return manifest;
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
        if (kind.isCombos()) {
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
        if (!Objects.equals(row.get(field), (value instanceof String s && s.isEmpty()) || Boolean.FALSE.equals(value) ? null : value)) {
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

    /** Where the avatars of {@code kind} live, relative to the repository root - in the game catalog. */
    public static Path imageFolder(TableKind kind) {
        return RepositoryFolder.CATALOG.resolve(Path.of("images", kind.imageFolder()));
    }

    private static Path imageTarget(TableKind kind, String fileName) {
        return imageFolder(kind).resolve(fileName);
    }

    // --- check and save ---

    public List<Problem> validate() {
        return new Validator(this).validate();
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
     * files, {@code catalog/names.json} and new avatars - and then {@code catalog/manifest.json} if the files
     * of the catalog no longer match it (see {@link #updateManifest}). Nothing is written if the
     * check finds an error.
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
        updateNames();

        Map<Path, String> texts = new LinkedHashMap<>();
        for (TableKind kind : TableKind.values()) {
            if (fileChanged(kind)) {
                texts.put(kind.relativePath(), render(kind));
            }
        }
        if (names.isModified()) {
            texts.put(NAMES, names.text());
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
        if (updateManifest(options.today())) {
            written.add(slashes(MANIFEST));
        }
        return new SaveResult(written, List.of());
    }

    private boolean imageStillUsed(Path target) {
        for (TableKind kind : TableKind.CATALOGS) {
            if (kind.imageFolder() != null && target.getParent().equals(imageFolder(kind))) {
                String fileName = target.getFileName().toString();
                return table(kind).rows().stream().anyMatch(row -> fileName.equals(row.getString(Fields.IMAGE)));
            }
        }
        return false;
    }

    /**
     * Brings the display names into {@code catalog/names.json}: the names of every catalog
     * entry as in its row, ids no longer in any catalog (deleted entries) removed.
     */
    private void updateNames() {
        Set<String> allIds = new HashSet<>();
        for (TableKind kind : TableKind.CATALOGS) {
            for (Row row : table(kind).rows()) {
                String id = row.getString(Fields.ID);
                if (id == null) {
                    continue;
                }
                allIds.add(id);
                for (Language language : Language.values()) {
                    names.set(id, language, row.getString(language.field()));
                }
            }
        }
        for (String id : List.copyOf(names.ids())) {
            if (!allIds.contains(id)) {
                names.remove(id);
            }
        }
    }

    /**
     * Rewrites {@code catalog/manifest.json} if the files below {@code catalog/} (size, SHA-256)
     * differ from the ones it lists: with the current files and the next {@code dataVersion} for
     * {@code today} (see {@link CatalogDataVersion#next}); {@code formatVersion} and {@code note}
     * stay as they are. A save that leaves the catalog alone (e.g. only CowScore changes) does
     * not touch the manifest.
     *
     * @return true if the manifest was written
     */
    private boolean updateManifest(LocalDate today) throws IOException {
        List<CatalogManifest.FileEntry> files = CatalogManifest.scan(root.resolve(RepositoryFolder.CATALOG));
        if (files.equals(manifest.files())) {
            return false;
        }
        CatalogManifest updated = manifest.with(manifest.dataVersion().next(today), files);
        writeAtomically(root.resolve(MANIFEST), updated.toJson().getBytes(StandardCharsets.UTF_8));
        return true;
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
