package org.c2w.gui.cowscore;

import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The "Info" tab's HTML files and placeholder values - no display needed. */
class CowScoreInfoTest {

    private static final List<String> LANGUAGES = List.of("deutsch", "english", "francais");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)}");

    @AfterEach
    void restoreBonuses() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
    }

    @Test
    @DisplayName("Every language has the HTML file with the same placeholders, all of them known and replaced")
    void placeholdersInAllLanguages() {
        Map<String, String> values = CowScoreInfoValues.values(Locale.GERMANY);
        Set<String> reference = null;
        for (String language : LANGUAGES) {
            String template = CowScoreInfoHtml.template(language);
            assertNotNull(template, language);
            Set<String> placeholders = placeholders(template);
            if (reference == null) {
                reference = placeholders;
            }
            assertEquals(reference, placeholders, language);
            assertEquals(Set.of(), difference(placeholders, values.keySet()), language + ": unknown placeholders");
            String filled = CowScoreInfoHtml.fill(template, values);
            assertFalse(filled.contains("${"), language);
            // U+2212 (minus sign) is missing in the interface font - a plain hyphen is used instead.
            assertFalse(template.contains("−"), language);
        }
        assertEquals(new TreeSet<>(values.keySet()), reference, "every value is used");
    }

    @Test
    @DisplayName("Example with the default bonuses: 3 / 4,85 / 1,0485 / 5,24")
    void exampleWithDefaults() {
        Map<String, String> values = CowScoreInfoValues.values(Locale.GERMANY);
        assertEquals("3", values.get("exampleRole"));
        assertEquals("4,85", values.get("exampleBonus"));
        assertEquals("1,0485", values.get("exampleFactor"));
        assertEquals("5,24", values.get("exampleScore"));
        assertEquals("100.000", values.get("powerDivisor"));
        assertEquals("500.000", values.get("examplePower"));
        assertEquals("0,6", values.get("warFlagPresentPercent"));
        assertEquals("1,25", values.get("relationPercent"));
        // Beside it: the same team without war flag and without the "Positive" mark.
        assertEquals("3", values.get("example2Role"));
        assertEquals("3", values.get("example2Bonus"));
        assertEquals("1,03", values.get("example2Factor"));
        assertEquals("5,15", values.get("example2Score"));

        Map<String, String> english = CowScoreInfoValues.values(Locale.UK);
        assertEquals("1.0485", english.get("exampleFactor"));
        assertEquals("500,000", english.get("examplePower"));
    }

    @Test
    @DisplayName("Example with changed bonuses (role 2 %, relation 1 %) matches the formula")
    void exampleWithChangedBonuses() {
        TeamScoreCalculator.setBonuses(new CowScoreBonuses(2.0, 1.5, 1.0, 1.25, 1.25, 1.25, 1.25));
        Map<String, String> values = CowScoreInfoValues.values(Locale.GERMANY);

        double bonus = 2 * 2.0 + 1.0 + TeamScoreCalculator.WAR_FLAG_PRESENT_PERCENT;
        double score = CowScoreInfoValues.EXAMPLE_POWER / TeamScoreCalculator.POWER_DIVISOR * (1 + bonus / 100);
        assertEquals("4", values.get("exampleRole"));
        assertEquals("5,6", values.get("exampleBonus"));
        assertEquals("1,056", values.get("exampleFactor"));
        assertEquals(String.format(Locale.GERMANY, "%.2f", score), values.get("exampleScore"));
        assertEquals("5,28", values.get("exampleScore"));
        assertEquals("2", values.get("rolePercent"));
        assertEquals("4", values.get("example2Bonus"));
        assertEquals("1,04", values.get("example2Factor"));
        assertEquals("5,20", values.get("example2Score"));
    }

    @Test
    @DisplayName("Placeholders without a value stay visible (and are logged), the rest is replaced")
    void unknownPlaceholderStays() {
        String filled = CowScoreInfoHtml.fill("<p>${rolePercent} ${noSuchValue}</p>", Map.of("rolePercent", "1,5"));
        assertEquals("<p>1,5 ${noSuchValue}</p>", filled);
    }

    private static Set<String> placeholders(String text) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.removeAll(b);
        return result;
    }
}
