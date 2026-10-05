package org.c2w.gui.cowscore;

import org.c2w.data.model.FortMark;
import org.c2w.data.repository.Catalog;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** {@link CowScoreDialog} with real catalogs in a temporary workspace. Skipped without a display. */
class CowScoreDialogTest {

    @TempDir
    Path workspace;

    private Catalog catalog;
    private CowScoreDialog dialog;

    @BeforeEach
    void createCatalog() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        catalog = new Catalog(workspace);
    }

    @AfterEach
    void disposeDialog() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
    }

    @Test
    @DisplayName("Four tabs in the order heroes, titans, pets, war flags")
    void tabOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES));

        List<CowScoreTab> tabs = List.of(CowScoreTab.HEROES, CowScoreTab.TITANS, CowScoreTab.PETS, CowScoreTab.WAR_FLAGS);
        assertEquals(tabs, List.of(CowScoreTab.values()));
        for (CowScoreTab tab : tabs) {
            assertEquals(LanguageService.displayName(tab.textKey()), dialog.tabTitle(tab));
        }
    }

    @Test
    @DisplayName("open selects the requested tab; a second open returns the same dialog and switches the tab")
    void singleInstance() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = CowScoreDialog.open(null, catalog, CowScoreTab.TITANS));
        assertEquals(CowScoreTab.TITANS, dialog.selectedTab());

        CowScoreDialog[] second = new CowScoreDialog[1];
        SwingUtilities.invokeAndWait(() -> second[0] = CowScoreDialog.open(null, catalog, CowScoreTab.PETS));
        assertSame(dialog, second[0]);
        assertEquals(CowScoreTab.PETS, dialog.selectedTab());
    }

    @Test
    @DisplayName("A change in a tab puts a * in front of that tab's title only")
    void unsavedTabTitle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES);
            @SuppressWarnings("unchecked")
            JComboBox<FortMark> combo = CowScoreTestSupport.findAll(
                    (Container) dialog.panel(CowScoreTab.HEROES).component(), JComboBox.class).get(0);
            combo.setSelectedItem(combo.getSelectedItem() == FortMark.NEGATIVE ? FortMark.POSITIVE : FortMark.NEGATIVE);
        });

        assertEquals("*" + LanguageService.displayName(CowScoreTab.HEROES.textKey()), dialog.tabTitle(CowScoreTab.HEROES));
        assertEquals(LanguageService.displayName(CowScoreTab.TITANS.textKey()), dialog.tabTitle(CowScoreTab.TITANS));
    }

    @Test
    @DisplayName("Saving a changed tab runs the save callback; saving without changes does not")
    void saveCallback() throws Exception {
        int[] calls = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES, () -> calls[0]++);
            assertTrue(dialog.saveAll());
            assertEquals(0, calls[0]);
            @SuppressWarnings("unchecked")
            JComboBox<FortMark> combo = CowScoreTestSupport.findAll(
                    (Container) dialog.panel(CowScoreTab.HEROES).component(), JComboBox.class).get(0);
            combo.setSelectedItem(combo.getSelectedItem() == FortMark.NEGATIVE ? FortMark.POSITIVE : FortMark.NEGATIVE);
            assertTrue(dialog.saveAll());
        });
        assertEquals(1, calls[0]);
    }
}
