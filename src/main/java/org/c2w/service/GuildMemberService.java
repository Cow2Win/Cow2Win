package org.c2w.service;

import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalPlayer;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.model.TitanTeam;
import org.c2w.data.repository.LineupRepository;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adding and deleting members of the open guild - from the member overview of the input stage.
 * Both save right away (like deleting a team in the team assignment); other unsaved changes of
 * the guild are saved with them.
 *
 * <p>Deleting cleans up: the member's entries disappear from every lineup of the guild (the files
 * and the open lineup), and its players in the Weltenschlacht journal become "former member".
 * A failure there does not stop the rest - it is collected in the {@link RemovalResult}.
 */
public final class GuildMemberService {

    private final AppContext context;
    private final GuildService guildService;

    public GuildMemberService(AppContext context) {
        this(context, new GuildService(context));
    }

    GuildMemberService(AppContext context, GuildService guildService) {
        this.context = context;
        this.guildService = guildService;
    }

    // --- add ---

    /** True if the guild has room for one more member (see {@link Guild#MAX_MEMBERS}). */
    public boolean canAddMember() {
        return members().size() < Guild.MAX_MEMBERS;
    }

    /** True if a member has {@code name} as id or display name (ignoring case). */
    public boolean isTaken(String name) {
        String trimmed = name == null ? "" : name.trim();
        return members().stream().anyMatch(m -> m.id().equalsIgnoreCase(trimmed)
                || m.displayName().equalsIgnoreCase(trimmed));
    }

