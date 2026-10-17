package org.c2w.infra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * The {@code manifest.json} of a game catalog (the {@code catalog/} folder of the repository,
 * see {@link CatalogStore}): which catalog format it has, which data state it is, and every
 * other file of the catalog with its size and SHA-256. Written by the data tool on save,
 * never by the app.
 *
 * @param formatVersion the catalog format, see {@link CatalogStore#SUPPORTED_CATALOG_FORMAT}
 * @param dataVersion   the data state
 * @param note          free text on what changed, may be empty
 * @param files         every file below the catalog folder except the manifest, sorted by path
 */
public record CatalogManifest(int formatVersion, CatalogDataVersion dataVersion, String note, List<FileEntry> files) {

    /** File name of the manifest in the catalog folder. */
    public static final String FILE_NAME = "manifest.json";

    /**
     * One catalog file.
     *
     * @param path   relative to the catalog folder, with {@code /}
     * @param size   in bytes
     * @param sha256 lower-case hex
     */
    public record FileEntry(String path, long size, String sha256) {
    }

    public CatalogManifest {
        if (dataVersion == null) {
            throw new IllegalArgumentException("dataVersion must not be null");
        }
        note = note == null ? "" : note;
        files = List.copyOf(files);
    }

    /**
     * Parses the text of a {@code manifest.json}.
     *
     * @throws IllegalArgumentException if it is not a valid manifest
     */
    public static CatalogManifest parse(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            Integer formatVersion = JsonSupport.getInteger(object, "formatVersion");
            if (formatVersion == null) {
                throw new IllegalArgumentException("manifest.json has no formatVersion");
            }
            List<FileEntry> files = new ArrayList<>();
            for (JsonElement element : JsonSupport.getArray(object, "files")) {
                JsonObject file = element.getAsJsonObject();
                files.add(new FileEntry(JsonSupport.getStringOrNull(file, "path"),
                        file.get("size").getAsLong(), JsonSupport.getStringOrNull(file, "sha256")));
            }
            return new CatalogManifest(formatVersion,
                    CatalogDataVersion.parse(JsonSupport.getStringOrNull(object, "dataVersion")),
                    JsonSupport.getString(object, "note", ""), files);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("manifest.json is malformed: " + e.getMessage(), e);
        }
    }

    /** The manifest as written to {@code manifest.json}: pretty-printed, trailing newline. */
    public String toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("formatVersion", formatVersion);
        object.addProperty("dataVersion", dataVersion.toString());
        object.addProperty("note", note);
        JsonArray array = new JsonArray();
        for (FileEntry file : files) {
            JsonObject entry = new JsonObject();
            entry.addProperty("path", file.path());
            entry.addProperty("size", file.size());
            entry.addProperty("sha256", file.sha256());
            array.add(entry);
        }
        object.add("files", array);
        return JsonSupport.GSON.toJson(object) + "\n";
    }

    /** The same manifest with other data version and files. */
    public CatalogManifest with(CatalogDataVersion newDataVersion, List<FileEntry> newFiles) {
        return new CatalogManifest(formatVersion, newDataVersion, note, newFiles);
    }

    /**
     * Every file below {@code catalogFolder} except the manifest itself, sorted by path, with
     * size and SHA-256 - what {@link #files} must list for that folder.
     */
    public static List<FileEntry> scan(Path catalogFolder) throws IOException {
        List<FileEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(catalogFolder)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String path = catalogFolder.relativize(file).toString().replace('\\', '/');
                if (!path.equals(FILE_NAME)) {
                    byte[] content = Files.readAllBytes(file);
                    entries.add(new FileEntry(path, content.length, sha256(content)));
                }
            }
        }
        entries.sort(Comparator.comparing(FileEntry::path));
        return entries;
    }

    /** Lower-case hex SHA-256 of {@code content}. */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
