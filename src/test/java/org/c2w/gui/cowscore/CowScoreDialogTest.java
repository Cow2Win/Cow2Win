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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    @DisplayName("Five tabs in the order heroes, titans, pets, war flags, hero combos")
    void tabOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES));

        List<CowScoreTab> tabs = List.of(CowScoreTab.HEROES, CowScoreTab.TITANS, CowScoreTab.PETS, CowScoreTab.WAR_FLAGS,
                CowScoreTab.HERO_COMBOS);
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
            JComboBox<FortMark> combo = firstEnabledCombo(dialog.panel(CowScoreTab.HEROES));
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
            JComboBox<FortMark> combo = firstEnabledCombo(dialog.panel(CowScoreTab.HEROES));
            combo.setSelectedItem(combo.getSelectedItem() == FortMark.NEGATIVE ? FortMark.POSITIVE : FortMark.NEGATIVE);
            assertTrue(dialog.saveAll());
        });
        assertEquals(1, calls[0]);
    }

    @Test
    @DisplayName("Every tab has the same list width - at least as wide as the longest war flag name")
    void sameListWidthEverywhere() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES));

        java.util.Set<Integer> widths = new java.util.HashSet<>();
        for (CowScoreTab tab : CowScoreTab.values()) {
            JSplitPane split = CowScoreTestSupport.findAll((Container) dialog.panel(tab).component(), JSplitPane.class).get(0);
            widths.add(split.getLeftComponent().getPreferredSize().width);
        }
        assertEquals(1, widths.size(), "one list width for all tabs: " + widths);
        int width = widths.iterator().next();
        JList<String> probe = new JList<>();
        FontMetrics metrics = probe.getFontMetrics(probe.getFont());
        int longestWarFlag = dialog.panel(CowScoreTab.WAR_FLAGS).listLabels().stream()
                .mapToInt(metrics::stringWidth).max().orElseThrow();
        assertTrue(width >= Math.min(longestWarFlag, CowScoreLayout.MAX_LIST_WIDTH), width + " < " + longestWarFlag);
        assertTrue(width >= CowScoreLayout.MIN_LIST_WIDTH && width <= CowScoreLayout.MAX_LIST_WIDTH);
    }

    @Test
    @DisplayName("Heroes tab: every fortification fits without a vertical scroll bar (screen large enough)")
    void heroDetailFitsWithoutScrolling() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES);
            dialog.validate();
        });
        GraphicsConfiguration screen = dialog.getGraphicsConfiguration();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        assumeTrue(dialog.getHeight() < screen.getBounds().height - insets.top - insets.bottom, "screen too small");

        JScrollPane detail = dialog.panel(CowScoreTab.HEROES).detailScrollPane();
        assertTrue(detail.getViewport().getExtentSize().height >= detail.getViewport().getView().getPreferredSize().height);
        assertTrue(dialog.getHeight() >= 560);
    }

    @Test
    @DisplayName("Column headers: heroes and titans Fortification | Buff | Rating, pets and war flags Fortification | Positive rating")
    void columnHeaders() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES));

        List<String> withBuff = List.of(LanguageService.displayName("fortMarks.column.fortification"),
                LanguageService.displayName("fortMarks.column.buff"), LanguageService.displayName("fortMarks.column.rating"));
        List<String> positiveOnly = List.of(LanguageService.displayName("fortMarks.column.fortification"),
                LanguageService.displayName("fortMarks.column.positiveRating"));
        assertEquals(withBuff, columnTitles(CowScoreTab.HEROES));
        assertEquals(withBuff, columnTitles(CowScoreTab.TITANS));
        assertEquals(positiveOnly, columnTitles(CowScoreTab.PETS));
        assertEquals(positiveOnly, columnTitles(CowScoreTab.WAR_FLAGS));
    }

    @Test
    @DisplayName("The information area shows what the controls of the active tab do, with the real percentages")
    void infoFollowsTab() throws Exception {
        SwingUtilities.invokeAndWait(() -> dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES));
        assertEquals(CowScoreDialog.infoText(CowScoreTab.HEROES), dialog.infoText());

        SwingUtilities.invokeAndWait(() -> dialog.selectTab(CowScoreTab.WAR_FLAGS));
        assertEquals(CowScoreDialog.infoText(CowScoreTab.WAR_FLAGS), dialog.infoText());
        for (CowScoreTab tab : CowScoreTab.values()) {
            String text = CowScoreDialog.infoText(tab);
            assertFalse(text.isBlank() || text.contains("{") || text.equals(tab.textKey() + ".info"), tab + ": " + text);
            assertTrue(text.contains("%"), tab + ": " + text);
        }
    }

    @Test
    @DisplayName("The information area is equally high in every tab: three lines plus padding")
    void infoAreaHeight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            dialog = CowScoreDialog.open(null, catalog, CowScoreTab.HEROES);
            dialog.validate();
        });

        JTextArea heroes = dialog.infoArea(CowScoreTab.HEROES);
        Insets insets = heroes.getInsets();
        assertEquals(new Insets(8, 14, 8, 14), insets);
        int expected = 3 * heroes.getFontMetrics(heroes.getFont()).getHeight() + insets.top + insets.bottom;
        for (CowScoreTab tab : CowScoreTab.values()) {
            assertEquals(expected, dialog.infoArea(tab).getPreferredSize().height, tab.name());
        }
    }

    private List<String> columnTitles(CowScoreTab tab) {
        JPanel header = CowScoreTestSupport.findAll((Container) dialog.panel(tab).component(), JPanel.class).stream()
                .filter(p -> "columnHeader".equals(p.getName())).findFirst().orElseThrow();
        return CowScoreTestSupport.findAll(header, JLabel.class).stream().map(JLabel::getText).toList();
    }

    /** The first combo box of {@code panel} that can be operated - a buff-matching row's combo box is locked. */
    @SuppressWarnings("unchecked")
    private static JComboBox<FortMark> firstEnabledCombo(CowScorePanel panel) {
        return CowScoreTestSupport.findAll((Container) panel.component(), JComboBox.class).stream()
                .filter(Component::isEnabled).findFirst().orElseThrow();
    }
}
