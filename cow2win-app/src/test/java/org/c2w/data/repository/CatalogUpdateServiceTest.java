package org.c2w.data.repository;

import org.c2w.data.repository.CatalogUpdateService.Outcome;
import org.c2w.data.repository.CatalogUpdateService.Status;
import org.c2w.infra.CatalogManifest;
import org.c2w.infra.CatalogRemote;
import org.c2w.infra.CatalogSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The catalog update against a local folder as the "published" catalog. */
class CatalogUpdateServiceTest {

    @TempDir
    Path temp;
    Path catalogDir;
    Path previousDir;
    Path downloadDir;

    @BeforeEach
    void folders() {
        catalogDir = temp.resolve("home/catalog");
        previousDir = temp.resolve("home/catalog.previous");
        downloadDir = temp.resolve("home/catalog.download");
    }

    @Test
    void noNetworkOrAnHttpErrorChangesNothing() {
        CatalogRemote offline = path -> {
            throw new IOException("GitHub could not be reached (ConnectException).");
        };
        Outcome outcome = service(offline).check(NO_PROGRESS);
        assertEquals(Status.FAILED, outcome.status());
        assertEquals("GitHub could not be reached (ConnectException).", outcome.reason());
        assertNothingChanged();
    }

    @Test
    void aBrokenManifestChangesNothing() {
        Outcome outcome = service(path -> "{ broken".getBytes(StandardCharsets.UTF_8)).check(NO_PROGRESS);
        assertEquals(Status.FAILED, outcome.status());
        assertTrue(outcome.reason().contains("malformed"), outcome.reason());
        assertNothingChanged();
    }

    @Test
    void aNewerFormatNeedsANewerProgram() throws IOException {
        Path published = TestCatalogs.catalog(temp.resolve("published"), "2099-01-01", 2);
        assertEquals(Status.NEEDS_NEWER_PROGRAM, service(new FolderRemote(published)).check(NO_PROGRESS).status());
        assertNothingChanged();
    }

    @Test
    void theSameOrAnOlderStateIsUpToDate() throws IOException {
        String builtIn = CatalogSource.builtIn().manifest().dataVersion().toString();
        for (String version : List.of(builtIn, "2020-01-01")) {
            Path published = TestCatalogs.catalog(temp.resolve("published-" + version), version, 1);
            Outcome outcome = service(new FolderRemote(published)).check(NO_PROGRESS);
            assertEquals(Status.UP_TO_DATE, outcome.status(), version);
            assertEquals(builtIn, outcome.installed().toString());
            assertNothingChanged();
        }
    }

    @Test
    void aNewHeroIsShownInstalledAndChosenAtTheNextStart() throws IOException {
        Path published = TestCatalogs.copyOfRepositoryCatalog(temp.resolve("published"));
        TestCatalogs.addTestHero(published);
        TestCatalogs.writeManifest(published, "2099-01-01", 1);
        FolderRemote remote = new FolderRemote(published);
        CatalogUpdateService service = service(remote);

        Outcome checked = service.check(NO_PROGRESS);

        assertEquals(Status.UPDATE_AVAILABLE, checked.status(), checked::toString);
        assertEquals("2099-01-01", checked.available().toString());
        assertEquals(List.of("testy"), checked.diff().newIds().get("data/heroes.json"));
        assertEquals("Testy DE", checked.diff().name("testy", "deutsch"));
        assertEquals(0, checked.diff().changed());
        assertFalse(Files.exists(catalogDir), "nothing installed before Apply");

        int before = remote.fetched.size();
        Outcome installed = service.install(NO_PROGRESS);

        assertEquals(Status.INSTALLED, installed.status(), installed::toString);
        assertEquals(List.of("images/heroes/Testy.png"), remote.fetched.subList(before, remote.fetched.size()),
                "only the changed image is downloaded, the others are copied from the installed catalog");
        assertFalse(Files.exists(downloadDir));
        assertTrue(Files.isRegularFile(catalogDir.resolve("images/heroes/Testy.png")));
        assertTrue(CatalogValidator.validate(CatalogSource.folder(catalogDir), null).isValid());
        assertEquals(catalogDir.toAbsolutePath().normalize(), CatalogSelector.select(catalogDir).source().folderPath(),
                "the next start chooses it");

        // A second call before a restart compares with the downloaded catalog.
        assertEquals(Status.UP_TO_DATE, service(remote).check(NO_PROGRESS).status());

        // The next update moves the installed catalog to catalog.previous.
        TestCatalogs.editArray(published, "data/heroes.json",
                heroes -> heroes.get(0).getAsJsonObject().add("roles", rolesOf("TANK")));
        TestCatalogs.writeManifest(published, "2099-01-02", 1);
        CatalogUpdateService next = service(remote);
        Outcome nextCheck = next.check(NO_PROGRESS);
        assertEquals(1, nextCheck.diff().changed());
        assertEquals(List.of(), nextCheck.diff().newIds().get("data/heroes.json"));
        assertEquals(Status.INSTALLED, next.install(NO_PROGRESS).status());
        assertEquals("2099-01-02", manifestOf(catalogDir).dataVersion().toString());
        assertEquals("2099-01-01", manifestOf(previousDir).dataVersion().toString());
    }

    @Test
    void aWrongChecksumIsRejected() throws IOException {
        Path published = TestCatalogs.catalog(temp.resolve("published"), "2099-01-01", 1);
        Files.writeString(published.resolve("data/pets.json"), "[]");
        Outcome outcome = service(new FolderRemote(published)).check(NO_PROGRESS);
        assertEquals(Status.REJECTED, outcome.status());
        assertEquals(List.of("Size or SHA-256 does not match the manifest: data/pets.json"), outcome.problems());
        assertNothingChanged();
    }

