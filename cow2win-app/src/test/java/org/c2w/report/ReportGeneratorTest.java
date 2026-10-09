package org.c2w.report;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ReportGenerator}: file naming and the main content of the HTML report. */
class ReportGeneratorTest {

    private static final String ALGORITHM = "Best possible lineup";

    private static Lineup lineupWithOneHeroTeam(String guildName) {
        String fortificationId = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO)
                .findFirst().orElseThrow().id();
        return new Lineup("g1", guildName, "Heroes: " + ALGORITHM, LocalDateTime.of(2026, 1, 1, 12, 0),
                List.of(new Lineup.Entry(fortificationId, "m1", Lineup.TeamType.HERO, 0)));
    }

    private static Guild guild(String name) {
        HeroTeam team = new HeroTeam("m1", 0, List.of(new Hero("galahad", List.of(Role.TANK))),
                null, null, 1_000_000, null);
        return new Guild("g1", name, List.of(new GuildMember("m1", "Member One", List.of(team), List.of())));
    }

    @Test
    @DisplayName("the suggested file name swaps the .lineup suffix for .html")
    void suggestedFileName() {
        assertEquals("2026-10-01.html", ReportGenerator.suggestedReportFileName(Path.of("guild", "2026-10-01.lineup")));
        assertEquals("notes.html", ReportGenerator.suggestedReportFileName(Path.of("notes")));
    }

    @Test
    @DisplayName("the report is a complete HTML page naming guild, lineup, algorithm and the heroes used")
    void reportContent() {
        String html = ReportGenerator.buildReportHtml(lineupWithOneHeroTeam("Cows & Co"), guild("Cows & Co"),
                "test-report.html");

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.trim().endsWith("</html>"));
        assertTrue(html.contains("Cows &amp; Co"), "guild name, HTML-escaped");
        assertFalse(html.contains("Cows & Co"), "no unescaped guild name");
        assertTrue(html.contains("test-report.html"));
        assertTrue(html.contains(AlgorithmDescriptions.localizedName(ALGORITHM)));
        assertTrue(html.contains(LanguageService.displayName("galahad")), "used heroes table");
    }

    @Test
    @DisplayName("blank file names and missing data are rejected")
    void rejectsInvalidInput() {
        Lineup lineup = lineupWithOneHeroTeam("Guild");
        Guild guild = guild("Guild");
        assertThrows(IllegalArgumentException.class, () -> ReportGenerator.buildReportHtml(lineup, guild, " "));
        assertThrows(IllegalArgumentException.class, () -> ReportGenerator.buildReportHtml(null, guild, "r.html"));
        assertThrows(IllegalArgumentException.class, () -> ReportGenerator.buildReportHtml(lineup, null, "r.html"));
    }
}
