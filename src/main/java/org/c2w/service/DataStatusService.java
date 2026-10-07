package org.c2w.service;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.model.TitanTeam;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.domain.LineupChangePlanService;
import org.c2w.domain.LineupComparisonService;
import org.c2w.service.DataStatus.Level;
import org.c2w.service.DataStatus.MemberFinding;
import org.c2w.service.DataStatus.MemberState;
import org.c2w.service.DataStatus.StageResult;
import org.c2w.service.DataStatus.TeamDeviation;
import org.c2w.service.DataStatus.TextPart;
import org.c2w.service.journal.SyncPlan;
import org.c2w.service.journal.SyncRow;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes the {@link DataStatus} of the open guild - where something is to be done, per
 * process stage. GUI-free and side-effect free: every input (including "today", the journal's
 * sync plan and the file times) is passed in, so the rules can be tested directly.
 *
 * <p>Rules (decision of 05.10.2026):
 * <ul>
 *   <li><b>Input:</b> a member is outdated only with evidence from the journal - the newest
 *       defense log shows a team differently than stored (a {@link SyncRow} that is not
 *       unchanged and whose target team was not edited since the battle, or an unmatched log
 *       team of the member). Without a journal there is no statement. Optionally (stale check
 *       on) a member is "too old" if the newest change of its teams of the selected type is
 *       older than the check's days or unknown. Members without teams are never marked.</li>
 *   <li><b>Strategic concept:</b> red if free slots could be filled with unassigned teams,
 *       orange if the lineup needs recalculating (guild or CowScore file newer than the lineup
 *       file) or is unsaved; no statement while the Original is open.</li>
 *   <li><b>Output:</b> red without an Original lineup, orange while the plan Original → open
 *       lineup has steps; no statement while the Original is open.</li>
 * </ul>
 */
public final class DataStatusService {

    private DataStatusService() {
    }

    /** The optional "outdated after X days" check. */
    public record StaleCheck(boolean enabled, int days) {
        public static final StaleCheck OFF = new StaleCheck(false, 30);
    }

    /**
     * Last-modified times of the files that decide whether the lineup needs recalculating;
     * null where a file does not exist.
     *
     * @param cowScoreFiles the workspace's CowScore files (heroes, titans, pets, war flags) and the
     *                      time the CowScore bonuses were changed in the settings
     */
    public record FileTimes(Instant guildFile, Instant lineupFile, List<Instant> cowScoreFiles) {
        public static final FileTimes NONE = new FileTimes(null, null, List.of());

        public FileTimes {
            cowScoreFiles = cowScoreFiles == null ? List.of()
                    : cowScoreFiles.stream().filter(Objects::nonNull).toList();
        }
    }

    /**
     * Everything the status is computed from.
     *
     * @param guild           the open guild, null if none
     * @param lineup          the open lineup as in memory
     * @param lineupFilePath  its file
     * @param lineupDirty     true while it has unsaved changes
     * @param syncPlan        the journal's evidence ({@link JournalSyncService#prepare()}), null if unknown
     * @param original        the guild's Original lineup, null if there is none
     */
    public record Input(Guild guild, Lineup lineup, Path lineupFilePath, boolean lineupDirty,
                        FortificationType fortificationType, StaleCheck staleCheck, LocalDate today,
                        SyncPlan syncPlan, Lineup original, FileTimes fileTimes) {
    }

    /** The data status for {@code in}. */
    public static DataStatus evaluate(Input in) {
        if (in.guild() == null) {
            return new DataStatus(StageResult.none(), StageResult.none(), StageResult.none(), null, 0, 0, 0,
                    null, Map.of());
        }
        Map<String, MemberFinding> members = memberFindings(in.guild(), in.fortificationType(), in.staleCheck(),
                in.today(), in.syncPlan());
        int staleJournal = (int) members.values().stream().filter(f -> f.state() == MemberState.STALE_JOURNAL).count();
        int tooOld = (int) members.values().stream().filter(f -> f.state() == MemberState.TOO_OLD).count();
        LocalDate newestDefense = in.syncPlan() != null && in.syncPlan().hasSource() ? in.syncPlan().source().date() : null;
        boolean originalOpen = in.lineupFilePath() != null && LineupFiles.isOriginal(in.lineupFilePath());

        return new DataStatus(
                input(staleJournal, tooOld, in.staleCheck(), newestDefense != null),
                concept(in, originalOpen),
                output(in, originalOpen),
                newestDefense, in.guild().members() == null ? 0 : in.guild().members().size(), staleJournal, tooOld,
                in.staleCheck() != null && in.staleCheck().enabled() ? in.staleCheck().days() : null,
                members);
    }

