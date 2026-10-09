package org.c2w.gui.stage;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.TitanTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link MemberOverviewModel} (GUI-free) and the sorting of {@link MemberTableModel}. */
class MemberOverviewModelTest {

    private static final LocalDate SEP_20 = LocalDate.of(2026, 9, 20);
    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);

    /** Anna: two hero teams (one without date), one titan team; Bert: one hero team only; Carl: no teams. */
    static Guild testGuild() {
        GuildMember anna = new GuildMember("anna", "Anna",
                List.of(new HeroTeam("anna", 0, List.of(), null, null, 1_000_000, SEP_20),
                        new HeroTeam("anna", 1, List.of(), null, null, 3_000_000, null)),
                List.of(new TitanTeam("anna", 0, List.of(), 500_000, OCT_01)));
        GuildMember bert = new GuildMember("bert", "Bert",
                List.of(new HeroTeam("bert", 0, List.of(), null, null, 2_500_000, OCT_01)), List.of());
        GuildMember carl = new GuildMember("carl", "Carl", List.of(), List.of());
        return new Guild("alpha", "Alpha", List.of(anna, bert, carl));
    }

    @Test
    @DisplayName("Per member: team count, total power, strongest team and newest change - for heroes and titans")
    void rows() {
        List<MemberOverviewModel.MemberRow> heroes = MemberOverviewModel.rows(testGuild(), FortificationType.HERO);
        assertEquals(List.of(
                new MemberOverviewModel.MemberRow("anna", "Anna", 2, 4_000_000, 3_000_000, SEP_20),
                new MemberOverviewModel.MemberRow("bert", "Bert", 1, 2_500_000, 2_500_000, OCT_01),
                new MemberOverviewModel.MemberRow("carl", "Carl", 0, 0, 0, null)), heroes);

        List<MemberOverviewModel.MemberRow> titans = MemberOverviewModel.rows(testGuild(), FortificationType.TITAN);
        assertEquals(List.of(
                new MemberOverviewModel.MemberRow("anna", "Anna", 1, 500_000, 500_000, OCT_01),
                new MemberOverviewModel.MemberRow("bert", "Bert", 0, 0, 0, null),
                new MemberOverviewModel.MemberRow("carl", "Carl", 0, 0, 0, null)), titans);
    }

    @Test
    @DisplayName("Sorting: total power numerically, dates chronologically with unknown dates last")
    void sorting() {
        MemberTableModel model = new MemberTableModel();
        model.setRows(MemberOverviewModel.rows(testGuild(), FortificationType.HERO));
        TableRowSorter<MemberTableModel> sorter = new TableRowSorter<>(model);

        sorter.setSortKeys(List.of(new RowSorter.SortKey(MemberTableModel.COLUMN_TOTAL_POWER, SortOrder.DESCENDING)));
        assertEquals(List.of("anna", "bert", "carl"), sortedIds(model, sorter));

        sorter.setSortKeys(List.of(new RowSorter.SortKey(MemberTableModel.COLUMN_TOTAL_POWER, SortOrder.ASCENDING)));
        assertEquals(List.of("carl", "bert", "anna"), sortedIds(model, sorter));

        sorter.setSortKeys(List.of(new RowSorter.SortKey(MemberTableModel.COLUMN_LAST_MODIFIED, SortOrder.ASCENDING)));
        assertEquals(List.of("anna", "bert", "carl"), sortedIds(model, sorter), "Sep 20, Oct 1, unknown last");
    }

    private static List<String> sortedIds(MemberTableModel model, TableRowSorter<MemberTableModel> sorter) {
        List<String> ids = new ArrayList<>();
        for (int view = 0; view < sorter.getViewRowCount(); view++) {
            ids.add(model.row(sorter.convertRowIndexToModel(view)).memberId());
        }
        return ids;
    }
}
