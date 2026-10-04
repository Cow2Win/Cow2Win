package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.TeamKind;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.parse.BattleLogCheck;
import org.c2w.i18n.LanguageService;
import org.c2w.service.journal.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        GROUPS.put("seasonKind", SeasonQuestion.Kind.values());
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
        GROUPS.put("statusFilter", BattleListFilter.StatusFilter.values());
        GROUPS.put("resultFilter", BattleListFilter.ResultFilter.values());
        GROUPS.put("action.saveCsv", LogDirection.values());
        GROUPS.put("detail.summary", LogDirection.values());
        GROUPS.put("detail.fort.undefended", LogDirection.values());
        GROUPS.put("detail.fort.captured", LogDirection.values());
        GROUPS.put("detail.fort.score", LogDirection.values());
        GROUPS.put("players.memberHint", JournalPlayersModel.HintKind.values());
        GROUPS.put("seasons.conflict", org.c2w.service.JournalMaintenanceService.SeasonConflict.Kind.values());
    }

    /** Groups always formatted with arguments - their apostrophes are doubled in the files. */
    private static final List<String> FORMATTED_GROUPS = List.of("planError", "block", "team", "evidence",
            "detail.summary", "detail.fort.undefended", "detail.fort.captured", "detail.fort.score",
            "players.memberHint", "seasons.conflict");

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
    }

    @Test
    void planErrorWithArguments() {
        String text = JournalTexts.planError(new PlanError(PlanError.Kind.LOGS_OF_OTHER_GUILD, "Beta", 193861L));

        assertTrue(text.contains("Beta"), text);
        assertTrue(text.contains("193861"), text);
    }
}
