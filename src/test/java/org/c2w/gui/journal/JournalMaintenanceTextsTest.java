package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalCounts;
import org.c2w.data.journal.db.JournalDatabase;
import org.c2w.data.journal.db.Season;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.i18n.LanguageService;
import org.c2w.service.JournalMaintenanceService;
import org.c2w.service.journal.ImportAnswers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Texts of the phase-5 windows: confirmations name the right scope, every used key exists everywhere. */
class JournalMaintenanceTextsTest extends JournalGuiTestSupport {

    private static final BattleSummary AUGE = new BattleSummary(7, LocalDate.of(2026, 9, 24),
            new GuildRef("Das Schwarze Auge", 79, 114834), BattleResult.WIN, BattleStatus.FINISHED, 851, 2783, 1782,
            Set.of(LogDirection.ATTACK, LogDirection.DEFENSE), 1, 1);

    @Test
    @DisplayName("Delete confirmations name date, opponent, logs and single fights")
    void deleteQuestions() {
        String one = JournalTexts.deleteBattlesQuestion(List.of(AUGE), new JournalCounts(1, 2, 124));
        assertTrue(one.contains(JournalTexts.date(AUGE.date())), one);
        assertTrue(one.contains("Das Schwarze Auge"), one);
        assertTrue(one.contains("2"), one);
        assertTrue(one.contains("124"), one);
        assertEquals(JournalTexts.text("journal.delete.battle", JournalTexts.date(AUGE.date()), "Das Schwarze Auge",
                "2", "124"), one);

        String several = JournalTexts.deleteBattlesQuestion(List.of(AUGE, AUGE, AUGE), new JournalCounts(3, 5, 1310));
        assertEquals(JournalTexts.text("journal.delete.battles", "3", "5", "1.310"), several);

        Season season = new Season(4, 2, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 12, 7), null);
        String only = JournalTexts.deleteSeasonQuestion(season, new JournalCounts(6, 12, 700), false);
        assertTrue(only.contains(JournalTexts.seasonText(season)), only);
        assertTrue(only.contains("6"), only);
        assertFalse(only.contains("700"), "keeping the battles: no fights are deleted");
        String with = JournalTexts.deleteSeasonQuestion(season, new JournalCounts(6, 12, 700), true);
        assertEquals(JournalTexts.text("journal.delete.seasonWithBattles", JournalTexts.seasonText(season), "6", "12",
                "700"), with);
        assertTrue(JournalTexts.seasonText(season).contains(JournalTexts.date(LocalDate.of(2026, 12, 6))),
                "the season is shown with its last day");
    }

    @Test
    @DisplayName("Season conflict and parse-again result texts")
    void seasonAndReparseTexts() {
        Season other = new Season(1, 3, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 12, 7), null);
        String overlap = JournalTexts.seasonConflict(new JournalMaintenanceService.SeasonConflict(
                JournalMaintenanceService.SeasonConflict.Kind.OVERLAP, other));
        assertTrue(overlap.contains(JournalTexts.seasonText(other)), overlap);

        String result = JournalTexts.reparseResult(new JournalMaintenanceService.ReparseResult(12, 5, 1, List.of()));
        assertEquals(JournalTexts.text("journal.reparse.result", "12", "5", "1"), result);
        String failed = JournalTexts.reparseResult(new JournalMaintenanceService.ReparseResult(1, 0, 0,
                List.of(new JournalMaintenanceService.ReparseResult.Failure(1, LogDirection.ATTACK, "x"))));
        assertTrue(failed.contains(JournalTexts.text("journal.reparse.failures", "1")), failed);
    }

    @Test
    @DisplayName("Deleting a guild mentions the journal only if the guild has one")
    void removeGuildNote() throws Exception {
        assertEquals("", JournalTexts.removeGuildJournalNote(false, 6));
        assertEquals(JournalTexts.text("journal.removeGuild.note"), JournalTexts.removeGuildJournalNote(true, null));
        assertEquals(JournalTexts.text("journal.removeGuild.noteCount", "6"), JournalTexts.removeGuildJournalNote(true, 6));

        assertEquals("", JournalActions.removeGuildJournalNote(context, guildService, "Alpha"));
        assertFalse(JournalDatabase.exists(guildService.guildDir("Alpha")), "asking created no journal");

        assertTrue(service().execute(service().prepare(BattleLogTestFiles.files("de")), ImportAnswers.defaults()).isSuccess());
        assertEquals(JournalTexts.text("journal.removeGuild.noteCount", "6"),
                JournalActions.removeGuildJournalNote(context, guildService, "Alpha"));

        // another guild with a journal that is not open: mentioned without a count
        guildService.createGuild("Beta");
        Files.createFile(guildService.guildDir("Beta").resolve(JournalDatabase.FILE_NAME));
        assertEquals(JournalTexts.text("journal.removeGuild.note"),
                JournalActions.removeGuildJournalNote(context, guildService, "Beta"));
    }

    @Test
    @DisplayName("Every text key used literally in the journal GUI exists in every language")
    void usedKeysExist() throws IOException {
        Pattern literal = Pattern.compile("\"((?:journal|menu\\.journal)\\.[A-Za-z0-9_.]*[A-Za-z0-9_])\"");
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src", "main", "java", "org", "c2w", "gui", "journal"))) {
            for (Path file : files.toList()) {
                Matcher m = literal.matcher(Files.readString(file));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }
        assertTrue(keys.size() > 150, "found " + keys.size());
        List<String> missing = new ArrayList<>();
        for (String language : LanguageService.availableLanguages()) {
            for (String key : keys) {
                String text = LanguageService.textIn(language, key);
                if (text == null || text.isBlank()) {
                    missing.add(language + ": " + key);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    @DisplayName("Phase-5 texts use no straight apostrophe (MessageFormat would swallow it)")
    void noStraightApostrophes() {
        List<String> prefixes = List.of("journal.detail.", "journal.players.", "journal.seasons.", "journal.mappings.",
                "journal.delete.", "journal.reparse.", "journal.saveCsv.", "journal.assignSeason.", "journal.action.",
                "journal.removeGuild.", "journal.noJournal", "journal.outcome.", "journal.assignmentStatus.",
                "journal.statusFilter.", "journal.resultFilter.", "journal.battles.count",
                // phase 6
                "journal.teams.", "journal.teamKind.", "journal.compositionSource.", "journal.skipReason.",
                "journal.players.createAll", "journal.players.resetAll", "menu.journal.buildTeams");
        List<String> wrong = new ArrayList<>();
        for (String language : LanguageService.availableLanguages()) {
            Properties props = new Properties();
            try (var in = LanguageService.openLanguageResource(language, language + ".properties");
                 var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            for (String key : props.stringPropertyNames()) {
                if (prefixes.stream().anyMatch(key::startsWith) && !key.equals("journal.players.hint")
                        && props.getProperty(key).contains("'")) {
                    wrong.add(language + ": " + key);
                }
            }
        }
        assertEquals(List.of(), wrong);
    }
}
