package org.c2w.util;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;

import java.util.*;

/**
 * Compares two {@link Lineup}s of the SAME guild - either two lineups loaded
 * from different ".lineup" files, or the currently open lineup against a
 * candidate a {@code org.c2w.eval.LineupAlgorithm} produced but that was
 * never saved (see {@code org.c2w.gui.LineupComparisonDialog}, the only
 * caller). Purely read-only: never mutates either {@link Lineup}, the
 * {@link Guild}, or {@link AppContext} - both lineups are only ever read.
 *
 * <p>Comparison granularity is per TEAM, not per lineup entry: a team is
 * identified by (teamMemberId, teamType, teamIndex) - the same identity
 * {@link Lineup.Entry} itself carries - since a given team can be assigned to
 * at most one fortification at a time. This directly answers "where did this
 * team move to/from", which a plain per-fortification diff alone cannot (it
 * would only show that SOME team changed at a fortification, not which one,
 * or where the previous occupant went) - see {@link #compare}.
 *
 * <p>Added 2026-09-13 per the user's request (see
 * cow2win-verbesserungsvorschlaege.md, section 3, "Lineup-Vergleich") - the
 * "current lineup vs. algorithm" case deliberately does NOT reuse
 * {@link AppContext#fortificationDiffFromLoaded}/{@link LineupBaseline}:
 * those are hard-wired to "loaded-from-disk vs. current in-memory lineup of
 * the SAME file" and only cover totalPower/buffMemberCount per fortification,
 * not which team moved where - a genuinely different, narrower question than
 * what this class answers for two arbitrary {@link Lineup} instances.
 */
public final class LineupComparisonService {

    private LineupComparisonService() {
        // Utility class, no instantiation
    }

    /**
     * Compares {@code before} against {@code after} (e.g. the currently
     * loaded lineup vs. a freshly computed algorithm candidate, or an older
     * saved lineup vs. a newer one) - {@code guild} resolves team member
     * names and buff matches for both sides, and is assumed to apply to both
     * lineups (the caller is responsible for warning if
     * {@code before.guildId()} and {@code after.guildId()} differ; this
     * method does not check that itself, since a caller may deliberately
     * want to compare across guilds).
     */
    public static LineupComparison compare(Lineup before, Lineup after, Guild guild) {
        if (before == null) {
            throw new IllegalArgumentException("before must not be null");
        }
        if (after == null) {
            throw new IllegalArgumentException("after must not be null");
        }
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }

        Map<TeamKey, Lineup.Entry> beforeByTeam = indexByTeam(before);
        Map<TeamKey, Lineup.Entry> afterByTeam = indexByTeam(after);

        Set<TeamKey> allTeamKeys = new LinkedHashSet<>();
        allTeamKeys.addAll(beforeByTeam.keySet());
        allTeamKeys.addAll(afterByTeam.keySet());

        List<TeamDiff> teamDiffs = new ArrayList<>();
        for (TeamKey key : allTeamKeys) {
            teamDiffs.add(TeamDiff.of(key, beforeByTeam.get(key), afterByTeam.get(key)));
        }
        teamDiffs.sort(Comparator
                .comparing((TeamDiff d) -> d.status() == TeamDiff.Status.UNCHANGED)
                .thenComparing(d -> fortificationDisplayName(d.currentFortificationId()))
                .thenComparing(d -> memberDisplayName(guild, d.teamKey().teamMemberId())));

        List<FortificationDiff> fortificationDiffs = buildFortificationDiffs(before, after, guild);

        Summary summary = Summary.of(teamDiffs);

