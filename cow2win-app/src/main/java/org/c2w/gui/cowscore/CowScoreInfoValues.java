package org.c2w.gui.cowscore;

import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Role;
import org.c2w.data.model.RoleBuff;
import org.c2w.data.model.TeamCombos;
import org.c2w.data.model.WarFlag;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;

import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The values of the placeholders in the "Info" tab's HTML ({@code language/<language>/cowScoreInfo.html}):
 * the percentages registered in {@link TeamScoreCalculator#bonuses()}, its fixed constants and the
 * example - computed by {@link TeamScoreCalculator} itself, so text and calculation always agree.
 *
 * <p>The example: a hero team with {@value #EXAMPLE_POWER} power at the foundry (tank buff), two tanks
 * in the team, another hero (no tank) marked "Positive" for the foundry, an unmarked war flag, no
 * marked pet, no combo ({@code example...}). Beside it, for comparison, the same team without war
 * flag and without the "Positive" mark ({@code example2...}).
 */
public final class CowScoreInfoValues {

    /** Team power of the example. */
    public static final int EXAMPLE_POWER = 500_000;

    private static final String EXAMPLE_FORTIFICATION = "foundry";

    private CowScoreInfoValues() {
    }

    /**
     * Placeholder name (without {@code ${}}) to formatted value, in the number format of {@code locale}.
     */
    public static Map<String, String> values(Locale locale) {
        CowScoreBonuses b = TeamScoreCalculator.bonuses();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("powerDivisor", number(locale, TeamScoreCalculator.POWER_DIVISOR, 0, 0));
        values.put("rolePercent", percent(locale, b.rolePercent()));
        values.put("elementPercent", percent(locale, b.elementPercent()));
        values.put("relationPercent", percent(locale, b.relationPercent()));
        values.put("petPercent", percent(locale, b.petPercent()));
        values.put("warFlagPercent", percent(locale, b.warFlagPercent()));
        values.put("comboPercent", percent(locale, b.comboPercent()));
        values.put("totemPercent", percent(locale, b.totemPercent()));
        values.put("warFlagPresentPercent", percent(locale, TeamScoreCalculator.WAR_FLAG_PRESENT_PERCENT));
        values.put("superTitanPercent", percent(locale, TeamScoreCalculator.SUPER_TITAN_PERCENT));

        Fortification foundry = new Fortification(EXAMPLE_FORTIFICATION, FortificationType.HERO, 5, 0, 0, 0,
                new RoleBuff(Role.TANK, BuffEffect.ARMOR_INCREASE, 3), List.of(), 1);
        List<Hero> tanks = List.of(new Hero("example-tank-1", List.of(Role.TANK)),
                new Hero("example-tank-2", List.of(Role.TANK)));
        Hero marked = new Hero("example-marked", List.of(Role.MAGE), null,
                new FortMarks(Map.of(EXAMPLE_FORTIFICATION, FortMark.POSITIVE)));
        Hero unmarked = new Hero("example-unmarked", List.of(Role.MAGE));

        values.put("examplePower", number(locale, EXAMPLE_POWER, 0, 0));
        // Example 1: with the marked hero and an unmarked war flag.
        putExample(values, "example", locale, foundry, new HeroTeam("example", 0,
                List.of(tanks.get(0), tanks.get(1), marked), null, new WarFlag("example-flag"), EXAMPLE_POWER, null));
        // Example 2, beside it for comparison: same power, no war flag, no hero marked "Positive".
        putExample(values, "example2", locale, foundry, new HeroTeam("example", 0,
                List.of(tanks.get(0), tanks.get(1), unmarked), null, null, EXAMPLE_POWER, null));
        return values;
    }

    /** {@code <prefix>Role}, {@code Bonus}, {@code Factor} and {@code Score} of one example team, computed by the calculator. */
    private static void putExample(Map<String, String> values, String prefix, Locale locale, Fortification fortification,
                                   HeroTeam team) {
        TeamScoreCalculator.HeroBonus bonus = TeamScoreCalculator.heroBonus(team, fortification, TeamCombos.NONE);
        double score = TeamScoreCalculator.scoreFor(team, fortification, TeamCombos.NONE).total();
        values.put(prefix + "Role", percent(locale, bonus.rolePercent()));
        values.put(prefix + "Bonus", percent(locale, bonus.totalPercent()));
        values.put(prefix + "Factor", number(locale, 1 + bonus.totalPercent() / 100.0, 0, 4));
        values.put(prefix + "Score", number(locale, score, 2, 2));
    }

    /** A percentage as in the tab hints: at most two decimals ("1,25" / "1.25"). */
    public static String percent(Locale locale, double value) {
        return number(locale, value, 0, 2);
    }

    private static String number(Locale locale, double value, int minDecimals, int maxDecimals) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(minDecimals);
        format.setMaximumFractionDigits(maxDecimals);
        return format.format(value);
    }
}
