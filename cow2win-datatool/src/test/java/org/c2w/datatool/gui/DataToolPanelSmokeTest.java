package org.c2w.datatool.gui;

import com.google.gson.JsonParser;
import org.c2w.datatool.TestResources;
import org.c2w.datatool.data.DataSet;
import org.c2w.datatool.data.TableKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JTable;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds the window content (lightweight components only, so also headless) on a copy of the real data. */
class DataToolPanelSmokeTest {

    @TempDir
    Path temp;

    @Test
    void allTabsArePresentWithOneRowPerEntry() throws IOException {
        Path root = TestResources.copyTo(temp);
        DataToolPanel panel = new DataToolPanel(DataSet.load(root));

        List<String> titles = IntStream.range(0, panel.tabs().getTabCount()).mapToObj(panel.tabs()::getTitleAt).toList();
        assertEquals(List.of("Heroes", "Titans", "Pets", "War flags", "Fortifications", "Hero CowScore",
                "Titan CowScore", "Pet CowScore", "War flag CowScore", "Hero combos", "Titan templates"), titles);
        for (TableKind kind : TableKind.values()) {
            int entries = JsonParser.parseString(Files.readString(root.resolve(kind.relativePath()),
                    StandardCharsets.UTF_8)).getAsJsonArray().size();
            assertEquals(entries, panel.table(kind).getRowCount(), kind.title());
        }

        // One mark column per hero fortification on the hero tab, per titan fortification on the titan tab.
        List<String> heroColumns = columnNames(panel, TableKind.HERO_COWSCORE);
        List<String> titanColumns = columnNames(panel, TableKind.TITAN_COWSCORE);
        assertEquals(List.of("ID", "Name"), heroColumns.subList(0, 2));
        assertTrue(heroColumns.size() > 2 && titanColumns.size() > 2, heroColumns + " / " + titanColumns);
        assertEquals(heroColumns, columnNames(panel, TableKind.PET_COWSCORE));
        assertEquals(panel.data().table(TableKind.FORTIFICATIONS).rows().size(),
                heroColumns.size() - 2 + titanColumns.size() - 2, "every fortification has exactly one mark column");
    }

    private static List<String> columnNames(DataToolPanel panel, TableKind kind) {
        JTable table = panel.table(kind);
        return IntStream.range(0, table.getColumnCount())
                .mapToObj(i -> String.valueOf(table.getColumnModel().getColumn(i).getHeaderValue())).toList();
    }
}
