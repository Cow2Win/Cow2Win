package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FortificationMapPanel} shows only the selected fortification type and keeps the selection - headless, never shown. */
class FortificationMapPanelTypeTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("Only fortifications of the selected type are on the map, each at its catalog cell; the summary follows the type")
    void mapFollowsFortificationType() {
        FortificationMapPanel map = new FortificationMapPanel(context);
        assertShowsOnly(map, FortificationType.HERO);
        assertInstanceOf(HeroLineupSummaryPanel.class, map.componentAt(0, 0));
        assertNull(map.componentAt(0, map.columns() - 1));

        context.setFortificationType(FortificationType.TITAN);
        assertShowsOnly(map, FortificationType.TITAN);
        assertNull(map.componentAt(0, 0));
        assertInstanceOf(TitanLineupSummaryPanel.class, map.componentAt(0, map.columns() - 1));
    }

    @Test
    @DisplayName("Empty rows and columns keep their size, so the map does not collapse without the other type")
    void gridStaysStable() {
        FortificationMapPanel map = new FortificationMapPanel(context);
        map.setSize(1000, 800);
        map.doLayout();

        int[][] dimensions = ((GridBagLayout) map.getLayout()).getLayoutDimensions();
        assertEquals(map.columns(), dimensions[0].length);
        assertEquals(map.rows(), dimensions[1].length);
        for (int width : dimensions[0]) {
            assertTrue(width > 0, "column width " + width);
        }
        for (int height : dimensions[1]) {
            assertTrue(height > 0, "row height " + height);
        }

        context.setFortificationType(FortificationType.TITAN);
        map.doLayout();
        int[][] titanDimensions = ((GridBagLayout) map.getLayout()).getLayoutDimensions();
        assertArrayEquals(dimensions[0], titanDimensions[0], "column widths");
        assertArrayEquals(dimensions[1], titanDimensions[1], "row heights");
    }

    @Test
    @DisplayName("Within one fortification type no cell holds two fortifications; Bastion and Shooting Range moved")
    void positionsUniquePerType() {
        List<Fortification> catalog = FortificationRepository.findAll();
        for (FortificationType type : FortificationType.values()) {
            List<Point> cells = new ArrayList<>();
            for (Fortification fort : catalog) {
                if (fort.type() == type) {
                    Point cell = new Point(fort.column(), fort.row());
                    assertFalse(cells.contains(cell), type + " cell " + cell + " used twice (" + fort.id() + ")");
                    cells.add(cell);
                }
            }
        }
        assertCell(catalog, "shooting-range", 3, 4);
        assertCell(catalog, "bastion", 3, 3);
    }

    @Test
    @DisplayName("A click selects, a second click clears, another fortification switches; switching the type clears")
    void selection() {
        FortificationMapPanel map = new FortificationMapPanel(context);
        List<Optional<Fortification>> notified = new ArrayList<>();
        map.addSelectionListener(notified::add);
        List<FortificationPanel> panels = fortificationPanels(map);
        Fortification first = panels.get(0).fortification();
        Fortification second = panels.get(1).fortification();

        map.toggleSelection(first);
        assertEquals(Optional.of(first), map.selectedFortification());
        assertTrue(panels.get(0).isSelected());

        map.toggleSelection(second);
        assertEquals(Optional.of(second), map.selectedFortification());
        assertFalse(panels.get(0).isSelected());
        assertTrue(panels.get(1).isSelected());

        map.toggleSelection(second);
        assertEquals(Optional.empty(), map.selectedFortification());

        map.toggleSelection(first);
        map.setShowChanges(true);
        assertEquals(Optional.of(first), map.selectedFortification(), "kept when the map is rebuilt");
        assertTrue(fortificationPanels(map).stream()
                .filter(p -> p.fortification().equals(first)).findFirst().orElseThrow().isSelected());

        context.setFortificationType(FortificationType.TITAN);
        assertEquals(Optional.empty(), map.selectedFortification());

        assertEquals(List.of(Optional.of(first), Optional.of(second), Optional.empty(),
                Optional.of(first), Optional.empty()), notified);
    }

    private static void assertCell(List<Fortification> catalog, String id, int row, int column) {
        Fortification fort = catalog.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
        assertEquals(row, fort.row(), id + " row");
        assertEquals(column, fort.column(), id + " column");
    }

    private static void assertShowsOnly(FortificationMapPanel map, FortificationType selected) {
        List<FortificationPanel> panels = fortificationPanels(map);
        long expected = FortificationRepository.findAll().stream().filter(f -> f.type() == selected).count();
        assertEquals(expected, panels.size());
        for (Fortification fort : FortificationRepository.findAll()) {
            JComponent component = map.componentAt(fort.row(), fort.column());
            if (fort.type() == selected) {
                FortificationPanel panel = assertInstanceOf(FortificationPanel.class, component, fort.id());
                assertEquals(fort, panel.fortification());
            }
        }
        for (FortificationPanel panel : panels) {
            assertEquals(selected, panel.fortification().type(), panel.fortification().id());
        }
    }

    private static List<FortificationPanel> fortificationPanels(FortificationMapPanel map) {
        List<FortificationPanel> panels = new ArrayList<>();
        for (int row = 0; row < map.rows(); row++) {
            for (int column = 0; column < map.columns(); column++) {
                JComponent component = map.componentAt(row, column);
                if (component instanceof FortificationPanel panel) {
                    panels.add(panel);
                }
            }
        }
        return panels;
    }
}
