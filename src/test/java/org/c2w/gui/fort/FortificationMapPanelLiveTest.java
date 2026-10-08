package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FortificationValueMode#LIVE_COMPARISON} on the {@link FortificationMapPanel} - headless, never shown. */
class FortificationMapPanelLiveTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private FortificationMapPanel map;
    private Path livePath;
    private Fortification first;
    private Fortification second;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
        GuildMember anna = new GuildMember("anna", "Anna",
                List.of(new HeroTeam("anna", 0, List.of(), null, null, 1_000_000, null),
                        new HeroTeam("anna", 1, List.of(), null, null, 3_000_000, null)), List.of());
        GuildMember bert = new GuildMember("bert", "Bert",
                List.of(new HeroTeam("bert", 0, List.of(), null, null, 2_500_000, null)), List.of());
        context.setGuild(new Guild(context.guild().id(), context.guild().name(), List.of(anna, bert),
                context.guild().gameGuildId()));

        List<Fortification> heroForts = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO && f.capacity() >= 2).toList();
        first = heroForts.get(0);
        second = heroForts.get(1);
        livePath = LineupFiles.originalPathFor(context.guildFilePath().getParent());

        map = new FortificationMapPanel(context);
        map.setValueMode(FortificationValueMode.LIVE_COMPARISON);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("Another lineup is open: the change per fortification against the live lineup")
    void comparedWithLive() throws Exception {
        LineupRepository.save(live(), livePath);
        openPlan();

        Map<String, Integer> diffs = diffs();
        assertEquals(3_000_000, diffs.get(first.id()), "one more team than live");
        assertEquals(-2_500_000, diffs.get(second.id()), "only occupied in live");
        assertOtherDiffsZero(diffs);
    }

    @Test
    @DisplayName("The live lineup itself is open: every change is 0")
    void liveItselfIsOpen() throws Exception {
        LineupRepository.save(live(), livePath);
        context.set(live(), livePath);

        assertTrue(diffs().values().stream().allMatch(diff -> diff == 0), diffs().toString());
    }

    @Test
    @DisplayName("Without a live file the whole power of the open lineup is the change")
    void noLiveFile() {
        assertFalse(Files.exists(livePath));
        openPlan();

        Map<String, Integer> diffs = diffs();
        assertEquals(4_000_000, diffs.get(first.id()));
        assertEquals(0, diffs.get(second.id()));
        assertOtherDiffsZero(diffs);
    }

    @Test
    @DisplayName("The live lineup is saved while another lineup is open: the rebuilt map shows the new change")
    void liveSavedWhileOtherOpen() throws Exception {
        LineupRepository.save(live(), livePath);
        openPlan();
        assertEquals(3_000_000, diffs().get(first.id()));

        // As when maintaining the live lineup: the live file is written and the guild set again.
        LineupRepository.save(lineup(List.of(entry(first, "anna", 0), entry(first, "anna", 1))), livePath);
        context.setGuild(context.guild());

        assertEquals(0, diffs().get(first.id()));
        assertEquals(0, diffs().get(second.id()));
    }

    @Test
    @DisplayName("The other modes are unchanged: power shows no live change, changes compare with the loaded state")
    void otherModes() throws Exception {
        LineupRepository.save(live(), livePath);
        openPlan();

        map.setValueMode(FortificationValueMode.CHANGES);
        assertTrue(diffs().values().stream().allMatch(diff -> diff == 0), "nothing changed since loading");
        map.setValueMode(FortificationValueMode.POWER);
        assertTrue(diffs().values().stream().allMatch(diff -> diff == 0), "nothing changed since loading");
    }

    /** Live: one team at the first fortification, one at the second. */
    private Lineup live() {
        return lineup(List.of(entry(first, "anna", 0), entry(second, "bert", 0)));
    }

    /** Opens another lineup: two teams at the first fortification, the second empty. */
    private void openPlan() {
        Lineup plan = lineup(List.of(entry(first, "anna", 0), entry(first, "anna", 1)));
        context.set(plan, context.guildFilePath().getParent().resolve("Plan" + LineupFiles.SUFFIX));
    }

    private Lineup lineup(List<Lineup.Entry> entries) {
        return new Lineup(context.guild().id(), context.guild().name(), "", LocalDateTime.now(), entries);
    }

    private static Lineup.Entry entry(Fortification fortification, String memberId, int teamIndex) {
        return new Lineup.Entry(fortification.id(), memberId, Lineup.TeamType.HERO, teamIndex);
    }

    private void assertOtherDiffsZero(Map<String, Integer> diffs) {
        diffs.forEach((id, diff) -> {
            if (!id.equals(first.id()) && !id.equals(second.id())) {
                assertEquals(0, diff, id);
            }
        });
    }

    /** The power change shown per fortification id. */
    private Map<String, Integer> diffs() {
        Map<String, Integer> diffs = new HashMap<>();
        for (int row = 0; row < map.rows(); row++) {
            for (int column = 0; column < map.columns(); column++) {
                JComponent component = map.componentAt(row, column);
                if (component instanceof FortificationPanel panel) {
                    diffs.put(panel.fortification().id(), panel.totalPowerDiff());
                }
            }
        }
        return diffs;
    }
}