    // --- input ---

    private static StageResult input(int staleJournal, int tooOld, StaleCheck staleCheck, boolean hasJournal) {
        List<TextPart> texts = new ArrayList<>();
        if (staleJournal > 0) {
            texts.add(TextPart.of("status.input.staleJournal", staleJournal));
        }
        if (tooOld > 0) {
            texts.add(TextPart.of("status.input.staleAge", tooOld, staleCheck.days()));
        }
        if (staleJournal > 0) {
            return new StageResult(Level.ACTION_NEEDED, texts);
        }
        if (tooOld > 0) {
            return new StageResult(Level.ATTENTION, texts);
        }
        return new StageResult(Level.OK, List.of(TextPart.of(hasJournal ? "status.input.ok" : "status.input.complete")));
    }

    /**
     * The finding of every member of {@code guild} - the one place the "outdated" rules live
     * (used for the input stage and the member table).
     */
    public static Map<String, MemberFinding> memberFindings(Guild guild, FortificationType fortificationType,
                                                            StaleCheck staleCheck, LocalDate today, SyncPlan syncPlan) {
        Map<String, List<TeamDeviation>> evidence = journalEvidence(syncPlan);
        LocalDate logDate = syncPlan != null && syncPlan.hasSource() ? syncPlan.source().date() : null;
        Map<String, MemberFinding> findings = new LinkedHashMap<>();
        if (guild == null || guild.members() == null) {
            return findings;
        }
        for (GuildMember member : guild.members()) {
            List<LocalDate> changes = teamChanges(member, fortificationType);
            LocalDate newest = changes.stream().filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
            Integer ageDays = newest == null || today == null ? null : (int) ChronoUnit.DAYS.between(newest, today);
            MemberState state;
            List<TeamDeviation> deviations = evidence.getOrDefault(member.id(), List.of());
            if (!deviations.isEmpty()) {
                state = MemberState.STALE_JOURNAL;
            } else if (changes.isEmpty()) {
                state = MemberState.NO_TEAMS;
            } else if (staleCheck != null && staleCheck.enabled() && (ageDays == null || ageDays > staleCheck.days())) {
                state = MemberState.TOO_OLD;
            } else {
                state = MemberState.OK;
            }
            findings.put(member.id(), new MemberFinding(state, newest, ageDays,
                    state == MemberState.STALE_JOURNAL ? logDate : null, deviations));
        }
        return findings;
    }

    /** The last changes (may be null) of the member's teams of the type - one entry per team. */
    private static List<LocalDate> teamChanges(GuildMember member, FortificationType fortificationType) {
        List<LocalDate> changes = new ArrayList<>();
        if (fortificationType == FortificationType.TITAN) {
            if (member.titanTeams() != null) {
                member.titanTeams().stream().filter(Objects::nonNull).map(TitanTeam::lastModified).forEach(changes::add);
            }
        } else if (member.heroTeams() != null) {
            member.heroTeams().stream().filter(Objects::nonNull).map(HeroTeam::lastModified).forEach(changes::add);
        }
        return changes;
    }

    /**
     * Per member id the teams the newest defense log shows differently than stored: rows that
     * are not unchanged and whose target team was not edited since the battle, and unmatched
     * log teams of the member.
     */
    static Map<String, List<TeamDeviation>> journalEvidence(SyncPlan syncPlan) {
        Map<String, List<TeamDeviation>> evidence = new HashMap<>();
        if (syncPlan == null || !syncPlan.hasSource()) {
            return evidence;
        }
        for (SyncRow row : syncPlan.rows()) {
            if (row.memberId() == null || row.unchanged()) {
                continue;
            }
            SyncRow.Target target = row.suggested();
            if (target.editedSinceBattle()) {
                continue;
            }
            evidence.computeIfAbsent(row.memberId(), id -> new ArrayList<>())
                    .add(new TeamDeviation(row.kind(), target.index(), target.storedPower(), row.logPower()));
        }
        for (SyncPlan.UnmatchedTeam unmatched : syncPlan.unmatched()) {
            if (unmatched.memberId() != null) {
                evidence.computeIfAbsent(unmatched.memberId(), id -> new ArrayList<>())
                        .add(new TeamDeviation(unmatched.kind(), -1, 0, unmatched.power()));
            }
        }
        return evidence;
    }

