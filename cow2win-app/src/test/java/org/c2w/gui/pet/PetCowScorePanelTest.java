package org.c2w.gui.pet;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Pet;
import org.c2w.data.repository.PetRepository;
import org.c2w.gui.cowscore.CowScoreTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** {@link PetCowScorePanel} with a real pet catalog in a temporary workspace - headless, never shown. */
class PetCowScorePanelTest {

    @TempDir
    Path workspace;

    @Test
    @DisplayName("Unsaved after a checkbox click, saved to petCowScore.json, unsaved again after restoring the defaults")
    void unsavedSaveAndRestoreDefaults() throws Exception {
        PetRepository repository = new PetRepository(workspace);
        PetCowScorePanel panel = new PetCowScorePanel(repository);
        AtomicInteger notifications = new AtomicInteger();
        panel.addChangeListener(e -> notifications.incrementAndGet());
        assertFalse(panel.hasUnsavedChanges());

        String petId = panel.selectedEntryId();
        FortMarks before = panel.currentMarks(petId);
        CowScoreTestSupport.findAll(panel, JCheckBox.class).get(0).doClick();

        assertTrue(panel.hasUnsavedChanges());
        assertTrue(notifications.get() > 0);
        FortMarks changed = panel.currentMarks(petId);
        assertNotEquals(before, changed);

        panel.save();
        assertFalse(panel.hasUnsavedChanges());
        Pet reloaded = new PetRepository(workspace).findById(petId).orElseThrow();
        assertEquals(changed, reloaded.fortMarks());

        panel.restoreDefaults();
        assertTrue(panel.hasUnsavedChanges());
        Map<String, FortMarks> defaults = repository.loadDefaultCowScores();
        for (Pet pet : repository.findAll()) {
            assertEquals(positiveOnly(defaults.getOrDefault(pet.id(), FortMarks.NONE)), panel.currentMarks(pet.id()), pet.id());
        }
    }

    private static FortMarks positiveOnly(FortMarks fortMarks) {
        Map<String, FortMark> marks = new LinkedHashMap<>();
        fortMarks.marks().forEach((id, mark) -> {
            if (mark == FortMark.POSITIVE) {
                marks.put(id, mark);
            }
        });
        return new FortMarks(marks);
    }
}
