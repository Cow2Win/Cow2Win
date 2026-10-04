package org.c2w.service;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogParser;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.infra.Logger;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Maintenance of the open guild's Weltenschlacht journal, without any GUI:
 * deleting battles and seasons, editing seasons (battles are reassigned by date
 * in the same transaction), setting a battle's season by hand, correcting player
 * assignments and name mappings, and parsing stored logs again.
 *
 * <p>Nothing here changes the Cow2Win guild (members, teams) - assignments only
 * link journal players to existing members. Reading methods never create a
 * journal file; writing methods need an existing journal ({@link JournalException}
 * otherwise).
 */
public final class JournalMaintenanceService {

    /** Progress of a longer task, e.g. parsing many logs again. */
    @FunctionalInterface
    public interface Progress {
        Progress NONE = (done, total) -> {
        };

        void update(int done, int total);
    }

    private final JournalStore journal;
    private final Supplier<BattleLogParser> parserFactory;
    private BattleLogParser baseParser;

    /** A service for the journal of the open guild of {@code context}, with the shipped catalogs. */
    public JournalMaintenanceService(AppContext context) {
        this(context.journal(), BattleLogParser::createDefault);
    }

    public JournalMaintenanceService(JournalStore journal, Supplier<BattleLogParser> parserFactory) {
        this.journal = Objects.requireNonNull(journal);
        this.parserFactory = Objects.requireNonNull(parserFactory);
    }

    /** The journal of the open guild, empty if it has none (never creates one). */
    public Optional<JournalRepository> repository() throws JournalException {
        return journal.repository(false);
    }

    private JournalRepository existing() throws JournalException {
        return repository().orElseThrow(() -> new JournalException("The open guild has no journal yet"));
    }

    // =====================================================================
    // Battles
    // =====================================================================

    /** Logs and single fights of these battles - for the delete confirmation. */
    public JournalCounts countBattles(Collection<Integer> battleIds) throws JournalException {
        Optional<JournalRepository> repo = repository();
        return repo.isEmpty() ? JournalCounts.NONE : repo.get().countBattles(battleIds);
    }

    /** Deletes the battles (with both logs) in one transaction; returns how many existed. */
    public int deleteBattles(Collection<Integer> battleIds) throws JournalException {
        if (battleIds.isEmpty()) {
            return 0;
        }
        int deleted = existing().inTransaction(r -> {
            int n = 0;
            for (int id : new LinkedHashSet<>(battleIds)) {
                if (r.deleteBattle(id)) {
                    n++;
                }
            }
            return n;
        });
        Logger.log("Journal: deleted " + deleted + " battle(s)");
        return deleted;
    }

    /** The seasons a battle of {@code date} may be assigned to by hand: those containing the date. */
    public List<Season> seasonsFor(LocalDate date) throws JournalException {
        Optional<JournalRepository> repo = repository();
        if (repo.isEmpty()) {
            return List.of();
        }
        return repo.get().listSeasons().stream().filter(s -> s.contains(date)).toList();
    }

    /**
     * Sets the season of a battle by hand: a season containing the battle's date, or
     * {@code null} for none.
     *
     * @throws IllegalArgumentException if the season does not contain the battle's date
     * @throws JournalException         if the battle or season does not exist
     */
    public void assignSeason(int battleId, Integer seasonId) throws JournalException {
        JournalRepository repo = existing();
        BattleSummary battle = repo.findBattleSummary(battleId)
                .orElseThrow(() -> new JournalException("Unknown battle id " + battleId));
        if (seasonId != null) {
            Season season = repo.listSeasons().stream().filter(s -> s.id() == seasonId).findFirst()
                    .orElseThrow(() -> new JournalException("Unknown season id " + seasonId));
            if (!season.contains(battle.date())) {
                throw new IllegalArgumentException("Season " + season.number() + " does not contain " + battle.date());
            }
        }
        repo.assignSeason(battleId, seasonId);
    }

    // =====================================================================
    // Seasons
    // =====================================================================

    /** Why a season cannot be saved: its number is taken, or it overlaps another season. */
    public record SeasonConflict(Kind kind, Season other) {
        public enum Kind {NUMBER_TAKEN, OVERLAP}
    }

    /**
     * Result of a season change.
     *
     * @param season     the saved season ({@code null} after deleting)
     * @param reassigned battles whose season changed by the reassignment by date
     * @param deleted    battles deleted with the season
     */
    public record SeasonChange(Season season, int reassigned, int deleted) {
    }

