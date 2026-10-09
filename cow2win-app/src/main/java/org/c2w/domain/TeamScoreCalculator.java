package org.c2w.domain;

import org.c2w.data.model.*;

import java.util.List;

/**
 * Computes a team's aggregate score (its "CowScore") against one specific
 * fortification - shared by every place that needs to rank/display a team's
 * fit for a fortification, e.g. {@code org.c2w.gui.fort.FortificationEntryDialog},
 * the value overview dialogs, the lineup algorithms (via {@code TeamSide})
 * and the report.
 *
 * <h2>Hero teams (see {@code cowscore-formel-konzept.md})</h2>
 * <pre>
 *     CowScore = totalPower / 100 000 x (1 + B)
 * </pre>
 * where the bonus {@code B} is the sum of (see {@link HeroBonus}):
 * <ul>
 *     <li><b>role buff</b>: {@link CowScoreBonuses#rolePercent()} (default 1.5 %) per hero whose
 *     role matches the fortification's {@link RoleBuff} (the automatic BUFF mark);</li>
 *     <li><b>hero/fortification relation</b>: +{@link CowScoreBonuses#relationPercent()} (default
 *     1.25 %) once if at least one hero is marked {@link FortMark#POSITIVE} for this
 *     fortification, minus the same once if at least one hero is
 *     marked {@link FortMark#NEGATIVE} (both: net 0); a mark on a buff-matching hero is
 *     ignored - the buff already counts;</li>
 *     <li><b>pet</b>: {@link CowScoreBonuses#petPercent()} (default 1.25 %) if the team's pet is
 *     marked for this fortification (its strength is already part of the power, so
 *     merely having one adds nothing);</li>
 *     <li><b>war flag</b>: {@link CowScoreBonuses#warFlagPercent()} (default 1.25 %) if the team's
 *     war flag is marked for this fortification, otherwise
 *     {@link #WAR_FLAG_PRESENT_PERCENT} for merely having one (its strength
 *     is not part of the power);</li>
 *     <li><b>team combos</b>: +{@link CowScoreBonuses#comboPercent()} (default 1.25 %) once if at
 *     least one active {@link TeamCombo} matches the team (all of its heroes are in
 *     the team) - no matter how many match, and independent of the
 *     fortification (see {@link #matchingCombos(HeroTeam)}).</li>
 * </ul>
 * Power stays the dominating factor - the bonus only decides between teams
 * of similar power.
 *
 * <p>The hero combos are loaded once at startup ({@code heroCombos.json},
 * see {@code TeamComboRepository}) and registered via {@link
 * #setHeroCombos}; until then (and in tests that do not set them) there are
 * none. Every hero-team method also has an overload taking the combos
 * explicitly.
 *
 * <h2>Titan teams</h2>
 * <pre>
 *     CowScore = totalPower / 100 000 x (1 + B)
 * </pre>
 * where the bonus {@code B} is the sum of (see {@link TitanBonus}):
 * <ul>
 *     <li><b>element buff</b>: {@link CowScoreBonuses#elementPercent()} (default 1.5 %) per titan
 *     whose element matches the fortification's {@link ElementBuff} (the automatic
 *     BUFF mark, at most 5 per team) - a double titan such as
 *     "Asherona and Pyro" is one catalog entry and thus counts once;</li>
 *     <li><b>titan/fortification relation</b>: +{@link CowScoreBonuses#relationPercent()}
 *     once if at least one titan is marked {@link FortMark#POSITIVE} for this
 *     fortification, minus the same once if at least one titan is
 *     marked {@link FortMark#NEGATIVE} (both: net 0); a mark on a buff-matching titan is ignored -
 *     the buff already counts;</li>
 *     <li><b>totems</b>: {@link CowScoreBonuses#totemPercent()} (default 1.25 %) per totem (at most
 *     2 totems, see {@link TitanTeam#totems()}) - independent of the
 *     fortification. Totems do not count as buff matches;</li>
 *     <li><b>team combos</b>: +{@link CowScoreBonuses#comboPercent()} (the same value as for
 *     hero teams) once if at least one active {@link TeamCombo} matches the team (all of its
 *     titans are in the team; a double titan is one member) - no matter how many match, and
 *     independent of the fortification (see {@link #matchingCombos(TitanTeam)});</li>
 *     <li><b>super titans</b>: {@link #SUPER_TITAN_PERCENT} (fixed 0.6 %) per super titan in the
 *     team ({@link Titan#superTitan()}, at most 5) - independent of the fortification and of
 *     marks (also for a super titan marked {@link FortMark#NEGATIVE}), and in addition to its
 *     element buff. Not a buff match.</li>
 * </ul>
 * The titan combos come from {@code titanCombos.json} (see {@code TeamComboRepository}) and
 * are registered via {@link #setTitanCombos}, exactly like the hero combos; every titan-team
 * method also has an overload taking the combos explicitly.
 *
 * <h2>Adjustable percentages</h2>
 * Every percentage except {@link #WAR_FLAG_PRESENT_PERCENT} and {@link #SUPER_TITAN_PERCENT}
 * (both fixed, below {@link CowScoreBonuses#MIN_BONUS}) can be adjusted in the settings
 * (see {@link CowScoreBonuses} for the ranges and the rule that a buff is the biggest bonus of
 * its side). They are registered via {@link #setBonuses} at startup and whenever the settings are
 * saved; until then (and in tests that do not set them) the defaults apply.
 */
