package org.c2w.gui.journal;

import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.TeamKind;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalCounts;
import org.c2w.data.journal.db.Season;
import org.c2w.data.model.Guild;
import org.c2w.gui.common.GuiUtils;
import org.c2w.i18n.LanguageService;
import org.c2w.service.JournalMaintenanceService;
import org.c2w.service.journal.PlanError;
import org.c2w.service.journal.PlayerQuestion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Display texts of the Weltenschlacht journal GUI: every enum of the import
 * service and the journal is shown through a language file key
 * {@code journal.<group>.<ENUM_NAME>} (see {@link #enumKey}), never through
 * text assembled in code. Swing-free, so it can be tested.
 *
 * <p>Plan errors and blocks are always formatted with arguments (guild name,
 * game guild id, member limit), so a literal apostrophe in their texts must be
 * written as {@code ''} in the language files.
 */
public final class JournalTexts {

    private JournalTexts() {
    }

    /** The language file key of an enum value: {@code journal.<group>.<NAME>}. */
    public static String enumKey(String group, Enum<?> value) {
        return "journal." + group + "." + value.name();
    }

    /** The display text of an enum value of {@code group}. */
    public static String of(String group, Enum<?> value) {
        return LanguageService.displayName(enumKey(group, value));
    }

    /** A text with placeholders. */
    public static String text(String key, Object... args) {
        return LanguageService.displayName(key, args);
    }

    /** A plan error with its guild name and game guild id. */
    public static String planError(PlanError error) {
        return LanguageService.displayName(enumKey("planError", error.kind()),
                error.guildName() == null ? "" : error.guildName(),
                error.gameGuildId() == null ? "" : String.valueOf(error.gameGuildId()));
    }

    /** Why the assistant cannot go on. */
    public static String block(ImportWizardModel.Block block) {
        return LanguageService.displayName(enumKey("block", block), String.valueOf(Guild.MAX_MEMBERS));
    }

    /** "Name (Server n)". */
    public static String opponent(GuildRef guild) {
        return LanguageService.displayName("journal.opponent", guild.name(), String.valueOf(guild.server()));
    }

    /** A number with grouping, as everywhere in Cow2Win (e.g. 1.382.741). */
    public static String number(long value) {
        return GuiUtils.NUMBER_FORMAT.format(value);
    }

    /** Ranking points with sign, e.g. "+851", "-42", "0". */
    public static String rankingPoints(Integer points) {
        if (points == null) {
            return "";
        }
        return points > 0 ? "+" + points : String.valueOf(points);
    }

    /** A date in the format of the configured language. */
    public static String date(LocalDate date) {
        return date == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()).format(date);
    }

    /** The locale of the configured language (for dates). */
    public static Locale locale() {
        return switch (LanguageService.configuredLanguage()) {
            case "deutsch" -> Locale.GERMANY;
            case "francais" -> Locale.FRANCE;
            default -> Locale.UK;
        };
    }

    /**
     * A player name exactly as in the log, with spaces that would otherwise be
     * invisible shown as "·": leading/trailing spaces, runs of several spaces and
     * non-breaking spaces (e.g. "Vale  " becomes "Vale··").
     */
    public static String visibleSpaces(String name) {
        if (name == null) {
            return "";
        }
        String text = name.replace(' ', '·');
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < sb.length() && sb.charAt(i) == ' '; i++) {
            sb.setCharAt(i, '·');
        }
        for (int i = sb.length() - 1; i >= 0 && sb.charAt(i) == ' '; i--) {
            sb.setCharAt(i, '·');
        }
        for (int i = 0; i < sb.length() - 1; i++) {
            if (sb.charAt(i) == ' ' && sb.charAt(i + 1) == ' ') {
                int j = i;
                while (j < sb.length() && sb.charAt(j) == ' ') {
                    sb.setCharAt(j++, '·');
                }
            }
        }
        return sb.toString();
    }

    /** A date and time in the format of the configured language. */
    public static String dateTime(LocalDateTime time) {
        return time == null ? ""
                : DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale()).format(time);
    }

    /** A fortification: its catalog name in the display language, else the raw name from the log. */
    public static String fortification(String fortificationId, String rawName) {
        return fortificationId == null ? (rawName == null ? "" : rawName) : LanguageService.displayName(fortificationId);
    }

    /** Team powers, e.g. "1.037.005, 980.112". */
    public static String powers(List<Integer> powers) {
        return powers.stream().map(JournalTexts::number).collect(Collectors.joining(", "));
    }

    /**
     * The confirmation for deleting battles, saying exactly what goes: one battle
     * "Battle of 24.09.2026 against Das Schwarze Auge - 2 logs, 124 single fights",
     * several "3 battles - 5 logs, 310 single fights".
     */
    public static String deleteBattlesQuestion(List<BattleSummary> battles, JournalCounts counts) {
        if (battles.size() == 1) {
            BattleSummary b = battles.get(0);
            return text("journal.delete.battle", date(b.date()), b.opponent().name(), String.valueOf(counts.logs()),
                    number(counts.fights()));
        }
        return text("journal.delete.battles", String.valueOf(battles.size()), String.valueOf(counts.logs()),
                number(counts.fights()));
    }

    /** The confirmation for deleting a season, with or without its battles. */
    public static String deleteSeasonQuestion(Season season, JournalCounts counts, boolean includeBattles) {
        String seasonText = seasonText(season);
        return includeBattles
                ? text("journal.delete.seasonWithBattles", seasonText, String.valueOf(counts.battles()),
                String.valueOf(counts.logs()), number(counts.fights()))
                : text("journal.delete.seasonOnly", seasonText, String.valueOf(counts.battles()));
    }

    /** "Season 2 (14.09.2026 – 06.12.2026)". */
    public static String seasonText(Season s) {
        return text("journal.battles.seasonItem", String.valueOf(s.number()), date(s.start()), date(s.lastDay()));
    }

    /** Why a season cannot be saved, naming the colliding season. */
    public static String seasonConflict(JournalMaintenanceService.SeasonConflict conflict) {
        return text("journal.seasons.conflict." + conflict.kind().name(), seasonText(conflict.other()));
    }

    /** "n battles change their season." - shown before a season change is saved. */
    public static String reassignPreview(int battles) {
        return text("journal.seasons.reassignPreview", String.valueOf(battles));
    }

    /** "n logs parsed again, parse problems a → b" (+ failures). */
    public static String reparseResult(JournalMaintenanceService.ReparseResult r) {
        String text = text("journal.reparse.result", String.valueOf(r.logs()), String.valueOf(r.problemsBefore()),
                String.valueOf(r.problemsAfter()));
        if (!r.failures().isEmpty()) {
            text += "\n" + text("journal.reparse.failures", String.valueOf(r.failures().size()));
        }
        return text;
    }

    /**
     * The addition to the "delete guild" confirmation: mentions the journal only if
     * the guild has one ({@code battles} {@code null} if it could not be counted).
     */
    public static String removeGuildJournalNote(boolean journalExists, Integer battles) {
        if (!journalExists) {
            return "";
        }
        return battles == null ? text("journal.removeGuild.note")
                : text("journal.removeGuild.noteCount", String.valueOf(battles));
    }

    /** "hero team 2" / "titan team 1" (1-based). */
    public static String team(TeamKind kind, int index) {
        return LanguageService.displayName(enumKey("team", kind), String.valueOf(index + 1));
    }

    /** E.g. "Power 1.382.741 ≈ hero team 2 (1.380.000)" or "same line-up as titan team 1". */
    public static String evidence(PlayerQuestion.RenameEvidence evidence) {
        String team = team(evidence.teamKind(), evidence.teamIndex());
        return switch (evidence.kind()) {
            case POWER -> LanguageService.displayName(enumKey("evidence", evidence.kind()),
                    number(evidence.logTeamPower()), team, number(evidence.memberTeamPower()));
            case SAME_UNITS -> LanguageService.displayName(enumKey("evidence", evidence.kind()), team);
        };
    }
}
