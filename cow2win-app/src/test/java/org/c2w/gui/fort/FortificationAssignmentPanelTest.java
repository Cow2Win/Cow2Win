package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.domain.FortificationAssignment;
import org.c2w.gui.stage.ConceptInfoModel;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.LineupService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FortificationAssignmentPanel}, the content of the dialog "fortification assignment" - headless, never shown. */
class FortificationAssignmentPanelTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private LineupService lineupService;
    private final FakeChooser chooser = new FakeChooser();
    private Fortification fort;
    private Fortification other;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
        Guild current = context.guild();
        List<GuildMember> members = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String id = "m" + i;
            members.add(new GuildMember(id, "Member " + i, List.of(
                    new HeroTeam(id, 0, List.of(new Hero(id + "-a", List.of(Role.MAGE))), 1000 * i),
                    new HeroTeam(id, 1, List.of(new Hero(id + "-b", List.of(Role.TANK))), 100 * i)), List.of()));
        }
        context.setGuild(new Guild(current.id(), current.name(), members));
        lineupService = new LineupService(context);
        List<Fortification> heroForts = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO).toList();
        fort = heroForts.get(0);
        other = heroForts.get(1);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    private FortificationAssignmentPanel panel() {
        return new FortificationAssignmentPanel(context, fort, chooser);
    }

    private static Lineup.Entry entry(Fortification fortification, String memberId, int teamIndex) {
        return new Lineup.Entry(fortification.id(), memberId, Lineup.TeamType.HERO, teamIndex);
    }

    @Test
    @DisplayName("Team rows show how many of the team's heroes match the fortification's buff, free places none")
    void buffCount() {
        Fortification roleFort = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO && f.buff() instanceof RoleBuff)
                .findFirst().orElseThrow();
        Role role = ((RoleBuff) roleFort.buff()).role();
        Role otherRole = role == Role.MAGE ? Role.TANK : Role.MAGE;
        Guild current = context.guild();
        context.setGuild(new Guild(current.id(), current.name(), List.of(
                new GuildMember("b1", "Buff One", List.of(new HeroTeam("b1", 0, List.of(
                        new Hero("b1-a", List.of(role)), new Hero("b1-b", List.of(role)),
                        new Hero("b1-c", List.of(otherRole))), 5000)), List.of()),
                new GuildMember("b2", "Buff Two", List.of(new HeroTeam("b2", 0, List.of(
                        new Hero("b2-a", List.of(otherRole))), 1000)), List.of()))));
        lineupService.assignTeam("b1", Lineup.TeamType.HERO, 0, roleFort);
        lineupService.assignTeam("b2", Lineup.TeamType.HERO, 0, roleFort);

        List<FortificationAssignmentPanel.Row> rows = new FortificationAssignmentPanel(context, roleFort, chooser).rows();

        assertEquals(LanguageService.displayName(FortificationAssignmentPanel.KEY_BUFF_COUNT, 2), rows.get(0).buffText());
        assertEquals(LanguageService.displayName(FortificationAssignmentPanel.KEY_BUFF_COUNT, 0), rows.get(1).buffText());
        assertEquals("", rows.get(2).buffText(), "free place");
    }

    @Test
    @DisplayName("A fortification without buff leaves the buff cell empty")
    void buffCountWithoutBuff() {
        Fortification plainFort = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO && f.buff() == null)
                .findFirst().orElseThrow();
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, plainFort);

        List<FortificationAssignmentPanel.Row> rows = new FortificationAssignmentPanel(context, plainFort, chooser).rows();

        assertEquals("m1", rows.get(0).team().entry().teamMemberId());
        assertEquals("", rows.get(0).buffText());
    }

    @Test
    @DisplayName("One row per place: assigned teams strongest first, then the free places, each with its buttons")
    void rows() {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, fort);
        lineupService.assignTeam("m2", Lineup.TeamType.HERO, 0, fort);

        FortificationAssignmentPanel panel = panel();

        List<FortificationAssignmentPanel.Row> rows = panel.rows();
        assertEquals(fort.capacity(), rows.size());
        assertEquals("m2", rows.get(0).team().entry().teamMemberId());
        assertEquals("m1", rows.get(1).team().entry().teamMemberId());
        assertEquals(2, rows.get(0).buttons().size(), "move/swap and remove");
        for (FortificationAssignmentPanel.Row row : rows.subList(2, rows.size())) {
            assertNull(row.team());
            assertEquals(1, row.buttons().size(), "assign team");
        }
        assertTrue(panel.factsText().startsWith("2/" + fort.capacity()), panel.factsText());
    }

    @Test
    @DisplayName("Assign: the chosen free team is added; the panel and the facts follow the lineup")
    void assignFreeTeam() {
        FortificationAssignmentPanel panel = panel();
        chooser.candidate = candidates -> candidates.get(0);

        panel.rows().get(0).buttons().get(0).doClick();

        assertEquals(List.of(entry(fort, "m5", 0)), context.lineup().entries(), "highest CowScore first");
        assertTrue(chooser.offered.get(0).isFree());
        assertTrue(context.isLineupDirty());
        assertEquals("m5", panel.rows().get(0).team().entry().teamMemberId());
        assertTrue(panel.factsText().startsWith("1/"), panel.factsText());
    }

    @Test
    @DisplayName("Assign a team from another fortification moves it here")
    void assignMovesTeam() {
        lineupService.assignTeam("m3", Lineup.TeamType.HERO, 0, other);
        FortificationAssignmentPanel panel = panel();
        chooser.candidate = candidates -> candidates.stream().filter(c -> !c.isFree()).findFirst().orElseThrow();

        panel.assignTeam();

        assertEquals(List.of(entry(fort, "m3", 0)), context.lineup().entries());
        FortificationAssignment.Candidate last = chooser.offered.get(chooser.offered.size() - 1);
        assertEquals(other.id(), last.currentFortificationId(), "the assigned teams come last");
    }

    @Test
    @DisplayName("Remove frees the place without asking")
    void remove() {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, fort);
        FortificationAssignmentPanel panel = panel();

        panel.rows().get(0).buttons().get(1).doClick();

        assertTrue(context.lineup().entries().isEmpty());
        assertNull(panel.rows().get(0).team());
    }

    @Test
    @DisplayName("Move to a fortification with room moves the team; the targets are the other fortifications of the type")
    void moveToFortificationWithRoom() {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, fort);
        FortificationAssignmentPanel panel = panel();
        chooser.target = other;

        panel.rows().get(0).buttons().get(0).doClick();

        assertEquals(List.of(entry(other, "m1", 0)), context.lineup().entries());
        assertTrue(chooser.targets.stream().noneMatch(t -> t.fortification().equals(fort)));
        assertTrue(chooser.targets.stream().allMatch(t -> t.fortification().type() == FortificationType.HERO));
        assertTrue(chooser.swapOffered.isEmpty(), "no swap needed");
    }

    @Test
    @DisplayName("Move to a full fortification swaps with the chosen team; cancelling the swap changes nothing")
    void moveToFullFortificationSwaps() {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, fort);
        fillUp(other);
        FortificationAssignmentPanel panel = panel();
        chooser.target = other;

        Lineup before = context.lineup();
        panel.moveTeam(panel.rows().get(0).team());
        assertSame(before, context.lineup(), "swap cancelled");
        assertEquals(other.capacity(), chooser.swapOffered.size());

        chooser.swapPartner = teams -> teams.get(0);
        panel.moveTeam(panel.rows().get(0).team());
        Lineup.Entry partner = chooser.swapOffered.get(0).entry();
        assertTrue(context.lineup().entries().contains(entry(other, "m1", 0)));
        assertTrue(context.lineup().entries().contains(new Lineup.Entry(fort.id(), partner.teamMemberId(),
                partner.teamType(), partner.teamIndex())));
        assertEquals(before.entries().size(), context.lineup().entries().size());
    }

    @Test
    @DisplayName("Two teams of the same member may share a fortification - no rule but the capacity")
    void noAssignmentRules() {
        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, fort);
        FortificationAssignmentPanel panel = panel();
        chooser.candidate = candidates -> candidates.stream()
                .filter(c -> c.memberId().equals("m1") && c.teamIndex() == 1).findFirst().orElseThrow();

        panel.assignTeam();

        assertEquals(2, ConceptInfoModel.teamsOf(fort, context.lineup(), context.guild()).size());
    }

    @Test
    @DisplayName("While the live lineup is open the panel is read-only: no buttons")
    void readOnlyForLiveLineup() {
        Path livePath = LineupFiles.originalPathFor(context.guildFilePath().getParent());
        context.set(new Lineup(context.guild().id(), "Original", "", LocalDateTime.now(),
                List.of(entry(fort, "m1", 0))), livePath);

        FortificationAssignmentPanel panel = panel();

        assertTrue(panel.isReadOnly());
        assertEquals(fort.capacity(), panel.rows().size());
        assertTrue(panel.rows().stream().allMatch(row -> row.buttons().isEmpty()));
    }

    @Test
    @DisplayName("A detached panel no longer follows the lineup")
    void detach() {
        FortificationAssignmentPanel panel = panel();
        panel.detach();

        lineupService.assignTeam("m1", Lineup.TeamType.HERO, 0, fort);

        assertNull(panel.rows().get(0).team());
    }

    /** Fills {@code fortification} with the second teams of the members, then the first ones of m2.. if needed. */
    private void fillUp(Fortification fortification) {
        int filled = 0;
        for (int index = 1; index >= 0 && filled < fortification.capacity(); index--) {
            for (int i = 1; i <= 5 && filled < fortification.capacity(); i++) {
                if (index == 0 && i == 1) {
                    continue;
                }
                assertTrue(lineupService.assignTeam("m" + i, Lineup.TeamType.HERO, index, fortification).assigned());
                filled++;
            }
        }
        assertEquals(fortification.capacity(), filled, "test guild too small for " + fortification.id());
    }

    /** Answers the panel's choices as configured - nothing chosen by default. */
    private static final class FakeChooser implements FortificationAssignmentPanel.Chooser {

        Function<List<FortificationAssignment.Candidate>, FortificationAssignment.Candidate> candidate = c -> null;
        Fortification target;
        Function<List<ConceptInfoModel.AssignedTeam>, ConceptInfoModel.AssignedTeam> swapPartner = t -> null;

        List<FortificationAssignment.Candidate> offered = List.of();
        List<FortificationAssignmentPanel.Target> targets = List.of();
        List<ConceptInfoModel.AssignedTeam> swapOffered = List.of();

        @Override
        public Optional<FortificationAssignment.Candidate> chooseCandidate(
                Fortification fortification, List<FortificationAssignment.Candidate> candidates) {
            offered = candidates;
            return Optional.ofNullable(candidate.apply(candidates));
        }

        @Override
        public Optional<Fortification> chooseTarget(List<FortificationAssignmentPanel.Target> targets) {
            this.targets = targets;
            return Optional.ofNullable(target);
        }

        @Override
        public Optional<ConceptInfoModel.AssignedTeam> chooseSwapPartner(Fortification target,
                                                                         List<ConceptInfoModel.AssignedTeam> teams) {
            swapOffered = teams;
            return Optional.ofNullable(swapPartner.apply(teams));
        }
    }
}