    /**
     * A new season after the last one in the 12-week raster (number + 1, start = end of
     * the last season); without seasons: number 1 from the oldest battle (else today).
     * The returned season has id 0 (not stored).
     */
    public Season suggestNewSeason(LocalDate today) throws JournalException {
        Optional<JournalRepository> repo = repository();
        List<Season> seasons = repo.isPresent() ? repo.get().listSeasons() : List.of();
        if (!seasons.isEmpty()) {
            Season last = seasons.get(seasons.size() - 1);
            int number = seasons.stream().mapToInt(Season::number).max().orElse(0) + 1;
            return new Season(0, number, last.end(), last.end().plus(Season.DEFAULT_LENGTH), null);
        }
        LocalDate start = today;
        if (repo.isPresent()) {
            start = repo.get().listBattles(null).stream().map(BattleSummary::date).min(Comparator.naturalOrder())
                    .orElse(today);
        }
        return new Season(0, 1, start, start.plus(Season.DEFAULT_LENGTH), null);
    }

    /** The first stored season {@code candidate} collides with (same number or overlapping dates). */
    public Optional<SeasonConflict> findConflict(Season candidate) throws JournalException {
        Optional<JournalRepository> repo = repository();
        if (repo.isEmpty()) {
            return Optional.empty();
        }
        for (Season other : repo.get().listSeasons()) {
            if (other.id() == candidate.id()) {
                continue;
            }
            if (other.number() == candidate.number()) {
                return Optional.of(new SeasonConflict(SeasonConflict.Kind.NUMBER_TAKEN, other));
            }
            if (other.start().isBefore(candidate.end()) && candidate.start().isBefore(other.end())) {
                return Optional.of(new SeasonConflict(SeasonConflict.Kind.OVERLAP, other));
            }
        }
        return Optional.empty();
    }

    /** Preview: battles that would change their season if {@code candidate} were saved (id 0 = new). */
    public int previewSave(Season candidate) throws JournalException {
        Optional<JournalRepository> repo = repository();
        if (repo.isEmpty()) {
            return 0;
        }
        List<Season> seasons = new ArrayList<>(repo.get().listSeasons());
        seasons.removeIf(s -> s.id() == candidate.id());
        seasons.add(candidate);
        return repo.get().countSeasonReassignments(seasons);
    }

    /** Preview: battles that would lose their season if the season were deleted keeping its battles. */
    public int previewDelete(int seasonId) throws JournalException {
        Optional<JournalRepository> repo = repository();
        if (repo.isEmpty()) {
            return 0;
        }
        List<Season> seasons = new ArrayList<>(repo.get().listSeasons());
        seasons.removeIf(s -> s.id() == seasonId);
        return repo.get().countSeasonReassignments(seasons);
    }

    /** Battles, logs and single fights of a season - for the delete confirmation. */
    public JournalCounts countSeason(int seasonId) throws JournalException {
        Optional<JournalRepository> repo = repository();
        return repo.isEmpty() ? JournalCounts.NONE : repo.get().countSeason(seasonId);
    }

    /**
     * Creates ({@code season.id() == 0}) or updates a season and reassigns all battles
     * by date - in one transaction.
     *
     * @throws JournalException if the season collides with another one (see {@link #findConflict})
     */
    public SeasonChange saveSeason(Season season) throws JournalException {
        SeasonChange change = existing().inTransaction(r -> {
            Season saved = season.id() == 0
                    ? withNote(r.createSeason(season.number(), season.start(), season.end()), season.note(), r)
                    : r.updateSeason(season);
            return new SeasonChange(saved, r.reassignSeasonsByDate(), 0);
        });
        Logger.log("Journal: saved season " + change.season().number() + " (" + change.season().start() + " - "
                + change.season().lastDay() + "), " + change.reassigned() + " battle(s) reassigned");
        return change;
    }

    private static Season withNote(Season created, String note, JournalRepository r) throws JournalException {
        if (note == null || note.isBlank()) {
            return created;
        }
        return r.updateSeason(new Season(created.id(), created.number(), created.start(), created.end(), note));
    }

    /**
     * Deletes a season - with all its battles, or keeping them (then they are without
     * season) - and reassigns the remaining battles by date, in one transaction.
     */
    public SeasonChange deleteSeason(int seasonId, boolean includeBattles) throws JournalException {
        SeasonChange change = existing().inTransaction(r -> {
            int battles = includeBattles ? r.countSeason(seasonId).battles() : 0;
            if (!r.deleteSeason(seasonId, includeBattles)) {
                throw new JournalException("Unknown season id " + seasonId);
            }
            return new SeasonChange(null, r.reassignSeasonsByDate(), battles);
        });
        Logger.log("Journal: deleted season " + seasonId + (includeBattles ? " with " + change.deleted() + " battle(s)" : ""));
        return change;
    }

    // =====================================================================
    // Players
    // =====================================================================

