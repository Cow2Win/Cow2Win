package org.c2w.domain;

/**
 * The adjustable percentages of the CowScore bonus B (see {@link TeamScoreCalculator}) - set in
 * the settings and registered via {@link TeamScoreCalculator#setBonuses}. Knows neither the
 * config file nor Swing.
 *
 * <p>Every small bonus lies between {@link #MIN_BONUS} and {@link #MAX_BONUS}, the two buffs
 * between {@link #MIN_BUFF} and {@link #MAX_BUFF}. Basic rule of the formula: the buff is the
 * biggest bonus of its side - the role buff is at least as big as relation, pet, war flag and
 * combo; the element buff at least as big as relation and totem (see {@link #normalized()}).
 *
 * @param rolePercent     per hero whose role matches the fortification's buff
 * @param elementPercent  per titan whose element matches the fortification's buff
 * @param relationPercent +/- once per team for a positively/negatively marked hero/titan (both sides)
 * @param petPercent      a pet marked for the fortification
 * @param warFlagPercent  a war flag marked for the fortification
 * @param comboPercent    once per team if an active hero combo matches
 * @param totemPercent    per totem of a titan team
 */
public record CowScoreBonuses(double rolePercent, double elementPercent, double relationPercent, double petPercent,
                              double warFlagPercent, double comboPercent, double totemPercent) {

    public static final double DEFAULT_BUFF = 1.5;
    public static final double DEFAULT_BONUS = 1.25;

    /** Range of the two buffs (role, element). */
    public static final double MIN_BUFF = 1.0;
    public static final double MAX_BUFF = 3.0;

    /** Range of every small bonus (relation, pet, war flag, combo, totem). */
    public static final double MIN_BONUS = 1.0;
    public static final double MAX_BONUS = 2.0;

    /** Step of the settings' spinners. */
    public static final double STEP = 0.05;

    /** The values the CowScore formula was designed with. */
    public static final CowScoreBonuses DEFAULTS = new CowScoreBonuses(DEFAULT_BUFF, DEFAULT_BUFF, DEFAULT_BONUS,
            DEFAULT_BONUS, DEFAULT_BONUS, DEFAULT_BONUS, DEFAULT_BONUS);

    /** Every value within its range, rounded to two decimals - the buffs not raised yet. */
    public CowScoreBonuses clamped() {
        return new CowScoreBonuses(buff(rolePercent), buff(elementPercent), bonus(relationPercent), bonus(petPercent),
                bonus(warFlagPercent), bonus(comboPercent), bonus(totemPercent));
    }

    /**
     * {@link #clamped()}, then each buff raised to the biggest small bonus of its side if it is
     * smaller - so the buff stays the biggest bonus.
     */
    public CowScoreBonuses normalized() {
        CowScoreBonuses c = clamped();
        return new CowScoreBonuses(Math.max(c.rolePercent, c.heroBonusMaximum()),
                Math.max(c.elementPercent, c.titanBonusMaximum()),
                c.relationPercent, c.petPercent, c.warFlagPercent, c.comboPercent, c.totemPercent);
    }

    /** The biggest small bonus of the hero side: relation, pet, war flag, combo - the role buff's minimum. */
    public double heroBonusMaximum() {
        return Math.max(Math.max(relationPercent, petPercent), Math.max(warFlagPercent, comboPercent));
    }

    /** The biggest small bonus of the titan side: relation, totem - the element buff's minimum. */
    public double titanBonusMaximum() {
        return Math.max(relationPercent, totemPercent);
    }

    private static double buff(double value) {
        return round(clamp(value, MIN_BUFF, MAX_BUFF));
    }

    private static double bonus(double value) {
        return round(clamp(value, MIN_BONUS, MAX_BONUS));
    }

    /** NaN (e.g. from a broken value) counts as the minimum. */
    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.clamp(value, min, max);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
