package org.c2w.gui.guild;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The hero team assignment for ONE member (never shown). Skipped without a display. */
class MemberTeamEntryDialogTest {

    /** Column of the member name in the dialog's table (#, fortification, member, ...). */
    private static final int MEMBER_COLUMN = 2;

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildHeroEntryDialog dialog;

    @BeforeEach
    void openWorkspace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");

        Hero hero = context.catalog().heroes().findAll().get(0);
        // Anna: team 0 at the bastion, team 1 without fortification; Bert: team 0 at the foundry.
        GuildMember anna = new GuildMember("anna", "Anna", List.of(new HeroTeam("anna", 0, List.of(hero), 100_000),
                new HeroTeam("anna", 1, List.of(hero), 90_000)), List.of());
        GuildMember bert = new GuildMember("bert", "Bert", List.of(new HeroTeam("bert", 0, List.of(hero), 70_000)), List.of());
        Guild guild = context.guild();
        guildService.saveGuild(new Guild(guild.id(), guild.name(), List.of(anna, bert), guild.gameGuildId()));
        LineupRepository.save(new Lineup(guild.id(), guild.name(), "", LocalDateTime.now(), List.of(
                        new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 0),
                        new Lineup.Entry("foundry", "bert", Lineup.TeamType.HERO, 0))),
                LineupFiles.originalPathFor(context.guildFilePath().getParent()));

        SwingUtilities.invokeAndWait(() -> dialog = new GuildHeroEntryDialog(null, context, "anna"));
    }

    @AfterEach
    void closeWorkspace() throws Exception {
        if (context == null) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            if (dialog != null) {
                dialog.dispose();
            }
        });
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("Only the member's teams (also the one without fortification); member, filter and search locked; title names the member")
    void onlyTheMembersTeams() {
        assertEquals(2, dialog.table().getRowCount());
        for (int row = 0; row < dialog.table().getRowCount(); row++) {
            assertEquals("Anna", dialog.table().getValueAt(row, MEMBER_COLUMN));
        }
        assertFalse(dialog.fortFilter().isEnabled());
        assertFalse(dialog.searchField().isEnabled());
        assertFalse(dialog.memberCombo().isEnabled());
        assertTrue(dialog.getTitle().endsWith(" – Anna"), dialog.getTitle());
    }

    @Test
    @DisplayName("A new team belongs to the member; with all 3 hero teams \"new team\" is disabled")
    void newTeamBelongsToMember() throws Exception {
        assertTrue(dialog.addButton().isEnabled());

        SwingUtilities.invokeAndWait(dialog::addRowForTest);

        assertEquals(3, dialog.table().getRowCount());
        int selected = dialog.table().getSelectedRow();
        assertEquals("Anna", dialog.table().getValueAt(selected, MEMBER_COLUMN));
        assertFalse(dialog.addButton().isEnabled(), "Anna has all her hero team slots now");
    }

    @Test
    @DisplayName("Saving keeps the teams and Live entries of the other members")
    void saveKeepsOtherMembers() throws Exception {
        boolean[] saved = new boolean[1];
        SwingUtilities.invokeAndWait(() -> saved[0] = dialog.saveForTest());

        assertTrue(saved[0]);
        List<Lineup.Entry> live = LineupRepository.load(LineupFiles.originalPathFor(context.guildFilePath().getParent()))
                .entries();
        assertTrue(live.contains(new Lineup.Entry("foundry", "bert", Lineup.TeamType.HERO, 0)), live.toString());
        assertTrue(live.contains(new Lineup.Entry("bastion", "anna", Lineup.TeamType.HERO, 0)), live.toString());
        GuildMember bert = context.guild().members().stream().filter(m -> m.id().equals("bert")).findFirst().orElseThrow();
        assertEquals(70_000, bert.heroTeams().get(0).totalPower());
        GuildMember anna = context.guild().members().stream().filter(m -> m.id().equals("anna")).findFirst().orElseThrow();
        assertEquals(90_000, anna.heroTeams().get(1).totalPower(), "the team without fortification is kept");
    }
}
