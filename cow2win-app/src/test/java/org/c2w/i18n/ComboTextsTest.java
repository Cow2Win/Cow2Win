package org.c2w.i18n;

import org.c2w.data.model.ComboSource;
import org.c2w.data.model.TeamCombo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The display name of a team combo - see {@link ComboTexts}. */
class ComboTextsTest {

    private static final Map<String, String> GERMAN_NAMES = Map.of("krista", "Krista", "lars", "Lars", "orion", "Orion");

    private static TeamCombo combo(String name, ComboSource source, String... memberIds) {
        return new TeamCombo("some-id", name, List.of(memberIds), source, null);
    }

    @Test
    @DisplayName("without a custom name: the members' localized names joined by ' + ', in file order")
    void builtFromMemberNames() {
        assertEquals("Krista + Lars + Orion",
                ComboTexts.displayName(combo(null, ComboSource.C2W, "krista", "lars", "orion"), GERMAN_NAMES::get));
        assertEquals("Lars + Krista",
                ComboTexts.displayName(combo("  ", ComboSource.USER, "lars", "krista"), GERMAN_NAMES::get),
                "a blank name counts as no name");
    }

    @Test
    @DisplayName("a custom name wins and is shown as is")
    void customName() {
        assertEquals("Mein Lieblingsduo",
                ComboTexts.displayName(combo("Mein Lieblingsduo", ComboSource.USER, "krista", "lars"), GERMAN_NAMES::get));
    }

    @Test
    @DisplayName("null combo -> empty string")
    void nullCombo() {
        assertEquals("", ComboTexts.displayName(null));
    }
}
