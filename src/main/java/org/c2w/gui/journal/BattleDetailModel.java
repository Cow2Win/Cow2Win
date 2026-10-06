package org.c2w.gui.journal;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogCheck;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;

import java.util.*;

/**
 * Everything the battle detail shows, prepared without Swing: the battle's head
 * data, both logs grouped by fortification (in file order) with every single
 * fight seen from OUR side - in the defense log our player is the defender and a
 * fight is "held" or "fallen", in the attack log our player is the attacker and a
 * fight is "won" or "lost" ({@link Fight#attackerWins()} is always the attacker's
 * view, so it is flipped for the defense log) - an overview per fortification and
 * direction, and the parse problems.
 *
 * <p>Own players are shown with their assignment: "log name → member", or the
 * status, or a hint that the assigned member no longer exists in the guild.
 */
public final class BattleDetailModel {

    /** A single fight's outcome from our point of view. */
    public enum Outcome {
        /** Defense: our defender held. */
        HELD,
        /** Defense: our defender was beaten. */
        FELL,
        /** Attack: our attacker won. */
        WON,
        /** Attack: our attacker lost. */
        LOST;

        /** True if the outcome is good for us. */
        public boolean good() {
            return this == HELD || this == WON;
        }
    }

    /**
     * One of our players as named in the log, with its journal assignment.
     *
     * @param rawName       name exactly as in the log
     * @param status        assignment status ({@link AssignmentStatus#OPEN} without one)
     * @param memberId      assigned member id, {@code null} unless assigned
     * @param memberName    name of that member in the open guild, {@code null} if it no longer exists
     */
    public record OwnPlayer(String rawName, AssignmentStatus status, String memberId, String memberName) {

        /** True if the player is assigned to a member id the guild no longer has. */
        public boolean memberMissing() {
            return status == AssignmentStatus.ASSIGNED && memberName == null;
        }

        /** E.g. "Puschel → Puschel", "Vale·· (open)", "Old (member no longer exists)". */
        public String label() {
            String name = JournalTexts.visibleSpaces(rawName);
            if (status == AssignmentStatus.ASSIGNED) {
                return memberName == null
                        ? JournalTexts.text("journal.detail.player.memberMissing", name)
                        : JournalTexts.text("journal.detail.player.assigned", name, memberName);
            }
            return JournalTexts.text("journal.detail.player.status", name,
                    JournalTexts.of("assignmentStatus", status));
        }
    }

    /**
     * One single fight from our point of view.
     *
     * @param position        position in the fortification
     * @param teamKind        hero or titan fight, {@code null} if the log has no units
     * @param ourPlayer       our attacker (attack log) or defender (defense log)
     * @param ourLevel        our player's level
     * @param ourPower        our team's power
     * @param opponentName    the opponent's player (raw name)
     * @param opponentLevel   the opponent's level
     * @param opponentPower   the opponent's team power
     * @param outcome         outcome for us
     * @param points          points of the row (for the attacker - in the defense log the opponent's)
     * @param buff            the defender's fortification buff, {@code null} if none
     * @param ourUnits        our team's units, empty if the log has none
     * @param opponentUnits   the opponent's units, empty if the log has none
     * @param lineNumber      line in the file
     */
    public record FightRow(int position, TeamKind teamKind, OwnPlayer ourPlayer, int ourLevel, int ourPower,
                           String opponentName, int opponentLevel, int opponentPower, Outcome outcome, int points,
                           DefenseBuff buff, List<FightUnit> ourUnits, List<FightUnit> opponentUnits, int lineNumber) {

        /** True if the log contains the teams of this fight. */
        public boolean hasUnits() {
            return !ourUnits.isEmpty() || !opponentUnits.isEmpty();
        }
    }

    /**
     * The rows of one fortification in one log, in file order.
     *
     * @param fortificationId   catalog id, {@code null} if unknown
     * @param fortificationName raw name from the log
     * @param buff              the defender's buff (of the first fight with one), {@code null} if none
     * @param undefended        positions captured without a fight
     * @param totalPositions    positions of the fortification if the log says so, else {@code null}
     * @param undefendedPoints  points for the positions captured without a fight
     * @param captured          true if the fortification was captured
     * @param capturePoints     capture bonus
     * @param fights            the single fights
     */
    public record FortGroup(String fortificationId, String fortificationName, DefenseBuff buff, int undefended,
                            Integer totalPositions, int undefendedPoints, boolean captured, int capturePoints,
                            List<FightRow> fights) {

        /** Display name: catalog name in the display language, else the raw name. */
        public String displayName() {
            return JournalTexts.fortification(fortificationId, fortificationName);
        }

        /** Sum of all points of this fortification (fights, undefended positions, capture bonus). */
        public int points() {
            return fights.stream().mapToInt(FightRow::points).sum() + undefendedPoints + capturePoints;
        }

        /** Fights that went well for us (held / won). */
        public int goodFights() {
            return (int) fights.stream().filter(f -> f.outcome().good()).count();
        }

        /** Fights that went badly for us (fallen / lost). */
        public int badFights() {
            return fights.size() - goodFights();
        }

        /** Number of positions: as stated by the log, else the distinct positions fought at plus undefended ones. */
        public int positions() {
            if (totalPositions != null) {
                return totalPositions;
            }
            return (int) fights.stream().mapToInt(FightRow::position).distinct().count() + undefended;
        }
    }

