package org.c2w.gui.hero;

import org.c2w.data.model.ComboSource;
import org.c2w.data.model.Hero;
import org.c2w.data.model.TeamCombo;
import org.c2w.data.model.TeamCombos;
import org.c2w.data.repository.HeroComboRepository;
import org.c2w.data.repository.HeroRepository;
import org.c2w.domain.TeamScoreCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link HeroComboPanel} with the real hero catalog and shipped combos in a temporary workspace - never shown. */
class HeroComboPanelTest {

    @TempDir
    Path workspace;

    private HeroComboRepository repository;
    private HeroComboPanel panel;
    private TeamCombos registeredBefore;

    @BeforeEach
    void createPanel() {
        registeredBefore = TeamScoreCalculator.heroCombos();
        HeroRepository heroes = new HeroRepository(workspace);
        repository = new HeroComboRepository(workspace, heroes.findAll().stream().map(Hero::id).toList());
        panel = new HeroComboPanel(repository, heroes);
    }

    @AfterEach
    void restoreCalculator() {
        TeamScoreCalculator.setHeroCombos(registeredBefore);
    }

    @Test
    @DisplayName("Changing a shipped combo makes it the user's own and the tab unsaved")
    void editingShippedComboMakesItUser() {
        panel.select("sebastian-nebula");
        assertEquals(ComboSource.C2W, panel.selectedDraft().source);
        assertFalse(panel.hasUnsavedChanges());

        panel.nameField().setText("Light and dark");

        assertEquals(ComboSource.USER, panel.selectedDraft().source);
        assertEquals("Light and dark", panel.selectedDraft().name);
        assertTrue(panel.hasUnsavedChanges());
    }

    @Test
    @DisplayName("A new combo gets its id from the members on saving - with a suffix if it is taken; it stays afterwards")
    void newComboId() throws IOException {
        panel.select("sebastian-nebula");
        panel.memberCombos().get(1).setSelectedItem("krista"); // id stays "sebastian-nebula"
        panel.newCombo();
        panel.memberCombos().get(0).setSelectedItem("sebastian");
        panel.memberCombos().get(1).setSelectedItem("nebula");
        HeroComboPanel.Draft created = panel.selectedDraft();
        assertNull(created.id);

        panel.save();

        assertEquals("sebastian-nebula-2", created.id);
        assertEquals(ComboSource.USER, created.source);
        panel.select("sebastian-nebula-2");
        panel.memberCombos().get(2).setSelectedItem("lars");
        panel.save();
        assertEquals("sebastian-nebula-2", created.id, "the id does not follow the members");
        assertEquals("sebastian-nebula", HeroComboPanel.newId(List.of("sebastian", "nebula"), Set.of()));
        assertEquals("a-b-3", HeroComboPanel.newId(List.of("a", "b"), Set.of("a-b", "a-b-2")));
    }

    @Test
    @DisplayName("Shipped combos cannot be deleted, own combos can")
    void deleteOnlyOwnCombos() {
        panel.select("sebastian-nebula");
        assertFalse(panel.deleteButton().isEnabled());
        int count = panel.drafts().size();
        panel.deleteSelected();
        assertEquals(count, panel.drafts().size());

        panel.newCombo();
        assertTrue(panel.deleteButton().isEnabled());
        panel.deleteSelected();
        assertEquals(count, panel.drafts().size());
    }

    @Test
    @DisplayName("Invalid combos (one hero, a hero twice, same heroes as another) are not saved")
    void invalidCombosRejected() throws IOException {
        panel.newCombo();
        panel.memberCombos().get(0).setSelectedItem("krista");
        assertNotNull(panel.problemKey(panel.selectedDraft()));
        assertThrows(IOException.class, panel::save);

        panel.memberCombos().get(1).setSelectedItem("krista");
        assertEquals("heroCombos.problem.duplicateHero", panel.problemKey(panel.selectedDraft()));
        assertThrows(IOException.class, panel::save);

        panel.memberCombos().get(0).setSelectedItem("sebastian");
        panel.memberCombos().get(1).setSelectedItem("nebula");
        assertEquals("heroCombos.problem.sameMembers", panel.problemKey(panel.selectedDraft()));
        assertThrows(IOException.class, panel::save);
        assertTrue(panel.hasUnsavedChanges());

        panel.memberCombos().get(1).setSelectedItem("lars");
        assertNull(panel.problemKey(panel.selectedDraft()));
        panel.save();
        assertFalse(panel.hasUnsavedChanges());
    }

    @Test
    @DisplayName("Restore defaults resets the shipped combos and keeps the user's own")
    void restoreDefaultsKeepsOwnCombos() throws IOException {
        panel.select("sebastian-nebula");
        panel.nameField().setText("Renamed");
        panel.newCombo();
        panel.memberCombos().get(0).setSelectedItem("krista");
        panel.memberCombos().get(1).setSelectedItem("nebula");
        panel.save();

        panel.restoreDefaults();

        HeroComboPanel.Draft shipped = panel.drafts().stream().filter(d -> "sebastian-nebula".equals(d.id)).findFirst().orElseThrow();
        assertEquals("", shipped.name);
        assertEquals(ComboSource.C2W, shipped.source);
        assertTrue(panel.drafts().stream().anyMatch(d -> "krista-nebula".equals(d.id)), "own combo kept");
        assertEquals(repository.defaultCombos().combos().size() + 1, panel.drafts().size());
        assertTrue(panel.hasUnsavedChanges());
    }

    @Test
    @DisplayName("Active off sets the deactivation date to today, on removes it")
    void activeToggle() {
        panel.select("sebastian-nebula");
        assertTrue(panel.activeCheckBox().isSelected());

        panel.activeCheckBox().doClick();
        assertEquals(LocalDate.now(), panel.selectedDraft().deactivated);
        assertEquals(ComboSource.USER, panel.selectedDraft().source);

        panel.activeCheckBox().doClick();
        assertNull(panel.selectedDraft().deactivated);
    }

    @Test
    @DisplayName("After saving, the CowScore uses the new combos right away")
    void saveRegistersCombos() throws IOException {
        panel.newCombo();
        panel.memberCombos().get(0).setSelectedItem("krista");
        panel.memberCombos().get(1).setSelectedItem("nebula");

        panel.save();

        assertTrue(TeamScoreCalculator.heroCombos().combos().stream().map(TeamCombo::id).anyMatch("krista-nebula"::equals));
        assertEquals(repository.combos(), TeamScoreCalculator.heroCombos());
    }
}
