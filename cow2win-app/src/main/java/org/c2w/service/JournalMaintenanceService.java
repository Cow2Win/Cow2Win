package org.c2w.service;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogParser;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.infra.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/**
 * Maintenance of the open guild's Weltenschlacht journal, without any GUI:
 * deleting battles, correcting player assignments and name mappings, and parsing
 * stored logs again. Seasons are not maintained here: the season of a battle is
 * computed from its date ({@link org.c2w.data.journal.SeasonCalendar}); a whole
 * season is deleted by filtering the battle list and deleting all its battles.
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
        JournalRepository repo = existing();
        Set<Integer> ids = new LinkedHashSet<>(battleIds);
        Optional<BattleSummary> single = ids.size() == 1 ? repo.findBattleSummary(ids.iterator().next()) : Optional.empty();
        int deleted = repo.inTransaction(r -> {
            int n = 0;
            for (int id : ids) {
                if (r.deleteBattle(id)) {
                    n++;
                }
            }
            return n;
        });
        Logger.log("Journal: deleted " + deleted + " battle(s)");
        if (deleted == 1 && single.isPresent()) {
            logBattleEvent(repo, "guildLog.battleDeleted", single.get());
        } else if (deleted > 0) {
            GuildLog.event(guildDir(repo), "guildLog.battlesDeleted", deleted);
        }
        return deleted;
    }

    /** The guild folder of the journal {@code repo} - where its guild log is written. */
    private static Path guildDir(JournalRepository repo) {
        return repo.database() == null ? null : repo.database().guildDir();
    }

    /** A guild log entry about one battle: {@code key} gets its date and opponent, then {@code more}. */
    private static void logBattleEvent(JournalRepository repo, String key, BattleSummary battle, Object... more) {
        Object[] args = new Object[2 + more.length];
        args[0] = battle.date() == null ? "" : battle.date().toString();
        args[1] = battle.opponent() == null ? "?" : battle.opponent().name();
        System.arraycopy(more, 0, args, 2, more.length);
        GuildLog.event(guildDir(repo), key, args);
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
        JournalRepository repo = existing();
        PlayerAssignment assignment = repo.setAssignment(playerId, member, status);
        String playerName = repo.listPlayers(true).stream().filter(p -> p.id() == playerId).map(JournalPlayer::name)
                .findFirst().orElse(String.valueOf(playerId));
        GuildLog.event(guildDir(repo), "guildLog.playerAssignmentChanged", playerName);
        return assignment;
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
        JournalRepository repo = existing();
        repo.putNameMapping(kind, rawName, catalogId);
        GuildLog.event(guildDir(repo), "guildLog.nameMappingChanged", rawName);
    }

    /** Removes a manual name mapping; false if there was none. */
    public boolean deleteNameMapping(NameMappingKind kind, String rawName) throws JournalException {
        JournalRepository repo = existing();
        boolean deleted = repo.deleteNameMapping(kind, rawName);
        if (deleted) {
            GuildLog.event(guildDir(repo), "guildLog.nameMappingDeleted", rawName);
        }
        return deleted;
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
     * the files are unchanged. Player assignments stay; no questions, no
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
        if (done > 0) {
            logReparsed(repo, battleIdsOrNull);
        }
        return new ReparseResult(done, before, after, failures);
    }

    /** The guild log entry for {@link #reparse}: all battles, one battle (date, opponent) or a number of battles. */
    private static void logReparsed(JournalRepository repo, Collection<Integer> battleIdsOrNull) throws JournalException {
        if (battleIdsOrNull == null) {
            GuildLog.event(guildDir(repo), "guildLog.allBattlesReparsed");
            return;
        }
        Set<Integer> ids = new LinkedHashSet<>(battleIdsOrNull);
        Optional<BattleSummary> single = ids.size() == 1 ? repo.findBattleSummary(ids.iterator().next()) : Optional.empty();
        if (single.isPresent()) {
            logBattleEvent(repo, "guildLog.battleReparsed", single.get());
        } else {
            GuildLog.event(guildDir(repo), "guildLog.battlesReparsed", ids.size());
        }
    }

    private synchronized BattleLogParser baseParser() {
        if (baseParser == null) {
            baseParser = parserFactory.get();
        }
        return baseParser;
    }
}
