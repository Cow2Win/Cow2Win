package org.c2w.service;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.PlayerAssignment;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.GuildRepository;
import org.c2w.i18n.GameNameNormalizer;
import org.c2w.infra.Config;
import org.c2w.service.journal.*;
import org.c2w.service.journal.TeamBuildPlan.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JournalTeamBuilderService} with the real sample logs; the real guild
 * "Deutscher Bund" (fixture {@code guild/deutscher-bund.json}) is the truth for
 * the compositions.
 */
class JournalTeamBuilderServiceTest {

    static final Path FIXTURE = Path.of("src", "test", "resources", "guild", "deutscher-bund.json");

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;
    private JournalTeamBuilderService service;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Leer");
        guildService.switchToGuild("Leer");
        service = new JournalTeamBuilderService(context, guildService);
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    private Guild fixture() throws Exception {
        return GuildRepository.load(FIXTURE, context.catalog());
    }

    /** Imports all 6 German battles; every defender without suggestion becomes a new member ("create all"). */
    private ImportResult importAllCreatingMembers() throws Exception {
        JournalImportService importer = new JournalImportService(context, guildService, context.journal(),
                BattleLogTestFiles::parser);
        ImportPlan plan = importer.prepare(BattleLogTestFiles.files("de"));
        ImportAnswers answers = ImportAnswers.defaults();
        for (PlayerQuestion q : plan.playerQuestions()) {
            if (q.nameSuggestions().isEmpty() && q.renameSuggestions().isEmpty()
                    && q.allowedAnswers().contains(PlayerAnswer.Kind.CREATE)) {
                answers = answers.withPlayer(q.id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE));
            }
        }
        ImportResult result = importer.execute(plan, answers);
        assertTrue(result.isSuccess(), result.errors().toString());
        return result;
    }

    @Test
    @DisplayName("End to end: empty guild -> 6 battles -> create all -> build teams; every composition matches the real guild")
    void emptyGuildEndToEnd() throws Exception {
        ImportResult imported = importAllCreatingMembers();
        assertEquals(27, imported.createdMembers().size());
        assertEquals(27, context.guild().members().size());
        assertTrue(context.guild().members().size() <= Guild.MAX_MEMBERS);
        assertTrue(JournalTeamBuilderService.hasMembersWithoutTeams(context.guild()));

        TeamBuildPlan plan = service.prepare();

        assertEquals(6, plan.defenseLogs());
        assertEquals(27, plan.members().size(), "every created member defends somewhere");
        assertEquals(List.of(), plan.skippedPlayers());
        assertEquals(List.of(), plan.membersWithoutLogData());

        // every composition (of a proposal or offered as known) against the real guild of 04.10.2026
        Guild truth = fixture();
        Map<String, GuildMember> truthByName = new HashMap<>();
        truth.members().forEach(m -> truthByName.put(GameNameNormalizer.key(m.name()), m));
        Map<CompositionSource, Integer> checked = new EnumMap<>(CompositionSource.class);
        List<String> attackMismatches = new ArrayList<>();
        Set<String> defenseMismatches = new TreeSet<>();
        Set<String> seen = new HashSet<>();
        List<String> notInFixture = new ArrayList<>();
        for (MemberPlan member : plan.members()) {
            GuildMember real = truthByName.get(GameNameNormalizer.key(member.memberName()));
            if (real == null) {
                notInFixture.add(member.memberName());
                assertTrue(member.proposals().stream().allMatch(p -> p.composition() == null
                        && p.knownCompositions().isEmpty()), member.memberName() + " has nothing to check");
                continue;
            }
            for (Proposal p : member.proposals()) {
                List<Composition> compositions = new ArrayList<>(p.knownCompositions());
                if (p.composition() != null) {
                    compositions.add(p.composition());
                }
                for (Composition c : compositions) {
                    if (!seen.add(member.memberId() + new TreeSet<>(c.unitIds()))) {
                        continue;
                    }
                    checked.merge(c.source(), 1, Integer::sum);
                    if (!hasTeam(real, c)) {
                        if (c.source() == CompositionSource.ATTACK_EXACT_POWER) {
                            attackMismatches.add(member.memberName() + " " + c);
                        } else {
                            assertEquals(LocalDate.of(2026, 9, 17), c.battleDate(), "defense units only on 17.09.");
                            defenseMismatches.add(member.memberName() + " " + c.kind());
                        }
                    }
                }
            }
        }
        System.out.println("Team builder end-to-end: members=" + plan.members().size() + ", proposals="
                + plan.proposals().size() + " " + plan.countBySource() + ", from source battle="
                + plan.proposals().stream().filter(Proposal::fromSourceBattle).count() + ", distinct compositions "
                + checked + ", attack mismatches " + attackMismatches.size() + ", 17.09. defense deviations "
                + defenseMismatches + ", not in the real guild " + notInFixture);
        assertTrue(notInFixture.size() <= 1, "only a player who left the guild: " + notInFixture);
        assertEquals(List.of(), attackMismatches, "exact power: error rate 0");
        assertTrue(checked.getOrDefault(CompositionSource.ATTACK_EXACT_POWER, 0) > 0, checked.toString());

        // every exact-power hit of every battle and position (also where the defense log has units)
        int[] exact = exactPowerHits(truthByName);
        System.out.println("Team builder exact-power hits over all battles and positions: " + exact[0]
                + ", of them not like the real guild: " + exact[1]);
        assertTrue(exact[0] >= 30, "hits " + exact[0]);
        assertEquals(0, exact[1], "exact power: error rate 0");
        // The defense units of 17.09. are the game's truth of that day; the fixture is the guild of 04.10.:
        // 4 teams changed since (other power), and 2 teams have the same power but one other titan in the
        // fixture (Vale: Vulcan/Moloch, Team Gandagom: Iyari/Solaris) - the parser reads the log correctly.
        assertEquals(new TreeSet<>(List.of("Andy TITAN", "Jay TITAN", "NeoSouls HERO", "Ordensritter HERO",
                "Team Gandagom TITAN", "Vale TITAN")), defenseMismatches);

        // proposals: source battle preselected, older battles not; known compositions only offered
        assertTrue(plan.proposals().stream().filter(p -> !p.fromSourceBattle()).noneMatch(Proposal::preselected));
        assertTrue(plan.proposals().stream().filter(Proposal::fromSourceBattle)
                .allMatch(p -> p.preselected() == (p.status() == ProposalStatus.AVAILABLE)));
        assertTrue(plan.proposals().stream().anyMatch(p -> p.composition() == null && !p.knownCompositions().isEmpty()),
                "a known composition is offered for a proposal without one");
        assertTrue(plan.proposals().stream().filter(p -> p.composition() != null)
                .allMatch(p -> p.knownCompositions().isEmpty()));

        // apply the preselection: only new teams, power from the source battle, lastModified = battle day
        TeamBuildSelection preselected = TeamBuildSelection.preselected(plan);
        TeamBuildResult result = service.apply(plan, preselected);
        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(preselected.targets().size(), result.created());
        assertEquals(0, result.filled());
        assertTrue(context.isGuildDirty());
        for (MemberPlan member : plan.members()) {
            GuildMember built = context.guild().members().stream().filter(m -> m.id().equals(member.memberId()))
                    .findFirst().orElseThrow();
            for (Proposal p : member.proposals().stream().filter(Proposal::preselected).toList()) {
                assertTrue(built.heroTeams().stream().anyMatch(t -> t.totalPower() == p.power()
                                && p.battleDate().equals(t.lastModified()) && t.warFlag() == null)
                        || built.titanTeams().stream().anyMatch(t -> t.totalPower() == p.power()
                        && p.battleDate().equals(t.lastModified())), p.toString());
            }
        }
        System.out.println("Team builder applied: " + result.created() + " teams, " + result.bySource()
                + ", dropped pets " + result.droppedPets() + ", dropped totems " + result.droppedTotems());
    }

    @Test
    @DisplayName("The real guild as open guild: teams with heroes/titans are never changed")
    void guildWithTeams() throws Exception {
        Path folder = Files.createDirectories(workspace.resolve("Deutscher Bund"));
        Files.copy(FIXTURE, folder.resolve("guild.json"));
        guildService.switchToGuild("Deutscher Bund");
        Guild before = context.guild();
        importAllCreatingMembers();
        Guild afterImport = context.guild();

        TeamBuildPlan plan = service.prepare();
        TeamBuildResult result = service.apply(plan, TeamBuildSelection.preselected(plan));

        assertTrue(result.isSuccess(), result.errors().toString());
        for (GuildMember old : afterImport.members()) {
            GuildMember now = context.guild().members().stream().filter(m -> m.id().equals(old.id())).findFirst()
                    .orElseThrow();
            for (HeroTeam t : old.heroTeams()) {
                if (t.heroes() != null && !t.heroes().isEmpty()) {
                    assertEquals(t, now.heroTeams().get(t.index()), old.id());
                }
            }
            for (TitanTeam t : old.titanTeams()) {
                if (!t.titans().isEmpty()) {
                    assertEquals(t, now.titanTeams().get(t.index()), old.id());
                }
            }
        }
        for (Proposal p : plan.proposals()) {
            if (p.status() == ProposalStatus.AVAILABLE) {
                MemberPlan member = plan.member(p.memberId()).orElseThrow();
                assertTrue(member.freeSlots(p.kind()) > 0 || !member.emptyTeams(p.kind()).isEmpty(),
                        "only free slots or empty teams: " + p);
            }
        }
        assertTrue(plan.proposals().stream().anyMatch(p -> p.status() == ProposalStatus.ALREADY_PRESENT));
        assertEquals(before.members().size(), afterImport.members().size(), "all defenders are already members");
    }

    @Test
    @DisplayName("An empty team is filled with power and composition; only selected proposals are applied")
    void fillEmptyTeam() throws Exception {
        importAllCreatingMembers();
        TeamBuildPlan first = service.prepare();
        Proposal withUnits = first.proposals().stream().filter(p -> p.preselected() && p.composition() != null)
                .findFirst().orElseThrow();
        GuildMember member = member(withUnits.memberId());
        boolean hero = withUnits.kind() == TeamKind.HERO;
        replaceMember(new GuildMember(member.id(), member.name(),
                hero ? List.of(new HeroTeam(member.id(), 0, List.of(), null, null, 0, null)) : List.of(),
                hero ? List.of() : List.of(new TitanTeam(member.id(), 0, List.of(), 0, null, null))));

        TeamBuildPlan plan = service.prepare();
        Proposal p = plan.proposal(withUnits.id()).orElseThrow();
        assertEquals(TeamBuildPlan.Target.fill(0), p.suggestedTarget());
        TeamBuildResult result = service.apply(plan, TeamBuildSelection.none().with(p.id(), p.suggestedTarget()));

        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(0, result.created());
        assertEquals(1, result.filled());
        GuildMember after = member(member.id());
        if (hero) {
            HeroTeam filled = after.heroTeams().get(0);
            assertEquals(p.power(), filled.totalPower());
            assertEquals(p.composition().unitIds(), filled.heroes().stream().map(Hero::id).toList());
            assertEquals(p.battleDate(), filled.lastModified());
            assertNull(filled.warFlag());
        } else {
            TitanTeam filled = after.titanTeams().get(0);
            assertEquals(p.power(), filled.totalPower());
            assertEquals(p.composition().unitIds(), filled.titans().stream().map(Titan::id).toList());
            assertEquals(p.battleDate(), filled.lastModified());
        }
        assertEquals(1, after.heroTeams().size() + after.titanTeams().size(), "nothing else applied");
        assertTrue(context.isGuildDirty());
    }

    @Test
    @DisplayName("A known composition from another fight is only offered; chosen, it is applied")
    void knownCompositionIsOnlyOffered() throws Exception {
        importAllCreatingMembers();
        TeamBuildPlan plan = service.prepare();
        Proposal p = plan.proposals().stream()
                .filter(x -> x.composition() == null && !x.knownCompositions().isEmpty() && x.selectable())
                .findFirst().orElseThrow();
        Composition known = p.knownCompositions().get(0);
        assertEquals(p.kind(), known.kind());

        TeamBuildSelection selection = TeamBuildSelection.none().with(p.id(), p.suggestedTarget());
        assertTrue(service.apply(plan, selection.withComposition(p.id(), known)).isSuccess());
        GuildMember m = member(p.memberId());
        Set<String> ids = new HashSet<>(known.unitIds());
        assertTrue(p.kind() == TeamKind.HERO
                ? m.heroTeams().stream().anyMatch(t -> ids.equals(new HashSet<>(t.heroes().stream().map(Hero::id).toList())))
                : m.titanTeams().stream().anyMatch(t -> ids.equals(new HashSet<>(t.titans().stream().map(Titan::id).toList()))));
    }

    // =====================================================================
    // rules with in-memory logs
    // =====================================================================

    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);
    private static final List<String> HEROES = List.of("dante", "aurora", "nebula", "sebastian", "martha");
    private static final List<String> HEROES_2 = List.of("galahad", "yasmine", "maya", "jhu", "astaroth");

    @Test
    @DisplayName("Composition from the attack log only with exactly the same power (1 % off: none)")
    void exactPowerOnly() {
        Fight defense = defense("Max", "citadel", 1, 1_000_000, List.of());
        Set<String> names = Set.of("Max");
        BattleLog defenseLog = log(LogDirection.DEFENSE, defense);

        Composition exact = compose(defense, attack("Max", 1_000_000, heroUnits(HEROES, "axel")));
        assertEquals(CompositionSource.ATTACK_EXACT_POWER, exact.source());
        assertEquals(HEROES, exact.unitIds());
        assertEquals("axel", exact.petId());

        for (int power : List.of(1_010_000, 990_000, 1_000_001)) {
            assertNull(compose(defense, attack("Max", power, heroUnits(HEROES, null))), "power " + power);
        }
        assertNull(compose(defense, attack("Moritz", 1_000_000, heroUnits(HEROES, null))), "another player");
        assertNull(compose(defense, attack("Max", 1_000_000, heroUnits(HEROES.subList(0, 4), null))), "only 4 heroes");
        List<FightUnit> unresolved = new ArrayList<>(heroUnits(HEROES.subList(0, 4), null));
        unresolved.add(new FightUnit(UnitKind.HERO, "Neuheld", null, null, null, "", 0, 6, 130, 1, 0, 0, 0, null));
        assertNull(compose(defense, attack("Max", 1_000_000, unresolved)), "a hero without catalog id");
        Fight withUnits = defense("Max", "citadel", 1, 1_000_000, heroUnits(HEROES_2, null));
        assertEquals(CompositionSource.DEFENSE_UNITS, JournalTeamBuilderService.composition(
                new JournalTeamBuilderService.BattleLogs(1, DAY, log(LogDirection.DEFENSE, withUnits), null),
                List.of(withUnits), names, TeamKind.HERO, 1_000_000).source());
    }

    @Test
    @DisplayName("More than 3 hero teams: the 4th proposal is over the limit")
    void limits() {
        BattleLog defense = log(LogDirection.DEFENSE,
                defense("Max", "citadel", 1, 1_000_000, List.of()), defense("Max", "citadel", 2, 900_000, List.of()),
                defense("Max", "barracks", 1, 800_000, List.of()), defense("Max", "bastion", 1, 700_000, List.of()));
        Guild guild = new Guild("g", "G", List.of(new GuildMember("max", "Max", List.of(), List.of())));

        TeamBuildPlan plan = JournalTeamBuilderService.plan(List.of(new JournalTeamBuilderService.BattleLogs(1, DAY,
                defense, null)), Map.of("Max", assigned("max")), guild, null);

        List<Proposal> proposals = plan.member("max").orElseThrow().proposals();
        assertEquals(4, proposals.size());
        assertEquals(3, proposals.stream().filter(p -> p.status() == ProposalStatus.AVAILABLE).count());
        assertEquals(1, proposals.stream().filter(p -> p.status() == ProposalStatus.OVER_LIMIT).count());
        assertTrue(proposals.stream().allMatch(p -> p.source() == CompositionSource.POWER_ONLY));
    }

    @Test
    @DisplayName("Older battles: a team seen only before is an extra proposal, not preselected")
    void olderBattles() {
        BattleLog newer = log(LogDirection.DEFENSE, defense("Max", "citadel", 1, 1_000_000, List.of()));
        BattleLog older = log(LogDirection.DEFENSE, defense("Max", "barracks", 3, 1_005_000, List.of()),
                defense("Max", "bastion", 2, 700_000, List.of()));
        Guild guild = new Guild("g", "G", List.of(new GuildMember("max", "Max", List.of(), List.of())));

        TeamBuildPlan plan = JournalTeamBuilderService.plan(List.of(
                new JournalTeamBuilderService.BattleLogs(1, DAY.minusWeeks(1), older, null),
                new JournalTeamBuilderService.BattleLogs(2, DAY, newer, null)), Map.of("Max", assigned("max")), guild, null);

        List<Proposal> proposals = plan.member("max").orElseThrow().proposals();
        assertEquals(2, proposals.size(), "1.005.000 is within 3 % of the source team - the same team");
        assertTrue(proposals.get(0).fromSourceBattle());
        assertTrue(proposals.get(0).preselected());
        assertFalse(proposals.get(1).fromSourceBattle());
        assertFalse(proposals.get(1).preselected());
        assertEquals(700_000, proposals.get(1).power());
        assertEquals(DAY.minusWeeks(1), proposals.get(1).battleDate());
        assertEquals(DAY, plan.member("max").orElseThrow().sourceDate());
    }

    @Test
    @DisplayName("Skipped defenders: open, not in Cow2Win, former, member missing")
    void skipped() {
        BattleLog defense = log(LogDirection.DEFENSE, defense("A", "citadel", 1, 1, List.of()),
                defense("B", "citadel", 2, 1, List.of()), defense("C", "citadel", 3, 1, List.of()),
                defense("D", "citadel", 4, 1, List.of()));
        Guild guild = new Guild("g", "G", List.of(new GuildMember("x", "X", List.of(), List.of())));
        Map<String, PlayerAssignment> assignments = Map.of(
                "B", new PlayerAssignment(2, null, AssignmentStatus.NOT_IN_COW2WIN, null),
                "C", new PlayerAssignment(3, null, AssignmentStatus.FORMER, null),
                "D", assigned("gone"));

        TeamBuildPlan plan = JournalTeamBuilderService.plan(List.of(new JournalTeamBuilderService.BattleLogs(1, DAY,
                defense, null)), assignments, guild, null);

        assertEquals(List.of(new SkippedPlayer("A", SkipReason.NOT_ASSIGNED, null),
                new SkippedPlayer("B", SkipReason.NOT_IN_COW2WIN, null),
                new SkippedPlayer("C", SkipReason.FORMER, null),
                new SkippedPlayer("D", SkipReason.MEMBER_MISSING, "gone")), plan.skippedPlayers());
        assertEquals(List.of("x"), plan.membersWithoutLogData());
        assertEquals(List.of(), plan.members());
    }

    @Test
    @DisplayName("apply: the same pet twice is an error (nothing changes); an invalid totem is dropped and reported")
    void validation() throws Exception {
        context.setGuild(new Guild(context.guild().id(), context.guild().name(),
                List.of(new GuildMember("max", "Max", List.of(), List.of()))));
        context.setGuildDirty(false);
        Guild before = context.guild();
        Fight d1 = defense("Max", "citadel", 1, 1_000_000, heroUnits(HEROES, "axel"));
        Fight d2 = defense("Max", "barracks", 1, 900_000, heroUnits(HEROES_2, "axel"));
        TeamBuildPlan pets = JournalTeamBuilderService.plan(List.of(new JournalTeamBuilderService.BattleLogs(1, DAY,
                log(LogDirection.DEFENSE, d1, d2), null)), Map.of("Max", assigned("max")), before,
                context.guildFilePath());

        TeamBuildResult failed = service.apply(pets, TeamBuildSelection.preselected(pets));

        assertFalse(failed.isSuccess());
        assertEquals(TeamBuildResult.Error.Kind.DUPLICATE_PET, failed.errors().get(0).kind());
        assertSame(before, context.guild(), "nothing changed");
        assertFalse(context.isGuildDirty());

        List<String> titanIds = List.of("sigurd", "nova", "mairi", "hyperion", "tenebris");
        List<Titan> titans = titanIds.stream().map(id -> context.catalog().titans().findById(id).orElseThrow()).toList();
        TitanElement notAllowed = Arrays.stream(TitanElement.values())
                .filter(e -> !TitanTeam.eligibleTotems(titans).contains(e)).findFirst().orElseThrow();
        Fight titanFight = defense("Max", "bridge", 1, 1_200_000, titanUnits(titanIds, notAllowed));
        TeamBuildPlan totems = JournalTeamBuilderService.plan(List.of(new JournalTeamBuilderService.BattleLogs(1, DAY,
                log(LogDirection.DEFENSE, titanFight), null)), Map.of("Max", assigned("max")), before,
                context.guildFilePath());

        TeamBuildResult ok = service.apply(totems, TeamBuildSelection.preselected(totems));

        assertTrue(ok.isSuccess(), ok.errors().toString());
        assertEquals(List.of(new TeamBuildResult.DroppedTotem("max", notAllowed)), ok.droppedTotems());
        TitanTeam built = member("max").titanTeams().get(0);
        assertEquals(titanIds, built.titans().stream().map(Titan::id).toList());
        assertFalse(built.totems().contains(notAllowed));
        assertEquals(Map.of(CompositionSource.DEFENSE_UNITS, 1), ok.bySource());
    }

    @Test
    @DisplayName("apply after a guild switch is refused")
    void guildChanged() throws Exception {
        importAllCreatingMembers();
        TeamBuildPlan plan = service.prepare();
        guildService.createGuild("Andere");
        guildService.switchToGuild("Andere");

        TeamBuildResult result = service.apply(plan, TeamBuildSelection.preselected(plan));

        assertEquals(TeamBuildResult.Error.Kind.GUILD_CHANGED, result.errors().get(0).kind());
        assertTrue(context.guild().members().isEmpty());
    }

    // --- helpers ---

    /** The composition of the hero defense fight with one attack fight of the same battle. */
    private static Composition compose(Fight defense, Fight attack) {
        return JournalTeamBuilderService.composition(new JournalTeamBuilderService.BattleLogs(1, DAY,
                        log(LogDirection.DEFENSE, defense), log(LogDirection.ATTACK, attack)),
                List.of(defense), Set.of("Max"), TeamKind.HERO, defense.defender().teamPower());
    }

    private GuildMember member(String id) {
        return context.guild().members().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    private void replaceMember(GuildMember replacement) {
        List<GuildMember> members = new ArrayList<>(context.guild().members());
        members.replaceAll(m -> m.id().equals(replacement.id()) ? replacement : m);
        context.setGuild(context.guild().withMembers(members));
    }

    private static PlayerAssignment assigned(String memberId) {
        return new PlayerAssignment(1, memberId, AssignmentStatus.ASSIGNED, null);
    }

    private static BattleLog log(LogDirection direction, Fight... fights) {
        BattleLogHeader header = new BattleLogHeader(DAY, null, null, null, null, direction, "deutsch", "test.csv");
        return new BattleLog(header, List.of(fights));
    }

    private static Fight defense(String defender, String fortId, int position, int power, List<FightUnit> units) {
        return new Fight(fortId, fortId, position, null, true, "Sieg", 35,
                new FightSide("Gegner", 130, 1, null, List.of()), new FightSide(defender, 130, power, null, units), 1);
    }

    private static Fight attack(String attacker, int power, List<FightUnit> units) {
        return new Fight("citadel", "citadel", 1, null, true, "Sieg", 35,
                new FightSide(attacker, 130, power, null, units), new FightSide("Gegner", 130, 1, null, List.of()), 1);
    }

    private static List<FightUnit> heroUnits(List<String> ids, String pet) {
        List<FightUnit> units = new ArrayList<>();
        ids.forEach(id -> units.add(unit(UnitKind.HERO, id, null)));
        if (pet != null) {
            units.add(unit(UnitKind.PET, pet, null));
        }
        return units;
    }

    private static List<FightUnit> titanUnits(List<String> ids, TitanElement totem) {
        List<FightUnit> units = new ArrayList<>();
        ids.forEach(id -> units.add(unit(UnitKind.TITAN, id, null)));
        units.add(unit(UnitKind.TOTEM, null, totem));
        return units;
    }

    private static FightUnit unit(UnitKind kind, String id, TitanElement totem) {
        return new FightUnit(kind, id == null ? "Totem" : id, id, totem, null, "", 0, 6, 130,
                kind == UnitKind.TOTEM ? null : 100_000, 0, 0, 0, null);
    }

    /**
     * Counts, over all stored battles and every (fortification, position) of an assigned defender,
     * the attack teams with exactly the defender team's power ({@code [0]}) and how many of them
     * differ from the real guild ({@code [1]}).
     */
    private int[] exactPowerHits(Map<String, GuildMember> truthByName) throws Exception {
        var repo = context.journal().repository(false).orElseThrow();
        Map<String, PlayerAssignment> assignments = repo.ownAssignmentsByName();
        Map<String, String> memberNames = new HashMap<>();
        context.guild().members().forEach(m -> memberNames.put(m.id(), m.name()));
        int hits = 0;
        int wrong = 0;
        for (var summary : repo.listBattles(null)) {
            var defense = repo.loadLog(summary.battleId(), LogDirection.DEFENSE);
            var attack = repo.loadLog(summary.battleId(), LogDirection.ATTACK);
            if (defense.isEmpty() || attack.isEmpty()) {
                continue;
            }
            var battle = new JournalTeamBuilderService.BattleLogs(summary.battleId(), summary.date(),
                    defense.get().log(), attack.get().log());
            Set<String> slots = new HashSet<>();
            for (Fight f : defense.get().log().fights()) {
                PlayerAssignment a = assignments.get(f.defender().playerName());
                TeamKind kind = JournalTeamBuilderService.teamKind(f);
                if (a == null || a.memberId() == null || kind == null
                        || !slots.add(f.defender().playerName() + f.fortificationId() + f.position())) {
                    continue;
                }
                Composition c = JournalTeamBuilderService.composition(battle, List.of(),
                        Set.of(f.defender().playerName()), kind, f.defender().teamPower());
                if (c != null) {
                    hits++;
                    GuildMember real = truthByName.get(GameNameNormalizer.key(memberNames.get(a.memberId())));
                    if (real == null || !hasTeam(real, c)) {
                        wrong++;
                    }
                }
            }
        }
        return new int[]{hits, wrong};
    }

    /** True if the real member has a team of the composition's kind with the same heroes/titans. */
    static boolean hasTeam(GuildMember member, Composition c) {
        Set<String> units = new HashSet<>(c.unitIds());
        if (c.kind() == TeamKind.HERO) {
            return member.heroTeams().stream().anyMatch(t -> t.heroes() != null
                    && units.equals(new HashSet<>(t.heroes().stream().map(Hero::id).toList())));
        }
        return member.titanTeams().stream()
                .anyMatch(t -> units.equals(new HashSet<>(t.titans().stream().map(Titan::id).toList())));
    }
}