        return new LineupComparison(teamDiffs, fortificationDiffs, summary);
    }

    private static Map<TeamKey, Lineup.Entry> indexByTeam(Lineup lineup) {
        Map<TeamKey, Lineup.Entry> result = new LinkedHashMap<>();
        for (Lineup.Entry entry : lineup.entries()) {
            result.put(TeamKey.of(entry), entry);
        }
        return result;
    }

    /** One row per fortification assigned in EITHER lineup, sorted by display name. */
    private static List<FortificationDiff> buildFortificationDiffs(Lineup before, Lineup after, Guild guild) {
        Set<String> fortificationIds = new LinkedHashSet<>();
        before.entries().forEach(e -> fortificationIds.add(e.fortificationId()));
        after.entries().forEach(e -> fortificationIds.add(e.fortificationId()));

        List<FortificationDiff> result = new ArrayList<>();
        for (String fortificationId : fortificationIds) {
            Fortification fortification = FortificationRepository.findById(fortificationId).orElse(null);
            int powerBefore = totalPower(before, fortificationId);
            int powerAfter = totalPower(after, fortificationId);
            int slotsBefore = countEntries(before, fortificationId);
            int slotsAfter = countEntries(after, fortificationId);
            int buffPercentBefore = fortification == null ? 0
                    : BuffCalculationService.calculateBuffForFortification(fortificationId, before, guild, fortification);
            int buffPercentAfter = fortification == null ? 0
                    : BuffCalculationService.calculateBuffForFortification(fortificationId, after, guild, fortification);
            result.add(new FortificationDiff(fortificationId, powerBefore, powerAfter,
                    slotsBefore, slotsAfter, buffPercentBefore, buffPercentAfter));
        }
        result.sort(Comparator.comparing(d -> fortificationDisplayName(d.fortificationId())));
        return result;
    }

    private static int totalPower(Lineup lineup, String fortificationId) {
        int total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.fortificationId().equals(fortificationId)) {
                total += entry.totalPower();
            }
        }
        return total;
    }

    private static int countEntries(Lineup lineup, String fortificationId) {
        int count = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.fortificationId().equals(fortificationId)) {
                count++;
            }
        }
        return count;
    }

    static String fortificationDisplayName(String fortificationId) {
        if (fortificationId == null) {
            return "";
        }
        return FortificationRepository.findById(fortificationId)
                .map(f -> LanguageService.displayName(f.id()))
                .orElse(fortificationId);
    }

    static String memberDisplayName(Guild guild, String memberId) {
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .map(GuildMember::name)
                .orElse(memberId);
    }

    /** Identity of one team, independent of which fortification it is currently assigned to - see class Javadoc. */
    public record TeamKey(String teamMemberId, Lineup.TeamType teamType, int teamIndex) {
        static TeamKey of(Lineup.Entry entry) {
            return new TeamKey(entry.teamMemberId(), entry.teamType(), entry.teamIndex());
        }
    }

    /**
     * One team's assignment before/after. Exactly one of {@link #before()}/
     * {@link #after()} is null for {@link Status#ADDED}/{@link Status#REMOVED}
     * respectively; both are non-null for {@link Status#MOVED}/
     * {@link Status#UPDATED}/{@link Status#UNCHANGED} (see {@link #of} for
     * how those three are told apart).
     */
    public record TeamDiff(TeamKey teamKey, Lineup.Entry before, Lineup.Entry after, Status status) {

        private static TeamDiff of(TeamKey key, Lineup.Entry before, Lineup.Entry after) {
            Status status;
            if (before == null) {
                status = Status.ADDED;
            } else if (after == null) {
                status = Status.REMOVED;
            } else if (!before.fortificationId().equals(after.fortificationId())) {
                status = Status.MOVED;
            } else if (before.totalPower() != after.totalPower() || before.weightedScore() != after.weightedScore()) {
                // Same fortification both times, but the entry itself changed - e.g. the
                // team's own composition/power was edited between the two lineups. Kept
                // distinct from MOVED (still "changed", so still surfaced when the GUI's
                // "only changes" filter is on - see LineupComparisonDialog#refreshTables)
                // but distinct from UNCHANGED, which a byte-for-byte identical entry stays.
                status = Status.UPDATED;
            } else {
                status = Status.UNCHANGED;
            }
            return new TeamDiff(key, before, after, status);
        }

        /** Fortification id this team is assigned to in {@code after}, or (for {@link Status#REMOVED}) the one it was removed from in {@code before} - whichever side is non-null. */
        public String currentFortificationId() {
            return after != null ? after.fortificationId() : before.fortificationId();
        }

        public String fortificationIdBefore() {
            return before == null ? null : before.fortificationId();
        }

        public String fortificationIdAfter() {
            return after == null ? null : after.fortificationId();
        }

        public int powerBefore() {
            return before == null ? 0 : before.totalPower();
        }

        public int powerAfter() {
            return after == null ? 0 : after.totalPower();
        }

        public int powerDiff() {
            return powerAfter() - powerBefore();
        }

        public double weightedScoreBefore() {
            return before == null ? 0 : before.weightedScore();
        }

        public double weightedScoreAfter() {
            return after == null ? 0 : after.weightedScore();
        }

        public double weightedScoreDiff() {
            return weightedScoreAfter() - weightedScoreBefore();
        }

        public enum Status { UNCHANGED, UPDATED, MOVED, ADDED, REMOVED }
    }

    /** One fortification's totals in both lineups - independent of which specific teams are assigned (see {@link TeamDiff} for that). */
    public record FortificationDiff(
            String fortificationId, int powerBefore, int powerAfter,
            int filledSlotsBefore, int filledSlotsAfter,
            int buffPercentBefore, int buffPercentAfter) {

        public int powerDiff() {
            return powerAfter - powerBefore;
        }

        public int buffPercentDiff() {
            return buffPercentAfter - buffPercentBefore;
        }

        public boolean isUnchanged() {
            return powerBefore == powerAfter && filledSlotsBefore == filledSlotsAfter
                    && buffPercentBefore == buffPercentAfter;
        }
    }

    /** Aggregate totals over every {@link TeamDiff}, for a one-line summary in the GUI. */
    public record Summary(
            int totalPowerBefore, int totalPowerAfter,
            int heroPowerBefore, int heroPowerAfter,
            int titanPowerBefore, int titanPowerAfter,
            long addedCount, long removedCount, long movedCount, long unchangedCount) {

        private static Summary of(List<TeamDiff> teamDiffs) {
            int totalBefore = 0, totalAfter = 0, heroBefore = 0, heroAfter = 0, titanBefore = 0, titanAfter = 0;
            long added = 0, removed = 0, moved = 0, unchanged = 0;
            for (TeamDiff diff : teamDiffs) {
                totalBefore += diff.powerBefore();
                totalAfter += diff.powerAfter();
                if (diff.teamKey().teamType() == Lineup.TeamType.HERO) {
                    heroBefore += diff.powerBefore();
                    heroAfter += diff.powerAfter();
                } else {
                    titanBefore += diff.powerBefore();
                    titanAfter += diff.powerAfter();
                }
                if (diff.status() == TeamDiff.Status.ADDED) {
                    added++;
                } else if (diff.status() == TeamDiff.Status.REMOVED) {
                    removed++;
                } else if (diff.status() == TeamDiff.Status.MOVED || diff.status() == TeamDiff.Status.UPDATED) {
                    // Summary-level counts don't distinguish "moved to a different
                    // fortification" from "same fortification, entry itself changed" -
                    // both are lumped into "moved" here; the per-row Status (surfaced
                    // in the GUI table) keeps the distinction, see TeamDiff#of.
                    moved++;
                } else {
                    unchanged++;
                }
            }
            return new Summary(totalBefore, totalAfter, heroBefore, heroAfter, titanBefore, titanAfter,
                    added, removed, moved, unchanged);
        }

        public int totalPowerDiff() {
            return totalPowerAfter - totalPowerBefore;
        }

        public long changedCount() {
            return addedCount + removedCount + movedCount;
        }
    }

    /** Full comparison result - see {@link #compare}. */
    public record LineupComparison(
            List<TeamDiff> teamDiffs, List<FortificationDiff> fortificationDiffs, Summary summary) {
    }
}
