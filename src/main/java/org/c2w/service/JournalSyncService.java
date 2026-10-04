package org.c2w.service;

import org.c2w.data.journal.BattleLog;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.TeamKind;
import org.c2w.data.journal.db.*;
import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.c2w.infra.Logger;
import org.c2w.service.journal.JournalTeams;
import org.c2w.service.journal.JournalTeams.LogTeam;
import org.c2w.service.journal.JournalTeams.Team;
import org.c2w.service.journal.SyncPlan;
import org.c2w.service.journal.SyncPlan.*;
import org.c2w.service.journal.SyncResult;
import org.c2w.service.journal.SyncRow;
import org.c2w.service.journal.SyncRow.Certainty;
import org.c2w.service.journal.SyncRow.LineupHint;
import org.c2w.service.journal.SyncRow.Target;
import org.c2w.service.journal.SyncRow.UnitsChange;
import org.c2w.service.journal.SyncSelection;
import org.c2w.service.journal.SyncSelection.Choice;
import org.c2w.service.journal.TeamBuildPlan.Composition;
import org.c2w.service.journal.TeamBuildPlan.CompositionSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Keeps the defense teams of the guild up to date from the journal: team power
 * and - if the defense log has them - heroes/titans with pet or totems are taken
 * from the NEWEST battle with a defense log. Two steps, no GUI: {@link #prepare}
 * matches and proposes, {@link #apply} changes the selected teams.
 *
 * <p>Rules (see the decision "only the defense log changes the guild"):
 * <ul>
 *   <li>Only the defense log of the newest battle (by day) with one; the attack log is
 *       never read. Older values never overwrite newer ones.</li>
 *   <li>Only defenders assigned to an existing member; several log names of a member
 *       are merged.</li>
 *   <li>One log team per (fortification, position); the kind comes from the fortification type.</li>
 *   <li>Log team -> stored team of the same kind: first the same heroes/titans
 *       ({@link Certainty#UNITS}), then a one-to-one pairing by power with the smallest sum
 *       of relative deviations {@code |log − stored| / log}: up to {@link #SURE_TOLERANCE}
 *       sure, up to {@link #UNSURE_TOLERANCE} unsure, beyond that no match. Another stored
 *       team within {@link #AMBIGUITY_MARGIN} of the paired one makes the match ambiguous.</li>
 *   <li>No team or member is created; empty teams only get the power; lineups and war
 *       flags are never changed.</li>
 * </ul>
 * {@link #apply} sets {@code lastModified} = battle day and the guild via
 * {@link GuildService} - unsaved afterwards.
 */
public final class JournalSyncService {

    /** Up to this relative deviation a power match is sure - the same constant as "same team" in the team builder. */
    public static final double SURE_TOLERANCE = JournalTeams.SAME_TEAM_POWER_TOLERANCE;

    /** Up to this relative deviation a power match is unsure; beyond it there is no match. */
    public static final double UNSURE_TOLERANCE = 0.10;

    /** Another stored team whose deviation is less than this apart (0.5 percentage points) makes a match ambiguous. */
    public static final double AMBIGUITY_MARGIN = 0.005;

    private final AppContext context;
    private final GuildService guildService;
    private final JournalStore journal;

    public JournalSyncService(AppContext context, GuildService guildService) {
        this(context, guildService, context.journal());
    }

    public JournalSyncService(AppContext context, GuildService guildService, JournalStore journal) {
        this.context = Objects.requireNonNull(context);
        this.guildService = Objects.requireNonNull(guildService);
        this.journal = Objects.requireNonNull(journal);
    }

    // =====================================================================
    // prepare
    // =====================================================================

    /**
     * Reads the defense log of the newest battle that has one and matches its teams
     * with the open guild. Writes nothing; never creates a journal file.
     */
    public SyncPlan prepare() throws JournalException {
        Guild guild = context.guild();
        Path guildFile = context.guildFilePath();
        if (guild == null) {
            return SyncPlan.empty(EmptyReason.NO_GUILD, guildFile);
        }
        Optional<JournalRepository> repo = journal.repository(false);
        if (repo.isEmpty()) {
            return SyncPlan.empty(EmptyReason.NO_JOURNAL, guildFile);
        }
        Optional<BattleSummary> newest = newestDefenseBattle(repo.get());
        if (newest.isEmpty()) {
            return SyncPlan.empty(EmptyReason.NO_DEFENSE_LOG, guildFile);
        }
        BattleSummary battle = newest.get();
        BattleLog defense = repo.get().loadLog(battle.battleId(), LogDirection.DEFENSE).orElseThrow().log();
        LogInfo info = repo.get().listLogs(battle.battleId()).stream()
                .filter(l -> l.direction() == LogDirection.DEFENSE).findFirst().orElse(null);
        Source source = new Source(battle.battleId(), battle.date(), battle.opponent(), battle.result(), battle.status(),
                info == null ? defense.header().language() : info.language(),
                info == null ? defense.header().fileName() : info.fileName(), info == null ? null : info.importedAt());
        SyncPlan plan = plan(source, defense, repo.get().ownAssignmentsByName(), guild, context.lineup(),
                context.catalog(), guildFile);
        Logger.log("Sync: battle of " + battle.date() + " (id " + battle.battleId() + "), " + plan.rows().size()
                + " row(s) " + countByCertainty(plan) + ", " + plan.rows().stream().filter(SyncRow::unchanged).count()
                + " unchanged, " + plan.unmatched().size() + " unmatched, " + plan.skippedPlayers().size()
                + " skipped player(s)");
        return plan;
    }

    /** The day of the newest battle with a defense log - empty without journal or such a battle. Never creates a file. */
    public Optional<LocalDate> newestDefenseBattleDate() throws JournalException {
        Optional<JournalRepository> repo = journal.repository(false);
        return repo.isEmpty() ? Optional.empty() : newestDefenseBattle(repo.get()).map(BattleSummary::date);
    }

    private static Optional<BattleSummary> newestDefenseBattle(JournalRepository repo) throws JournalException {
        // listBattles is newest first (day, then id)
        return repo.listBattles(null).stream().filter(b -> b.directions().contains(LogDirection.DEFENSE)).findFirst();
    }

    private static Map<Certainty, Long> countByCertainty(SyncPlan plan) {
        Map<Certainty, Long> counts = new EnumMap<>(Certainty.class);
        for (Certainty c : Certainty.values()) {
            counts.put(c, plan.count(c));
        }
        return counts;
    }

    /**
     * The sync plan for {@code guild} from one defense log - the core of {@link #prepare},
     * usable with in-memory logs.
     *
     * @param assignments own players' assignments by exact raw name
     * @param lineup      the open lineup for the lineup hints ({@code null}: no hints)
     */
    public static SyncPlan plan(Source source, BattleLog defense, Map<String, PlayerAssignment> assignments, Guild guild,
                                Lineup lineup, Catalog catalog, Path guildFile) {
        if (guild == null) {
            return SyncPlan.empty(EmptyReason.NO_GUILD, guildFile);
        }
        Lineup ownLineup = lineup != null && Objects.equals(lineup.guildId(), guild.id()) ? lineup : null;
        JournalTeams.Defenders defenders = JournalTeams.defenders(List.of(defense), assignments, guild);
        List<SyncRow> rows = new ArrayList<>();
        List<UnmatchedTeam> unmatched = new ArrayList<>();
        List<PowerConflict> conflicts = new ArrayList<>();
        List<TeamRef> notInLog = new ArrayList<>();
        List<GuildMember> members = new ArrayList<>(guild.members());
        members.sort(Comparator.comparing(GuildMember::name, String.CASE_INSENSITIVE_ORDER));
        for (GuildMember member : members) {
            Set<String> names = defenders.logNames().get(member.id());
            if (names == null) {
                continue;
            }
            List<LogTeam> logTeams = JournalTeams.logTeams(defense, names, m -> Logger.log("Sync: " + m));
            for (LogTeam t : logTeams) {
                if (t.powers().size() > 1) {
                    conflicts.add(new PowerConflict(member.id(), t.logName(), t.fortificationId(),
                            t.fortificationName(), t.position(), t.powers()));
                }
            }
            for (TeamKind kind : TeamKind.values()) {
                List<LogTeam> ofKind = logTeams.stream().filter(t -> t.kind() == kind).toList();
                List<Team> stored = JournalTeams.teams(member, kind);
                Map<LogTeam, Match> matches = match(ofKind, stored, source.date());
                for (LogTeam t : ofKind) {
                    Match m = matches.get(t);
                    if (m == null) {
                        unmatched.add(new UnmatchedTeam(member.id(), t.logName(), kind, t.lastPower(),
                                t.fortificationId(), t.fortificationName(), t.position()));
                    } else {
                        rows.add(row(member, t, m, source.date(), ownLineup));
                    }
                }
                Set<Integer> matched = new HashSet<>();
                matches.values().forEach(m -> matched.add(m.index()));
                stored.stream().filter(s -> !matched.contains(s.index()))
                        .forEach(s -> notInLog.add(new TeamRef(member.id(), kind, s.index())));
            }
        }
        List<String> membersNotInLog = guild.members().stream().map(GuildMember::id)
                .filter(id -> !defenders.logNames().containsKey(id)).toList();
        return new SyncPlan(null, source, rows, unmatched, conflicts, notInLog, defenders.skipped(), membersNotInLog,
                guildFile);
    }

    // --- matching ---

    /** A stored team matched to a log team. */
    private record Match(int index, Certainty certainty) {
    }

    /** The log's units as composition (all names resolved), else {@code null}. */
    private static Composition logUnits(LogTeam t, LocalDate date) {
        List<org.c2w.data.journal.FightUnit> units = t.units();
        if (units.isEmpty() || !JournalTeams.allResolved(units)) {
            return null;
        }
        return JournalTeams.fromUnits(units, t.kind(), CompositionSource.DEFENSE_UNITS, date, t.lastPower());
    }

    /**
     * Matches the log teams of one member and kind with its stored teams of that kind: same
     * units first, then the best one-to-one pairing by power (most pairs, then the smallest
     * sum of deviations; ties to the lower index).
     */
    static Map<LogTeam, Match> match(List<LogTeam> logTeams, List<Team> stored, LocalDate date) {
        Map<LogTeam, Match> result = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        for (LogTeam t : logTeams) {
            Composition units = logUnits(t, date);
            if (units == null) {
                continue;
            }
            Set<String> ids = new HashSet<>(units.unitIds());
            stored.stream().filter(s -> !s.empty() && !used.contains(s.index()) && s.unitIds().equals(ids))
                    .findFirst().ifPresent(s -> {
                        used.add(s.index());
                        result.put(t, new Match(s.index(), Certainty.UNITS));
                    });
        }
        List<LogTeam> open = logTeams.stream().filter(t -> !result.containsKey(t)).toList();
        List<Team> free = stored.stream().filter(s -> !used.contains(s.index())).toList();
        int[] best = bestPairing(open, free);
        for (int i = 0; i < open.size(); i++) {
            if (best[i] < 0) {
                continue;
            }
            LogTeam t = open.get(i);
            Team s = free.get(best[i]);
            double deviation = SyncRow.deviation(t.lastPower(), s.power());
            Certainty certainty = deviation <= SURE_TOLERANCE ? Certainty.SURE : Certainty.UNSURE;
            if (deviation > 0) {
                for (int k = 0; k < free.size(); k++) {
                    if (k == best[i] || pairedExactly(k, best, open, free)) {
                        continue;
                    }
                    double other = SyncRow.deviation(t.lastPower(), free.get(k).power());
                    if (other <= UNSURE_TOLERANCE && Math.abs(other - deviation) < AMBIGUITY_MARGIN) {
                        certainty = Certainty.AMBIGUOUS;
                    }
                }
            }
            result.put(t, new Match(s.index(), certainty));
        }
        return result;
    }

    /** True if stored team {@code k} is paired with a log team of exactly its power. */
    private static boolean pairedExactly(int k, int[] pairing, List<LogTeam> open, List<Team> free) {
        for (int i = 0; i < pairing.length; i++) {
            if (pairing[i] == k && open.get(i).lastPower() == free.get(k).power()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The best one-to-one pairing of log teams with stored teams by trying every
     * combination (at most a few teams per member): {@code result[i]} is the stored
     * team of log team i, or -1. Pairs beyond {@link #UNSURE_TOLERANCE} are not allowed.
     */
    static int[] bestPairing(List<LogTeam> logTeams, List<Team> stored) {
        double[][] deviation = new double[logTeams.size()][stored.size()];
        for (int i = 0; i < logTeams.size(); i++) {
            for (int j = 0; j < stored.size(); j++) {
                deviation[i][j] = SyncRow.deviation(logTeams.get(i).lastPower(), stored.get(j).power());
            }
        }
        int[] current = new int[logTeams.size()];
        int[] best = new int[logTeams.size()];
        Arrays.fill(best, -1);
        double[] bestScore = {-1, Double.MAX_VALUE}; // pairs, sum of deviations
        search(0, current, new boolean[stored.size()], 0, 0, deviation, best, bestScore);
        return best;
    }

    private static void search(int i, int[] current, boolean[] taken, int pairs, double sum, double[][] deviation,
                               int[] best, double[] bestScore) {
        if (i == current.length) {
            if (pairs > bestScore[0] || pairs == bestScore[0] && sum < bestScore[1] - 1e-12) {
                bestScore[0] = pairs;
                bestScore[1] = sum;
                System.arraycopy(current, 0, best, 0, current.length);
            }
            return;
        }
        for (int j = 0; j < taken.length; j++) {
            if (!taken[j] && deviation[i][j] <= UNSURE_TOLERANCE) {
                taken[j] = true;
                current[i] = j;
                search(i + 1, current, taken, pairs + 1, sum + deviation[i][j], deviation, best, bestScore);
                taken[j] = false;
            }
        }
        current[i] = -1;
        search(i + 1, current, taken, pairs, sum, deviation, best, bestScore);
    }

    // --- rows ---

    private static SyncRow row(GuildMember member, LogTeam t, Match m, LocalDate date, Lineup lineup) {
        Composition units = logUnits(t, date);
        List<Target> targets = new ArrayList<>();
        if (t.kind() == TeamKind.HERO) {
            for (HeroTeam team : member.heroTeams()) {
                boolean empty = JournalTeams.isEmpty(team);
                UnitsChange change = units == null ? null : heroChange(team, units);
                targets.add(new Target(team.index(), team.totalPower(), team.lastModified(), empty,
                        editedSince(team.lastModified(), date), change, change != null && !empty,
                        lineupHint(lineup, member.id(), t, team.index())));
            }
        } else {
            for (TitanTeam team : member.titanTeams()) {
                boolean empty = team.titans().isEmpty();
                UnitsChange change = units == null ? null : titanChange(team, units);
                targets.add(new Target(team.index(), team.totalPower(), team.lastModified(), empty,
                        editedSince(team.lastModified(), date), change, change != null && !empty,
                        lineupHint(lineup, member.id(), t, team.index())));
            }
        }
        String id = member.id() + "|" + t.kind() + "|" + t.slotKey();
        return new SyncRow(id, member.id(), member.name(), t.logName(), t.kind(), t.fortificationId(),
                t.fortificationName(), t.position(), t.lastPower(), m.certainty(), m.index(), !t.units().isEmpty(),
                units, targets);
    }

    private static boolean editedSince(LocalDate lastModified, LocalDate battleDay) {
        return lastModified != null && battleDay != null && lastModified.isAfter(battleDay);
    }

    private static UnitsChange heroChange(HeroTeam team, Composition units) {
        List<String> before = team.heroes() == null ? List.of()
                : team.heroes().stream().filter(Objects::nonNull).map(Hero::id).toList();
        String petBefore = team.pet() == null ? null : team.pet().id();
        UnitsChange change = new UnitsChange(minus(units.unitIds(), before), minus(before, units.unitIds()),
                petBefore, units.petId(), Set.of(), Set.of());
        return change.unitsChanged() || change.petChanged() ? change : null;
    }

    private static UnitsChange titanChange(TitanTeam team, Composition units) {
        List<String> before = team.titans().stream().filter(Objects::nonNull).map(Titan::id).toList();
        UnitsChange change = new UnitsChange(minus(units.unitIds(), before), minus(before, units.unitIds()),
                null, null, team.totems(), units.totems());
        return change.unitsChanged() || change.totemsChanged() ? change : null;
    }

    private static List<String> minus(List<String> a, List<String> b) {
        return a.stream().filter(x -> !b.contains(x)).toList();
    }

    /** Where the open lineup has the team, if not at the log's fortification. */
    private static LineupHint lineupHint(Lineup lineup, String memberId, LogTeam t, int index) {
        if (lineup == null) {
            return null;
        }
        Lineup.TeamType type = t.kind() == TeamKind.HERO ? Lineup.TeamType.HERO : Lineup.TeamType.TITAN;
        Optional<Lineup.Entry> entry = lineup.entries().stream().filter(e -> memberId.equals(e.teamMemberId())
                && e.teamType() == type && e.teamIndex() == index).findFirst();
        if (entry.isEmpty()) {
            return new LineupHint(LineupHint.Kind.NOT_IN_LINEUP, null);
        }
        if (t.fortificationId() != null && !t.fortificationId().equals(entry.get().fortificationId())) {
            return new LineupHint(LineupHint.Kind.OTHER_FORTIFICATION, entry.get().fortificationId());
        }
        return null;
    }

    // =====================================================================
    // apply
    // =====================================================================

    /**
     * Changes the selected teams of the open guild: power and/or heroes/titans with pet or
     * totems, {@code lastModified} = battle day; everything else (war flags, other teams,
     * lineups) stays. Validates everything first; on any error nothing changes. On success
     * the guild is set via {@link GuildService} and is unsaved.
     */
    public SyncResult apply(SyncPlan plan, SyncSelection selection) {
        Guild guild = context.guild();
        if (guild == null || !plan.hasSource() || !Objects.equals(plan.guildFile(), context.guildFilePath())) {
            return SyncResult.failed(List.of(new SyncResult.Error(SyncResult.Error.Kind.GUILD_CHANGED, null, null)));
        }
        Applied applied = applyTo(guild, plan, selection, context.catalog());
        if (!applied.errors.isEmpty()) {
            return SyncResult.failed(applied.errors);
        }
        if (applied.teams > 0) {
            guildService.updateGuild(applied.guild);
        }
        Logger.log("Sync: " + applied.teams + " team(s) changed (power " + applied.power + ", units " + applied.units
                + "), dropped pets " + applied.droppedPets.size() + ", dropped totems " + applied.droppedTotems.size());
        return new SyncResult(List.of(), applied.teams, applied.power, applied.units, applied.droppedPets,
                applied.droppedTotems);
    }

    /**
     * What {@link #apply} would reject for this selection on {@code guild} - empty if it can be
     * applied. For the dialog, to block "apply" early. Changes nothing.
     */
    public static List<SyncResult.Error> check(Guild guild, SyncPlan plan, SyncSelection selection, Catalog catalog) {
        if (guild == null || !plan.hasSource()) {
            return List.of(new SyncResult.Error(SyncResult.Error.Kind.GUILD_CHANGED, null, null));
        }
        return List.copyOf(applyTo(guild, plan, selection, catalog).errors);
    }

    /** What {@link #applyTo} produced. */
    static final class Applied {
        final List<SyncResult.Error> errors = new ArrayList<>();
        final List<SyncResult.DroppedPet> droppedPets = new ArrayList<>();
        final List<SyncResult.DroppedTotem> droppedTotems = new ArrayList<>();
        Guild guild;
        int teams;
        int power;
        int units;
    }

    /** A selected row with its choice. */
    private record Selected(SyncRow row, Choice choice) {
    }

    /** The guild with the selection applied (no side effects) - errors instead of a guild if invalid. */
    static Applied applyTo(Guild guild, SyncPlan plan, SyncSelection selection, Catalog catalog) {
        Applied result = new Applied();
        Map<String, List<Selected>> byMember = new LinkedHashMap<>();
        Set<String> targets = new HashSet<>();
        for (Map.Entry<String, Choice> e : selection.choices().entrySet()) {
            if (!e.getValue().any()) {
                continue;
            }
            Optional<SyncRow> row = plan.row(e.getKey());
            if (row.isEmpty()) {
                result.errors.add(new SyncResult.Error(SyncResult.Error.Kind.UNKNOWN_ROW, e.getKey(), null));
                continue;
            }
            SyncRow r = row.get();
            Choice c = e.getValue();
            Optional<Target> target = r.target(c.teamIndex());
            if (target.isEmpty()) {
                error(result, SyncResult.Error.Kind.UNKNOWN_TEAM, r);
                continue;
            }
            if (!targets.add(r.memberId() + "|" + r.kind() + "|" + c.teamIndex())) {
                error(result, SyncResult.Error.Kind.TARGET_TWICE, r);
                continue;
            }
            if (c.units() && !target.get().unitsSelectable()) {
                error(result, SyncResult.Error.Kind.UNITS_NOT_SELECTABLE, r);
                continue;
            }
            byMember.computeIfAbsent(r.memberId(), k -> new ArrayList<>()).add(new Selected(r, c));
        }
        Map<String, GuildMember> members = new LinkedHashMap<>();
        guild.members().forEach(m -> members.put(m.id(), m));
        LocalDate day = plan.source().date();
        for (Map.Entry<String, List<Selected>> e : byMember.entrySet()) {
            GuildMember member = members.get(e.getKey());
            if (member == null) {
                result.errors.add(new SyncResult.Error(SyncResult.Error.Kind.UNKNOWN_MEMBER, null, e.getKey()));
                continue;
            }
            GuildMember changed = applyToMember(member, e.getValue(), day, catalog, result);
            if (changed != null) {
                members.put(member.id(), changed);
            }
        }
        if (result.errors.isEmpty()) {
            result.guild = guild.withMembers(new ArrayList<>(members.values()));
        }
        return result;
    }

    private static void error(Applied result, SyncResult.Error.Kind kind, SyncRow row) {
        result.errors.add(new SyncResult.Error(kind, row.id(), row.memberId()));
    }

    /** The member with the selected rows applied, {@code null} on an error. */
    private static GuildMember applyToMember(GuildMember member, List<Selected> selected, LocalDate day,
                                             Catalog catalog, Applied result) {
        List<HeroTeam> heroTeams = new ArrayList<>(member.heroTeams());
        List<TitanTeam> titanTeams = new ArrayList<>(member.titanTeams());
        Set<Integer> heroUnitsTaken = new HashSet<>();
        List<SyncResult.DroppedTotem> droppedTotems = new ArrayList<>();
        int teams = 0;
        int power = 0;
        int units = 0;
        for (Selected s : selected) {
            SyncRow row = s.row();
            Choice c = s.choice();
            int index = c.teamIndex();
            boolean hero = row.kind() == TeamKind.HERO;
            if (index < 0 || index >= (hero ? heroTeams.size() : titanTeams.size())) {
                error(result, SyncResult.Error.Kind.UNKNOWN_TEAM, row);
                return null;
            }
            Composition log = c.units() ? row.logUnits() : null;
            if (hero) {
                HeroTeam old = heroTeams.get(index);
                int newPower = c.power() ? row.logPower() : old.totalPower();
                HeroTeam team = log == null
                        ? new HeroTeam(old.memberId(), index, old.heroes(), old.pet(), old.warFlag(), newPower, day)
                        : JournalTeams.heroTeam(member.id(), index, log.unitIds(), log.petId(), old.warFlag(), newPower,
                        day, catalog, pet -> result.droppedPets.add(
                                new SyncResult.DroppedPet(member.id(), TeamKind.HERO, index, pet)));
                if (team == null) {
                    error(result, SyncResult.Error.Kind.UNKNOWN_CATALOG_ID, row);
                    return null;
                }
                heroTeams.set(index, team);
                if (log != null) {
                    heroUnitsTaken.add(index);
                }
            } else {
                TitanTeam old = titanTeams.get(index);
                int newPower = c.power() ? row.logPower() : old.totalPower();
                TitanTeam team = log == null
                        ? new TitanTeam(old.memberId(), index, old.titans(), newPower, day, old.totems())
                        : JournalTeams.titanTeam(member.id(), index, log.unitIds(), log.totems(), newPower, day,
                        catalog, totem -> droppedTotems.add(
                                new SyncResult.DroppedTotem(member.id(), TeamKind.TITAN, index, totem)));
                if (team == null) {
                    error(result, SyncResult.Error.Kind.UNKNOWN_CATALOG_ID, row);
                    return null;
                }
                titanTeams.set(index, team);
            }
            teams++;
            power += c.power() ? 1 : 0;
            units += log != null ? 1 : 0;
        }
        dropDuplicatePets(member, heroTeams, heroUnitsTaken, result);
        result.droppedTotems.addAll(droppedTotems);
        result.teams += teams;
        result.power += power;
        result.units += units;
        return new GuildMember(member.id(), member.name(), heroTeams, titanTeams);
    }

    /**
     * A pet may be used only once per member: where a team that took over the log's units got
     * a pet another hero team has, that team keeps its previous pet (if still free) or none,
     * and the log's pet is reported as dropped.
     */
    private static void dropDuplicatePets(GuildMember member, List<HeroTeam> heroTeams, Set<Integer> unitsTaken,
                                          Applied result) {
        for (int round = 0; round <= heroTeams.size(); round++) {
            Map<String, List<Integer>> byPet = new HashMap<>();
            for (HeroTeam t : heroTeams) {
                if (t.pet() != null) {
                    byPet.computeIfAbsent(t.pet().id(), k -> new ArrayList<>()).add(t.index());
                }
            }
            Optional<Integer> conflict = byPet.values().stream().filter(l -> l.size() > 1).flatMap(List::stream)
                    .filter(i -> unitsTaken.contains(i) && !samePet(heroTeams.get(i), member.heroTeams().get(i)))
                    .findFirst();
            if (conflict.isEmpty()) {
                return;
            }
            int index = conflict.get();
            HeroTeam t = heroTeams.get(index);
            Pet previous = member.heroTeams().get(index).pet();
            boolean previousFree = previous != null && heroTeams.stream()
                    .noneMatch(o -> o.index() != index && o.pet() != null && o.pet().id().equals(previous.id()));
            result.droppedPets.add(new SyncResult.DroppedPet(member.id(), TeamKind.HERO, index, t.pet().id()));
            heroTeams.set(index, new HeroTeam(t.memberId(), index, t.heroes(), previousFree ? previous : null,
                    t.warFlag(), t.totalPower(), t.lastModified()));
        }
    }

    private static boolean samePet(HeroTeam a, HeroTeam b) {
        return Objects.equals(a.pet() == null ? null : a.pet().id(), b.pet() == null ? null : b.pet().id());
    }
}
