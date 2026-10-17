package org.c2w.data.repository;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.c2w.data.journal.parse.NameResolver;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Hero;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.CatalogStore;
import org.c2w.infra.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A downloaded catalog with one hero more than the built-in one ({@code testy}): once it is the
 * active catalog, the whole app sees the hero - repository, image, names, battle-log names - and
 * the workspace's {@code cowScore.json} gets an entry for it without any other value changing.
 */
class DownloadedCatalogTest {

    @TempDir
    Path temp;

    @AfterEach
    void backToTheBuiltInCatalog() {
        CatalogStore.useBuiltIn();
        LanguageService.resetCache();
    }

    @Test
    void anAdditionalHeroIsKnownEverywhere() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        new HeroRepository(workspace);
        Map<String, JsonElement> cowScoresBefore = cowScores(workspace);

        Path folder = TestCatalogs.copyOfRepositoryCatalog(temp.resolve("catalog"));
        TestCatalogs.addTestHero(folder);
        TestCatalogs.writeManifest(folder, "2099-01-01", 1);
        CatalogSelector.applyAtStartup(folder);
        LanguageService.resetCache();
        assertEquals(folder.toAbsolutePath().normalize(), CatalogStore.active().folderPath(), "catalog chosen");

        Hero testy = new HeroRepository(workspace).findById("testy").orElseThrow();
        assertEquals("/images/heroes/Testy.png", testy.imagePath());
        assertEquals(FortMarks.NONE, testy.fortMarks());
        URL image = CatalogStore.imageUrl("images/heroes/Testy.png");
        assertNotNull(image);
        assertEquals(folder.toAbsolutePath().normalize().resolve("images/heroes/Testy.png"), Path.of(toUri(image)));
        assertNotNull(IconLoader.iconFor(testy.imagePath(), 32));

        String previous = Config.getLanguage();
        try {
            Config.setLanguage("francais");
            assertEquals("Testy FR", LanguageService.displayName("testy"));
        } finally {
            Config.setLanguage(previous);
        }
        assertEquals("Testy DE", LanguageService.textIn("deutsch", "testy"));
        assertEquals("testy", NameResolver.load().id(NameResolver.NameKind.HERO, "Testy EN"));

        Map<String, JsonElement> cowScoresAfter = cowScores(workspace);
        assertTrue(cowScoresAfter.containsKey("testy"), "workspace cowScore.json completed");
        cowScoresAfter.remove("testy");
        assertEquals(cowScoresBefore, cowScoresAfter, "no other value changed");
    }

    private static java.net.URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The entries of the workspace's {@code cowScore.json} by id. */
    private static Map<String, JsonElement> cowScores(Path workspace) throws IOException {
        Map<String, JsonElement> byId = new HashMap<>();
        for (JsonElement entry : JsonParser.parseString(
                Files.readString(workspace.resolve("cowScore.json"), StandardCharsets.UTF_8)).getAsJsonArray()) {
            byId.put(entry.getAsJsonObject().get("id").getAsString(), entry);
        }
        assertTrue(byId.size() > 50, () -> "cowScore.json entries: " + byId.keySet());
        return byId;
    }
}
