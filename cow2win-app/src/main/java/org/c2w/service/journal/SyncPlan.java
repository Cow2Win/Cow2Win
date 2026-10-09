package org.c2w.service.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.TeamKind;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.service.journal.TeamBuildPlan.SkippedPlayer;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * What {@code JournalSyncService#prepare} found: the defense teams of the
 * newest battle with a defense log, matched against the stored teams of the
 * guild members. Writes nothing; the GUI shows it and answers with a
 * {@link SyncSelection}.
 *
 * @param emptyReason    why there is nothing to sync ({@code null} if there is a source)
 * @param source         the battle and its defense log ({@code null} with an {@code emptyReason})
 * @param rows           one per log team matched to a stored team (members by name, hero teams first)
 * @param unmatched      log teams of assigned members without a stored team to match - hint only
 * @param powerConflicts positions with more than one team power in the log - hint only (the last one is used)
 * @param teamsNotInLog  stored teams of members in the log that no log team matched - hint only
 * @param skippedPlayers defenders not linked to an existing member, with the reason
 * @param membersNotInLog ids of guild members not defending in this log
 * @param guildFile      the guild the plan was made for
 */
public record SyncPlan(EmptyReason emptyReason, Source source, List<SyncRow> rows, List<UnmatchedTeam> unmatched,
                       List<PowerConflict> powerConflicts, List<TeamRef> teamsNotInLog,
                       List<SkippedPlayer> skippedPlayers, List<String> membersNotInLog, Path guildFile) {

    public SyncPlan {
        rows = List.copyOf(rows);
        unmatched = List.copyOf(unmatched);
        powerConflicts = List.copyOf(powerConflicts);
        teamsNotInLog = List.copyOf(teamsNotInLog);
        skippedPlayers = List.copyOf(skippedPlayers);
        membersNotInLog = List.copyOf(membersNotInLog);
    }

    /** A plan without a source. */
    public static SyncPlan empty(EmptyReason reason, Path guildFile) {
        return new SyncPlan(reason, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), guildFile);
    }

    /** Why a plan has no source. */
    public enum EmptyReason {
        /** No guild is open. */
        NO_GUILD,
        /** The guild has no journal (file) yet. */
        NO_JOURNAL,
        /** The journal has no battle with a defense log. */
        NO_DEFENSE_LOG
    }

    /**
     * The battle the sync takes its values from.
     *
     * @param battleId   journal battle id
     * @param date       battle day - becomes {@code lastModified} of every changed team
     * @param opponent   opponent guild
     * @param result     result of the battle
     * @param status     running or finished
     * @param language   language of the stored defense log ({@code null} if unknown)
     * @param fileName   file name of the stored defense log ({@code null} if unknown)
     * @param importedAt when the defense log was imported ({@code null} if unknown)
     */
    public record Source(int battleId, LocalDate date, GuildRef opponent, BattleResult result, BattleStatus status,
                         String language, String fileName, LocalDateTime importedAt) {
    }

    /**
     * A team of a member in the log without a stored team to match (none of that kind left,
     * or every remaining one more than {@code UNSURE} away). Nothing is changed or created.
     *
     * @param memberId           the member
     * @param logName            the defender's name in the log
     * @param kind               hero or titan team
     * @param power              team power in the log
     * @param fortificationId    catalog id of the fortification ({@code null} if unknown)
     * @param fortificationName  raw fortification name
     * @param position           position in the fortification
     */
    public record UnmatchedTeam(String memberId, String logName, TeamKind kind, int power, String fortificationId,
                                String fortificationName, int position) {
    }

    /**
     * One (fortification, position) of a member with different team powers in the same log.
     *
     * @param powers the powers in log order; the last one is used
     */
    public record PowerConflict(String memberId, String logName, String fortificationId, String fortificationName,
                                int position, List<Integer> powers) {
        public PowerConflict {
            powers = List.copyOf(powers);
        }
    }

    /** A stored team: member, kind and index. */
    public record TeamRef(String memberId, TeamKind kind, int index) {
    }

    // --- convenience ---

    /** True if there is a source battle. */
    public boolean hasSource() {
        return source != null;
    }

    /** The row with this id. */
    public Optional<SyncRow> row(String id) {
        return rows.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** The rows of a member. */
    public List<SyncRow> rowsOf(String memberId) {
        return rows.stream().filter(r -> r.memberId().equals(memberId)).toList();
    }

    /** Number of rows with this certainty. */
    public long count(SyncRow.Certainty certainty) {
        return rows.stream().filter(r -> r.certainty() == certainty).count();
    }
}