    /**
     * Sets the assignment of an own-guild journal player - never changes the guild.
     *
     * @param memberId required for {@link AssignmentStatus#ASSIGNED}, must be a member of {@code guild};
     *                 ignored for every other status
     * @throws IllegalArgumentException if the member is not in the guild
     */
    public PlayerAssignment setAssignment(int playerId, AssignmentStatus status, String memberId, Guild guild)
            throws JournalException {
        String member = status == AssignmentStatus.ASSIGNED ? memberId : null;
        if (member != null && guild.members().stream().map(GuildMember::id).noneMatch(member::equals)) {
            throw new IllegalArgumentException("Unknown member " + member);
        }
        return existing().setAssignment(playerId, member, status);
    }

    // =====================================================================
    // Name mappings
    // =====================================================================

    /** True if {@code catalogId} is a valid target for a mapping of {@code kind}. */
    public boolean isValidMappingTarget(NameMappingKind kind, String catalogId) {
        return baseParser().names().isValidTarget(kind, catalogId);
    }

    /**
     * Changes the catalog id of a manual name mapping. Applies to future imports and
     * to logs parsed again ({@link #reparse}).
     *
     * @throws IllegalArgumentException if the id is not in the catalog of that kind
     */
    public void putNameMapping(NameMappingKind kind, String rawName, String catalogId) throws JournalException {
        if (!isValidMappingTarget(kind, catalogId)) {
            throw new IllegalArgumentException("Not a valid " + kind + " id: " + catalogId);
        }
        existing().putNameMapping(kind, rawName, catalogId);
    }

    /** Removes a manual name mapping; false if there was none. */
    public boolean deleteNameMapping(NameMappingKind kind, String rawName) throws JournalException {
        return existing().deleteNameMapping(kind, rawName);
    }

    // =====================================================================
    // Parse again
    // =====================================================================

    /**
     * Result of parsing stored logs again.
     *
     * @param logs           logs parsed again and replaced
     * @param problemsBefore parse problems of these logs before
     * @param problemsAfter  parse problems of these logs now
     * @param failures       logs that could not be parsed again (they stay unchanged)
     */
    public record ReparseResult(int logs, int problemsBefore, int problemsAfter, List<Failure> failures) {
        public ReparseResult {
            failures = List.copyOf(failures);
        }

        /** A log that could not be parsed again. */
        public record Failure(int battleId, LogDirection direction, String message) {
        }
    }

    /**
     * Parses the stored original CSVs of these battles ({@code null}: of all battles)
     * again with the current parser and name mappings and replaces the logs - even if
     * the files are unchanged. Seasons and player assignments stay; no questions, no
     * guild changes (new unknown players have no assignment = open). Every log is
     * replaced in its own transaction; a failing log is reported and left as it was.
     */
    public ReparseResult reparse(Collection<Integer> battleIdsOrNull, Progress progress) throws JournalException {
        Optional<JournalRepository> found = repository();
        if (found.isEmpty()) {
            return new ReparseResult(0, 0, 0, List.of());
        }
        JournalRepository repo = found.get();
        Map<NameMappingKind, Map<String, String>> mappings = repo.nameMappingsByKind();
        BattleLogParser parser = mappings.isEmpty() ? baseParser() : baseParser().withNameMappings(mappings);
        Set<Integer> wanted = battleIdsOrNull == null ? null : new HashSet<>(battleIdsOrNull);
        List<LogInfo> logs = repo.listLogs(null).stream()
                .filter(l -> wanted == null || wanted.contains(l.battleId())).toList();
        Progress p = progress == null ? Progress.NONE : progress;
        int done = 0;
        int before = 0;
        int after = 0;
        List<ReparseResult.Failure> failures = new ArrayList<>();
        p.update(0, logs.size());
        for (LogInfo log : logs) {
            try {
                Optional<JournalRepository.Reparsed> reparsed = repo.reparseLog(log.battleId(), log.direction(), parser);
                if (reparsed.isPresent()) {
                    done++;
                    before += reparsed.get().problemsBefore();
                    after += reparsed.get().problemsAfter();
                }
            } catch (JournalLockedException e) {
                throw e;
            } catch (JournalException e) {
                Logger.logException("Could not parse the " + log.direction() + " log of battle " + log.battleId()
                        + " again", e);
                failures.add(new ReparseResult.Failure(log.battleId(), log.direction(), e.getMessage()));
            }
            p.update(done + failures.size(), logs.size());
        }
        Logger.log("Journal: parsed " + done + " log(s) again, parse problems " + before + " -> " + after
                + (failures.isEmpty() ? "" : ", " + failures.size() + " failed"));
        return new ReparseResult(done, before, after, failures);
    }

    private synchronized BattleLogParser baseParser() {
        if (baseParser == null) {
            baseParser = parserFactory.get();
        }
        return baseParser;
    }
}
