package org.c2w.datatool.data;

import org.c2w.datatool.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One case per check rule, each on freshly loaded real data. */
class ValidationTest {

    @TempDir
    Path temp;
    DataSet data;

    @BeforeEach
    void load() throws IOException {
        data = DataSet.load(TestResources.copyTo(temp));
    }

    @Test
    void duplicateId() {
        Row pet = newPet("Albus Two");
        data.setValue(TableKind.PETS, pet, Fields.ID, "albus");
        assertError(TableKind.PETS, Fields.ID, "Duplicate id albus");
    }

    @Test
    void unknownReferences() {
        Row cowScore = data.addRow(TableKind.PET_COWSCORE);
        data.setValue(TableKind.PET_COWSCORE, cowScore, Fields.ID, "no-such-pet");
        assertError(TableKind.PET_COWSCORE, Fields.ID, "no-such-pet");

        Row combo = data.table(TableKind.HERO_COMBOS).findById("krista-lars");
        data.setValue(TableKind.HERO_COMBOS, combo, Fields.HERO_IDS, List.of("krista", "nobody"));
        assertError(TableKind.HERO_COMBOS, Fields.HERO_IDS, "unknown id nobody");

        Row template = data.table(TableKind.TITAN_TEMPLATES).rows().get(0);
        data.setValue(TableKind.TITAN_TEMPLATES, template, Fields.TITAN_IDS, List.of("eden", "no-titan"));
        assertError(TableKind.TITAN_TEMPLATES, Fields.TITAN_IDS, "unknown id no-titan");

        Row fortification = data.table(TableKind.FORTIFICATIONS).findById("bastion");
        data.setValue(TableKind.FORTIFICATIONS, fortification, Fields.PREREQUISITES, List.of("atlantis"));
        assertError(TableKind.FORTIFICATIONS, Fields.PREREQUISITES, "unknown id atlantis");

        Row marked = data.table(TableKind.HERO_COWSCORE).findById("fluffy");
        data.setValue(TableKind.HERO_COWSCORE, marked, Fields.mark("bastion-of-fire"), "POSITIVE");
        assertError(TableKind.HERO_COWSCORE, Fields.ID, "no HERO fortification");
    }

    @Test
    void countsAndBounds() {
        Row combo = data.table(TableKind.HERO_COMBOS).findById("krista-lars");
        data.setValue(TableKind.HERO_COMBOS, combo, Fields.HERO_IDS, List.of("krista"));
        assertError(TableKind.HERO_COMBOS, Fields.HERO_IDS, "1 entries, allowed 2-5");

        Row fortification = data.table(TableKind.FORTIFICATIONS).findById("bastion");
        data.setValue(TableKind.FORTIFICATIONS, fortification, Fields.STRATEGIC_IMPORTANCE, 11);
        assertError(TableKind.FORTIFICATIONS, Fields.STRATEGIC_IMPORTANCE, "out of range");
    }

    @Test
    void missingNameInOneLanguage() {
        Row pet = data.table(TableKind.PETS).findById("vex");
        data.setValue(TableKind.PETS, pet, Language.FR.field(), "");
        assertError(TableKind.PETS, Language.FR.field(), "Name FR is empty");
    }

    @Test
    void keyCollisionWithAUiText() {
        Row pet = newPet("Toolbar");
        data.setValue(TableKind.PETS, pet, Fields.ID, "toolbar.guild");
        assertError(TableKind.PETS, Fields.ID, "Key collision");
    }

    @Test
    void incompleteBuff() {
        Row fortification = data.table(TableKind.FORTIFICATIONS).findById("barracks");
        data.setValue(TableKind.FORTIFICATIONS, fortification, Fields.BUFF_KIND, Fields.BUFF_KIND_ROLE);
        data.setValue(TableKind.FORTIFICATIONS, fortification, Fields.BUFF_EFFECT, "ARMOR_INCREASE");
        data.setValue(TableKind.FORTIFICATIONS, fortification, Fields.BUFF_PERCENT, 3);
        assertError(TableKind.FORTIFICATIONS, Fields.BUFF_ROLE, "role missing");
    }