public final class TeamScoreCalculator {

    /**
     * Divisor a team's totalPower is scaled down by - totalPower is typically
     * six/seven digits, this turns it into a handy single/double digit figure.
     * The single source of this value.
     */
    public static final double POWER_DIVISOR = 100_000.0;

    /** Bonus in percent for merely fielding a war flag (not marked for the fortification). */
    public static final double WAR_FLAG_PRESENT_PERCENT = 0.6;

    /** Bonus in percent per super titan in a titan team - fixed, at every fortification. */
    public static final double SUPER_TITAN_PERCENT = 0.6;

    /** The bonus percentages registered from the settings - see {@link #setBonuses}. */
    private static volatile CowScoreBonuses bonuses = CowScoreBonuses.DEFAULTS;

    /** The hero combos registered at startup - see {@link #setHeroCombos}. */
    private static volatile TeamCombos heroCombos = TeamCombos.NONE;

    /** The titan combos registered at startup - see {@link #setTitanCombos}. */
    private static volatile TeamCombos titanCombos = TeamCombos.NONE;

    private TeamScoreCalculator() {
    }

    /**
     * Registers the hero combos every hero-team calculation without explicit
     * combos uses - at startup ({@code WorkspaceBootstrap}) and after every
     * save in the CowScore dialog. null clears them.
     */
    public static void setHeroCombos(TeamCombos combos) {
        heroCombos = combos == null ? TeamCombos.NONE : combos;
    }

    /** The hero combos currently registered via {@link #setHeroCombos}. */
    public static TeamCombos heroCombos() {
        return heroCombos;
    }

    /**
     * Registers the titan combos every titan-team calculation without explicit
     * combos uses - at startup ({@code WorkspaceBootstrap}) and after every
     * save in the CowScore dialog. null clears them.
     */
    public static void setTitanCombos(TeamCombos combos) {
        titanCombos = combos == null ? TeamCombos.NONE : combos;
    }

    /** The titan combos currently registered via {@link #setTitanCombos}. */
    public static TeamCombos titanCombos() {
        return titanCombos;
    }

    /**
     * Registers the bonus percentages every calculation uses - at startup from the settings
     * ({@code WorkspaceBootstrap}) and again whenever they are saved there. null means the defaults.
     */
    public static void setBonuses(CowScoreBonuses newBonuses) {
        bonuses = newBonuses == null ? CowScoreBonuses.DEFAULTS : newBonuses;
    }

