package org.c2w.gui.journal;

import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.db.OwnPlayerStats;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;

import java.util.*;

/**
 * The player assignments of the own guild, prepared for the players dialog -
 * Swing-free. Rows are the journal players (names exactly as in the log) with
 * status, assigned member, defense statistics and problems; filters "only open"
 * and "only problems". Hints list members of the open guild without any journal
 * player, and members missing from the latest defense logs (possible typo, rename
 * or left the guild). Only shows - the guild is never changed here.
 */
public final class JournalPlayersModel {

    /** Number of latest defense logs a member should appear in. */
    public static final int RECENT_DEFENSE_LOGS = 3;

    /**
     * One journal player.
     *
     * @param stats         player, assignment and defense statistics
     * @param memberName    name of the assigned member, {@code null} if not assigned or the member is gone
     * @param memberMissing assigned to a member id the guild no longer has
     * @param sameMember    number of players assigned to the same member (former names), 0 if not assigned
     */
    public record Row(OwnPlayerStats stats, String memberName, boolean memberMissing, int sameMember) {

        public AssignmentStatus status() {
            return stats.status();
        }

        /** Open, or assigned to a member that no longer exists. */
        public boolean hasProblem() {
            return status() == AssignmentStatus.OPEN || memberMissing;
        }
    }

    /** Why a member is listed in the hints. */
    public enum HintKind {
        /** No journal player is assigned to the member. */
        NO_PLAYER,
        /** The member's players do not defend in any of the latest defense logs. */
        NOT_IN_RECENT_DEFENSES
    }

    /** A member of the open guild worth a look. */
    public record MemberHint(GuildMember member, HintKind kind) {
    }

    /**
     * The data of the dialog.
     *
     * @param hasJournal       false if the guild has no journal (nothing was created)
     * @param players          the own-guild players
     * @param recentDefenders  player ids defending in the latest defense logs
     * @param recentLogs       number of defense logs considered (0 = no defense log at all)
     */
    public record Data(boolean hasJournal, List<OwnPlayerStats> players, Set<Integer> recentDefenders, int recentLogs) {
        public static final Data EMPTY = new Data(false, List.of(), Set.of(), 0);

        public Data {
            players = List.copyOf(players);
            recentDefenders = Set.copyOf(recentDefenders);
        }
    }

    private final Data data;
    private final Guild guild;
    private final List<Row> rows;
    private boolean onlyOpen;
    private boolean onlyProblems;

    public JournalPlayersModel(Data data, Guild guild) {
        this.data = Objects.requireNonNull(data);
        this.guild = guild;
        Map<String, String> names = new HashMap<>();
        if (guild != null) {
            guild.members().forEach(m -> names.put(m.id(), m.displayName()));
        }
        Map<String, Integer> perMember = new HashMap<>();
        for (OwnPlayerStats p : data.players()) {
            if (p.memberId() != null) {
                perMember.merge(p.memberId(), 1, Integer::sum);
            }
        }
        List<Row> list = new ArrayList<>();
        for (OwnPlayerStats p : data.players()) {
            String memberId = p.memberId();
            String name = memberId == null ? null : names.get(memberId);
            list.add(new Row(p, name, memberId != null && name == null,
                    memberId == null ? 0 : perMember.get(memberId)));
        }
        this.rows = List.copyOf(list);
    }

    /** Reads the players of the open guild's journal - never creates a journal file. */
    public static Data load(Optional<JournalRepository> repository) throws JournalException {
        if (repository.isEmpty()) {
            return Data.EMPTY;
        }
        JournalRepository repo = repository.get();
        JournalRepository.RecentDefenders recent = repo.recentDefenders(RECENT_DEFENSE_LOGS);
        return new Data(true, repo.listOwnPlayerStats(), recent.playerIds(), recent.battleDays().size());
    }

    public Data data() {
        return data;
    }

    public void setOnlyOpen(boolean onlyOpen) {
        this.onlyOpen = onlyOpen;
    }

    public void setOnlyProblems(boolean onlyProblems) {
        this.onlyProblems = onlyProblems;
    }

    public boolean onlyOpen() {
        return onlyOpen;
    }

    public boolean onlyProblems() {
        return onlyProblems;
    }

    /** All rows. */
    public List<Row> allRows() {
        return rows;
    }

    /** The rows passing the filters. */
    public List<Row> rows() {
        return rows.stream()
                .filter(r -> !onlyOpen || r.status() == AssignmentStatus.OPEN)
                .filter(r -> !onlyProblems || r.hasProblem())
                .toList();
    }

    /**
     * Members of the open guild without an assigned journal player, then members whose
     * players are missing from the latest defense logs (only if there is a defense log).
     */
    public List<MemberHint> memberHints() {
        if (guild == null || !data.hasJournal()) {
            return List.of();
        }
        Map<String, List<OwnPlayerStats>> playersByMember = new HashMap<>();
        for (OwnPlayerStats p : data.players()) {
            if (p.memberId() != null) {
                playersByMember.computeIfAbsent(p.memberId(), k -> new ArrayList<>()).add(p);
            }
        }
        List<MemberHint> noPlayer = new ArrayList<>();
        List<MemberHint> notRecent = new ArrayList<>();
        for (GuildMember member : guild.members()) {
            List<OwnPlayerStats> players = playersByMember.get(member.id());
            if (players == null) {
                noPlayer.add(new MemberHint(member, HintKind.NO_PLAYER));
            } else if (data.recentLogs() > 0
                    && players.stream().noneMatch(p -> data.recentDefenders().contains(p.player().id()))) {
                notRecent.add(new MemberHint(member, HintKind.NOT_IN_RECENT_DEFENSES));
            }
        }
        List<MemberHint> hints = new ArrayList<>(noPlayer);
        hints.addAll(notRecent);
        return hints;
    }

    /** The members of the open guild, by name - choices for "assign to". */
    public List<GuildMember> members() {
        if (guild == null) {
            return List.of();
        }
        List<GuildMember> members = new ArrayList<>(guild.members());
        members.sort(Comparator.comparing(GuildMember::displayName, String.CASE_INSENSITIVE_ORDER));
        return members;
    }
}
