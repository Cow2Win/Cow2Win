package org.c2w.gui.action;

import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ActionId} and {@link Stage}: every text key exists in all three language files. */
class ActionIdTest {

    private static final List<String> LANGUAGES = List.of("deutsch", "english", "francais");

    @Test
    @DisplayName("Every action id has a stage and a text key present in every language")
    void everyActionIdHasStageAndTextKey() {
        for (ActionId id : ActionId.values()) {
            assertNotNull(id.stage(), id + " has no stage");
            assertNotNull(id.textKey(), id + " has no text key");
            assertKeyInAllLanguages(id.textKey(), id.name());
        }
    }

    @Test
    @DisplayName("One COWSCORE action instead of the former four")
    void singleCowScoreAction() {
        assertEquals(Stage.MASTER_DATA, ActionId.COWSCORE.stage());
        for (String former : List.of("COWSCORE_HEROES", "COWSCORE_TITANS", "COWSCORE_PETS", "COWSCORE_WAR_FLAGS")) {
            assertThrows(IllegalArgumentException.class, () -> ActionId.valueOf(former), former);
        }
    }

    @Test
    @DisplayName("Every stage but GENERAL has a menu text, the three process stages also a stage text")
    void stageTextKeys() {
        for (Stage stage : Stage.values()) {
            if (stage == Stage.GENERAL) {
                assertNull(stage.menuTextKey());
                assertNull(stage.stageTextKey());
                continue;
            }
            assertKeyInAllLanguages(stage.menuTextKey(), stage.name());
            if (stage == Stage.MASTER_DATA) {
                assertNull(stage.stageTextKey());
            } else {
                assertKeyInAllLanguages(stage.stageTextKey(), stage.name());
            }
        }
    }

    private static void assertKeyInAllLanguages(String key, String owner) {
        for (String language : LANGUAGES) {
            assertNotNull(LanguageService.textIn(language, key),
                    owner + ": key '" + key + "' missing in " + language);
        }
    }
}