    /** The bonus percentages currently registered via {@link #setBonuses}. */
    public static CowScoreBonuses bonuses() {
        return bonuses;
    }

    /**
     * The individual bonus components of a hero team at one fortification,
     * each in percent - see the class Javadoc for how each is determined.
     */
    public record HeroBonus(double rolePercent, double relationPercent, double petPercent, double warFlagPercent,
                            double comboPercent) {

        /** The bonus B in percent - the sum of all components. */
        public double totalPercent() {
            return rolePercent + relationPercent + petPercent + warFlagPercent + comboPercent;
        }

        /** The components in a fixed order (role, relation, pet, war flag, combo) - see {@link Breakdown#memberScores()}. */
        List<Double> asList() {
            return List.of(rolePercent, relationPercent, petPercent, warFlagPercent, comboPercent);
        }
    }

    /**
     * The active registered hero combos (see {@link #setHeroCombos}) all of
     * whose heroes are in {@code team}, in file order - empty if none match.
     */
    public static List<TeamCombo> matchingCombos(HeroTeam team) {
        return matchingCombos(team, heroCombos);
    }

    /** Like {@link #matchingCombos(HeroTeam)}, with explicitly given {@code combos}. */
    public static List<TeamCombo> matchingCombos(HeroTeam team, TeamCombos combos) {
        if (team.heroes() == null || combos == null) {
            return List.of();
        }
        return combos.matching(team.heroes().stream().map(Hero::id).toList());
    }

    /** The bonus components of {@code team} at {@code fortification}, with the registered hero combos - see the class Javadoc. */
    public static HeroBonus heroBonus(HeroTeam team, Fortification fortification) {
        return heroBonus(team, fortification, heroCombos);
    }

    /** Like {@link #heroBonus(HeroTeam, Fortification)}, with explicitly given {@code combos}. */
    public static HeroBonus heroBonus(HeroTeam team, Fortification fortification, TeamCombos combos) {
        CowScoreBonuses b = bonuses;
        Buff buff = fortification.buff();
        String fortificationId = fortification.id();

        long roleMatches = team.heroes().stream().filter(h -> h.matchesBuff(buff)).count();
        double role = roleMatches * b.rolePercent();

        // A mark on a buff-matching hero is ignored - the buff already counts.
        boolean anyPositive = team.heroes().stream().filter(h -> !h.matchesBuff(buff))
                .anyMatch(h -> h.fortMark(fortificationId) == FortMark.POSITIVE);
        boolean anyNegative = team.heroes().stream().filter(h -> !h.matchesBuff(buff))
                .anyMatch(h -> h.fortMark(fortificationId) == FortMark.NEGATIVE);
        double relation = (anyPositive ? b.relationPercent() : 0) - (anyNegative ? b.relationPercent() : 0);

        double pet = team.pet() != null && team.pet().isMarkedFor(fortificationId) ? b.petPercent() : 0;

        double warFlag = 0;
        if (team.warFlag() != null) {
            warFlag = team.warFlag().isMarkedFor(fortificationId) ? b.warFlagPercent() : WAR_FLAG_PRESENT_PERCENT;
        }
        double combo = matchingCombos(team, combos).isEmpty() ? 0 : b.comboPercent();
        return new HeroBonus(role, relation, pet, warFlag, combo);
    }

    /**
     * Scores {@code team} against {@code fortification} with the registered
     * hero combos: totalPower / 100 000 x (1 + B) - see the class Javadoc.
     * {@link Breakdown#memberScores()} holds the five bonus components
     * (role, relation, pet, war flag, combo) converted into score points
     * (powerTerm x percent / 100), so that total = powerTerm +
     * sum(memberScores) holds.
     */
    public static Breakdown scoreFor(HeroTeam team, Fortification fortification) {
        return scoreFor(team, fortification, heroCombos);
    }

