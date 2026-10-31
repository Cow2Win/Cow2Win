package org.c2w.gui.manual;

import org.c2w.gui.action.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ManualChapters}: the chapter F1 opens for a stage. */
class ManualChaptersTest {

    @Test
    @DisplayName("Input, concept and output open their chapter; no stage opens \"Getting started\"")
    void forStage() {
        assertEquals("input", ManualChapters.forStage(Stage.INPUT));
        assertEquals("concept", ManualChapters.forStage(Stage.CONCEPT));
        assertEquals("output", ManualChapters.forStage(Stage.OUTPUT));
        assertEquals("getting-started", ManualChapters.forStage(null));
    }

    @Test
    @DisplayName("The other stages fall back to \"Getting started\"")
    void otherStages() {
        for (Stage stage : Stage.values()) {
            if (stage != Stage.INPUT && stage != Stage.CONCEPT && stage != Stage.OUTPUT) {
                assertEquals(ManualChapters.GETTING_STARTED, ManualChapters.forStage(stage), stage.name());
            }
        }
    }

    @Test
    @DisplayName("Ten distinct chapters, introduction first, about last")
    void allChapters() {
        assertEquals(10, ManualChapters.ALL.size());
        assertEquals(10, new HashSet<>(ManualChapters.ALL).size());
        assertEquals(ManualChapters.INTRODUCTION, ManualChapters.ALL.getFirst());
        assertEquals(ManualChapters.ABOUT, ManualChapters.ALL.getLast());
    }
}
