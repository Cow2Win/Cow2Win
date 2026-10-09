package org.c2w.service;

import org.c2w.data.journal.TeamKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The data status of the open guild per process stage - input, strategic concept, output - as
 * computed by {@link DataStatusService}: a traffic light {@link Level}, the parts of a short
 * text (language file keys with arguments - the GUI builds the texts) and detail values for
 * the status bar and the member table. Immutable.
 *
 * @param input          the input stage
 * @param concept        the strategic concept stage
 * @param output         the output stage
 * @param newestDefense  day of the newest defense log, null without one
 * @param memberCount    members of the guild
 * @param staleJournal   members outdated according to the journal
 * @param tooOld         members whose teams are older than the stale check's days (0 with the check off)
 * @param staleAfterDays the stale check's days, null with the check off
 * @param members        per member id its finding (see {@link MemberFinding})
 */
public record DataStatus(StageResult input, StageResult concept, StageResult output,
                         LocalDate newestDefense, int memberCount, int staleJournal, int tooOld,
                         Integer staleAfterDays, Map<String, MemberFinding> members) {

    public DataStatus {
        members = Map.copyOf(members);
    }

    /** The traffic light of a stage (mapped 1:1 to the GUI's stage status). */
    public enum Level {
        /** No statement (e.g. no guild, or the Original lineup is open). */
        NONE,
        OK,
        ATTENTION,
        ACTION_NEEDED
    }

    /** The process stages, in order. */
    public enum Area {
        INPUT, CONCEPT, OUTPUT
    }

    /** One part of a short text: a language file key and its arguments. */
    public record TextPart(String key, List<Object> args) {
        public TextPart {
            args = List.copyOf(args);
        }

        public static TextPart of(String key, Object... args) {
            return new TextPart(key, List.of(args));
        }
    }

    /**
     * One stage's result.
     *
     * @param texts the parts of its short text, most important first (at most two); empty for
     *              no short text
     */
    public record StageResult(Level level, List<TextPart> texts) {
        public StageResult {
            texts = List.copyOf(texts);
        }

        static StageResult none() {
            return new StageResult(Level.NONE, List.of());
        }
    }

    /** How fresh a member's teams are. */
    public enum MemberState {
        /** The newest defense log shows a team that differs from the stored one. */
        STALE_JOURNAL,
        /** With the stale check on: the newest change of its teams is older than the check's days, or unknown. */
        TOO_OLD,
        /** No finding. */
        OK,
        /** No finding - the member has no teams of the selected fortification type. */
        NO_TEAMS
    }

    /**
     * A team the newest defense log shows differently than stored.
     *
     * @param index       stored team index, -1 if the log team matched no stored team
     * @param storedPower the stored team's power (0 without a stored team)
     */
    public record TeamDeviation(TeamKind kind, int index, int storedPower, int logPower) {
    }

    /**
     * One member's finding.
     *
     * @param lastModified the newest change of its teams of the selected type, null if unknown
     * @param ageDays      days since {@code lastModified}, null if unknown
     * @param logDate      for {@link MemberState#STALE_JOURNAL}: the day of the defense log
     * @param deviations   for {@link MemberState#STALE_JOURNAL}: the teams the log shows differently
     */
    public record MemberFinding(MemberState state, LocalDate lastModified, Integer ageDays, LocalDate logDate,
                                List<TeamDeviation> deviations) {
        public MemberFinding {
            deviations = List.copyOf(deviations);
        }

        /** True for a finding that marks the member as outdated (red or orange). */
        public boolean isStale() {
            return state == MemberState.STALE_JOURNAL || state == MemberState.TOO_OLD;
        }
    }

    /** The result of {@code area}. */
    public StageResult result(Area area) {
        return switch (area) {
            case INPUT -> input;
            case CONCEPT -> concept;
            case OUTPUT -> output;
        };
    }

    /**
     * Where to go next: the first stage (input → concept → output) with
     * {@link Level#ACTION_NEEDED}, otherwise the first with {@link Level#ATTENTION}; empty if
     * every stage is {@link Level#OK} or {@link Level#NONE}.
     */
    public Optional<Area> nextStep() {
        for (Level level : List.of(Level.ACTION_NEEDED, Level.ATTENTION)) {
            for (Area area : Area.values()) {
                if (result(area).level() == level) {
                    return Optional.of(area);
                }
            }
        }
        return Optional.empty();
    }

    /** The finding of the member with {@code memberId}, empty if unknown. */
    public Optional<MemberFinding> member(String memberId) {
        return Optional.ofNullable(members.get(memberId));
    }
}