    /** Like {@link #scoreFor(HeroTeam, Fortification)}, with explicitly given {@code combos}. */
    public static Breakdown scoreFor(HeroTeam team, Fortification fortification, TeamCombos combos) {
        HeroBonus bonus = heroBonus(team, fortification, combos);
        double powerTerm = team.totalPower() / POWER_DIVISOR;
        List<Double> bonusPoints = bonus.asList().stream().map(percent -> powerTerm * percent / 100.0).toList();
        return breakdownFor(bonusPoints, powerTerm);
    }

    /**
     * The fortification-independent score of {@code team} with the
     * registered hero combos - used by the lineup algorithms for
     * fortifications without a buff and as a general ranking (see {@code
     * HeroTeam#sortScore()}): totalPower / 100 000 x (1 + {@link
     * #WAR_FLAG_PRESENT_PERCENT} if the team fields a war flag + {@link
     * CowScoreBonuses#comboPercent()} if a team combo matches). Everything else in the bonus
     * depends on a specific fortification.
     */
    public static double sortScore(HeroTeam team) {
        return sortScore(team, heroCombos);
    }

    /** Like {@link #sortScore(HeroTeam)}, with explicitly given {@code combos}. */
    public static double sortScore(HeroTeam team, TeamCombos combos) {
        double percent = team.warFlag() != null ? WAR_FLAG_PRESENT_PERCENT : 0;
        if (!matchingCombos(team, combos).isEmpty()) {
            percent += bonuses.comboPercent();
        }
        return team.totalPower() / POWER_DIVISOR * (1 + percent / 100.0);
    }

    /**
     * The individual bonus components of a titan team at one fortification,
     * each in percent - see the class Javadoc ("Titan teams") for how each is
     * determined.
     */
    public record TitanBonus(double elementPercent, double relationPercent, double totemPercent, double comboPercent,
                             double superTitanPercent) {

        /** The bonus B in percent - the sum of all components. */
        public double totalPercent() {
            return elementPercent + relationPercent + totemPercent + comboPercent + superTitanPercent;
        }

        /**
         * The components in a fixed order (element, relation, totems, combo, super titans) - see
         * {@link Breakdown#memberScores()}.
         */
        List<Double> asList() {
            return List.of(elementPercent, relationPercent, totemPercent, comboPercent, superTitanPercent);
        }
    }

    /**
     * The super titan bonus of {@code team} in percent: {@link #SUPER_TITAN_PERCENT} per super
     * titan in the team - independent of the fortification and of any marks.
     */
    public static double superTitanPercent(TitanTeam team) {
        if (team.titans() == null) {
            return 0;
        }
        return team.titans().stream().filter(Titan::superTitan).count() * SUPER_TITAN_PERCENT;
    }

    /**
     * The active registered titan combos (see {@link #setTitanCombos}) all of
     * whose titans are in {@code team}, in file order - empty if none match.
     */
    public static List<TeamCombo> matchingCombos(TitanTeam team) {
        return matchingCombos(team, titanCombos);
    }

    /** Like {@link #matchingCombos(TitanTeam)}, with explicitly given {@code combos}. */
    public static List<TeamCombo> matchingCombos(TitanTeam team, TeamCombos combos) {
        if (team.titans() == null || combos == null) {
            return List.of();
        }
        return combos.matching(team.titans().stream().map(Titan::id).toList());
    }

    /** The bonus components of {@code team} at {@code fortification}, with the registered titan combos - see the class Javadoc. */
    public static TitanBonus titanBonus(TitanTeam team, Fortification fortification) {
        return titanBonus(team, fortification, titanCombos);
    }

