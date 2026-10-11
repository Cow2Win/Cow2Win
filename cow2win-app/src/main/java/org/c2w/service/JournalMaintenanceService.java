package org.c2w.service;

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
 * deleting battles and correcting player assignments and name mappings.
 * Seasons are not maintained here: the season of a battle is computed from its
 * date ({@link org.c2w.data.journal.SeasonCalendar}); a whole season is deleted
 * by filtering the battle list and deleting all its battles.
 *
 * <p>Nothing here changes the Cow2Win guild (members, teams) - assignments only
 * link journal players to existing members. Reading methods never create a
 * journal file; writing methods need an existing journal ({@link JournalException}
 * otherwise).
 */
public final class JournalMaintenanceService {

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
     * Changes the catalog id of a manual name mapping. Applies to future imports
     * (stored logs keep the ids they were imported with).
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

    private synchronized BattleLogParser baseParser() {
        if (baseParser == null) {
            baseParser = parserFactory.get();
        }
        return baseParser;
    }
}
