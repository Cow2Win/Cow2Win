package org.c2w.util;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.eval.LineupAlgorithms;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ReportGenerator {

    /** File name suffix stripped from the lineup file name before appending {@value #REPORT_FILE_SUFFIX} - see class Javadoc. */
    private static final String LINEUP_FILE_SUFFIX = ".lineup";

    /** File name suffix of a generated report. */
    private static final String REPORT_FILE_SUFFIX = ".html";

    /**
     * Cap on how many rows the "Used heroes"/"Used titans" tables show - per
     * the user's explicit request (added 2026-09-23): only the most-used 15
     * units, so a large guild's report does not grow an overlong tail. Rows
     * are already sorted most-used first (see {@link #usedUnitsTableHtml}),
     * so this simply keeps the top of that list; a note under the table says
     * how many further units were omitted.
     */
    private static final int MAX_USED_UNITS_ROWS = 15;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // --- Colors ---
    // Report colors are written directly onto the elements (an explicit hex
    // rule in the style block, or an inline style on the cell) instead of
    // being left to the renderer's defaults, and the old CSS3
    // "tr:nth-child(even)" zebra rule is emitted per row (see #tdOpen) rather
    // than as a selector - added 2026-09-23 at the user's request so the
    // report looks the same in a web browser and in the app's Swing preview.
    // Swing's JEditorPane HTML engine supports only a limited CSS subset and
    // ignores nth-child, so leaving these to CSS made the two diverge.
    private static final String COLOR_TEXT = "#000000";
    private static final String COLOR_PAGE_BG = "#ffffff";
    private static final String COLOR_HEADER_BG = "#dddddd";
    private static final String COLOR_ALT_ROW_BG = "#f5f5f5";
    private static final String COLOR_BORDER = "#bbbbbb";
    private static final String COLOR_RULE = "#999999";

    /** Inline background style for a header cell - see the Colors section. */
    private static final String TH_BG_STYLE = " style=\"background-color:" + COLOR_HEADER_BG + ";\"";

    private ReportGenerator() {
        // Utility class, no instantiation
    }

    /**
     * Builds and returns the report HTML for the given lineup/guild WITHOUT
     * writing it anywhere - added 2026-09-23 when report generation stopped
     * auto-saving per the user's explicit request. {@code
     * ToolbarPanel#onGenerateReport()} now hands this HTML straight to
     * {@code ReportViewerDialog}, whose "Save report..." toolbar button lets
     * the user pick a directory and write it there. {@code reportFileName}
     * only feeds the report's "Lineup" meta row (see {@link
     * #metaTableHtml}); pass {@link #suggestedReportFileName(Path)}.
     */
    public static String buildReportHtml(Lineup lineup, Guild guild, String reportFileName) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        if (reportFileName == null || reportFileName.isBlank()) {
            throw new IllegalArgumentException("reportFileName must not be null or blank");
        }
        return buildHtml(lineup, guild, reportFileName);
    }

    /**
     * The default file name the report viewer's Save button offers for this
     * lineup: its file name with the {@value #LINEUP_FILE_SUFFIX} suffix (if
     * any) swapped for {@value #REPORT_FILE_SUFFIX}, i.e. the very name the
     * old auto-saving {@code generate} used to write - just the file name,
     * not a full path, since the directory is now the user's choice (see
     * {@link #buildReportHtml} and {@code ReportViewerDialog}).
     */
    public static String suggestedReportFileName(Path lineupFilePath) {
        if (lineupFilePath == null) {
            throw new IllegalArgumentException("lineupFilePath must not be null");
        }
        return reportPathFor(lineupFilePath).getFileName().toString();
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

        html.append("<h2>Top used heroes</h2>\n");
        html.append(usedUnitsTableHtml(lineup, guild, Lineup.TeamType.HERO));

        html.append("<h2>Top used titans</h2>\n");
        html.append(usedUnitsTableHtml(lineup, guild, Lineup.TeamType.TITAN));

        html.append("<h2>Lineup data</h2>\n");
        html.append(entriesTableHtml(lineup, guild));

        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static String styleBlock() {
        // Structural rules (fonts, borders, spacing) stay in the style block;
        // the color-critical backgrounds (header cells, zebra rows) are set
        // inline on the cells instead - see the Colors section for why.
        return "<style>\n"
                + "body { font-family: Arial, Helvetica, sans-serif; margin: 24px; color: " + COLOR_TEXT
                + "; background-color: " + COLOR_PAGE_BG + "; }\n"
                + "h1 { margin-bottom: 4px; }\n"
                + "h2 { margin-top: 32px; border-bottom: 1px solid " + COLOR_RULE + "; padding-bottom: 4px; }\n"
                + "table { border-collapse: collapse; margin-top: 12px; width: 100%; }\n"
                + "th, td { border: 1px solid " + COLOR_BORDER + "; padding: 6px 10px; text-align: left; }\n"
                + ".meta-table, .stats-table { width: auto; }\n"
                + ".number { text-align: right; }\n"
                + "</style>\n";
    }

    /** Opening &lt;th&gt; tag with the header background anchored inline (see Colors); {@code number} adds the right-align class. */
    private static String thOpen(boolean number) {
        return "<th" + (number ? " class=\"number\"" : "") + TH_BG_STYLE + ">";
    }

    /**
     * Opening &lt;td&gt; tag for a data cell, carrying the zebra background
     * inline on even rows (1-based {@code rowPosition}) so the striping shows
     * identically in a browser and in Swing's JEditorPane - the
     * element-anchored replacement for the old {@code tr:nth-child(even)}
     * rule (see Colors). {@code number} adds the right-align class.
     */
    private static String tdOpen(boolean number, int rowPosition) {
        String bg = rowPosition % 2 == 0 ? " style=\"background-color:" + COLOR_ALT_ROW_BG + ";\"" : "";
        return "<td" + (number ? " class=\"number\"" : "") + bg + ">";
    }

    private static String metaTableHtml(Lineup lineup, String lineupName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"meta-table\">\n");
        int pos = 0;
        appendMetaRow(sb, ++pos, "Guild", lineup.guildName());
        appendMetaRow(sb, ++pos, "Lineup", lineupName);
        appendMetaRow(sb, ++pos, "Lineup created at", lineup.createdAt().format(TIMESTAMP_FORMAT));
        appendMetaRow(sb, ++pos, "Report generated at", java.time.LocalDateTime.now().format(TIMESTAMP_FORMAT));
        appendAlgorithmRowsIfPresent(sb, pos, lineup);
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
    private static void appendAlgorithmRowsIfPresent(StringBuilder sb, int startPosition, Lineup lineup) {
        String algorithmName = lineup.algorithmName();
        if (algorithmName == null || algorithmName.isBlank()) {
            return;
        }
        int pos = startPosition;
        // Since 2026-09-24 heroes and titans can be filled by different algorithms, and
        // algorithmName combines both ("Heroes: ...; Titans: ...") - one row pair per side.
        // A legacy single-algorithm name (older lineup files) is shown as-is, like before.
        Map<Lineup.TeamType, String> bySide = LineupAlgorithms.parseAlgorithmName(algorithmName);
        if (bySide.isEmpty()) {
            appendAlgorithmRows(sb, pos, "Algorithm", algorithmName);
            return;
        }
        if (bySide.containsKey(Lineup.TeamType.HERO)) {
            pos = appendAlgorithmRows(sb, pos, "Hero algorithm", bySide.get(Lineup.TeamType.HERO));
        }
        if (bySide.containsKey(Lineup.TeamType.TITAN)) {
            appendAlgorithmRows(sb, pos, "Titan algorithm", bySide.get(Lineup.TeamType.TITAN));
        }
    }

    /** Appends an algorithm name row plus, if one is on file, its description row; returns the last row position used. */
    private static int appendAlgorithmRows(StringBuilder sb, int startPosition, String label, String displayName) {
        int pos = startPosition;
        appendMetaRow(sb, ++pos, label, displayName);
        String description = AlgorithmDescriptions.forDisplayName(displayName);
        if (!description.isBlank()) {
            appendMetaRow(sb, ++pos, label + " description", description);
        }
        return pos;
    }

    private static void appendMetaRow(StringBuilder sb, int rowPosition, String label, String value) {
        sb.append("<tr>").append(thOpen(false)).append(escape(label)).append("</th>")
                .append(tdOpen(false, rowPosition)).append(escape(value)).append("</td></tr>\n");
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
                .append(thOpen(false)).append("Fortification</th>")
                .append(thOpen(false)).append("Team member</th>")
                .append(thOpen(false)).append("Team composition</th>")
                .append(thOpen(true)).append("Power</th>")
                .append("</tr>\n");

        int pos = 1; // header is child 1; data rows start at child 2 (matches the old nth-child zebra)
        for (Lineup.Entry entry : entries) {
            pos++;
            sb.append("<tr>");
            sb.append(tdOpen(false, pos)).append(escape(fortificationDisplayName(entry.fortificationId()))).append("</td>");
            sb.append(tdOpen(false, pos)).append(escape(memberName(guild, entry.teamMemberId()))).append("</td>");
            sb.append(tdOpen(false, pos)).append(escape(teamCompositionOf(guild, entry))).append("</td>");
            sb.append(tdOpen(true, pos)).append(BuffCalculationService.totalPowerOf(entry, guild)).append("</td>");
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
                .append(thOpen(false)).append("Fortification</th>")
                .append(thOpen(false)).append("Buff</th>")
                .append(thOpen(true)).append("Buff %</th>")
                .append(thOpen(true)).append("Matching role/element</th>")
                .append(thOpen(true)).append("CowScore</th>")
                .append("</tr>\n");

        int pos = 1; // header is child 1; data rows start at child 2 (matches the old nth-child zebra)
        for (String fortificationId : fortificationIds) {
            pos++;
            Fortification fortification = FortificationRepository.findById(fortificationId).orElseThrow();
            Buff buff = fortification.buff();
            int buffPercent = BuffCalculationService.calculateBuffForFortification(
                    fortificationId, lineup, guild, fortification);
            int matchingCount = BuffCalculationService.countMatchingMembersForFortification(
                    fortificationId, lineup, guild, buff);
            double cowScore = BuffCalculationService.sumCowScoreForFortification(
                    fortificationId, lineup, guild, fortification);

            sb.append("<tr>");
            sb.append(tdOpen(false, pos)).append(escape(fortificationDisplayName(fortificationId))).append("</td>");
            sb.append(tdOpen(false, pos)).append(escape(buffDisplayText(buff))).append("</td>");
            sb.append(tdOpen(true, pos)).append(buffPercent).append("%</td>");
            sb.append(tdOpen(true, pos)).append(matchingCount).append("</td>");
            sb.append(tdOpen(true, pos)).append(formatCowScore(cowScore)).append("</td>");
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

    /**
     * One row per distinct hero (for {@code teamType} HERO) or titan (TITAN)
     * that appears in at least one team deployed by this lineup, with how
     * many teams across the whole lineup it shows up in - per the user's
     * explicit request (added 2026-09-23): a "used heroes"/"used titans"
     * breakdown that makes it easy to see e.g. that Galahad is fielded 5
     * times overall, regardless of which member or fortification the team
     * sits at. Rows are sorted by that count (most-used first), then by
     * display name for a stable order among ties. A unit counts once per
     * team it belongs to (so a hero fielded in three different teams counts
     * 3); teams that can no longer be resolved are skipped via the same
     * defensive bounds checks as {@link #teamCompositionOf(Guild, Lineup.Entry)}
     * - see {@link #unitIdsOf(Guild, Lineup.Entry)}.
     */
    private static String usedUnitsTableHtml(Lineup lineup, Guild guild, Lineup.TeamType teamType) {
        Map<String, Integer> counts = new HashMap<>();
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() != teamType) {
                continue;
            }
            for (String unitId : unitIdsOf(guild, entry)) {
                counts.merge(unitId, 1, Integer::sum);
            }
        }

        String unitLabel = teamType == Lineup.TeamType.HERO ? "hero" : "titan";
        StringBuilder sb = new StringBuilder();
        if (counts.isEmpty()) {
            sb.append("<p>No ").append(unitLabel).append(" is used in this lineup.</p>\n");
            return sb.toString();
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator
                .comparing(Map.Entry<String, Integer>::getValue).reversed()
                .thenComparing(e -> LanguageService.displayName(e.getKey())));

        sb.append("<table>\n<tr>")
                .append(thOpen(false)).append(teamType == Lineup.TeamType.HERO ? "Hero" : "Titan").append("</th>")
                .append(thOpen(true)).append("Count</th>")
                .append("</tr>\n");

        List<Map.Entry<String, Integer>> shown = sorted.subList(0, Math.min(sorted.size(), MAX_USED_UNITS_ROWS));
        int pos = 1; // header is child 1; data rows start at child 2 (matches the old nth-child zebra)
        for (Map.Entry<String, Integer> countEntry : shown) {
            pos++;
            sb.append("<tr>");
            sb.append(tdOpen(false, pos)).append(escape(LanguageService.displayName(countEntry.getKey()))).append("</td>");
            sb.append(tdOpen(true, pos)).append(countEntry.getValue()).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");

        int omitted = sorted.size() - shown.size();
        if (omitted > 0) {
            sb.append("<p>... and ").append(omitted).append(" more ").append(unitLabel)
                    .append(omitted == 1 ? "" : "s").append(" (showing the top ").append(MAX_USED_UNITS_ROWS)
                    .append(").</p>\n");
        }
        return sb.toString();
    }

    /**
     * The hero/titan ids in the team this entry points at, or an empty list
     * if the member or team can no longer be resolved (e.g. it was removed
     * from the guild after this entry was created) - the id-only counterpart
     * of {@link #teamCompositionOf(Guild, Lineup.Entry)}, sharing its
     * defensive bounds checks. Used by {@link #usedUnitsTableHtml}.
     */
    private static List<String> unitIdsOf(Guild guild, Lineup.Entry entry) {
        Optional<GuildMember> member = findMember(guild, entry.teamMemberId());
        if (member.isEmpty()) {
            return List.of();
        }
        if (entry.teamType() == Lineup.TeamType.HERO) {
            if (entry.teamIndex() >= member.get().heroTeams().size()) {
                return List.of();
            }
            return member.get().heroTeams().get(entry.teamIndex()).heroes().stream()
                    .map(Hero::id).toList();
        } else {
            if (entry.teamIndex() >= member.get().titanTeams().size()) {
                return List.of();
            }
            return member.get().titanTeams().get(entry.teamIndex()).titans().stream()
                    .map(Titan::id).toList();
        }
    }

    private static String statisticsTableHtml(Lineup lineup, Guild guild) {
        int heroPower = totalPower(lineup, guild, Lineup.TeamType.HERO);
        int titanPower = totalPower(lineup, guild, Lineup.TeamType.TITAN);
        int heroBuffCount = BuffCalculationService.countHeroesIncreasingBuff(lineup, guild);
        int titanBuffCount = BuffCalculationService.countTitansIncreasingBuff(lineup, guild);
        double heroCowScore = BuffCalculationService.sumHeroCowScore(lineup, guild);
        double titanCowScore = BuffCalculationService.sumTitanCowScore(lineup, guild);
        int guildHeroPower = guildHeroPower(guild);
        int guildTitanPower = guildTitanPower(guild);

        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"stats-table\">\n");
        int pos = 0;
        appendStatRow(sb, ++pos, "Total hero power (deployed)", heroPower);
        appendStatRow(sb, ++pos, "Total hero power (guild)", guildHeroPower);
        appendStatRow(sb, ++pos, "Hero power deployed", percentText(heroPower, guildHeroPower));
        appendStatRow(sb, ++pos, "Heroes increasing a buff", heroBuffCount);
        appendStatRow(sb, ++pos, "Hero CowScore", formatCowScore(heroCowScore));
        appendStatRow(sb, ++pos, "Total titan power (deployed)", titanPower);
        appendStatRow(sb, ++pos, "Total titan power (guild)", guildTitanPower);
        appendStatRow(sb, ++pos, "Titan power deployed", percentText(titanPower, guildTitanPower));
        appendStatRow(sb, ++pos, "Titans increasing a buff", titanBuffCount);
        appendStatRow(sb, ++pos, "Titan CowScore", formatCowScore(titanCowScore));
        appendStatRow(sb, ++pos, "Total power (heroes + titans, deployed)", heroPower + titanPower);
        appendStatRow(sb, ++pos, "Total power (heroes + titans, guild)", guildHeroPower + guildTitanPower);
        appendStatRow(sb, ++pos, "Total power deployed", percentText(heroPower + titanPower, guildHeroPower + guildTitanPower));
        appendStatRow(sb, ++pos, "Total team assignments", lineup.entries().size());
        sb.append("</table>\n");
        return sb.toString();
    }

    /** Formats a summed CowScore total (see {@link BuffCalculationService#sumHeroCowScore}/{@link BuffCalculationService#sumTitanCowScore}/{@link BuffCalculationService#sumCowScoreForFortification}) to one decimal place, {@link Locale#ROOT} like the rest of this English-only report - same precision {@code LineupSummaryPanel}'s COW_SCORE_FORMAT uses in the GUI. */
    private static String formatCowScore(double cowScore) {
        return String.format(Locale.ROOT, "%.1f", cowScore);
    }

    private static void appendStatRow(StringBuilder sb, int rowPosition, String label, int value) {
        appendStatRow(sb, rowPosition, label, String.valueOf(value));
    }

    private static void appendStatRow(StringBuilder sb, int rowPosition, String label, String value) {
        sb.append("<tr>").append(thOpen(false)).append(escape(label)).append("</th>")
                .append(tdOpen(true, rowPosition)).append(escape(value)).append("</td></tr>\n");
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

    /** Sums every entry's current team totalPower (see {@link BuffCalculationService#totalPowerOf}) over every entry of the given team type - same helper {@code LineupSummaryPanel} keeps privately. */
    private static int totalPower(Lineup lineup, Guild guild, Lineup.TeamType teamType) {
        int total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() == teamType) {
                total += BuffCalculationService.totalPowerOf(entry, guild);
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