    /** Like {@link #titanBonus(TitanTeam, Fortification)}, with explicitly given {@code combos}. */
    public static TitanBonus titanBonus(TitanTeam team, Fortification fortification, TeamCombos combos) {
        CowScoreBonuses b = bonuses;
        Buff buff = fortification.buff();
        String fortificationId = fortification.id();

        long elementMatches = team.titans().stream().filter(t -> t.matchesBuff(buff)).count();
        double element = elementMatches * b.elementPercent();

        // A mark on a buff-matching titan is ignored - the buff already counts.
        boolean anyPositive = team.titans().stream().filter(t -> !t.matchesBuff(buff))
                .anyMatch(t -> t.fortMark(fortificationId) == FortMark.POSITIVE);
        boolean anyNegative = team.titans().stream().filter(t -> !t.matchesBuff(buff))
                .anyMatch(t -> t.fortMark(fortificationId) == FortMark.NEGATIVE);
        double relation = (anyPositive ? b.relationPercent() : 0) - (anyNegative ? b.relationPercent() : 0);

        double totems = team.totems().size() * b.totemPercent();
        double combo = matchingCombos(team, combos).isEmpty() ? 0 : b.comboPercent();
        return new TitanBonus(element, relation, totems, combo, superTitanPercent(team));
    }

    /**
     * The TITAN-side counterpart of {@link #scoreFor(HeroTeam, Fortification)},
     * with the registered titan combos: totalPower / 100 000 x (1 + B) - see
     * the class Javadoc. {@link Breakdown#memberScores()} holds the five bonus
     * components (element, relation, totems, combo, super titans) converted into
     * score points (powerTerm x percent / 100), so that total = powerTerm +
     * sum(memberScores) holds.
     */
    public static Breakdown scoreFor(TitanTeam team, Fortification fortification) {
        return scoreFor(team, fortification, titanCombos);
    }

    /** Like {@link #scoreFor(TitanTeam, Fortification)}, with explicitly given {@code combos}. */
    public static Breakdown scoreFor(TitanTeam team, Fortification fortification, TeamCombos combos) {
        TitanBonus bonus = titanBonus(team, fortification, combos);
        double powerTerm = team.totalPower() / POWER_DIVISOR;
        List<Double> bonusPoints = bonus.asList().stream().map(percent -> powerTerm * percent / 100.0).toList();
        return breakdownFor(bonusPoints, powerTerm);
    }

    /**
     * The fortification-independent score of {@code team} with the registered
     * titan combos - the TITAN-side counterpart of {@link #sortScore(HeroTeam)}
     * (see {@code TitanTeam#sortScore()}): totalPower / 100 000 x (1 + number
     * of totems x {@link CowScoreBonuses#totemPercent()} + {@link
     * CowScoreBonuses#comboPercent()} if a titan combo matches + number of
     * super titans x {@link #SUPER_TITAN_PERCENT}). Everything else in the
     * bonus depends on a specific fortification.
     */
    public static double sortScore(TitanTeam team) {
        return sortScore(team, titanCombos);
    }

    /** Like {@link #sortScore(TitanTeam)}, with explicitly given {@code combos}. */
    public static double sortScore(TitanTeam team, TeamCombos combos) {
        double percent = team.totems().size() * bonuses.totemPercent() + superTitanPercent(team);
        if (!matchingCombos(team, combos).isEmpty()) {
            percent += bonuses.comboPercent();
        }
        return team.totalPower() / POWER_DIVISOR * (1 + percent / 100.0);
    }

    private static Breakdown breakdownFor(List<Double> memberScores, double powerTerm) {
        double total = powerTerm + memberScores.stream().mapToDouble(Double::doubleValue).sum();
        return new Breakdown(memberScores, powerTerm, total);
    }

    /**
     * One team's score breakdown against one fortification: total =
     * powerTerm + sum(memberScores), where memberScores are the bonus
     * components in score points - for a hero team the five components
     * (role, relation, pet, war flag, combo), see {@link
     * #scoreFor(HeroTeam, Fortification)}; for a titan team the five
     * components (element, relation, totems, combo, super titans), see {@link
     * #scoreFor(TitanTeam, Fortification)}.
     */
    public record Breakdown(List<Double> memberScores, double powerTerm, double total) {

        /** Everything except the power term: sum(memberScores) (= total - powerTerm). */
        public double scoreWithoutPower() {
            return memberScores.stream().mapToDouble(Double::doubleValue).sum();
        }
    }
}
