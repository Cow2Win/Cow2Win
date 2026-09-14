package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.eval.BestPossibleLineupAlgorithm.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Second unit test class for this project (see {@link BestPossibleLineupAlgorithmTest}, the
 * first one, which covers {@link BestPossibleLineupAlgorithm#computeUnlockDepths(List)} only).
 * This class covers the actual team-to-fortification assignment logic: sorting criteria
 * (strongest-first for the bridge, best-buff-fit vs. lowest-sortScore for everything else),
 * buff-fit computation, and edge cases such as incomplete (fewer than 5 members) teams.
 *
 * <p>Two complementary styles of test are used here:
 * <ul>
 *     <li>{@link AssignStrongestFirstTests}, {@link AssignOneBuffedTests} and
 *     {@link AssignOneUnbuffedTests} call
 *     {@link BestPossibleLineupAlgorithm#assignStrongestFirst}/{@link BestPossibleLineupAlgorithm#assignOne}
 *     directly with a synthetic {@link Fortification} and a hand-built candidate pool - both
 *     methods take the fortification and pool as plain parameters, so (like
 *     {@code computeUnlockDepths} in the first test class) they were widened from {@code private}
 *     to package-private for exactly this purpose. This gives full control over totalPower,
 *     buff-fit and sortScore values without depending on the real fortification catalog.</li>
 *     <li>{@link FillFortificationsIntegrationTests} instead calls the public
 *     {@link BestPossibleLineupAlgorithm#run(Lineup, Guild)} entry point. This is necessary for
 *     {@code fillFortifications()} itself (the bridge/coverage/fill-remaining-capacity
 *     orchestration and its interaction with {@code computeUnlockDepths}), because that method -
 *     unlike {@code assignStrongestFirst}/{@code assignOne} - reads the fortification catalog
 *     directly from {@code FortificationRepository} and cannot be pointed at a synthetic one.
 *     These tests therefore rely on the REAL {@code fortifications.json} catalog (the same one
 *     {@code BestPossibleLineupAlgorithmTest#realCatalogMatchesDocumentedDepths} pins to exactly
 *     20 entries) and use its known ids/capacities/strategicImportance/buffs, kept few and small
 *     enough (well below every fortification's capacity, except where capacity is the point) to
 *     stay traceable by hand.</li>
 * </ul>
 */
class BestPossibleLineupAlgorithmAssignmentTest {

    // --- shared fixtures -------------------------------------------------

    private static Fortification fort(String id, FortificationType type, int capacity, Buff buff) {
        return new Fortification(id, type, capacity, 100, 0, 0, buff, List.of(), 1);
    }

    /** A hero with an explicit generalScore/roles, avatar and buffFitScores overrides left at their defaults. */
    private static Hero hero(String id, ScoreTier generalScore, Role... roles) {
        return new Hero(id, List.of(roles), null, generalScore, null);
    }

    private static Titan titan(String id, ScoreTier generalScore, TitanElement element) {
        return new Titan(id, element, null, generalScore, null);
    }

    private static Candidate<HeroTeam> candidate(String memberId, HeroTeam team, int teamIndex) {
        return new Candidate<>(memberId, team, teamIndex, team.totalPower(), team.sortScore());
    }

    private static Candidate<TitanTeam> candidate(String memberId, TitanTeam team, int teamIndex) {
        return new Candidate<>(memberId, team, teamIndex, team.totalPower(), team.sortScore());
    }

    // --- assignStrongestFirst (Criterion 1: the bridge) -------------------

    @Nested
    @DisplayName("assignStrongestFirst (bridge: Criterion 1)")
    class AssignStrongestFirstTests {

        @Test
        @DisplayName("Fills free capacity with the strongest candidates first, ties broken by memberId")
        void fillsStrongestFirstTiesBrokenByMemberId() {
            Fortification bridge = fort("bridge", FortificationType.HERO, 2, null);
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(
                    candidate("m1", new HeroTeam("m1", List.of(hero("h1", ScoreTier.STANDARD, Role.TANK)), 500), 0),
                    candidate("m2", new HeroTeam("m2", List.of(hero("h2", ScoreTier.STANDARD, Role.TANK)), 800), 0),
                    candidate("m3", new HeroTeam("m3", List.of(hero("h3", ScoreTier.STANDARD, Role.TANK)), 800), 0),
                    candidate("m4", new HeroTeam("m4", List.of(hero("h4", ScoreTier.STANDARD, Role.TANK)), 300), 0)
            ));
            List<Lineup.Entry> updatedEntries = new ArrayList<>();

            BestPossibleLineupAlgorithm.assignStrongestFirst(bridge, pool, updatedEntries, Lineup.TeamType.HERO);

            // capacity 2, so only the two 800-power candidates make the cut - m1 (500) and m4
            // (300) are weaker and must remain untouched in the pool for later passes.
            assertEquals(2, updatedEntries.size());
            assertEquals("m2", updatedEntries.get(0).teamMemberId(), "tie at 800 power broken by memberId: m2 before m3");
            assertEquals("m3", updatedEntries.get(1).teamMemberId());
            for (Lineup.Entry entry : updatedEntries) {
                assertEquals("bridge", entry.fortificationId());
                assertEquals(800, entry.totalPower());
                assertEquals(0, entry.buffFitScore(), "the bridge never has a buff, so buffFitScore must be 0");
                assertEquals(800.0, entry.weightedScore(), "weightedScore must be the value the pick was made on (totalPower)");
            }
            assertEquals(2, pool.size(), "the two assigned candidates must be removed from the pool");
            assertTrue(pool.stream().noneMatch(c -> c.memberId().equals("m2") || c.memberId().equals("m3")));
        }

        @Test
        @DisplayName("Only fills the FREE capacity, honoring entries already present (e.g. manual picks)")
        void onlyFillsFreeCapacity() {
            Fortification bridge = fort("bridge", FortificationType.HERO, 3, null);
            List<Lineup.Entry> updatedEntries = new ArrayList<>(List.of(
                    new Lineup.Entry("bridge", "existing1", Lineup.TeamType.HERO, 0, 999, 0, 999),
                    new Lineup.Entry("bridge", "existing2", Lineup.TeamType.HERO, 0, 999, 0, 999)
            ));
            // Only 1 free slot left (capacity 3 - 2 existing entries).
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(
                    candidate("weak", new HeroTeam("weak", List.of(hero("h1", ScoreTier.STANDARD, Role.TANK)), 500), 0),
                    candidate("strong", new HeroTeam("strong", List.of(hero("h2", ScoreTier.STANDARD, Role.TANK)), 900), 0)
            ));

            BestPossibleLineupAlgorithm.assignStrongestFirst(bridge, pool, updatedEntries, Lineup.TeamType.HERO);

            assertEquals(3, updatedEntries.size(), "capacity is 3, no more than that may ever be assigned");
            assertEquals("strong", updatedEntries.get(2).teamMemberId(), "the single free slot goes to the stronger remaining candidate");
            assertEquals(1, pool.size());
            assertEquals("weak", pool.get(0).memberId(), "the weaker candidate that didn't fit must remain in the pool");
        }

        @Test
        @DisplayName("Does nothing when the fortification has no free capacity left")
        void doesNothingWhenFull() {
            Fortification bridge = fort("bridge", FortificationType.HERO, 1, null);
            List<Lineup.Entry> updatedEntries = new ArrayList<>(List.of(
                    new Lineup.Entry("bridge", "existing", Lineup.TeamType.HERO, 0, 999, 0, 999)
            ));
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(
                    candidate("m1", new HeroTeam("m1", List.of(hero("h1", ScoreTier.STANDARD, Role.TANK)), 500), 0)
            ));

            BestPossibleLineupAlgorithm.assignStrongestFirst(bridge, pool, updatedEntries, Lineup.TeamType.HERO);

            assertEquals(1, updatedEntries.size(), "no new entry may be added - the fortification is already full");
            assertEquals(1, pool.size(), "the pool must stay untouched");
        }
    }

    // --- assignOne (Criteria 3 and 5: buffed vs. unbuffed) ----------------

    @Nested
    @DisplayName("assignOne (Criterion 3: buffed fortification picks the best buff fit)")
    class AssignOneBuffedTests {

        @Test
        @DisplayName("Picks the candidate with the highest role-match buffFitScore")
        void picksHighestBuffFitScore() {
            Buff warriorBuff = new RoleBuff(Role.WARRIOR, BuffEffect.MAGIC_DEFENSE_INCREASE, 3.0);
            Fortification bastion = fort("bastion", FortificationType.HERO, 5, warriorBuff);

            HeroTeam noWarriors = new HeroTeam("a", List.of(hero("h1", ScoreTier.STANDARD, Role.MAGE)), 1000);
            HeroTeam twoWarriors = new HeroTeam("b", List.of(
                    hero("h2", ScoreTier.STANDARD, Role.WARRIOR), hero("h3", ScoreTier.STANDARD, Role.WARRIOR)), 500);
            HeroTeam oneWarrior = new HeroTeam("c", List.of(hero("h4", ScoreTier.STANDARD, Role.WARRIOR)), 200);
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(
                    candidate("a", noWarriors, 0), candidate("b", twoWarriors, 0), candidate("c", oneWarrior, 0)));
            List<Lineup.Entry> updatedEntries = new ArrayList<>();

            BestPossibleLineupAlgorithm.assignOne(bastion, pool, updatedEntries, Lineup.TeamType.HERO, HeroTeam::buffFitScore);

            assertEquals(1, updatedEntries.size());
            Lineup.Entry entry = updatedEntries.get(0);
            assertEquals("b", entry.teamMemberId(), "team 'b' has the best buff fit (2 warriors) despite the lowest totalPower");
            assertEquals(2, entry.buffFitScore());
            assertEquals(500, entry.totalPower());
            assertEquals(2.0, entry.weightedScore(), "weightedScore for a buffed pick must be the buffFitScore");
            assertEquals(2, pool.size(), "only the chosen candidate is removed from the pool");
            assertTrue(pool.stream().noneMatch(c -> c.memberId().equals("b")));
        }

        @Test
        @DisplayName("Breaks a buff-fit tie by lowest totalPower, keeping stronger teams in the pool")
        void breaksTieByLowestTotalPower() {
            Buff tankBuff = new RoleBuff(Role.TANK, BuffEffect.HEALTH_INCREASE, 10.0);
            Fortification foundry = fort("foundry", FortificationType.HERO, 5, tankBuff);

            HeroTeam strongTie = new HeroTeam("x", List.of(
                    hero("h1", ScoreTier.STANDARD, Role.TANK), hero("h2", ScoreTier.STANDARD, Role.TANK)), 900);
            HeroTeam weakTie = new HeroTeam("y", List.of(
                    hero("h3", ScoreTier.STANDARD, Role.TANK), hero("h4", ScoreTier.STANDARD, Role.TANK)), 300);
            HeroTeam worseFit = new HeroTeam("z", List.of(hero("h5", ScoreTier.STANDARD, Role.TANK)), 100);
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(
                    candidate("x", strongTie, 0), candidate("y", weakTie, 0), candidate("z", worseFit, 0)));
            List<Lineup.Entry> updatedEntries = new ArrayList<>();

            BestPossibleLineupAlgorithm.assignOne(foundry, pool, updatedEntries, Lineup.TeamType.HERO, HeroTeam::buffFitScore);

            assertEquals("y", updatedEntries.get(0).teamMemberId(),
                    "both x and y fit equally well (2 tanks) - the weaker one (y) must be picked so x stays available");
            assertTrue(pool.stream().anyMatch(c -> c.memberId().equals("x")), "the stronger, equally-fitting candidate must remain in the pool");
        }

        @Test
        @DisplayName("Works identically for TITAN teams via ElementBuff (element match instead of role match)")
        void worksForTitanTeamsViaElementBuff() {
            Buff fireBuff = new ElementBuff(TitanElement.FIRE, BuffEffect.HEALTH_INCREASE, 8.0);
            Fortification bastionOfFire = fort("bastion-of-fire", FortificationType.TITAN, 4, fireBuff);

            TitanTeam noFireTitans = new TitanTeam("p", List.of(titan("t1", ScoreTier.STANDARD, TitanElement.WATER)), 1000);
            TitanTeam twoFireTitans = new TitanTeam("q", List.of(
                    titan("t2", ScoreTier.STANDARD, TitanElement.FIRE), titan("t3", ScoreTier.STANDARD, TitanElement.FIRE)), 400);
            List<Candidate<TitanTeam>> pool = new ArrayList<>(List.of(candidate("p", noFireTitans, 0), candidate("q", twoFireTitans, 0)));
            List<Lineup.Entry> updatedEntries = new ArrayList<>();

            BestPossibleLineupAlgorithm.assignOne(bastionOfFire, pool, updatedEntries, Lineup.TeamType.TITAN, TitanTeam::buffFitScore);

            assertEquals("q", updatedEntries.get(0).teamMemberId());
            assertEquals(2, updatedEntries.get(0).buffFitScore());
        }

        @Test
        @DisplayName("Edge case: an incomplete team (1 of up to 5 members) is scored correctly, not skipped or penalized")
        void incompleteTeamIsScoredCorrectly() {
            Buff healerBuff = new RoleBuff(Role.HEALER, BuffEffect.HEALING_INCREASE, 7.0);
            Fortification cityHall = fort("city-hall", FortificationType.HERO, 5, healerBuff);

            // Only 1 of the (up to 5 possible) heroes, but that one matches - vs. a fuller,
            // 4-hero team where only 1 hero happens to match too. Same buffFitScore (1) either
            // way, so the tie-break (lowest totalPower) must decide, proving the incomplete
            // team's score was computed from its ACTUAL roster (not padded/defaulted).
            HeroTeam oneHealerOnly = new HeroTeam("single", List.of(hero("h1", ScoreTier.STANDARD, Role.HEALER)), 50);
            HeroTeam fourHeroesOneHealer = new HeroTeam("full", List.of(
                    hero("h2", ScoreTier.STANDARD, Role.HEALER), hero("h3", ScoreTier.STANDARD, Role.TANK),
                    hero("h4", ScoreTier.STANDARD, Role.MAGE), hero("h5", ScoreTier.STANDARD, Role.WARRIOR)), 400);
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(candidate("single", oneHealerOnly, 0), candidate("full", fourHeroesOneHealer, 0)));
            List<Lineup.Entry> updatedEntries = new ArrayList<>();

            BestPossibleLineupAlgorithm.assignOne(cityHall, pool, updatedEntries, Lineup.TeamType.HERO, HeroTeam::buffFitScore);

            assertEquals(1, updatedEntries.get(0).buffFitScore(), "the 1-hero team's single healer must still count as a match");
            assertEquals("single", updatedEntries.get(0).teamMemberId(), "equal buffFitScore (1) - tie-break must pick the lower totalPower");
        }
    }

    @Nested
    @DisplayName("assignOne (Criterion 5: unbuffed fortification picks the lowest sortScore)")
    class AssignOneUnbuffedTests {

        @Test
        @DisplayName("Picks the lowest sortScore, NOT the lowest totalPower, when the two disagree")
        void picksLowestSortScoreNotLowestTotalPower() {
            Fortification lighthouse = fort("lighthouse", FortificationType.HERO, 5, null);

            // Higher totalPower (120 000) but poor heroes (NEGATIVE=0.4 each):
            // sortScore = 5*0.4 + 120000/100000 = 2.0 + 1.2 = 3.2
            HeroTeam highPowerLowGeneralScore = new HeroTeam("strongButWeakHeroes", List.of(
                    hero("h1", ScoreTier.NEGATIVE, Role.TANK), hero("h2", ScoreTier.NEGATIVE, Role.TANK),
                    hero("h3", ScoreTier.NEGATIVE, Role.TANK), hero("h4", ScoreTier.NEGATIVE, Role.TANK),
                    hero("h5", ScoreTier.NEGATIVE, Role.TANK)), 120_000);
            // Lower totalPower (100 000) but excellent heroes (ELEVATED=0.9 each):
            // sortScore = 5*0.9 + 100000/100000 = 4.5 + 1.0 = 5.5
            HeroTeam lowPowerHighGeneralScore = new HeroTeam("weakerButEliteHeroes", List.of(
                    hero("h6", ScoreTier.ELEVATED, Role.TANK), hero("h7", ScoreTier.ELEVATED, Role.TANK),
                    hero("h8", ScoreTier.ELEVATED, Role.TANK), hero("h9", ScoreTier.ELEVATED, Role.TANK),
                    hero("h10", ScoreTier.ELEVATED, Role.TANK)), 100_000);
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(
                    candidate("strongButWeakHeroes", highPowerLowGeneralScore, 0),
                    candidate("weakerButEliteHeroes", lowPowerHighGeneralScore, 0)));
            List<Lineup.Entry> updatedEntries = new ArrayList<>();

            BestPossibleLineupAlgorithm.assignOne(lighthouse, pool, updatedEntries, Lineup.TeamType.HERO, HeroTeam::buffFitScore);

            Lineup.Entry entry = updatedEntries.get(0);
            assertEquals("strongButWeakHeroes", entry.teamMemberId(),
                    "sortScore (3.2) must decide, even though this team has MORE totalPower than the other candidate (sortScore 5.5)");
            assertEquals(0, entry.buffFitScore(), "no buff to fit, so buffFitScore must be 0");
            assertEquals(3.2, entry.weightedScore(), 1e-9, "weightedScore for an unbuffed pick must be the chosen candidate's sortScore");
        }

        @Test
        @DisplayName("Edge case: sortScore of an incomplete (1-hero) team is computed from its actual roster, not a padded one")
        void incompleteTeamSortScoreUsesActualRosterSize() {
            Fortification mageAcademy = fort("mage-academy", FortificationType.HERO, 3, null);

            // 1 hero at STANDARD (0.8): sortScore = 0.8 + 10000/100000 = 0.8 + 0.1 = 0.9
            HeroTeam oneHero = new HeroTeam("solo", List.of(hero("h1", ScoreTier.STANDARD, Role.TANK)), 10_000);
            // 5 heroes at STANDARD (0.8) each: sortScore = 4.0 + 10000/100000 = 4.0 + 0.1 = 4.1
            HeroTeam fiveHeroes = new HeroTeam("full", List.of(
                    hero("h2", ScoreTier.STANDARD, Role.TANK), hero("h3", ScoreTier.STANDARD, Role.TANK),
                    hero("h4", ScoreTier.STANDARD, Role.TANK), hero("h5", ScoreTier.STANDARD, Role.TANK),
                    hero("h6", ScoreTier.STANDARD, Role.TANK)), 10_000);
            List<Candidate<HeroTeam>> pool = new ArrayList<>(List.of(candidate("solo", oneHero, 0), candidate("full", fiveHeroes, 0)));
            List<Lineup.Entry> updatedEntries = new ArrayList<>();

            BestPossibleLineupAlgorithm.assignOne(mageAcademy, pool, updatedEntries, Lineup.TeamType.HERO, HeroTeam::buffFitScore);

            assertEquals("solo", updatedEntries.get(0).teamMemberId(),
                    "the 1-hero team has the lower sortScore (0.9 vs. 4.1) precisely because it is scored by its own roster size");
        }
    }

    // --- fillFortifications, via the public run() entry point -------------

    @Nested
    @DisplayName("fillFortifications via run() (real fortifications.json catalog)")
    class FillFortificationsIntegrationTests {

        private static GuildMember heroMember(String id, int totalPower) {
            HeroTeam team = new HeroTeam(id, List.of(hero(id + "-hero", ScoreTier.STANDARD, Role.MAGE)), totalPower);
            return new GuildMember(id, id, List.of(team), List.of());
        }

        private static GuildMember titanMember(String id, int totalPower) {
            TitanTeam team = new TitanTeam(id, List.of(titan(id + "-titan", ScoreTier.STANDARD, TitanElement.WATER)), totalPower);
            return new GuildMember(id, id, List.of(), List.of(team));
        }

        private static Lineup emptyLineup(Guild guild) {
            return new Lineup(guild.id(), guild.name(), "", null, List.of());
        }

        @Test
        @DisplayName("Bridge (Criterion 1) takes the strongest teams up to its capacity, breadth-first coverage (Criterion 2) picks up the rest")
        void bridgeThenBreadthFirstCoverage() {
            // heros-bridge has capacity 6 - 7 single-team members means exactly 1 team is left
            // over for the coverage pass afterwards.
            List<GuildMember> members = List.of(
                    heroMember("m700", 700), heroMember("m650", 650), heroMember("m600", 600),
                    heroMember("m550", 550), heroMember("m500", 500), heroMember("m450", 450),
                    heroMember("m100", 100));
            Guild guild = new Guild("g1", "Guild", members);
            Lineup result = new BestPossibleLineupAlgorithm().run(emptyLineup(guild), guild);

            List<Lineup.Entry> bridgeEntries = entriesFor(result, "heros-bridge");
            assertEquals(6, bridgeEntries.size(), "bridge capacity is 6");
            assertEquals(Map.of("m700", 700, "m650", 650, "m600", 600, "m550", 550, "m500", 500, "m450", 450),
                    bridgeEntries.stream().collect(Collectors.toMap(Lineup.Entry::teamMemberId, Lineup.Entry::totalPower)),
                    "the 6 STRONGEST teams must be the ones on the bridge");

            List<Lineup.Entry> barracksEntries = entriesFor(result, "barracks");
            assertEquals(1, barracksEntries.size(),
                    "the single leftover (weakest) team must go to 'barracks' - the highest-strategicImportance, "
                            + "lowest-unlockDepth non-bridge HERO fortification");
            assertEquals("m100", barracksEntries.get(0).teamMemberId());

            assertEquals(7, result.entries().size(), "no team may be left unassigned or assigned twice");
        }

        @Test
        @DisplayName("Coverage pass (Criterion 2) gives every fortification its FIRST team, in strategicImportance/unlockDepth/id order, before any gets a second")
        void breadthBeforeDepthAcrossMultipleFortifications() {
            // 6 for the bridge + exactly 4 more: per the documented order (strategicImportance
            // desc, then unlockDepth asc, then id asc) the next four HERO fortifications after
            // heros-bridge are barracks, lighthouse, mage-academy (all importance 4, depth 0,
            // ordered by id) and then engineerium (importance 2, depth 1, the lowest-id
            // candidate at that tier) - see BestPossibleLineupAlgorithmTest's documented depth
            // table for bastion-of-fire/gates-of-nature/bastion-of-ice etc.
            List<GuildMember> members = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                members.add(heroMember("bridgeTeam" + i, 1000 - i));
            }
            for (int i = 0; i < 4; i++) {
                members.add(heroMember("coverageTeam" + i, 50 - i));
            }
            Guild guild = new Guild("g2", "Guild", members);
            Lineup result = new BestPossibleLineupAlgorithm().run(emptyLineup(guild), guild);

            assertEquals(6, entriesFor(result, "heros-bridge").size());
            assertEquals(1, entriesFor(result, "barracks").size());
            assertEquals(1, entriesFor(result, "lighthouse").size());
            assertEquals(1, entriesFor(result, "mage-academy").size());
            assertEquals(1, entriesFor(result, "engineerium").size(),
                    "engineerium (importance 2, depth 1, lowest id at that tier) must be the 4th fortification covered, "
                            + "NOT foundry (also importance 2/depth 1, but 'foundry' > 'engineerium')");
            assertTrue(entriesFor(result, "foundry").isEmpty(), "the pool is exhausted after 10 teams - foundry must get nothing yet");
            assertTrue(entriesFor(result, "bastion").isEmpty());
            assertEquals(10, result.entries().size());
        }

        @Test
        @DisplayName("Edge case: an already-assigned (e.g. manual) team is left untouched and never reassigned elsewhere (additive algorithm)")
        void preExistingManualAssignmentIsPreservedAndTeamIsNotReoffered() {
            GuildMember manuallyPlaced = heroMember("manual", 999_999); // by far the strongest - would otherwise win the bridge
            GuildMember others = heroMember("other", 100);
            Guild guild = new Guild("g3", "Guild", List.of(manuallyPlaced, others));

            Lineup.Entry manualEntry = new Lineup.Entry("citadel", "manual", Lineup.TeamType.HERO, 0, 999_999, 0, 999_999);
            Lineup lineupWithManualPick = new Lineup(guild.id(), guild.name(), "", null, List.of(manualEntry));

            Lineup result = new BestPossibleLineupAlgorithm().run(lineupWithManualPick, guild);

            assertTrue(result.entries().contains(manualEntry), "the manual entry must be preserved exactly as-is");
            List<Lineup.Entry> forManualMember = result.entries().stream()
                    .filter(e -> e.teamMemberId().equals("manual")).toList();
            assertEquals(1, forManualMember.size(), "the manually-placed team must not receive a SECOND assignment elsewhere");
            assertEquals("citadel", forManualMember.get(0).fortificationId());

            // The 'other' member's much weaker team is free to be picked up normally.
            assertTrue(result.entries().stream().anyMatch(e -> e.teamMemberId().equals("other")));
        }

        @Test
        @DisplayName("HERO and TITAN sides run independently - a guild with only titan teams produces only TITAN entries")
        void titanSideRunsIndependentlyOfHeroSide() {
            List<GuildMember> members = List.of(titanMember("t1", 500), titanMember("t2", 300));
            Guild guild = new Guild("g4", "Guild", members);

            Lineup result = new BestPossibleLineupAlgorithm().run(emptyLineup(guild), guild);

            assertEquals(2, result.entries().size());
            assertTrue(result.entries().stream().allMatch(e -> e.teamType() == Lineup.TeamType.TITAN));
            assertTrue(result.entries().stream().allMatch(e -> e.fortificationId().equals("bridge")),
                    "both titan teams fit within the TITAN bridge's capacity (6), so both must land there (Criterion 1)");
        }

        private List<Lineup.Entry> entriesFor(Lineup lineup, String fortificationId) {
            return lineup.entries().stream().filter(e -> e.fortificationId().equals(fortificationId)).toList();
        }
    }
}
