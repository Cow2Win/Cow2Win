package org.c2w.domain;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * A small guild and lineup for the summed CowScore (see {@link BuffCalculationService#sumHeroCowScore}),
 * shared by the domain, report and overview tests. Uses real fortifications from the catalog, since
 * the sums look them up there.
 *
 * <ul>
 *     <li>m1 hero team 0 (1 000 000 power): {@link #roleFort} with two heroes of its buff role - bonus;</li>
 *     <li>m1 hero team 1 (700 000 power): {@link #plainHeroFort} (no buff), no marks/pet/war flag - no bonus;</li>
 *     <li>m2 titan team 0 (2 000 000 power): {@link #elementFort} with one titan of its buff element - bonus;</li>
 *     <li>m2 titan team 1 (500 000 power): {@link #elementFort}, a titan of another element.</li>
 * </ul>
 */
public final class CowScoreFixture {

    public static final int PLAIN_HERO_POWER = 700_000;

    public final Fortification roleFort;
    public final Fortification plainHeroFort;
    public final Fortification elementFort;
    public final HeroTeam buffHeroTeam;
    public final HeroTeam plainHeroTeam;
    public final TitanTeam buffTitanTeam;
    public final TitanTeam otherTitanTeam;
    public final Guild guild;
    public final Lineup lineup;

    public CowScoreFixture() {
        List<Fortification> all = FortificationRepository.findAll();
        roleFort = all.stream().filter(f -> f.type() == FortificationType.HERO && f.buff() instanceof RoleBuff)
                .findFirst().orElseThrow();
        plainHeroFort = all.stream().filter(f -> f.type() == FortificationType.HERO && f.buff() == null)
                .findFirst().orElseThrow();
        elementFort = all.stream().filter(f -> f.type() == FortificationType.TITAN && f.buff() instanceof ElementBuff)
                .findFirst().orElseThrow();
        Role role = ((RoleBuff) roleFort.buff()).role();
        Role otherRole = Arrays.stream(Role.values()).filter(r -> r != role).findFirst().orElseThrow();
        TitanElement element = ((ElementBuff) elementFort.buff()).element();
        TitanElement otherElement = Arrays.stream(TitanElement.values()).filter(e -> e != element).findFirst().orElseThrow();

        buffHeroTeam = new HeroTeam("m1", 0, List.of(new Hero("h1", List.of(role)), new Hero("h2", List.of(role)),
                new Hero("h3", List.of(otherRole))), null, null, 1_000_000, null);
        plainHeroTeam = new HeroTeam("m1", 1, List.of(new Hero("h4", List.of(otherRole))), null, null,
                PLAIN_HERO_POWER, null);
        buffTitanTeam = new TitanTeam("m2", 0, List.of(new Titan("t1", element)), 2_000_000);
        otherTitanTeam = new TitanTeam("m2", 1, List.of(new Titan("t2", otherElement)), 500_000);
        guild = new Guild("g1", "Guild", List.of(
                new GuildMember("m1", "Member One", List.of(buffHeroTeam, plainHeroTeam), List.of()),
                new GuildMember("m2", "Member Two", List.of(), List.of(buffTitanTeam, otherTitanTeam))));
        lineup = new Lineup("g1", "Guild", "", LocalDateTime.of(2026, 10, 16, 12, 0), List.of(
                new Lineup.Entry(roleFort.id(), "m1", Lineup.TeamType.HERO, 0),
                new Lineup.Entry(plainHeroFort.id(), "m1", Lineup.TeamType.HERO, 1),
                new Lineup.Entry(elementFort.id(), "m2", Lineup.TeamType.TITAN, 0),
                new Lineup.Entry(elementFort.id(), "m2", Lineup.TeamType.TITAN, 1)));
    }

    /** The summed CowScore totals of the placed hero teams. */
    public double expectedHeroCowScore() {
        return TeamScoreCalculator.scoreFor(buffHeroTeam, roleFort).total()
                + TeamScoreCalculator.scoreFor(plainHeroTeam, plainHeroFort).total();
    }

    /** The summed CowScore totals of the placed titan teams (all at {@link #elementFort}). */
    public double expectedTitanCowScore() {
        return TeamScoreCalculator.scoreFor(buffTitanTeam, elementFort).total()
                + TeamScoreCalculator.scoreFor(otherTitanTeam, elementFort).total();
    }
}
