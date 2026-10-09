package org.c2w.gui.cowscore;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Hero;
import org.c2w.data.repository.HeroRepository;
import org.c2w.data.repository.TitanRepository;
import org.c2w.gui.hero.HeroCowScorePanel;
import org.c2w.gui.titan.TitanCowScorePanel;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The locked "Positive (buff)" combo box of a buff-matching hero/titan row - panels are never shown. */
class BuffLockTest {

    @TempDir
    Path workspace;

    @Test
    @DisplayName("Hero with matching role (Corvus at the foundry): combo box locked, shows \"Positive (buff)\"; other rows operable")
    void heroComboLockedOnBuffMatch() {
        HeroCowScorePanel panel = new HeroCowScorePanel(new HeroRepository(workspace));
        panel.selectEntry("corvus");

        JComboBox<?> foundry = combo(panel, "foundry");
        assertFalse(foundry.isEnabled());
        assertEquals(LanguageService.displayName("fortMark.BUFF"), foundry.getSelectedItem());
        assertNotNull(foundry.getToolTipText());
        assertTrue(combo(panel, "city-hall").isEnabled());
        assertFalse(panel.hasUnsavedChanges());
    }

    @Test
    @DisplayName("Titan with matching element (Ignis at the bastion of fire): combo box locked; other rows operable")
    void titanComboLockedOnBuffMatch() {
        TitanCowScorePanel panel = new TitanCowScorePanel(new TitanRepository(workspace));
        panel.selectEntry("ignis");

        JComboBox<?> bastion = combo(panel, "bastion-of-fire");
        assertFalse(bastion.isEnabled());
        assertEquals(LanguageService.displayName("fortMark.BUFF"), bastion.getSelectedItem());
        assertTrue(combo(panel, "moon-temple").isEnabled());
    }

    @Test
    @DisplayName("A mark on a buff-matching row is removed when the entry is opened and not saved")
    void existingBuffMarkRemovedOnOpen() throws IOException {
        HeroRepository repository = new HeroRepository(workspace);
        // In memory only (as from a hand-edited file) - loading would already clean it up.
        repository.saveCowScores(repository.findAll().stream()
                .map(h -> h.id().equals("corvus")
                        ? new Hero(h.id(), h.roles(), h.imagePath(),
                        new FortMarks(Map.of("foundry", FortMark.NEGATIVE, "city-hall", FortMark.POSITIVE)))
                        : h)
                .toList());
        HeroCowScorePanel panel = new HeroCowScorePanel(repository);
        assertFalse(panel.hasUnsavedChanges());

        panel.selectEntry("corvus");

        assertTrue(panel.hasUnsavedChanges());
        assertEquals(Map.of("city-hall", FortMark.POSITIVE), panel.currentMarks("corvus").marks());
        panel.save();
        assertEquals(Map.of("city-hall", FortMark.POSITIVE),
                new HeroRepository(workspace).findById("corvus").orElseThrow().fortMarks().marks());
    }

    private static JComboBox<?> combo(CowScorePanel panel, String fortificationId) {
        return CowScoreTestSupport.findAll((Container) panel.component(), JComboBox.class).stream()
                .filter(c -> ("mark:" + fortificationId).equals(c.getName())).findFirst().orElseThrow();
    }
}