    @Test
    void aWrongImageChecksumIsRejectedOnInstall() throws IOException {
        Path published = TestCatalogs.copyOfRepositoryCatalog(temp.resolve("published"));
        TestCatalogs.addTestHero(published);
        TestCatalogs.writeManifest(published, "2099-01-01", 1);
        Files.write(published.resolve("images/heroes/Testy.png"), new byte[]{1, 2, 3});
        CatalogUpdateService service = service(new FolderRemote(published));
        assertEquals(Status.UPDATE_AVAILABLE, service.check(NO_PROGRESS).status());

        Outcome outcome = service.install(NO_PROGRESS);

        assertEquals(Status.REJECTED, outcome.status());
        assertEquals(List.of("Size or SHA-256 does not match the manifest: images/heroes/Testy.png"), outcome.problems());
        assertNothingChanged();
    }

    @Test
    void aDroppedIdIsRejected() throws IOException {
        Path published = TestCatalogs.copyOfRepositoryCatalog(temp.resolve("published"));
        TestCatalogs.editArray(published, "data/pets.json", pets -> pets.remove(0));
        TestCatalogs.writeManifest(published, "2099-01-01", 1);
        CatalogUpdateService service = service(new FolderRemote(published));
        assertEquals(Status.UPDATE_AVAILABLE, service.check(NO_PROGRESS).status());

        Outcome outcome = service.install(NO_PROGRESS);

        assertEquals(Status.REJECTED, outcome.status());
        assertTrue(outcome.problems().stream().anyMatch(problem -> problem.startsWith("data/pets.json: id ")
                && problem.contains(" is missing")), outcome::toString);
        assertNothingChanged();
    }

    @Test
    void aPathWithDotDotIsRejectedBeforeAnythingIsWritten() throws IOException {
        Path published = TestCatalogs.catalog(temp.resolve("published"), "2099-01-01", 1);
        CatalogManifest manifest = manifestOf(published);
        List<CatalogManifest.FileEntry> files = new ArrayList<>(manifest.files());
        files.add(new CatalogManifest.FileEntry("images/../../evil.png", 3, CatalogManifest.sha256(new byte[]{1, 2, 3})));
        Files.writeString(published.resolve(CatalogManifest.FILE_NAME), manifest.with(manifest.dataVersion(), files).toJson());
        FolderRemote remote = new FolderRemote(published);

        Outcome outcome = service(remote).check(NO_PROGRESS);

        assertEquals(Status.REJECTED, outcome.status());
        assertEquals(List.of("Path not allowed in a catalog: images/../../evil.png"), outcome.problems());
        assertEquals(List.of(CatalogManifest.FILE_NAME), remote.fetched, "nothing but the manifest fetched");
        assertNothingChanged();
        assertFalse(Files.exists(temp.resolve("evil.png")));
    }

    @Test
    void cancellingAfterTheDifferencesChangesNothing() throws IOException {
        Path published = TestCatalogs.copyOfRepositoryCatalog(temp.resolve("published"));
        TestCatalogs.addTestHero(published);
        TestCatalogs.writeManifest(published, "2099-01-01", 1);
        CatalogUpdateService service = service(new FolderRemote(published));
        assertEquals(Status.UPDATE_AVAILABLE, service.check(NO_PROGRESS).status());

        service.discard();
        assertNothingChanged();

        CatalogUpdateService other = service(new FolderRemote(published));
        assertEquals(Status.UPDATE_AVAILABLE, other.check(NO_PROGRESS).status());
        assertEquals(Status.CANCELLED, other.install(CANCELLED).status());
        assertNothingChanged();
    }

    // --- helpers ---

    private CatalogUpdateService service(CatalogRemote remote) {
        return new CatalogUpdateService(remote, catalogDir, previousDir, downloadDir);
    }

    private void assertNothingChanged() {
        assertFalse(Files.exists(downloadDir), "work folder removed");
        assertFalse(Files.exists(catalogDir), "nothing installed");
        assertFalse(Files.exists(previousDir), "nothing moved");
    }

    private static CatalogManifest manifestOf(Path folder) throws IOException {
        return CatalogManifest.parse(Files.readString(folder.resolve(CatalogManifest.FILE_NAME)));
    }

    private static com.google.gson.JsonArray rolesOf(String role) {
        com.google.gson.JsonArray roles = new com.google.gson.JsonArray();
        roles.add(role);
        return roles;
    }

    private static final CatalogUpdateService.Progress NO_PROGRESS = new CatalogUpdateService.Progress() {
        @Override
        public void update(CatalogUpdateService.Phase phase, int done, int total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    private static final CatalogUpdateService.Progress CANCELLED = new CatalogUpdateService.Progress() {
        @Override
        public void update(CatalogUpdateService.Phase phase, int done, int total) {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    /** A local folder as the published catalog, recording every fetched path. */
    private static final class FolderRemote implements CatalogRemote {
        private final Path folder;
        final List<String> fetched = new ArrayList<>();

        FolderRemote(Path folder) {
            this.folder = folder;
        }

        @Override
        public byte[] fetch(String path) throws IOException {
            fetched.add(path);
            Path file = folder.resolve(path);
            if (!Files.isRegularFile(file)) {
                throw new IOException("GitHub answered HTTP 404 for " + path + ".");
            }
            return Files.readAllBytes(file);
        }
    }
}