    /**
     * One stored log, prepared.
     *
     * @param direction attack or defense
     * @param info      bookkeeping data (file, language, import, parser version)
     * @param forts     fortifications in order of their first row
     */
    public record DirectionView(LogDirection direction, LogInfo info, List<FortGroup> forts) {

        public List<FightRow> fights() {
            return forts.stream().flatMap(f -> f.fights().stream()).toList();
        }

        public int goodFights() {
            return forts.stream().mapToInt(FortGroup::goodFights).sum();
        }

        public int badFights() {
            return forts.stream().mapToInt(FortGroup::badFights).sum();
        }

        /** Sum of all points - equals the log's total. */
        public int points() {
            return forts.stream().mapToInt(FortGroup::points).sum();
        }

        /** The captured fortifications (defense: the ones we lost). */
        public List<FortGroup> capturedForts() {
            return forts.stream().filter(FortGroup::captured).toList();
        }

        /** True if any fight of this log carries units. */
        public boolean hasUnits() {
            return fights().stream().anyMatch(FightRow::hasUnits);
        }
    }

    /** One parse problem of one log. */
    public record ProblemRow(LogDirection direction, int lineNumber, String line, String reason) {
    }

    private final BattleSummary battle;
    private final List<LogInfo> logs;
    private final Map<LogDirection, DirectionView> views = new EnumMap<>(LogDirection.class);
    private final List<ProblemRow> problems = new ArrayList<>();
    private final BattleLogCheck.Result check;
    private final GuildRef ownGuild;

    /**
     * @param battle      the battle's list row
     * @param logs        bookkeeping data of its stored logs
     * @param parsed      the stored logs as loaded ({@code JournalRepository#loadLog})
     * @param assignments own players' assignments by exact raw name
     * @param guild       the open guild (member names), may be {@code null}
     */
    public BattleDetailModel(BattleSummary battle, List<LogInfo> logs, Map<LogDirection, BattleLogParseResult> parsed,
                             Map<String, PlayerAssignment> assignments, Guild guild) {
        this.battle = Objects.requireNonNull(battle);
        this.logs = List.copyOf(logs);
        Map<String, String> memberNames = new HashMap<>();
        if (guild != null) {
            for (GuildMember m : guild.members()) {
                memberNames.put(m.id(), m.displayName());
            }
        }
        GuildRef own = null;
        for (LogDirection direction : List.of(LogDirection.DEFENSE, LogDirection.ATTACK)) {
            BattleLogParseResult result = parsed.get(direction);
            if (result == null) {
                continue;
            }
            own = result.log().header().ownGuild();
            LogInfo info = logs.stream().filter(l -> l.direction() == direction).findFirst().orElse(null);
            views.put(direction, new DirectionView(direction, info,
                    group(direction, result.log(), assignments, memberNames)));
            for (ParseProblem p : result.problems()) {
                problems.add(new ProblemRow(direction, p.lineNumber(), p.line(), p.reason()));
            }
        }
        this.ownGuild = own;
        BattleLogParseResult attack = parsed.get(LogDirection.ATTACK);
        BattleLogParseResult defense = parsed.get(LogDirection.DEFENSE);
        this.check = BattleLogCheck.check(attack == null ? null : attack.log(), defense == null ? null : defense.log());
    }

    /** Reads everything for one battle; empty if the battle no longer exists. */
    public static Optional<BattleDetailModel> load(JournalRepository repo, int battleId, Guild guild)
            throws JournalException {
        Optional<BattleSummary> battle = repo.findBattleSummary(battleId);
        if (battle.isEmpty()) {
            return Optional.empty();
        }
        Map<LogDirection, BattleLogParseResult> parsed = new EnumMap<>(LogDirection.class);
        for (LogDirection direction : LogDirection.values()) {
            repo.loadLog(battleId, direction).ifPresent(r -> parsed.put(direction, r));
        }
        return Optional.of(new BattleDetailModel(battle.get(), repo.listLogs(battleId), parsed,
                repo.ownAssignmentsByName(), guild));
    }