    @Test
    void sameMapPosition() {
        DataTable table = data.table(TableKind.FORTIFICATIONS);
        Row bastion = table.findById("bastion");
        Row barracks = table.findById("barracks");
        data.setValue(TableKind.FORTIFICATIONS, barracks, Fields.ROW, bastion.getInteger(Fields.ROW));
        data.setValue(TableKind.FORTIFICATIONS, barracks, Fields.COLUMN, bastion.getInteger(Fields.COLUMN));
        assertError(TableKind.FORTIFICATIONS, Fields.ROW, "Same map position");
    }

    @Test
    void aMissingImageIsOnlyAWarning() {
        newPet("Nimbus");
        data.setValue(TableKind.PETS, data.table(TableKind.PETS).findById("nimbus"), Fields.IMAGE, "Nimbus.png");
        List<Problem> problems = data.validate();
        assertTrue(problems.stream().anyMatch(p -> !p.isError() && p.message().contains("Nimbus.png is missing")),
                problems::toString);
        assertFalse(problems.stream().anyMatch(Problem::isError), problems::toString);
    }

    @Test
    void unusualIdsAndCharactersAreWarnings() {
        Row pet = newPet("Nimbus");
        data.setValue(TableKind.PETS, pet, Fields.ID, "Nimbus");
        Row titan = data.table(TableKind.TITANS).findById("tidus-and-gelo");
        data.setValue(TableKind.TITANS, titan, Language.EN.field(), "Tidus’ Gelo");
        List<Problem> problems = data.validate();
        assertTrue(problems.stream().anyMatch(p -> !p.isError() && p.message().contains("Nimbus deviates")),
                problems::toString);
        assertTrue(problems.stream().anyMatch(p -> !p.isError() && p.message().contains("rarely appears")),
                problems::toString);
        assertFalse(problems.stream().anyMatch(Problem::isError), problems::toString);
    }

    @Test
    void threeRolesAreLegitimate() {
        Row hero = data.table(TableKind.HEROES).findById("aidan");
        data.setValue(TableKind.HEROES, hero, Fields.ROLES, List.of("SUPPORT", "HEALER", "MAGE"));
        List<Problem> problems = data.validate();
        assertFalse(problems.stream().anyMatch(p -> Fields.ROLES.equals(p.field())), problems::toString);

        data.setValue(TableKind.HEROES, hero, Fields.ROLES, List.of());
        assertError(TableKind.HEROES, Fields.ROLES, "0 entries");
    }

    @Test
    void titanRoles() {
        Row titan = data.table(TableKind.TITANS).findById("hyperion");
        data.setValue(TableKind.TITANS, titan, Fields.ROLES, List.of("MAGE", "MAGE"));
        assertError(TableKind.TITANS, Fields.ROLES, "MAGE is listed twice");

        data.setValue(TableKind.TITANS, titan, Fields.ROLES, List.of("HEALER"));
        assertError(TableKind.TITANS, Fields.ROLES, "unknown id HEALER");

        data.setValue(TableKind.TITANS, titan, Fields.ROLES, List.of());
        assertError(TableKind.TITANS, Fields.ROLES, "0 entries");
    }

    private Row newPet(String name) {
        Row pet = data.addRow(TableKind.PETS);
        for (Language language : Language.values()) {
            data.setValue(TableKind.PETS, pet, language.field(), name);
        }
        return pet;
    }

    private void assertError(TableKind table, String field, String text) {
        List<Problem> problems = data.validate();
        assertTrue(problems.stream().anyMatch(p -> p.isError() && p.table() == table && field.equals(p.field())
                && p.message().contains(text)), () -> "expected error '" + text + "' in " + problems);
    }
}
