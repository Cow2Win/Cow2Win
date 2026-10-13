package org.c2w.domain;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link FortificationAssignment}: the candidates for assigning a team to one fortification - GUI-free. */
class FortificationAssignmentTest {

    private static Fortification heroFortification(int n) {
        return FortificationRepository.findAll().stream().filter(f -> f.type() == FortificationType.HERO)
                .skip(n).findFirst().orElseThrow();
    }

    private static HeroTeam heroTeam(String memberId, int index, int power) {
        return new HeroTeam(memberId, index, List.of(new Hero(memberId + index, List.of(Role.MAGE))), power);
    }

    /** m1: hero teams 500 and 900; m2: hero team 700; m3: hero team 300 and a titan team. */
    private static Guild guild() {
        return new Guild("g", "G", List.of(
                new GuildMember("m1", "Anna", List.of(heroTeam("m1", 0, 500), heroTeam("m1", 1, 900)), List.of()),
                new GuildMember("m2", "Bert", List.of(heroTeam("m2", 0, 700)), List.of()),
                new GuildMember("m3", "Cleo", List.of(heroTeam("m3", 0, 300)),
                        List.of(new TitanTeam("m3", 0, List.of(new Titan("t", TitanElement.WATER)), 5000)))));
    }

    private static Lineup lineup(Lineup.Entry... entries) {
        return new Lineup("g", "G", "", LocalDateTime.now(), List.of(entries));
    }

    @Test
    @DisplayName("Free teams first, then those in other fortifications, each by CowScore; teams of this fortification and of the other type are missing")
    void candidates() {
        Fortification target = heroFortification(0);
        Fortification other = heroFortification(1);
        Guild guild = guild();
        Lineup lineup = lineup(
                new Lineup.Entry(target.id(), "m2", Lineup.TeamType.HERO, 0),
                new Lineup.Entry(other.id(), "m1", Lineup.TeamType.HERO, 1),
                new Lineup.Entry(other.id(), "m3", Lineup.TeamType.HERO, 0));

        List<FortificationAssignment.Candidate> candidates = FortificationAssignment.candidatesFor(target, lineup, guild);

        assertEquals(List.of("m1#0", "m1#1", "m3#0"),
                candidates.stream().map(c -> c.memberId() + "#" + c.teamIndex()).toList());
        assertTrue(candidates.get(0).isFree());
        assertEquals("Anna", candidates.get(0).memberName());
        assertEquals(500, candidates.get(0).power());
        assertEquals(other.id(), candidates.get(1).currentFortificationId());
        assertEquals(other.id(), candidates.get(2).currentFortificationId());
        assertTrue(candidates.stream().allMatch(c -> c.teamType() == Lineup.TeamType.HERO));
        assertEquals(TeamScoreCalculator.scoreFor(guild.members().get(0).heroTeams().get(1), target).total(),
                candidates.get(1).cowScore(), 1e-9);
    }

    @Test
    @DisplayName("Within each part the higher CowScore comes first")
    void sortedByCowScore() {
        Fortification target = heroFortification(0);
        List<FortificationAssignment.Candidate> candidates = FortificationAssignment.candidatesFor(target, lineup(), guild());

        assertEquals(List.of(900, 700, 500, 300), candidates.stream().map(FortificationAssignment.Candidate::power).toList());
        assertTrue(candidates.stream().allMatch(FortificationAssignment.Candidate::isFree));
    }

    @Test
    @DisplayName("Titan fortifications offer the titan teams only")
    void titanCandidates() {
        Fortification titanFort = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.TITAN).findFirst().orElseThrow();
        List<FortificationAssignment.Candidate> candidates = FortificationAssignment.candidatesFor(titanFort, lineup(), guild());

        assertEquals(1, candidates.size());
        assertEquals(Lineup.TeamType.TITAN, candidates.get(0).teamType());
        assertEquals("m3", candidates.get(0).memberId());
    }
}
