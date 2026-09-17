package org.c2w.util;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.eval.AlgorithmDescriptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ReportGenerator {

    /** File name suffix stripped from the lineup file name before appending {@value #REPORT_FILE_SUFFIX} - see class Javadoc. */
    private static final String LINEUP_FILE_SUFFIX = ".lineup";

    /** File name suffix of a generated report. */
    private static final String REPORT_FILE_SUFFIX = ".html";

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportGenerator() {
        // Utility class, no instantiation
    }

    /**
     * Builds the report HTML for the given lineup/guild and saves it next to
     * {@code lineupFilePath} (see {@link #reportPathFor(Path)}), overwriting
     * any previous report for the same lineup file.
     *
     * @return the path the report was saved to, so the caller (see
     *         {@code ToolbarPanel#onGenerateReport()}) can show it in a
     *         dialog right away
     * @throws IOException if the file cannot be written
     */
    public static Path generate(Lineup lineup, Guild guild, Path lineupFilePath) throws IOException {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        if (lineupFilePath == null) {
            throw new IllegalArgumentException("lineupFilePath must not be null");
        }
        Path reportPath = reportPathFor(lineupFilePath);

        String html = buildHtml(lineup, guild,reportPath.getFileName().toString());

        Files.writeString(reportPath, html, StandardCharsets.UTF_8);
        return reportPath;
    }

    /** See class Javadoc for the naming rule and the reasoning behind it. */
    private static Path reportPathFor(Path lineupFilePath) {
        String fileName = lineupFilePath.getFileName().toString();
        String baseName = fileName.endsWith(LINEUP_FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - LINEUP_FILE_SUFFIX.length())
                : fileName;
        return lineupFilePath.resolveSibling(baseName + REPORT_FILE_SUFFIX);
    }

    // --- HTML building ---

    private static String buildHtml(Lineup lineup, Guild guild,String lineupName) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Lineup Report - ").append(escape(lineup.guildName())).append("</title>\n");
        html.append(styleBlock());
        html.append("</head>\n<body>\n");

        html.append("<h1>Lineup Report</h1>\n");
        html.append(metaTableHtml(lineup,lineupName));

        html.append("<h2>Statistics</h2>\n");
        html.append(statisticsTableHtml(lineup, guild));

        html.append("<h2>Fortifications</h2>\n");
        html.append(fortificationsTableHtml(lineup, guild));

        html.append("<h2>Lineup data</h2>\n");
        html.append(entriesTableHtml(lineup, guild));

        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static String styleBlock() {
        return "<style>\n"
                + "body { font-family: Arial, Helvetica, sans-serif; margin: 24px; }\n"
                + "h1 { margin-bottom: 4px; }\n"
                + "h2 { margin-top: 32px; border-bottom: 1px solid #999999; padding-bottom: 4px; }\n"
                + "table { border-collapse: collapse; margin-top: 12px; width: 100%; }\n"
                + "th, td { border: 1px solid #bbbbbb; padding: 6px 10px; text-align: left; }\n"
                + "th { background-color: #dddddd; }\n"
                + "tr:nth-child(even) { background-color: #f5f5f5; }\n"
                + ".meta-table, .stats-table { width: auto; }\n"
                + ".number { text-align: right; }\n"
                + "</style>\n";
    }

    private static String metaTableHtml(Lineup lineup, String lineupName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"meta-table\">\n");
        appendMetaRow(sb, "Guild", lineup.guildName());
        appendMetaRow(sb, "Lineup", lineupName);
        appendMetaRow(sb, "Lineup created at", lineup.createdAt().format(TIMESTAMP_FORMAT));
        appendMetaRow(sb, "Report generated at", java.time.LocalDateTime.now().format(TIMESTAMP_FORMAT));
        appendAlgorithmRowsIfPresent(sb, lineup);
        sb.append("</table>\n");
        return sb.toString();
    }

    /**
     * Adds an "Algorithm" row (and, if one is on file, an "Algorithm
     * description" row right below it) to the meta table - per the user's
     * explicit request (added 2026-09-16): whenever a lineup was (at least
     * partly) filled by an algorithm run, the report's very first section
     * ("Lineup Report") should name that algorithm and explain how it
     * works, not just leave it implicit in the raw lineup data further
     * down. Skipped entirely for a lineup with no {@link
     * Lineup#algorithmName()} yet (see that field's Javadoc) - e.g. a
     * brand-new lineup or one built purely from manual picks - since there
     * is nothing to report here in that case.
     */
    private static void appendAlgorithmRowsIfPresent(StringBuilder sb, Lineup lineup) {
        String algorithmName = lineup.algorithmName();
        if (algorithmName == null || algorithmName.isBlank()) {
            return;
        }
        appendMetaRow(sb, "Algorithm", algorithmName);
        String description = AlgorithmDescriptions.forDisplayName(algorithmName);
        if (!description.isBlank()) {
            appendMetaRow(sb, "Algorithm description", description);
        }
    }

    private static void appendMetaRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(escape(label)).append("</th><td>").append(escape(value)).append("</td></tr>\n");
    }

    /**
     * One row per {@link Lineup.Entry}, sorted by fortification display name
     * then team member name so related assignments are easy to scan - see
     * class Javadoc for exactly which fields/extras are shown.
     */
    private static String entriesTableHtml(Lineup lineup, Guild guild) {
        List<Lineup.Entry> entries = new ArrayList<>(lineup.entries());
        entries.sort(Comparator
                .comparing((Lineup.Entry e) -> fortificationDisplayName(e.fortificationId()))
                .thenComparing(e -> memberName(guild, e.teamMemberId())));

        StringBuilder sb = new StringBuilder();
        if (entries.isEmpty()) {
            sb.append("<p>This lineup has no team assignments yet.</p>\n");
            return sb.toString();
        }

        sb.append("<table>\n<tr>")
                .append("<th>Fortification</th>")
                .append("<th>Team member</th>")
                .append("<th>Team composition</th>")
                .append("<th class=\"number\">Power</th>")
                .append("</tr>\n");

        for (Lineup.Entry entry : entries) {
            sb.append("<tr>");
            sb.append("<td>").append(escape(fortificationDisplayName(entry.fortificationId()))).append("</td>");
            sb.append("<td>").append(escape(memberName(guild, entry.teamMemberId()))).append("</td>");
            sb.append("<td>").append(escape(teamCompositionOf(guild, entry))).append("</td>");
            sb.append("<td class=\"number\">").append(entry.totalPower()).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
        return sb.toString();
    }

    /**
     * One row per fortification actually used in this lineup (i.e. that has
     * at least one {@link Lineup.Entry}) AND has a buff, sorted by
     * fortification display name - per the user's explicit follow-up request
     * (added 2026-09-04): the fortification's own buff display text and the
     * buff percentage {@link BuffCalculationService#calculateBuffForFortification}
     * currently computes for it, plus a count broken out of that same
     * calculation - how many deployed heroes/titans satisfy the buff's
     * role/element requirement (see
     * {@link BuffCalculationService#countMatchingMembersForFortification}).
     * A fortification without a buff, or with no lineup entries at all, is
     * skipped entirely (Claude's own reading of the request - nothing
     * meaningful to report for it here, and the full ~20-entry catalog would
     * otherwise dwarf this lineup-centric report with mostly-zero rows).
     */
    private static String fortificationsTableHtml(Lineup lineup, Guild guild) {
        List<String> fortificationIds = lineup.entries().stream()
                .map(Lineup.Entry::fortificationId)
                .distinct()
                .filter(id -> FortificationRepository.findById(id).map(f -> f.buff() != null).orElse(false))
                .sorted(Comparator.comparing(ReportGenerator::fortificationDisplayName))
                .toList();

        StringBuilder sb = new StringBuilder();
        if (fortificationIds.isEmpty()) {
            sb.append("<p>No fortification with a buff is used in this lineup.</p>\n");
            return sb.toString();
        }

        sb.append("<table>\n<tr>")
                .append("<th>Fortification</th>")
                .append("<th>Buff</th>")
                .append("<th class=\"number\">Buff %</th>")
                .append("<th class=\"number\">Matching role/element</th>")
                .append("<th class=\"number\">CowScore</th>")
                .append("</tr>\n");

        for (String fortificationId : fortificationIds) {
            Fortification fortification = FortificationRepository.findById(fortificationId).orElseThrow();
            Buff buff = fortification.buff();
            int buffPercent = BuffCalculationService.calculateBuffForFortification(
                    fortificationId, lineup, guild, fortification);
            int matchingCount = BuffCalculationService.countMatchingMembersForFortification(
                    fortificationId, lineup, guild, buff);
            double cowScore = BuffCalculationService.sumCowScoreForFortification(
                    fortificationId, lineup, guild, fortification);

            sb.append("<tr>");
            sb.append("<td>").append(escape(fortificationDisplayName(fortificationId))).append("</td>");
            sb.append("<td>").append(escape(buffDisplayText(buff))).append("</td>");
            sb.append("<td class=\"number\">").append(buffPercent).append("%</td>");
            sb.append("<td class=\"number\">").append(matchingCount).append("</td>");
            sb.append("<td class=\"number\">").append(formatCowScore(cowScore)).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
        return sb.toString();
    }

    /**
     * Display text for a buff: its manually maintained {@link Buff#display()}
     * if set, otherwise a fallback built from effect/bonusPercent - same
     * fallback rule {@code FortificationInfoPanel#buffText()} already
     * uses, except formatted via {@link #formatBonusPercent(double)}
     * ({@link Locale#ROOT}) rather than {@code Config#NUMBER_FORMAT}'s German
     * locale, which would read oddly (a German decimal comma) inside this
     * otherwise English-only report (see class Javadoc).
     */
    private static String buffDisplayText(Buff buff) {
        String text = buff.display();
        if (text == null || text.isBlank()) {
            text = buff.effect().name() + " (" + formatBonusPercent(buff.bonusPercent()) + "%)";
        }
        return text;
    }

    /** Formats a buff's raw {@code bonusPercent} for this report - see {@link #buffDisplayText(Buff)}. */
    private static String formatBonusPercent(double bonusPercent) {
        if (bonusPercent == Math.floor(bonusPercent)) {
            return String.valueOf((long) bonusPercent);
        }
        return String.format(Locale.ROOT, "%.1f", bonusPercent);
    }

    private static String statisticsTableHtml(Lineup lineup, Guild guild) {
        int heroPower = totalPower(lineup, Lineup.TeamType.HERO);
        int titanPower = totalPower(lineup, Lineup.TeamType.TITAN);
        int heroBuffCount = BuffCalculationService.countHeroesIncreasingBuff(lineup, guild);
        int titanBuffCount = BuffCalculationService.countTitansIncreasingBuff(lineup, guild);
        double heroCowScore = BuffCalculationService.sumHeroCowScore(lineup, guild);
        double titanCowScore = BuffCalculationService.sumTitanCowScore(lineup, guild);
        int guildHeroPower = guildHeroPower(guild);
        int guildTitanPower = guildTitanPower(guild);

        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"stats-table\">\n");
        appendStatRow(sb, "Total hero power (deployed)", heroPower);
        appendStatRow(sb, "Total hero power (guild)", guildHeroPower);
        appendStatRow(sb, "Hero power deployed", percentText(heroPower, guildHeroPower));
        appendStatRow(sb, "Heroes increasing a buff", heroBuffCount);
        appendStatRow(sb, "Hero CowScore", formatCowScore(heroCowScore));
        appendStatRow(sb, "Total titan power (deployed)", titanPower);
        appendStatRow(sb, "Total titan power (guild)", guildTitanPower);
        appendStatRow(sb, "Titan power deployed", percentText(titanPower, guildTitanPower));
        appendStatRow(sb, "Titans increasing a buff", titanBuffCount);
        appendStatRow(sb, "Titan CowScore", formatCowScore(titanCowScore));
        appendStatRow(sb, "Total power (heroes + titans, deployed)", heroPower + titanPower);
        appendStatRow(sb, "Total power (heroes + titans, guild)", guildHeroPower + guildTitanPower);
        appendStatRow(sb, "Total power deployed", percentText(heroPower + titanPower, guildHeroPower + guildTitanPower));
        appendStatRow(sb, "Total team assignments", lineup.entries().size());
        sb.append("</table>\n");
        return sb.toString();
    }

    /** Formats a summed CowScore total (see {@link BuffCalculationService#sumHeroCowScore}/{@link BuffCalculationService#sumTitanCowScore}/{@link BuffCalculationService#sumCowScoreForFortification}) to one decimal place, {@link Locale#ROOT} like the rest of this English-only report - same precision {@code LineupSummaryPanel}'s COW_SCORE_FORMAT uses in the GUI. */
    private static String formatCowScore(double cowScore) {
        return String.format(Locale.ROOT, "%.1f", cowScore);
    }

    private static void appendStatRow(StringBuilder sb, String label, int value) {
        appendStatRow(sb, label, String.valueOf(value));
    }

    private static void appendStatRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(escape(label)).append("</th><td class=\"number\">").append(escape(value))
                .append("</td></tr>\n");
    }

    /** Sums {@link HeroTeam#totalPower()} over EVERY hero team in the guild, deployed or not - see class Javadoc's "guild total" figures. */
    private static int guildHeroPower(Guild guild) {
        int total = 0;
        for (GuildMember member : guild.members()) {
            for (HeroTeam team : member.heroTeams()) {
                total += team.totalPower();
            }
        }
        return total;
    }

    /** Sums {@link TitanTeam#totalPower()} over EVERY titan team in the guild, deployed or not - titan counterpart of {@link #guildHeroPower(Guild)}. */
    private static int guildTitanPower(Guild guild) {
        int total = 0;
        for (GuildMember member : guild.members()) {
            for (TitanTeam team : member.titanTeams()) {
                total += team.totalPower();
            }
        }
        return total;
    }

    /**
     * What percentage of {@code guildTotal} is currently deployed
     * ({@code deployed}), formatted to one decimal place (e.g. "76.5%") -
     * or "n/a" if {@code guildTotal} is 0 (nothing to divide by, e.g. a
     * guild with no hero teams at all yet).
     */
    private static String percentText(int deployed, int guildTotal) {
        if (guildTotal <= 0) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * deployed / guildTotal);
    }

    // --- lookups/helpers ---

    private static String fortificationDisplayName(String fortificationId) {
        Optional<Fortification> fortification = FortificationRepository.findById(fortificationId);
        return fortification.map(f -> LanguageService.displayName(f.id())).orElse(fortificationId);
    }

    private static String memberName(Guild guild, String memberId) {
        return findMember(guild, memberId).map(GuildMember::name).orElse(memberId);
    }

    private static Optional<GuildMember> findMember(Guild guild, String memberId) {
        return guild.members().stream().filter(m -> m.id().equals(memberId)).findFirst();
    }

    /**
     * Comma-separated display names of the heroes/titans in the team this
     * entry points at, or "?" if the member/team can no longer be resolved
     * (e.g. the member or team was removed from the guild after this entry
     * was created) - mirrors the defensive bounds checks
     * {@link BuffCalculationService} already uses for the same lookup.
     */
    private static String teamCompositionOf(Guild guild, Lineup.Entry entry) {
        Optional<GuildMember> member = findMember(guild, entry.teamMemberId());
        if (member.isEmpty()) {
            return "?";
        }
        if (entry.teamType() == Lineup.TeamType.HERO) {
            if (entry.teamIndex() >= member.get().heroTeams().size()) {
                return "?";
            }
            HeroTeam team = member.get().heroTeams().get(entry.teamIndex());
            return team.heroes().stream().map(h -> LanguageService.displayName(h.id()))
                    .collect(java.util.stream.Collectors.joining(", "));
        } else {
            if (entry.teamIndex() >= member.get().titanTeams().size()) {
                return "?";
            }
            TitanTeam team = member.get().titanTeams().get(entry.teamIndex());
            return team.titans().stream().map(t -> LanguageService.displayName(t.id()))
                    .collect(java.util.stream.Collectors.joining(", "));
        }
    }

    /** Sums {@link Lineup.Entry#totalPower()} over every entry of the given team type - same helper {@code LineupSummaryPanel} keeps privately. */
    private static int totalPower(Lineup lineup, Lineup.TeamType teamType) {
        int total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() == teamType) {
                total += entry.totalPower();
            }
        }
        return total;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
