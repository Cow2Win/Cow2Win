package org.c2w.gui.stage;

import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.MainMenuBar;
import org.c2w.gui.StageStatus;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.DataStatus;
import org.c2w.service.DataStatus.Level;
import org.c2w.service.DataStatus.MemberFinding;
import org.c2w.service.DataStatus.MemberState;
import org.c2w.service.DataStatus.StageResult;
import org.c2w.service.DataStatus.TeamDeviation;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link InputStageView}: action list, member table and member section - headless, never shown. */
class InputStageViewTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private InputStageView view;
    /** Members whose team assignment the view opened. */
    private final java.util.List<String> openedTeamEntries = new java.util.ArrayList<>();

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
        Guild testGuild = MemberOverviewModelTest.testGuild();
        context.setGuild(new Guild(context.guild().id(), context.guild().name(), testGuild.members(),
                context.guild().gameGuildId()));
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
        view = new InputStageView(context, actions, openedTeamEntries::add);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("The action list holds the entries of the input menu, in order, and nothing else")
    void actionList() {
        assertEquals(MainMenuBar.menuFor(Stage.INPUT).allActionIds(),
                view.actionList().rows().stream().map(row -> ((AppAction) row.action()).id()).toList());
    }

    @Test
    @DisplayName("The table lists every member, sorted by total power descending")
    void table() {
        assertEquals(3, view.table().getRowCount());
        assertEquals("Anna", view.table().getValueAt(0, MemberTableModel.COLUMN_MEMBER));
        assertEquals(4_000_000, view.table().getValueAt(0, MemberTableModel.COLUMN_TOTAL_POWER));
        assertEquals("Bert", view.table().getValueAt(1, MemberTableModel.COLUMN_MEMBER));
    }

    @Test
    @DisplayName("No selection shows the hint; selecting a member shows its name and its teams")
    void memberSection() {
        String hint = LanguageService.displayName("stageInfo.member.none");
        assertTrue(view.memberSectionTexts().stream().anyMatch(text -> text.contains(hint)));

        view.selectMember("anna");
        List<String> texts = view.memberSectionTexts();
        assertTrue(texts.contains("Anna"), texts.toString());
        assertTrue(texts.stream().anyMatch(text -> text.startsWith(LanguageService.displayName("stageInfo.member.team", 2))),
                texts.toString());
        assertTrue(texts.stream().noneMatch(text -> text.contains(hint)));

        view.selectMember("carl");
        assertTrue(view.memberSectionTexts().stream().anyMatch(text -> text.contains(
                LanguageService.displayName("stageInfo.member.noTeams", LanguageService.displayName("fortificationMap.showHeroes")))));
    }

    @Test
    @DisplayName("Switching the fortification type shows the titan teams in table and info panel, keeping the selection")
    void fortificationTypeSwitch() {
        view.selectMember("anna");
        context.setFortificationType(FortificationType.TITAN);

        assertEquals("Anna", view.table().getValueAt(0, MemberTableModel.COLUMN_MEMBER));
        assertEquals(500_000, view.table().getValueAt(0, MemberTableModel.COLUMN_TOTAL_POWER));
        assertEquals(1, view.table().getValueAt(0, MemberTableModel.COLUMN_TEAMS));
        List<String> texts = view.memberSectionTexts();
        assertTrue(texts.contains("Anna"), texts.toString());
        assertTrue(texts.stream().anyMatch(text -> text.contains(GuiUtils.NUMBER_FORMAT.format(500_000))), texts.toString());
        assertTrue(texts.stream().noneMatch(text -> text.startsWith(LanguageService.displayName("stageInfo.member.team", 2))),
                "only one titan team: " + texts);
    }

    @Test
    @DisplayName("The table texts take the color of the selected fortification type, the member count keeps its color")
    void tableTextColorFollowsFortificationType() {
        java.awt.Color countColor = view.countLabel().getForeground();
        assertEquals(FortificationTypeStyle.color(FortificationType.HERO), memberCellColor(false));
        assertEquals(FortificationTypeStyle.color(FortificationType.HERO), memberCellColor(true), "selected row");

        context.setFortificationType(FortificationType.TITAN);
        assertEquals(FortificationTypeStyle.color(FortificationType.TITAN), memberCellColor(false));
        assertEquals(FortificationTypeStyle.color(FortificationType.TITAN), memberCellColor(true), "selected row");
        assertEquals(countColor, view.countLabel().getForeground());

        context.setFortificationType(FortificationType.HERO);
        assertEquals(FortificationTypeStyle.color(FortificationType.HERO), memberCellColor(false));
    }

    /** Foreground of the rendered member cell in the first row. */
    private java.awt.Color memberCellColor(boolean selected) {
        javax.swing.JTable table = view.table();
        Object value = table.getValueAt(0, MemberTableModel.COLUMN_MEMBER);
        return table.getCellRenderer(0, MemberTableModel.COLUMN_MEMBER)
                .getTableCellRendererComponent(table, value, selected, false, 0, MemberTableModel.COLUMN_MEMBER)
                .getForeground();
    }

    private static DataStatus statusWithFindings() {
        StageResult ok = new StageResult(Level.OK, List.of());
        LocalDate logDay = LocalDate.of(2026, 10, 1);
        return new DataStatus(ok, ok, ok, logDay, 3, 1, 1, 30, Map.of(
                "anna", new MemberFinding(MemberState.STALE_JOURNAL, logDay.minusDays(5), 9, logDay,
                        List.of(new TeamDeviation(TeamKind.HERO, 1, 1_000_000, 1_100_000))),
                "bert", new MemberFinding(MemberState.TOO_OLD, null, 40, null, List.of()),
                "carl", new MemberFinding(MemberState.NO_TEAMS, null, null, null, List.of())));
    }

    private MemberTableModel.StatusValue statusOf(String name) {
        for (int row = 0; row < view.table().getRowCount(); row++) {
            if (name.equals(view.table().getValueAt(row, MemberTableModel.COLUMN_MEMBER))) {
                return (MemberTableModel.StatusValue) view.table().getValueAt(row, MemberTableModel.COLUMN_STATUS);
            }
        }
        return null;
    }

    @Test
    @DisplayName("Traffic light per row: red per journal, orange too old, green without finding (also without teams)")
    void trafficLights() {
        assertEquals(StageStatus.NONE, statusOf("Anna").light(), "no data status yet");
        view.setDataStatus(statusWithFindings());

        assertEquals(StageStatus.ACTION_NEEDED, statusOf("Anna").light());
        assertEquals(LanguageService.displayName("memberOverview.stale.journal",
                InfoSections.shortDate(LocalDate.of(2026, 10, 1))), statusOf("Anna").tooltip());
        assertEquals(StageStatus.ATTENTION, statusOf("Bert").light());
        assertEquals(LanguageService.displayName("memberOverview.stale.days", 40), statusOf("Bert").tooltip());
        assertEquals(StageStatus.OK, statusOf("Carl").light());
        assertEquals(LanguageService.displayName("memberOverview.stale.none"), statusOf("Carl").tooltip());
        assertEquals("", view.table().getColumnName(MemberTableModel.COLUMN_STATUS));
    }

    @Test
    @DisplayName("\"Show outdated only\" hides the green members; the count adds \"({k} shown)\"")
    void onlyStaleFilter() {
        view.setDataStatus(statusWithFindings());
        view.selectMember("carl");
        view.onlyStaleToggle().doClick();

        assertEquals(2, view.table().getRowCount());
        assertNull(statusOf("Carl"));
        assertTrue(view.countText().endsWith(LanguageService.displayName("memberOverview.shown", 2)), view.countText());
        assertTrue(view.countText().startsWith(LanguageService.displayName("memberOverview.count", 3, Guild.MAX_MEMBERS)));
        assertEquals(-1, view.table().getSelectedRow(), "the hidden member is no longer selected");

        view.onlyStaleToggle().doClick();
        assertEquals(3, view.table().getRowCount());
        assertEquals(LanguageService.displayName("memberOverview.count", 3, Guild.MAX_MEMBERS), view.countText());
    }

    @Test
    @DisplayName("The info panel names the team the defense log shows differently")
    void deviationLines() {
        view.setDataStatus(statusWithFindings());
        view.selectMember("anna");
        List<String> texts = view.memberSectionTexts();
        assertTrue(texts.stream().anyMatch(text -> text.contains(LanguageService.displayName(
                "memberOverview.stale.journal", InfoSections.shortDate(LocalDate.of(2026, 10, 1))))), texts.toString());
        assertTrue(texts.stream().anyMatch(text -> text.contains(JournalTexts.team(TeamKind.HERO, 1))
                && text.contains(JournalTexts.number(1_100_000))), texts.toString());

        view.selectMember("bert");
        assertTrue(view.memberSectionTexts().stream().noneMatch(text -> text.contains(JournalTexts.team(TeamKind.HERO, 1) + ":")));
    }

    @Test
    @DisplayName("\"+\" and \"-\" next to the count: \"-\" only with a selected member")
    void memberButtons() {
        assertTrue(view.addMemberButton().isEnabled());
        view.selectMember(null);
        assertFalse(view.removeMemberButton().isEnabled());

        view.selectMember("anna");
        assertTrue(view.removeMemberButton().isEnabled());
    }

    @Test
    @DisplayName("\"+\" is disabled at the maximum number of members, with the reason as tooltip")
    void addDisabledWhenFull() {
        List<org.c2w.data.model.GuildMember> members = new java.util.ArrayList<>();
        for (int i = 1; i <= Guild.MAX_MEMBERS; i++) {
            members.add(new org.c2w.data.model.GuildMember("m" + i, "M" + i, List.of(), List.of()));
        }
        context.setGuild(context.guild().withMembers(members));
        context.setGuildDirty(true); // fires dirtyStateChanged - the view refreshes

        assertFalse(view.addMemberButton().isEnabled());
        assertEquals(LanguageService.displayName("common.maxMembers", Guild.MAX_MEMBERS),
                view.addMemberButton().getToolTipText());
    }

    @Test
    @DisplayName("Enter on a member opens the team assignment for this member only")
    void enterOpensTeamEntryForMember() {
        view.selectMember("bert");
        view.table().getActionMap().get("c2w.openTeamEntry").actionPerformed(null);

        assertEquals(List.of("bert"), openedTeamEntries);
    }
}
