package org.c2w.data.repository;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.CatalogDataVersion;
import org.c2w.infra.CatalogManifest;
import org.c2w.infra.CatalogRemote;
import org.c2w.infra.CatalogSource;
import org.c2w.infra.CatalogStore;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Downloads a newer game catalog and installs it for the next start (Swing-free; the dialog is
 * only a surface over it). One instance per run: {@link #check} (manifest, format, version,
 * the small files, the differences), then {@link #install} (images, {@link CatalogValidator},
 * swapping the folders) or {@link #discard}. Works in a download folder that is emptied first
 * and removed again on every way out except a successful install; the installed catalog is
 * only touched by the final swap.
 *
 * <p>"Installed" is the catalog the next start would use ({@link CatalogSelector}) - after an
 * install without a restart that is the downloaded one, not the running one.
 */
public final class CatalogUpdateService {

    /** What the download is doing - for a progress display. */
    public enum Phase { MANIFEST, DATA_FILES, IMAGES, CHECKING, INSTALLING }

    /** Progress display and cancel request; called on the worker thread. */
    public interface Progress {
        void update(Phase phase, int done, int total);

        boolean isCancelled();
    }

    /** How a step ended. */
    public enum Status {
        /** The catalog could not be checked (network, HTTP, malformed manifest) - nothing changed. */
        FAILED,
        /** The published catalog has a newer format than this program understands - nothing changed. */
        NEEDS_NEWER_PROGRAM,
        /** The published catalog is not newer than the installed one - nothing changed. */
        UP_TO_DATE,
        /** The published catalog is broken (see the problems) - nothing changed. */
        REJECTED,
        /** A newer catalog is ready to install (see the differences). */
        UPDATE_AVAILABLE,
        /** The newer catalog is installed and used from the next start on. */
        INSTALLED,
        /** Cancelled by the user - nothing changed. */
        CANCELLED
    }

    /**
     * The result of {@link #check} or {@link #install}.
     *
     * @param reason    for {@link Status#FAILED}: one sentence (English) on why
     * @param problems  for {@link Status#REJECTED}: the errors found (English)
     * @param installed the data version of the installed catalog (null if not known yet)
     * @param available the data version of the published catalog (null if not known yet)
     * @param diff      for {@link Status#UPDATE_AVAILABLE}: what the new catalog adds and changes
     */
    public record Outcome(Status status, String reason, List<String> problems, CatalogDataVersion installed,
                          CatalogDataVersion available, Diff diff) {
        public Outcome {
            problems = problems == null ? List.of() : List.copyOf(problems);
        }
    }

    /**
     * The differences of the published catalog from the installed one.
     *
     * @param newIds   per data file ({@code data/heroes.json}, ...): the ids the installed catalog lacks
     * @param changed  how many ids exist in both but differ in their data, name or image
     * @param newNames the names of the published catalog (id, language, name) - for showing the new ids
     */
    public record Diff(Map<String, List<String>> newIds, int changed, Map<String, Map<String, String>> newNames) {

        /** The name of {@code id} in {@code language}, else in English, else the id. */
        public String name(String id, String language) {
            Map<String, String> names = newNames.getOrDefault(id, Map.of());
            return names.getOrDefault(language, names.getOrDefault("english", id));
        }
    }

    private final CatalogRemote remote;
    private final Path catalogDir;
    private final Path previousDir;
    private final Path downloadDir;

    // State between check and install.
    private byte[] manifestBytes;
    private CatalogManifest manifest;
    private CatalogSource installed;
    private CatalogManifest installedManifest;

    /** How many files were fetched from {@link #remote} - for the log. */
    private int fetched;

    /**
     * @param catalogDir  the installed downloaded catalog ({@code ~/.cow2Win/catalog})
     * @param previousDir where the replaced one goes ({@code ~/.cow2Win/catalog.previous})
     * @param downloadDir the work folder ({@code ~/.cow2Win/catalog.download})
     */
    public CatalogUpdateService(CatalogRemote remote, Path catalogDir, Path previousDir, Path downloadDir) {
        this.remote = Objects.requireNonNull(remote);
        this.catalogDir = Objects.requireNonNull(catalogDir);
        this.previousDir = Objects.requireNonNull(previousDir);
        this.downloadDir = Objects.requireNonNull(downloadDir);
    }

    /**
     * Steps 1-5: loads and checks the published manifest, compares its version with the installed
     * catalog, loads the small files ({@code names.json}, {@code data/*.json}) and computes the
     * differences. Every outcome but {@link Status#UPDATE_AVAILABLE} leaves no download folder.
     */
    public Outcome check(Progress progress) {
        try {
            deleteRecursively(downloadDir);
            Files.createDirectories(downloadDir);

            progress.update(Phase.MANIFEST, 0, 1);
            try {
                manifestBytes = fetch(CatalogManifest.FILE_NAME);
            } catch (IOException e) {
                return end(new Outcome(Status.FAILED, e.getMessage(), null, null, null, null));
            }
            try {
                manifest = CatalogManifest.parse(new String(manifestBytes, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                return end(new Outcome(Status.FAILED, "The published manifest is malformed (" + e.getMessage() + ").",
                        null, null, null, null));
            }
            if (manifest.formatVersion() > CatalogStore.SUPPORTED_CATALOG_FORMAT) {
                return end(new Outcome(Status.NEEDS_NEWER_PROGRAM, null, null, null, manifest.dataVersion(), null));
            }
            CatalogValidator.Result manifestCheck = CatalogValidator.checkManifest(manifest);
            if (!manifestCheck.isValid()) {
                return end(rejected(manifestCheck.errors().stream().map(CatalogValidator.Finding::message).toList()));
            }
            installed = CatalogSelector.select(catalogDir).source();
            installedManifest = installed.manifest();
            if (manifest.dataVersion().compareTo(installedManifest.dataVersion()) <= 0) {
                return end(new Outcome(Status.UP_TO_DATE, null, null, installedManifest.dataVersion(),
                        manifest.dataVersion(), null));
            }

            List<CatalogManifest.FileEntry> small = manifest.files().stream()
                    .filter(file -> file.path().endsWith(".json")).toList();
            for (int i = 0; i < small.size(); i++) {
                if (progress.isCancelled()) {
                    return end(cancelled());
                }
                progress.update(Phase.DATA_FILES, i, small.size());
                CatalogManifest.FileEntry file = small.get(i);
                byte[] content = fetch(file.path());
                if (!matches(file, content)) {
                    return end(rejected(List.of("Size or SHA-256 does not match the manifest: " + file.path())));
                }
                write(file.path(), content);
            }
            for (String required : List.of(CatalogValidator.NAMES, CatalogValidator.HEROES, CatalogValidator.TITANS,
                    CatalogValidator.PETS, CatalogValidator.WAR_FLAGS, CatalogValidator.FORTIFICATIONS)) {
                if (!Files.isRegularFile(downloadDir.resolve(required))) {
                    return end(rejected(List.of("Missing file: " + required)));
                }
            }
            if (progress.isCancelled()) {
                return end(cancelled());
            }
            return new Outcome(Status.UPDATE_AVAILABLE, null, null, installedManifest.dataVersion(),
                    manifest.dataVersion(), diff());
        } catch (IOException | RuntimeException e) {
            Logger.logException("Catalog update: check failed", e);
            return end(new Outcome(Status.FAILED, e.getMessage(), null, null, null, null));
        }
    }

    /**
     * Steps 6-8 after {@link #check} returned {@link Status#UPDATE_AVAILABLE}: fetches the images
     * that differ from the installed catalog (the others are copied from it), checks the whole
     * catalog with {@link CatalogValidator} against the installed one and swaps the folders.
     */
    public Outcome install(Progress progress) {
        if (manifest == null || installed == null) {
            throw new IllegalStateException("check() did not find an update");
        }
        CatalogDataVersion from = installedManifest.dataVersion();
        CatalogDataVersion to = manifest.dataVersion();
        try {
            Map<String, String> installedSha = new HashMap<>();
            installedManifest.files().forEach(file -> installedSha.put(file.path(), file.sha256()));
            List<CatalogManifest.FileEntry> images = manifest.files().stream()
                    .filter(file -> !file.path().endsWith(".json")).toList();
            for (int i = 0; i < images.size(); i++) {
                if (progress.isCancelled()) {
                    return end(cancelled());
                }
                progress.update(Phase.IMAGES, i, images.size());
                CatalogManifest.FileEntry file = images.get(i);
                byte[] content = file.sha256().equals(installedSha.get(file.path()))
                        ? installed.readBytes(file.path())
                        : fetch(file.path());
                if (!matches(file, content)) {
                    return end(rejected(List.of("Size or SHA-256 does not match the manifest: " + file.path())));
                }
                write(file.path(), content);
            }
            write(CatalogManifest.FILE_NAME, manifestBytes);

            if (progress.isCancelled()) {
                return end(cancelled());
            }
            progress.update(Phase.CHECKING, 0, 1);
            CatalogValidator.Result result = CatalogValidator.validate(CatalogSource.folder(downloadDir),
                    CatalogValidator.idsOf(installed));
            result.warnings().forEach(warning -> Logger.log("Catalog update " + to + ": " + warning.message()));
            if (!result.isValid()) {
                return end(rejected(result.errors().stream().map(CatalogValidator.Finding::message).toList()));
            }
            if (progress.isCancelled()) {
                return end(cancelled());
            }

            progress.update(Phase.INSTALLING, 0, 1);
            String failure = swap();
            if (failure != null) {
                return end(new Outcome(Status.FAILED, failure, null, from, to, null));
            }
            Logger.log("Catalog update: " + to + " installed in " + catalogDir + " (was " + from + ", "
                    + fetched + " file(s) downloaded) - used from the next start on");
            return new Outcome(Status.INSTALLED, null, null, from, to, null);
        } catch (IOException | RuntimeException e) {
            Logger.logException("Catalog update: install failed", e);
            return end(new Outcome(Status.FAILED, e.getMessage(), null, from, to, null));
        }
    }

    /** Throws the download away (e.g. the user did not take the update) - nothing changes. */
    public void discard() {
        try {
            deleteRecursively(downloadDir);
        } catch (IOException e) {
            Logger.logException("Catalog update: could not remove " + downloadDir, e);
        }
    }

    // --- steps ---

    /**
     * Step 8: {@code catalog.previous} removed, {@code catalog} becomes {@code catalog.previous},
     * the download becomes {@code catalog}. On failure the old state is restored as far as
     * possible; returns a one-sentence reason then, null on success.
     */
    private String swap() throws IOException {
        deleteRecursively(previousDir);
        boolean movedOld = false;
        try {
            if (Files.exists(catalogDir)) {
                Files.move(catalogDir, previousDir);
                movedOld = true;
            }
            Files.move(downloadDir, catalogDir);
            return null;
        } catch (IOException e) {
            Logger.logException("Catalog update: swapping the catalog folders failed", e);
            if (movedOld && !Files.exists(catalogDir)) {
                try {
                    Files.move(previousDir, catalogDir);
                } catch (IOException restore) {
                    Logger.logException("Catalog update: could not restore " + catalogDir + " from " + previousDir, restore);
                }
            }
            return "The new catalog could not be put in place (" + e.getMessage() + ").";
        }
    }

    /** The new ids and the number of changed ids, per data file, compared with the installed catalog. */
    private Diff diff() throws IOException {
        Map<String, Map<String, String>> newNames = LanguageService.parseCatalogNames(read(CatalogValidator.NAMES));
        Map<String, Map<String, String>> oldNames = LanguageService.parseCatalogNames(
                installed.readText(CatalogValidator.NAMES));
        Map<String, String> newSha = new HashMap<>();
        manifest.files().forEach(file -> newSha.put(file.path(), file.sha256()));
        Map<String, String> oldSha = new HashMap<>();
        installedManifest.files().forEach(file -> oldSha.put(file.path(), file.sha256()));

        Map<String, List<String>> newIds = new LinkedHashMap<>();
        int changed = 0;
        for (Map.Entry<String, String> dataFile : imageFolders().entrySet()) {
            Map<String, JsonObject> before = byId(installed.readText(dataFile.getKey()));
            Map<String, JsonObject> after = byId(read(dataFile.getKey()));
            List<String> added = new ArrayList<>();
            for (Map.Entry<String, JsonObject> entry : after.entrySet()) {
                String id = entry.getKey();
                JsonObject old = before.get(id);
                if (old == null) {
                    added.add(id);
                } else if (!old.equals(entry.getValue())
                        || !Objects.equals(oldNames.get(id), newNames.get(id))
                        || imageChanged(entry.getValue(), dataFile.getValue(), oldSha, newSha)) {
                    changed++;
                }
            }
            newIds.put(dataFile.getKey(), List.copyOf(added));
        }
        return new Diff(newIds, changed, newNames);
    }

    private static boolean imageChanged(JsonObject entry, String folder, Map<String, String> oldSha,
                                        Map<String, String> newSha) {
        String image = folder == null ? null : JsonSupport.getStringOrNull(entry, "image");
        if (image == null) {
            return false;
        }
        String path = "images/" + folder + "/" + image;
        return !Objects.equals(oldSha.get(path), newSha.get(path));
    }

    // --- helpers ---

    private byte[] fetch(String path) throws IOException {
        fetched++;
        return remote.fetch(path);
    }

    private static boolean matches(CatalogManifest.FileEntry file, byte[] content) {
        return content.length == file.size() && CatalogManifest.sha256(content).equals(file.sha256());
    }

    private void write(String relativePath, byte[] content) throws IOException {
        Path target = downloadDir.resolve(relativePath).normalize();
        if (!target.startsWith(downloadDir.normalize())) {
            throw new IOException("Path outside the download folder: " + relativePath);
        }
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(downloadDir.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static Map<String, JsonObject> byId(String json) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            if (element.isJsonObject()) {
                String id = JsonSupport.getStringOrNull(element.getAsJsonObject(), "id");
                if (id != null) {
                    result.put(id, element.getAsJsonObject());
                }
            }
        }
        return result;
    }

    /** The data files in the order shown, each with its image folder (null: no images). */
    private static Map<String, String> imageFolders() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(CatalogValidator.HEROES, "heroes");
        files.put(CatalogValidator.TITANS, "titans");
        files.put(CatalogValidator.PETS, "pets");
        files.put(CatalogValidator.WAR_FLAGS, "flags");
        files.put(CatalogValidator.FORTIFICATIONS, null);
        return files;
    }

    private Outcome rejected(List<String> problems) {
        problems.forEach(problem -> Logger.log("Catalog update rejected: " + problem));
        return new Outcome(Status.REJECTED, null, problems,
                installedManifest == null ? null : installedManifest.dataVersion(),
                manifest == null ? null : manifest.dataVersion(), null);
    }

    private Outcome cancelled() {
        return new Outcome(Status.CANCELLED, null, null, null, null, null);
    }

    /** Removes the download folder and logs a failure - for every outcome that ends the run without an install. */
    private Outcome end(Outcome outcome) {
        if (outcome.status() == Status.FAILED) {
            Logger.log("Catalog update: the catalog could not be checked - " + outcome.reason());
        }
        discard();
        return outcome;
    }

    static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