    private static List<FortGroup> group(LogDirection direction, BattleLog log, Map<String, PlayerAssignment> assignments,
                                         Map<String, String> memberNames) {
        Map<String, FortDraft> forts = new LinkedHashMap<>();
        for (BattleLogEntry entry : log.entries()) {
            switch (entry) {
                case Fight f -> {
                    FortDraft fort = forts.computeIfAbsent(fortKey(f.fortificationId(), f.fortificationName()),
                            k -> new FortDraft(f.fortificationId(), f.fortificationName()));
                    fort.fights.add(row(direction, f, assignments, memberNames));
                    if (fort.buff == null && f.defender().buff() != null) {
                        fort.buff = f.defender().buff();
                    }
                }
                case FortEvent e -> {
                    FortDraft fort = forts.computeIfAbsent(fortKey(e.fortificationId(), e.fortificationName()),
                            k -> new FortDraft(e.fortificationId(), e.fortificationName()));
                    if (e.kind() == FortEventKind.UNDEFENDED) {
                        fort.undefended += e.freePositions() == null ? 0 : e.freePositions();
                        fort.undefendedPoints += e.points();
                        if (e.totalPositions() != null) {
                            fort.totalPositions = e.totalPositions();
                        }
                    } else {
                        fort.captured = true;
                        fort.capturePoints += e.points();
                    }
                }
            }
        }
        return forts.values().stream().map(FortDraft::toGroup).toList();
    }

    private static String fortKey(String id, String name) {
        return id != null ? "id:" + id : "name:" + name;
    }

    private static FightRow row(LogDirection direction, Fight f, Map<String, PlayerAssignment> assignments,
                                Map<String, String> memberNames) {
        boolean defense = direction == LogDirection.DEFENSE;
        FightSide ours = defense ? f.defender() : f.attacker();
        FightSide theirs = defense ? f.attacker() : f.defender();
        Outcome outcome = defense ? (f.attackerWins() ? Outcome.FELL : Outcome.HELD)
                : (f.attackerWins() ? Outcome.WON : Outcome.LOST);
        return new FightRow(f.position(), f.teamKind(), ownPlayer(ours.playerName(), assignments, memberNames),
                ours.level(), ours.teamPower(), theirs.playerName(), theirs.level(), theirs.teamPower(), outcome,
                f.points(), f.defender().buff(), ours.units(), theirs.units(), f.lineNumber());
    }

    /** Our player with its assignment and - if assigned - the member's current name. */
    static OwnPlayer ownPlayer(String rawName, Map<String, PlayerAssignment> assignments,
                               Map<String, String> memberNames) {
        PlayerAssignment a = assignments.get(rawName);
        if (a == null) {
            return new OwnPlayer(rawName, AssignmentStatus.OPEN, null, null);
        }
        String memberId = a.status() == AssignmentStatus.ASSIGNED ? a.memberId() : null;
        return new OwnPlayer(rawName, a.status(), memberId, memberId == null ? null : memberNames.get(memberId));
    }

    private static final class FortDraft {
        final String id;
        final String name;
        final List<FightRow> fights = new ArrayList<>();
        DefenseBuff buff;
        int undefended;
        Integer totalPositions;
        int undefendedPoints;
        boolean captured;
        int capturePoints;

        FortDraft(String id, String name) {
            this.id = id;
            this.name = name;
        }

        FortGroup toGroup() {
            return new FortGroup(id, name, buff, undefended, totalPositions, undefendedPoints, captured, capturePoints,
                    fights);
        }
    }

    // --- accessors ---

    public BattleSummary battle() {
        return battle;
    }

    /** The own guild as named in the logs, {@code null} without logs. */
    public GuildRef ownGuild() {
        return ownGuild;
    }

    public List<LogInfo> logs() {
        return logs;
    }

    /** The defense log, prepared - empty if the battle has none (then the view shows a hint + import). */
    public Optional<DirectionView> defense() {
        return Optional.ofNullable(views.get(LogDirection.DEFENSE));
    }

    /** The attack log, prepared (display only) - empty if the battle has none. */
    public Optional<DirectionView> attack() {
        return Optional.ofNullable(views.get(LogDirection.ATTACK));
    }

    public Optional<DirectionView> view(LogDirection direction) {
        return Optional.ofNullable(views.get(direction));
    }

    /** Ranking point check of both logs. */
    public BattleLogCheck.Result check() {
        return check;
    }

    /** Parse problems of both logs, defense first. */
    public List<ProblemRow> problems() {
        return List.copyOf(problems);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    /**
     * One row of the fortification overview.
     *
     * @param direction  attack or defense
     * @param fort       the fortification group
     */
    public record FortOverviewRow(LogDirection direction, FortGroup fort) {
    }

    /** All fortifications of both logs, defense first. */
    public List<FortOverviewRow> fortOverview() {
        List<FortOverviewRow> rows = new ArrayList<>();
        for (LogDirection direction : List.of(LogDirection.DEFENSE, LogDirection.ATTACK)) {
            DirectionView view = views.get(direction);
            if (view != null) {
                view.forts().forEach(f -> rows.add(new FortOverviewRow(direction, f)));
            }
        }
        return rows;
    }
}
