package org.c2w.data.repository;

import org.c2w.infra.CatalogDataVersion;
import org.c2w.infra.CatalogManifest;
import org.c2w.infra.CatalogSource;
import org.c2w.infra.CatalogStore;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the game catalog once at startup, before anything reads it: a downloaded catalog
 * (normally {@code ~/.cow2Win/catalog/}) is used only if its manifest is readable with a
 * supported {@code formatVersion}, its {@code dataVersion} is newer than the built-in
 * catalog's, and {@link CatalogValidator} finds no error in it (every listed file present with
 * the right size and SHA-256, ...). Otherwise the built-in catalog stays active - e.g. after a
 * program update that brings a newer catalog. A rejected folder is never deleted, only the
 * reason is logged.
 */
public final class CatalogSelector {

    /** How many validator errors the log line of a rejected catalog names. */
    private static final int LOGGED_ERRORS = 5;

    /**
     * The outcome of the choice.
     *
     * @param source  the catalog to use
     * @param logLines what to log at startup: why a downloaded catalog is not used (if so)
     */
    public record Selection(CatalogSource source, List<String> logLines) {
        public Selection {
            logLines = List.copyOf(logLines);
        }
    }

    private static volatile List<String> startupLogLines = List.of();

    private CatalogSelector() {
    }

    /**
     * Chooses between the built-in catalog and the one in {@code downloadedFolder} (see the class
     * Javadoc). Only reads - never changes the active catalog or any file.
     */
    public static Selection select(Path downloadedFolder) {
        CatalogSource builtIn = CatalogSource.builtIn();
        if (downloadedFolder == null || !Files.isDirectory(downloadedFolder)) {
            return new Selection(builtIn, List.of());
        }
        CatalogSource downloaded = CatalogSource.folder(downloadedFolder);
        String rejected = "Downloaded catalog in " + downloaded.folderPath() + " not used: ";
        CatalogManifest manifest;
        try {
            manifest = downloaded.manifest();
        } catch (IOException | RuntimeException e) {
            return new Selection(builtIn, List.of(rejected + CatalogManifest.FILE_NAME + " cannot be read ("
                    + e.getMessage() + ")"));
        }
        if (manifest.formatVersion() > CatalogStore.SUPPORTED_CATALOG_FORMAT) {
            return new Selection(builtIn, List.of(rejected + "formatVersion " + manifest.formatVersion()
                    + " needs a newer program (this one supports up to " + CatalogStore.SUPPORTED_CATALOG_FORMAT + ")"));
        }
        CatalogDataVersion builtInVersion;
        try {
            builtInVersion = builtIn.manifest().dataVersion();
        } catch (IOException | RuntimeException e) {
            return new Selection(builtIn, List.of(rejected + "the built-in catalog's manifest cannot be read ("
                    + e.getMessage() + ")"));
        }
        if (manifest.dataVersion().compareTo(builtInVersion) <= 0) {
            return new Selection(builtIn, List.of(rejected + "its dataVersion " + manifest.dataVersion()
                    + " is not newer than the built-in " + builtInVersion));
        }
        CatalogValidator.Result result = CatalogValidator.validate(downloaded, null);
        if (!result.isValid()) {
            List<CatalogValidator.Finding> errors = result.errors();
            List<String> shown = new ArrayList<>();
            errors.stream().limit(LOGGED_ERRORS).forEach(error -> shown.add(error.message()));
            String more = errors.size() > LOGGED_ERRORS ? " (and " + (errors.size() - LOGGED_ERRORS) + " more)" : "";
            return new Selection(builtIn, List.of(rejected + String.join("; ", shown) + more));
        }
        List<String> lines = new ArrayList<>();
        result.warnings().forEach(warning -> lines.add("Downloaded catalog: " + warning.message()));
        return new Selection(downloaded, lines);
    }

    /**
     * Chooses the catalog ({@link #select}) and makes it the active one - once at startup, before
     * anything reads the catalog. Logs nothing itself (the configuration and with it the log file
     * are not loaded yet): {@link #logActiveCatalog} writes the result into the start block.
     */
    public static void applyAtStartup(Path downloadedFolder) {
        Selection selection = select(downloadedFolder);
        CatalogStore.use(selection.source());
        startupLogLines = selection.logLines();
    }

    /** Logs why a downloaded catalog is not used (if so) and which catalog is active, with dataVersion and note. */
    public static void logActiveCatalog() {
        startupLogLines.forEach(Logger::log);
        CatalogSource active = CatalogStore.active();
        try {
            CatalogManifest manifest = active.manifest();
            String note = manifest.note().isBlank() ? "" : " (" + manifest.note() + ")";
            Logger.log("Catalog: " + active.describe() + ", data version " + manifest.dataVersion() + note);
        } catch (IOException | RuntimeException e) {
            Logger.log("Catalog: " + active.describe() + ", data version unknown (" + e.getMessage() + ")");
        }
    }
}