    // --- strategic concept ---

    private static StageResult concept(Input in, boolean originalOpen) {
        if (in.lineup() == null) {
            return StageResult.none();
        }
        if (originalOpen) {
            return new StageResult(Level.NONE, List.of(TextPart.of("status.concept.originalOpen")));
        }
        int freeSlots = freeSlots(in.lineup(), in.fortificationType());
        int unassignedTeams = unassignedTeams(in.guild(), in.lineup(), in.fortificationType());
        boolean recalculate = needsRecalculation(in.fileTimes());

        List<TextPart> texts = new ArrayList<>();
        if (freeSlots > 0) {
            texts.add(TextPart.of("status.concept.freeSlots", freeSlots));
        }
        if (recalculate) {
            texts.add(TextPart.of("status.concept.recalculate"));
        }
        if (in.lineupDirty()) {
            texts.add(TextPart.of("status.concept.unsaved"));
        }
        texts = texts.subList(0, Math.min(2, texts.size()));
        if (freeSlots > 0 && unassignedTeams > 0) {
            return new StageResult(Level.ACTION_NEEDED, texts);
        }
        if (recalculate || in.lineupDirty()) {
            return new StageResult(Level.ATTENTION, texts);
        }
        return new StageResult(Level.OK, texts.isEmpty() ? List.of(TextPart.of("status.concept.ok")) : texts);
    }

    /** Capacity minus assigned teams, over every fortification of the type. */
    static int freeSlots(Lineup lineup, FortificationType fortificationType) {
        Map<String, Long> assigned = lineup.entries().stream()
                .collect(Collectors.groupingBy(Lineup.Entry::fortificationId, Collectors.counting()));
        int free = 0;
        for (Fortification fortification : FortificationRepository.findAll()) {
            if (fortification.type() == fortificationType) {
                free += Math.max(0, fortification.capacity() - assigned.getOrDefault(fortification.id(), 0L).intValue());
            }
        }
        return free;
    }

    /** Stored teams of the type that are assigned to no fortification of the lineup. */
    static int unassignedTeams(Guild guild, Lineup lineup, FortificationType fortificationType) {
        Lineup.TeamType teamType = fortificationType == FortificationType.TITAN ? Lineup.TeamType.TITAN : Lineup.TeamType.HERO;
        Set<String> assigned = lineup.entries().stream()
                .filter(e -> e.teamType() == teamType)
                .map(e -> e.teamMemberId() + "#" + e.teamIndex())
                .collect(Collectors.toSet());
        int unassigned = 0;
        if (guild.members() == null) {
            return 0;
        }
        for (GuildMember member : guild.members()) {
            List<Integer> indexes = new ArrayList<>();
            if (teamType == Lineup.TeamType.TITAN) {
                if (member.titanTeams() != null) {
                    member.titanTeams().stream().filter(Objects::nonNull).map(TitanTeam::index).forEach(indexes::add);
                }
            } else if (member.heroTeams() != null) {
                member.heroTeams().stream().filter(Objects::nonNull).map(HeroTeam::index).forEach(indexes::add);
            }
            for (int index : indexes) {
                if (!assigned.contains(member.id() + "#" + index)) {
                    unassigned++;
                }
            }
        }
        return unassigned;
    }

    /** True if the guild file or a CowScore file is newer than the saved lineup file. */
    static boolean needsRecalculation(FileTimes times) {
        if (times == null || times.lineupFile() == null) {
            return false;
        }
        if (times.guildFile() != null && times.guildFile().isAfter(times.lineupFile())) {
            return true;
        }
        return times.cowScoreFiles().stream().anyMatch(t -> t.isAfter(times.lineupFile()));
    }

    // --- output ---

    private static StageResult output(Input in, boolean originalOpen) {
        if (in.lineup() == null || originalOpen) {
            return StageResult.none();
        }
        if (in.original() == null) {
            return new StageResult(Level.ACTION_NEEDED, List.of(TextPart.of("status.output.noOriginal")));
        }
        int steps = LineupChangePlanService.from(LineupComparisonService.compare(in.original(), in.lineup(), in.guild())).size();
        return steps > 0
                ? new StageResult(Level.ATTENTION, List.of(TextPart.of("status.output.steps", steps)))
                : new StageResult(Level.OK, List.of(TextPart.of("status.output.ok")));
    }

}
