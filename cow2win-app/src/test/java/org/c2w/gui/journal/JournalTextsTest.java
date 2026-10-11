package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.TeamKind;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.parse.BattleLogCheck;
import org.c2w.data.model.TitanElement;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.service.journal.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Every enum the journal GUI shows has a text in every language, and formatting keeps the apostrophes. */
class JournalTextsTest {

    /** Language file group -> enum values shown under it. */
    private static final Map<String, Enum<?>[]> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("planError", PlanError.Kind.values());
        GROUPS.put("fileError", PlannedFile.Error.values());
        GROUPS.put("warning", PlannedBattle.Warning.values());
        GROUPS.put("logAction", PlannedBattle.LogAction.values());
        GROUPS.put("answer", PlayerAnswer.Kind.values());
        GROUPS.put("nameKind", NameMappingKind.values());
        GROUPS.put("battleResult", BattleResult.values());
        GROUPS.put("battleStatus", BattleStatus.values());
        GROUPS.put("verdict", BattleLogCheck.Verdict.values());
        GROUPS.put("importError", ImportResult.ImportError.Kind.values());
        GROUPS.put("step", ImportWizardModel.Step.values());
        GROUPS.put("block", ImportWizardModel.Block.values());
        GROUPS.put("direction", LogDirection.values());
        GROUPS.put("battles.logs", LogDirection.values());
        GROUPS.put("team", TeamKind.values());
        GROUPS.put("evidence", PlayerQuestion.RenameEvidence.Kind.values());
        // phase 5
        GROUPS.put("assignmentStatus", org.c2w.data.journal.db.AssignmentStatus.values());
        GROUPS.put("outcome", BattleDetailModel.Outcome.values());
        GROUPS.put("resultFilter", BattleListFilter.ResultFilter.values());
        GROUPS.put("detail.summary", LogDirection.values());
        GROUPS.put("detail.fort.undefended", LogDirection.values());
        GROUPS.put("detail.fort.captured", LogDirection.values());
        GROUPS.put("detail.fort.score", LogDirection.values());
        GROUPS.put("players.memberHint", JournalPlayersModel.HintKind.values());
        // phase 6
        GROUPS.put("teamKind", TeamKind.values());
        GROUPS.put("skipReason", TeamBuildPlan.SkipReason.values());
        GROUPS.put("teams.target", TeamBuildPlan.Target.Kind.values());
        GROUPS.put("teams.error", TeamBuildResult.Error.Kind.values());
        GROUPS.put("teams.errorShort", TeamBuildResult.Error.Kind.values());
        GROUPS.put("teams.badge", TeamKind.values());
        // phase 7
        GROUPS.put("syncCertainty", SyncRow.Certainty.values());
        GROUPS.put("sync.empty", SyncPlan.EmptyReason.values());
        GROUPS.put("sync.error", SyncResult.Error.Kind.values());
        GROUPS.put("sync.hint", SyncModel.Hint.values());
    }

    /** Groups always formatted with arguments - their apostrophes are doubled in the files. */
    private static final List<String> FORMATTED_GROUPS = List.of("planError", "block", "team", "evidence",
            "detail.summary", "detail.fort.undefended", "detail.fort.captured", "detail.fort.score",
            "players.memberHint", "teams.target", "teams.error", "sync.error", "sync.hint");

    @Test
    @DisplayName("Every enum value has a non-empty text in every language")
    void everyEnumValueHasATextInEveryLanguage() {
        List<String> missing = new ArrayList<>();
        for (String language : LanguageService.availableLanguages()) {
            for (Map.Entry<String, Enum<?>[]> group : GROUPS.entrySet()) {
                for (Enum<?> value : group.getValue()) {
                    String key = JournalTexts.enumKey(group.getKey(), value);
                    String text = LanguageService.textIn(language, key);
                    if (text == null || text.isBlank()) {
                        missing.add(language + ": " + key);
                    }
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    @DisplayName("Apostrophes: doubled only where the text is formatted, and they survive formatting")
    void apostrophes() {
        List<String> wrong = new ArrayList<>();
        for (String language : LanguageService.availableLanguages()) {
            for (Map.Entry<String, Enum<?>[]> group : GROUPS.entrySet()) {
                boolean formatted = FORMATTED_GROUPS.contains(group.getKey());
                for (Enum<?> value : group.getValue()) {
                    String key = JournalTexts.enumKey(group.getKey(), value);
                    String text = LanguageService.textIn(language, key);
                    if (formatted) {
                        String result = MessageFormat.format(text, "Gilde", "193861", "x");
                        if (text.contains("'") && !result.contains("'")) {
                            wrong.add(language + ": " + key + " loses its apostrophe -> " + result);
                        }
                    } else if (text.contains("''")) {
                        wrong.add(language + ": " + key + " is shown unformatted but has ''");
                    }
                }
            }
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    @DisplayName("Team builder: the same texts in every language, the removed ones are gone")
    void teamBuilderTexts() {
        Set<String> german = teamBuilderKeys("deutsch");
        for (String language : LanguageService.availableLanguages()) {
            assertEquals(german, teamBuilderKeys(language), language);
        }
        for (String removed : List.of("journal.teams.hint", "journal.teams.onlyWithoutTeams", "journal.teams.member",
                "journal.teams.existing", "journal.teams.none", "journal.teams.empty", "journal.teams.present",
                "journal.teams.overLimit", "journal.teams.known", "journal.teams.knownNone", "journal.teams.knownHint",
                "journal.teams.knownItem", "journal.teams.skipped", "journal.teams.withoutData",
                "journal.teams.error.INVALID_COMPOSITION", "journal.compositionSource.DEFENSE_UNITS",
                "journal.compositionSource.ATTACK_EXACT_POWER", "journal.compositionSource.POWER_ONLY")) {
            assertFalse(german.contains(removed), removed);
            assertNull(LanguageService.textIn("deutsch", removed), removed);
        }
        for (String kept : List.of("journal.teams.clear", "journal.teams.skippedLine", "journal.teams.where",
                "journal.teams.title", "journal.teams.noDefenseLogs", "journal.teams.chip.newHero.one",
                "journal.teams.chip.newHero.other", "journal.teams.chip.newTitan.one",
                "journal.teams.chip.newTitan.other", "journal.teams.rules")) {
            assertTrue(german.contains(kept), kept);
        }
    }

    private static Set<String> teamBuilderKeys(String language) {
        java.util.Properties props = new java.util.Properties();
        try (var in = LanguageService.openLanguageResource(language, language + ".properties");
             var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        Set<String> keys = new java.util.TreeSet<>();
        props.stringPropertyNames().stream()
                .filter(k -> k.startsWith("journal.teams.") || k.startsWith("journal.compositionSource."))
                .forEach(keys::add);
        return keys;
    }

    @Test
    void visibleSpaces() {
        assertEquals("Vale··", JournalTexts.visibleSpaces("Vale  "));
        assertEquals("·Vale", JournalTexts.visibleSpaces(" Vale"));
        assertEquals("Team Gandagom", JournalTexts.visibleSpaces("Team Gandagom"));
        assertEquals("A··B", JournalTexts.visibleSpaces("A  B"));
        assertEquals("A·B", JournalTexts.visibleSpaces("A B"));
        assertEquals("", JournalTexts.visibleSpaces(null));
    }

    @Test
    void numbers() {
        assertEquals("+851", JournalTexts.rankingPoints(851));
        assertEquals("-42", JournalTexts.rankingPoints(-42));
        assertEquals("0", JournalTexts.rankingPoints(0));
        assertEquals("", JournalTexts.rankingPoints(null));
        assertEquals("1.382.741", JournalTexts.number(1_382_741));
        assertEquals("+4.512", JournalTexts.rankingPointsTotal(4512));
        assertEquals("-286", JournalTexts.rankingPointsTotal(-286));
        assertEquals("-1.630", JournalTexts.rankingPointsTotal(-1630));
        assertEquals("0", JournalTexts.rankingPointsTotal(0));
    }

    @Test
    @DisplayName("Sync: power change with delta and percent, unit changes, certainty")
    void syncTexts() {
        String up = JournalTexts.powerChange(1_376_972, 1_400_474);
        assertTrue(up.contains("1.376.972") && up.contains("1.400.474") && up.contains("+23.502"), up);
        assertTrue(JournalTexts.powerChange(1_000_000, 990_000).contains("−10.000"));
        assertTrue(JournalTexts.powerChange(5, 5).contains("5"));

        SyncRow.UnitsChange change = new SyncRow.UnitsChange(List.of("nova"), List.of("sigurd"), null, null,
                Set.of(TitanElement.FIRE),
                Set.of(TitanElement.WATER));
        String text = JournalTexts.unitsChange(change);
        assertTrue(text.startsWith("+ " + LanguageService.displayName("nova") + ", − "
                + LanguageService.displayName("sigurd")), text);
        assertTrue(text.contains(TotemTexts.name(TitanElement.WATER)), text);
        String pet = JournalTexts.unitsChange(new SyncRow.UnitsChange(List.of(), List.of(), "vex", "albus",
                Set.of(), Set.of()));
        assertTrue(pet.contains(LanguageService.displayName("albus")) && !pet.contains("+ "), pet);
        assertEquals("", JournalTexts.unitsChange(null));
        assertTrue(JournalTexts.certainty(null).startsWith("✎"));
        assertTrue(JournalTexts.certainty(SyncRow.Certainty.SURE).contains("3"));
    }

    @Test
    void planErrorWithArguments() {
        String text = JournalTexts.planError(new PlanError(PlanError.Kind.LOGS_OF_OTHER_GUILD, "Beta", 193861L));

        assertTrue(text.contains("Beta"), text);
        assertTrue(text.contains("193861"), text);
    }
}
