package org.c2w.report;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.CowScoreFixture;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ReportGenerator}: file naming and the main content of the HTML report. */
class ReportGeneratorTest {

    private static final String ALGORITHM = "HODOR";

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
    @DisplayName("statistics and fortification table show the full CowScore (power term + bonus)")
    void cowScoreIsFullValue() {
        TeamScoreCalculator.setBonuses(CowScoreBonuses.DEFAULTS);
        CowScoreFixture fixture = new CowScoreFixture();

        String html = ReportGenerator.buildReportHtml(fixture.lineup, fixture.guild, "r.html");

        assertEquals(oneDecimal(fixture.expectedHeroCowScore()), statValue(html, "report.statHeroCowScore"));
        assertEquals(oneDecimal(fixture.expectedTitanCowScore()), statValue(html, "report.statTitanCowScore"));
        // The fortification table lists only buffed fortifications.
        Matcher row = Pattern.compile("<tr><td[^>]*>" + Pattern.quote(LanguageService.displayName(fixture.roleFort.id()) + "</td>")
                + "(?:<td[^>]*>[^<]*</td>){3}<td[^>]*>([^<]*)</td></tr>").matcher(html);
        assertTrue(row.find(), "fortification row of " + fixture.roleFort.id());
        assertEquals(oneDecimal(TeamScoreCalculator.scoreFor(fixture.buffHeroTeam, fixture.roleFort).total()), row.group(1));
    }

    private static String statValue(String html, String labelKey) {
        Matcher matcher = Pattern.compile(Pattern.quote(LanguageService.displayName(labelKey) + "</th>")
                + "<td[^>]*>([^<]*)</td>").matcher(html);
        assertTrue(matcher.find(), labelKey);
        return matcher.group(1);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
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
    @DisplayName("the entries table shows pet and war flag of a hero team after the team composition, empty otherwise")
    void entriesShowPetAndWarFlag() {
        HeroTeam withExtras = new HeroTeam("m1", 0, List.of(new Hero("galahad", List.of(Role.TANK))),
                new Pet("albus"), new WarFlag("flag-bastion"), 1_000_000, null);
        HeroTeam plain = new HeroTeam("m2", 0, List.of(new Hero("galahad", List.of(Role.TANK))), 900_000);
        TitanTeam titans = new TitanTeam("m3", 0, List.of(new Titan("ignis", TitanElement.FIRE)), 800_000);
        Guild guild = new Guild("g1", "Guild", List.of(
                new GuildMember("m1", "Pet Owner", List.of(withExtras), List.of()),
                new GuildMember("m2", "No Extras", List.of(plain), List.of()),
                new GuildMember("m3", "Titan Owner", List.of(), List.of(titans))));
        String heroFort = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.HERO).findFirst().orElseThrow().id();
        String titanFort = FortificationRepository.findAll().stream()
                .filter(f -> f.type() == FortificationType.TITAN).findFirst().orElseThrow().id();
        Lineup lineup = new Lineup("g1", "Guild", "", LocalDateTime.of(2026, 1, 1, 12, 0), List.of(
                new Lineup.Entry(heroFort, "m1", Lineup.TeamType.HERO, 0),
                new Lineup.Entry(heroFort, "m2", Lineup.TeamType.HERO, 0),
                new Lineup.Entry(titanFort, "m3", Lineup.TeamType.TITAN, 0)));

        String html = ReportGenerator.buildReportHtml(lineup, guild, "r.html");

        String galahad = LanguageService.displayName("galahad");
        assertEquals(List.of(galahad, LanguageService.displayName("albus"), LanguageService.displayName("flag-bastion")),
                entryCells(html, "Pet Owner").subList(2, 5));
        assertEquals(List.of(galahad, "", ""), entryCells(html, "No Extras").subList(2, 5));
        assertEquals(List.of(LanguageService.displayName("ignis"), "", ""), entryCells(html, "Titan Owner").subList(2, 5));
        assertFalse(html.contains("null"));

        String header = html.substring(html.indexOf(LanguageService.displayName("report.columnFortification")));
        int composition = header.indexOf(LanguageService.displayName("report.columnTeamComposition"));
        int pet = header.indexOf(LanguageService.displayName("report.columnPet"));
        int warFlag = header.indexOf(LanguageService.displayName("report.columnWarFlag"));
        int power = header.indexOf(LanguageService.displayName("report.columnPower"));
        assertTrue(composition < pet && pet < warFlag && warFlag < power, "column order");
    }

    /** The cells of the entries table row of {@code memberName}: fortification, member, composition, pet, war flag, power. */
    private static List<String> entryCells(String html, String memberName) {
        for (String row : html.split("<tr>")) {
            List<String> cells = new java.util.ArrayList<>();
            java.util.regex.Matcher cell = java.util.regex.Pattern.compile("<td[^>]*>(.*?)</td>").matcher(row);
            while (cell.find()) {
                cells.add(cell.group(1));
            }
            if (cells.size() == 6 && cells.get(1).equals(memberName)) {
                return cells;
            }
        }
        throw new AssertionError("no entries row for " + memberName);
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