    /**
     * Adds a member named {@code name} (trimmed; its id is the name), saves the guild and writes
     * "member added" to the guild log.
     *
     * @throws IllegalArgumentException for a blank or taken name, or if the guild is full
     * @throws IOException              if the guild could not be saved - then nothing changed
     */
    public GuildMember addMember(String name) throws IOException {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A member needs a name");
        }
        if (!canAddMember()) {
            throw new IllegalArgumentException("A guild has at most " + Guild.MAX_MEMBERS + " members");
        }
        if (isTaken(trimmed)) {
            throw new IllegalArgumentException("There is already a member '" + trimmed + "'");
        }
        GuildMember member = new GuildMember(trimmed, trimmed, List.of(), List.of());
        List<GuildMember> members = new ArrayList<>(members());
        members.add(member);
        guildService.saveGuild(context.guild().withMembers(members), GuildService.SaveOrigin.MEMBER_OVERVIEW);
        GuildLog.event(guildDir(), "guildLog.memberAdded", member.displayName());
        Logger.log("Member added: " + member.id());
        return member;
    }

    // --- delete ---

    /**
     * What deleting a member affects - for the confirmation.
     *
     * @param heroTeams  the member's hero teams with content
     * @param titanTeams the member's titan teams with content
     * @param entries    its entries over every lineup file of the guild
     * @param lineups    the lineup files with at least one of them
     */
    public record RemovalImpact(String displayName, int heroTeams, int titanTeams, int entries, int lineups) {
    }

    /**
     * What deleting did besides removing the member.
     *
     * @param removedEntries lineup entries removed from the files
     * @param changedLineups lineup files written
     * @param formerPlayers  journal players set to "former member"
     * @param failures       files (or "journal") that could not be cleaned up - empty if all went well
     */
    public record RemovalResult(int removedEntries, int changedLineups, int formerPlayers, List<String> failures) {
    }

    /** What deleting the member {@code memberId} would affect. */
    public RemovalImpact impactOf(String memberId) {
        GuildMember member = member(memberId);
        int entries = 0;
        int lineups = 0;
        for (String fileName : LineupService.listLineupFileNames(guildDir())) {
            try {
                long count = LineupRepository.load(guildDir().resolve(fileName)).entries().stream()
                        .filter(e -> memberId.equals(e.teamMemberId())).count();
                entries += (int) count;
                lineups += count > 0 ? 1 : 0;
            } catch (IOException e) {
                Logger.logException("Could not read " + fileName + " to count the entries of member " + memberId, e);
            }
        }
        int heroTeams = (int) member.heroTeams().stream().filter(GuildMemberService::hasContent).count();
        int titanTeams = (int) member.titanTeams().stream().filter(GuildMemberService::hasContent).count();
        return new RemovalImpact(member.displayName(), heroTeams, titanTeams, entries, lineups);
    }

    /**
     * Deletes the member {@code memberId}, in this order: removes it from the guild and saves
     * the guild; removes its entries from every lineup file of the guild (written only if
     * changed); removes them from the open lineup too (it stays unsaved if it was, otherwise it
     * still matches its file); sets its journal players to "former member" (if there is a journal).
     *
     * @throws IOException if the guild could not be saved - then nothing changed
     */
    public RemovalResult removeMember(String memberId) throws IOException {
        GuildMember member = member(memberId);
        List<GuildMember> members = new ArrayList<>(members());
        members.remove(member);
        guildService.saveGuild(context.guild().withMembers(members), GuildService.SaveOrigin.MEMBER_OVERVIEW);
        GuildLog.event(guildDir(), "guildLog.memberRemoved", member.displayName());
        Logger.log("Member removed: " + memberId);

        List<String> failures = new ArrayList<>();
        int removedEntries = 0;
        int changedLineups = 0;
        for (String fileName : LineupService.listLineupFileNames(guildDir())) {
            Path file = guildDir().resolve(fileName);
            try {
                Lineup lineup = LineupRepository.load(file);
                Lineup cleaned = withoutMember(lineup, memberId);
                int removed = lineup.entries().size() - cleaned.entries().size();
                if (removed > 0) {
                    LineupRepository.save(cleaned, file);
                    removedEntries += removed;
                    changedLineups++;
                    Logger.log("Removed " + removed + " entries of member " + memberId + " from " + file);
                }
            } catch (IOException | RuntimeException e) {
                failures.add(fileName);
                Logger.logException("Could not remove the entries of member " + memberId + " from " + file, e);
            }
        }
        cleanOpenLineup(memberId, failures);
        int formerPlayers = setJournalPlayersFormer(memberId, failures);
        return new RemovalResult(removedEntries, changedLineups, formerPlayers, List.copyOf(failures));
    }

    /** The open lineup without the member's entries - unsaved stays unsaved, saved stays saved (the file is clean already). */
    private void cleanOpenLineup(String memberId, List<String> failures) {
        Lineup open = context.lineup();
        if (open == null) {
            return;
        }
        Lineup cleaned = withoutMember(open, memberId);
        if (cleaned.entries().size() == open.entries().size()) {
            return;
        }
        Path openFile = context.lineupFilePath();
        boolean fileFailed = openFile != null && openFile.getFileName() != null
                && failures.contains(openFile.getFileName().toString());
        if (context.isLineupDirty() || fileFailed || openFile == null) {
            context.setLineup(cleaned);
            context.setLineupDirty(true);
        } else {
            context.set(cleaned, openFile);
            context.markLineupSaved();
        }
    }

    /** Sets every journal player assigned to the member to "former member"; returns how many. */
    private int setJournalPlayersFormer(String memberId, List<String> failures) {
        try {
            Optional<JournalRepository> repository = context.journal().repository(false);
            if (repository.isEmpty()) {
                return 0;
            }
            List<JournalPlayer> players = repository.get().findPlayersByMember(memberId);
            for (JournalPlayer player : players) {
                repository.get().setAssignment(player.id(), null, AssignmentStatus.FORMER);
            }
            if (!players.isEmpty()) {
                Logger.log("Journal: " + players.size() + " player(s) of member " + memberId + " set to former member");
            }
            return players.size();
        } catch (JournalException | RuntimeException e) {
            failures.add("journal");
            Logger.logException("Could not set the journal players of member " + memberId + " to former member", e);
            return 0;
        }
    }

    private static Lineup withoutMember(Lineup lineup, String memberId) {
        List<Lineup.Entry> entries = lineup.entries().stream().filter(e -> !memberId.equals(e.teamMemberId())).toList();
        return new Lineup(lineup.guildId(), lineup.guildName(), lineup.algorithmName(), lineup.createdAt(), entries);
    }

    private static boolean hasContent(HeroTeam team) {
        return team != null && (team.totalPower() > 0 || (team.heroes() != null && !team.heroes().isEmpty()));
    }

    private static boolean hasContent(TitanTeam team) {
        return team != null && (team.totalPower() > 0 || (team.titans() != null && !team.titans().isEmpty()));
    }

    private List<GuildMember> members() {
        Guild guild = context.guild();
        return guild == null || guild.members() == null ? List.of() : guild.members();
    }

    private GuildMember member(String memberId) {
        return members().stream().filter(m -> m.id().equals(memberId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No member '" + memberId + "'"));
    }

    private Path guildDir() {
        return context.guildFilePath().getParent();
    }
}
