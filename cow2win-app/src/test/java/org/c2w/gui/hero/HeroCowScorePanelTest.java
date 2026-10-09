package org.c2w.gui.hero;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Hero;
import org.c2w.data.repository.HeroRepository;
import org.c2w.gui.cowscore.CowScoreTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** {@link HeroCowScorePanel} with a real hero catalog in a temporary workspace - headless, never shown. */
class HeroCowScorePanelTest {

    @TempDir
    Path workspace;

    @Test
    @DisplayName("Unsaved after a combo change, saved to cowScore.json, unsaved again after restoring the defaults")
    void unsavedSaveAndRestoreDefaults() throws Exception {
        HeroRepository repository = new HeroRepository(workspace);
        HeroCowScorePanel panel = new HeroCowScorePanel(repository);
        AtomicInteger notifications = new AtomicInteger();
        panel.addChangeListener(e -> notifications.incrementAndGet());
        assertFalse(panel.hasUnsavedChanges());

        String heroId = panel.selectedEntryId();
        FortMarks before = panel.currentMarks(heroId);
        @SuppressWarnings("unchecked")
        JComboBox<FortMark> combo = CowScoreTestSupport.findAll(panel, JComboBox.class).get(0);
        combo.setSelectedItem(combo.getSelectedItem() == FortMark.NEGATIVE ? FortMark.POSITIVE : FortMark.NEGATIVE);

        assertTrue(panel.hasUnsavedChanges());
        assertTrue(notifications.get() > 0);
        FortMarks changed = panel.currentMarks(heroId);
        assertNotEquals(before, changed);

        panel.save();
        assertFalse(panel.hasUnsavedChanges());
        Hero reloaded = new HeroRepository(workspace).findById(heroId).orElseThrow();
        assertEquals(changed, reloaded.fortMarks());

        panel.restoreDefaults();
        assertTrue(panel.hasUnsavedChanges());
        Map<String, FortMarks> defaults = repository.loadDefaultCowScores();
        for (Hero hero : repository.findAll()) {
            assertEquals(defaults.getOrDefault(hero.id(), FortMarks.NONE), panel.currentMarks(hero.id()), hero.id());
        }
    }

    @Test
    @DisplayName("Selecting the value a combo already shows changes nothing")
    void unchangedSelectionStaysSaved() {
        HeroCowScorePanel panel = new HeroCowScorePanel(new HeroRepository(workspace));
        JComboBox<?> combo = CowScoreTestSupport.findAll(panel, JComboBox.class).get(0);
        combo.setSelectedItem(combo.getSelectedItem());
        assertFalse(panel.hasUnsavedChanges());
    }
}
