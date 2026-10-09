package org.c2w.gui;

import org.c2w.gui.action.Stage;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ProcessBar}: tiles, highlight, display only, traffic light, "next step" - headless, never shown. */
class ProcessBarTest {

    @Test
    @DisplayName("Three tiles: input, strategic concept, output - numbered 1 to 3, titled with the stage names")
    void tiles() {
        ProcessBar bar = new ProcessBar();

        List<ProcessBar.StageTile> tiles = bar.tiles();
        assertEquals(List.of(Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT), tiles.stream().map(ProcessBar.StageTile::stage).toList());
        assertEquals(List.of(1, 2, 3), tiles.stream().map(ProcessBar.StageTile::number).toList());
        for (ProcessBar.StageTile tile : tiles) {
            assertEquals(LanguageService.displayName(tile.stage().stageTextKey()), tile.title());
            assertEquals(LanguageService.displayName(tile.stage().subtitleKey()), tile.subtitle());
            assertEquals(tile.subtitle(), tile.getToolTipText());
        }
    }

    @Test
    @DisplayName("By default the tiles are display only: default cursor, no mouse listener except the tooltip's")
    void tilesAreNotClickable() {
        ProcessBar bar = new ProcessBar();

        for (ProcessBar.StageTile tile : bar.tiles()) {
            assertEquals(Cursor.DEFAULT_CURSOR, tile.getCursor().getType(), tile.stage().name());
            // setToolTipText registers the ToolTipManager as mouse listener - that one is expected.
            for (Object listener : tile.getMouseListeners()) {
                assertInstanceOf(ToolTipManager.class, listener, tile.stage().name());
            }
        }
    }

    @Test
    @DisplayName("Only available tiles are clickable: hand cursor, a click reports the stage and makes it active")
    void availableTilesAreClickable() {
        ProcessBar bar = new ProcessBar();
        List<Stage> selected = new ArrayList<>();
        bar.addStageSelectionListener(selected::add);
        bar.setActiveStage(Stage.INPUT);

        bar.setStageAvailable(Stage.CONCEPT, true);
        assertEquals(Cursor.HAND_CURSOR, bar.tile(Stage.CONCEPT).getCursor().getType());
        assertEquals(Cursor.DEFAULT_CURSOR, bar.tile(Stage.INPUT).getCursor().getType());
        assertEquals(Cursor.DEFAULT_CURSOR, bar.tile(Stage.OUTPUT).getCursor().getType());

        click(bar.tile(Stage.OUTPUT));
        assertEquals(List.of(), selected, "a tile without a view reports nothing");
        assertEquals(Stage.INPUT, bar.activeStage());

        click(bar.tile(Stage.CONCEPT));
        assertEquals(List.of(Stage.CONCEPT), selected);
        assertEquals(Stage.CONCEPT, bar.activeStage());

        bar.setStageAvailable(Stage.CONCEPT, false);
        assertEquals(Cursor.DEFAULT_CURSOR, bar.tile(Stage.CONCEPT).getCursor().getType());
        click(bar.tile(Stage.CONCEPT));
        assertEquals(List.of(Stage.CONCEPT), selected);
    }

    private static void click(Component tile) {
        tile.dispatchEvent(new MouseEvent(tile, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                5, 5, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    @DisplayName("The strategic concept is active by default; setActiveStage moves the highlight")
    void activeStage() {
        ProcessBar bar = new ProcessBar();
        assertEquals(Stage.CONCEPT, bar.activeStage());
        assertEquals(1, bar.tiles().stream().filter(ProcessBar.StageTile::isActive).count());

        bar.setActiveStage(Stage.INPUT);
        assertEquals(Stage.INPUT, bar.activeStage());
        assertTrue(bar.tile(Stage.INPUT).isActive());
        assertFalse(bar.tile(Stage.CONCEPT).isActive());
    }

    @Test
    @DisplayName("No traffic light by default; a status is kept and has the expected color")
    void status() {
        ProcessBar bar = new ProcessBar();
        bar.tiles().forEach(tile -> assertEquals(StageStatus.NONE, tile.status()));

        bar.setStatus(Stage.OUTPUT, StageStatus.ACTION_NEEDED);
        assertEquals(StageStatus.ACTION_NEEDED, bar.tile(Stage.OUTPUT).status());

        assertNull(StageStatus.NONE.color());
        assertEquals(IconLoader.GREEN, StageStatus.OK.color());
        assertEquals(ContextBar.UNSAVED_COLOR, StageStatus.ATTENTION.color());
        assertEquals(IconLoader.RED, StageStatus.ACTION_NEEDED.color());
    }

    @Test
    @DisplayName("\"Next step\" is hidden by default and shown once it gets an action")
    void nextStep() {
        ProcessBar bar = new ProcessBar();
        assertFalse(bar.isNextStepVisible());

        bar.setNextStepAction(new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
            }
        });
        assertTrue(bar.isNextStepVisible());

        bar.setNextStepAction(null);
        assertFalse(bar.isNextStepVisible());
    }

    @Test
    @DisplayName("Texts that do not fit are cut with \"…\"")
    void ellipsize() {
        FontMetrics metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
                .getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        String text = "Strategic concept";
        assertEquals(text, ProcessBar.ellipsize(text, metrics, 1000));
        String cut = ProcessBar.ellipsize(text, metrics, metrics.stringWidth(text) / 2);
        assertTrue(cut.endsWith("…"), cut);
        assertTrue(metrics.stringWidth(cut) <= metrics.stringWidth(text) / 2, cut);
    }

    @Test
    @DisplayName("setSubtitle replaces the shown subtitle; the tooltip shows short text and static subtitle")
    void subtitle() {
        ProcessBar bar = new ProcessBar();
        ProcessBar.StageTile tile = bar.tile(Stage.INPUT);
        String staticSubtitle = tile.subtitle();
        assertEquals(staticSubtitle, tile.shownSubtitle());
        assertEquals(staticSubtitle, tile.getToolTipText());

        bar.setSubtitle(Stage.INPUT, "2 outdated per journal");
        assertEquals("2 outdated per journal", tile.shownSubtitle());
        assertTrue(tile.getToolTipText().contains("2 outdated per journal"), tile.getToolTipText());
        assertTrue(tile.getToolTipText().contains(staticSubtitle), tile.getToolTipText());

        bar.setSubtitle(Stage.INPUT, null);
        assertEquals(staticSubtitle, tile.shownSubtitle());
        assertEquals(staticSubtitle, tile.getToolTipText());
    }

    @Test
    @DisplayName("\"Next step\" shows the name of its action")
    void nextStepText() {
        ProcessBar bar = new ProcessBar();
        AbstractAction action = new AbstractAction("Next step → Output") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
            }
        };
        bar.setNextStepAction(action);
        assertEquals("Next step → Output", bar.nextStepText().replace("->", "→"));
        action.putValue(Action.NAME, "Next step → Input");
        assertEquals("Next step → Input", bar.nextStepText().replace("->", "→"));
    }
}
