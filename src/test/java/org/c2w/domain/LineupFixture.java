package org.c2w.domain;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A small guild plus a "before" and an "after" lineup that cover every kind
 * of change, for {@link LineupComparisonServiceTest} and
 * {@link LineupChangePlanServiceTest}. Uses real fortification ids from the
 * catalog, since both services look fortifications up there.
 *
 * <ul>
 *     <li>m1 hero team 0 (1 000 power): fortification A in both - unchanged;</li>
 *     <li>m1 hero team 1 (2 000 power): A before, B after - moved;</li>
 *     <li>m1 titan team 0 (500 power): titan fortification T before, gone after - removed;</li>
 *     <li>m2 hero team 0 (3 000 power): not deployed before, C after - added.</li>
 * </ul>
 */
final class LineupFixture {

    final String fortA;
    final String fortB;
    final String fortC;
    final String fortT;
    final Guild guild;
    final Lineup before;
    final Lineup after;

    LineupFixture() {
        List<Fortification> heroForts = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO && f.capacity() >= 2)
                .toList();
        fortA = heroForts.get(0).id();
        fortB = heroForts.get(1).id();
        fortC = heroForts.get(2).id();
        fortT = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.TITAN)
                .findFirst().orElseThrow().id();

        GuildMember m1 = new GuildMember("m1", "Member One",
                List.of(heroTeam("m1", 0, 1_000), heroTeam("m1", 1, 2_000)),
                List.of(new TitanTeam("m1", 0, List.of(new Titan("ignis", TitanElement.FIRE)), 500, null)));
        GuildMember m2 = new GuildMember("m2", "Member Two", List.of(heroTeam("m2", 0, 3_000)), List.of());
        guild = new Guild("g1", "Guild", List.of(m1, m2));

        before = lineup(
                hero(fortA, "m1", 0),
                hero(fortA, "m1", 1),
                new Lineup.Entry(fortT, "m1", Lineup.TeamType.TITAN, 0));
        after = lineup(
                hero(fortA, "m1", 0),
                hero(fortB, "m1", 1),
                hero(fortC, "m2", 0));
    }

    private static HeroTeam heroTeam(String memberId, int index, int power) {
        return new HeroTeam(memberId, index, List.of(new Hero("h-" + memberId + "-" + index, List.of(Role.MAGE))),
                null, null, power, null);
    }

    private static Lineup.Entry hero(String fortificationId, String memberId, int teamIndex) {
        return new Lineup.Entry(fortificationId, memberId, Lineup.TeamType.HERO, teamIndex);
    }

    private static Lineup lineup(Lineup.Entry... entries) {
        return new Lineup("g1", "Guild", "", LocalDateTime.of(2026, 1, 1, 0, 0), List.of(entries));
    }
}
