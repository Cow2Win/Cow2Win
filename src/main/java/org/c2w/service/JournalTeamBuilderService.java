package org.c2w.service;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.*;
import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.c2w.infra.Logger;
import org.c2w.service.journal.JournalTeams;
import org.c2w.service.journal.JournalTeams.LogTeam;
import org.c2w.service.journal.JournalTeams.Team;
import org.c2w.service.journal.TeamBuildPlan;
import org.c2w.service.journal.TeamBuildPlan.*;
import org.c2w.service.journal.TeamBuildResult;
import org.c2w.service.journal.TeamBuildSelection;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Builds the defense teams of guild members from the journal - for a guild
 * that starts empty (or has gaps) and imports battle logs first. Two steps, no
 * GUI: {@link #prepare} proposes, {@link #apply} adds the selected teams.
 *
 * <p>Rules (see the decision "only the defense log changes the guild", with its
 * exception "exact power"):
 * <ul>
 *   <li>Members and teams come from the DEFENSE logs: our defenders, the team kind
 *       from the fortification type, the team power, fortification + position.</li>
 *   <li>The source battle of a member is the newest battle it defends in; every
 *       (fortification, position) there is one proposal. Teams seen only in older
 *       battles are extra proposals, not preselected.</li>
 *   <li>Composition: the defender's units in the defense log, else an attack team
 *       of the same player in the SAME battle with EXACTLY the same team power
 *       (5 resolved heroes or titans) - no tolerance; else power only.</li>
 *   <li>Teams with heroes/titans are never changed; only missing teams are added
 *       and empty teams (no heroes/titans) filled. Lineups are not touched.</li>
 * </ul>
 * {@link #apply} sets the guild via {@link GuildService} - unsaved afterwards.
 */
public final class JournalTeamBuilderService {

    /** A team power within this share counts as the same team (matching, not composition). */
    public static final double SAME_TEAM_POWER_TOLERANCE = JournalTeams.SAME_TEAM_POWER_TOLERANCE;

    /** Units of a complete team in a log. */
    public static final int TEAM_SIZE = JournalTeams.TEAM_SIZE;

    private final AppContext context;
    private final GuildService guildService;
    private final JournalStore journal;

    public JournalTeamBuilderService(AppContext context, GuildService guildService) {
        this(context, guildService, context.journal());
    }

    public JournalTeamBuilderService(AppContext context, GuildService guildService, JournalStore journal) {
        this.context = Objects.requireNonNull(context);
        this.guildService = Objects.requireNonNull(guildService);
        this.journal = Objects.requireNonNull(journal);
    }

    /** One battle's logs as input for {@link #plan}. */
    public record BattleLogs(int battleId, LocalDate date, BattleLog defense, BattleLog attack) {
    }

    // =====================================================================
    // prepare
    // =====================================================================

    /**
     * Reads all battles with a defense log and proposes teams for the members of the
     * open guild. Writes nothing; never creates a journal file.
     */
    public TeamBuildPlan prepare() throws JournalException {
        Guild guild = context.guild();
        Path guildFile = context.guildFilePath();
        Optional<JournalRepository> repo = journal.repository(false);
        if (guild == null || repo.isEmpty()) {
            return plan(List.of(), Map.of(), guild, guildFile);
        }
        List<BattleLogs> battles = new ArrayList<>();
        for (BattleSummary b : repo.get().listBattles(null)) {
            if (!b.directions().contains(LogDirection.DEFENSE)) {
                continue;
            }
            BattleLog defense = repo.get().loadLog(b.battleId(), LogDirection.DEFENSE).orElseThrow().log();
            BattleLog attack = repo.get().loadLog(b.battleId(), LogDirection.ATTACK)
                    .map(BattleLogParseResult::log).orElse(null);
            battles.add(new BattleLogs(b.battleId(), b.date(), defense, attack));
        }
        TeamBuildPlan plan = plan(battles, repo.get().ownAssignmentsByName(), guild, guildFile);
        Logger.log("Team builder: " + battles.size() + " defense log(s), " + plan.members().size() + " member(s), "
                + plan.proposals().size() + " proposal(s) " + plan.countBySource());
        return plan;
    }

    /** True if the open guild has a member without any team - the import result then offers the team builder. */
    public static boolean hasMembersWithoutTeams(Guild guild) {
        return guild != null && guild.members().stream()
                .anyMatch(m -> m.heroTeams().isEmpty() && m.titanTeams().isEmpty());
    }

    /**
     * The proposals for {@code guild} from {@code battles} (any order) - the core of
     * {@link #prepare}, usable with in-memory logs.
     *
     * @param assignments own players' assignments by exact raw name
     */
    public static TeamBuildPlan plan(List<BattleLogs> battles, Map<String, PlayerAssignment> assignments, Guild guild,
                                     Path guildFile) {
        if (guild == null) {
            return new TeamBuildPlan(List.of(), List.of(), List.of(), 0, guildFile);
        }
        List<BattleLogs> newestFirst = new ArrayList<>(battles);
        newestFirst.sort(Comparator.comparing(BattleLogs::date).thenComparingInt(BattleLogs::battleId).reversed());
        Map<String, GuildMember> members = new LinkedHashMap<>();
        guild.members().forEach(m -> members.put(m.id(), m));

        // who defends: member id -> its log names; skipped defenders
        JournalTeams.Defenders defenders = JournalTeams.defenders(
                newestFirst.stream().map(BattleLogs::defense).toList(), assignments, guild);
        Map<String, Set<String>> logNames = defenders.logNames();

        List<MemberPlan> plans = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : logNames.entrySet()) {
            plans.add(memberPlan(members.get(e.getKey()), e.getValue(), newestFirst));
        }
        plans.sort(Comparator.comparing(MemberPlan::memberName, String.CASE_INSENSITIVE_ORDER));
        List<String> withoutData = guild.members().stream().map(GuildMember::id)
                .filter(id -> !logNames.containsKey(id)).toList();
        return new TeamBuildPlan(plans, defenders.skipped(), withoutData, battles.size(), guildFile);
    }

    /** A team of the member seen at one (fortification, position) of one battle. */
    private record Seen(BattleLogs battle, String fortificationId, String fortificationName, int position, TeamKind kind,
                        int power, Composition composition) {
    }

    private static MemberPlan memberPlan(GuildMember member, Set<String> names, List<BattleLogs> newestFirst) {
        // every (fortification, position) the member defended, per battle, newest battle first
        List<List<Seen>> perBattle = new ArrayList<>();
        for (BattleLogs b : newestFirst) {
            List<Seen> seen = new ArrayList<>();
            for (LogTeam t : JournalTeams.logTeams(b.defense(), names, m -> Logger.log("Team builder: " + m))) {
                int power = t.firstPower();
                seen.add(new Seen(b, t.fortificationId(), t.fortificationName(), t.position(), t.kind(), power,
                        composition(b, t.fights(), names, t.kind(), power)));
            }
            if (!seen.isEmpty()) {
                perBattle.add(seen);
            }
        }
        List<Seen> source = new ArrayList<>(perBattle.get(0));
        source.sort(Comparator.comparing(Seen::kind));
        List<Seen> extra = new ArrayList<>();
        List<Seen> taken = new ArrayList<>(source);
        for (int i = 1; i < perBattle.size(); i++) {
            for (Seen s : perBattle.get(i)) {
                if (taken.stream().noneMatch(t -> sameTeam(t, s))) {
                    extra.add(s);
                    taken.add(s);
                }
            }
        }
        // known compositions: every composition of the member, newest first, distinct by units
        List<Composition> known = new ArrayList<>();
        for (List<Seen> battle : perBattle) {
            for (Seen s : battle) {
                if (s.composition() != null && known.stream().noneMatch(k -> k.sameUnits(s.composition()))) {
                    known.add(s.composition());
                }
            }
        }
        return match(member, names, perBattle.get(0).get(0).battle().date(), source, extra, known);
    }

    /** The team kind of a fight - see {@link JournalTeams#teamKind}. */
    static TeamKind teamKind(Fight fight) {
        return JournalTeams.teamKind(fight);
    }

    /**
     * The composition of a defender team: its units in the defense log, else an attack team of
     * the same player in the same battle with exactly the same power - else {@code null}.
     */
    static Composition composition(BattleLogs battle, List<Fight> defenseFights, Set<String> names, TeamKind kind,
                                   int power) {
        for (Fight f : defenseFights) {
            Composition c = fromUnits(f.defender().units(), kind, CompositionSource.DEFENSE_UNITS, battle.date(), power);
            if (c != null) {
                return c;
            }
        }
        if (battle.attack() == null) {
            return null;
        }
        for (Fight f : battle.attack().fights()) {
            if (names.contains(f.attacker().playerName()) && f.attacker().teamPower() == power) {
                Composition c = fromUnits(f.attacker().units(), kind, CompositionSource.ATTACK_EXACT_POWER,
                        battle.date(), power);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    /** A composition from log units - see {@link JournalTeams#fromUnits}. */
    static Composition fromUnits(List<FightUnit> units, TeamKind kind, CompositionSource source, LocalDate date,
                                 int power) {
        return JournalTeams.fromUnits(units, kind, source, date, power);
    }

    /** Same team: same kind and power within the tolerance, or - if both known - the same units. */
    private static boolean sameTeam(Seen a, Seen b) {
        if (a.kind() != b.kind()) {
            return false;
        }
        if (a.composition() != null && b.composition() != null && a.composition().sameUnits(b.composition())) {
            return true;
        }
        return closePower(a.power(), b.power());
    }

    static boolean closePower(int a, int b) {
        return JournalTeams.closePower(a, b);
    }

    // --- matching with the guild ---

    private static MemberPlan match(GuildMember member, Set<String> names, LocalDate sourceDate, List<Seen> source,
                                    List<Seen> extra, List<Composition> known) {
        List<Proposal> proposals = new ArrayList<>();
        Map<TeamKind, Integer> free = new EnumMap<>(TeamKind.class);
        Map<TeamKind, List<Integer>> emptyTeams = new EnumMap<>(TeamKind.class);
        for (TeamKind kind : TeamKind.values()) {
            List<Team> teams = JournalTeams.teams(member, kind);
            free.put(kind, JournalTeams.maxTeams(kind) - teams.size());
            List<Integer> empty = teams.stream().filter(Team::empty).map(Team::index).toList();
            emptyTeams.put(kind, empty);

            List<Seen> kindSource = source.stream().filter(s -> s.kind() == kind).toList();
            List<Seen> kindExtra = extra.stream().filter(s -> s.kind() == kind).toList();
            Map<Seen, Integer> present = new HashMap<>();
            for (Seen s : concat(kindSource, kindExtra)) {
                teams.stream().filter(t -> !t.empty()).filter(t -> matches(t, s)).findFirst()
                        .ifPresent(t -> present.put(s, t.index()));
            }
            // suggested targets: empty teams by nearest power (source battle first), then free slots
            Map<Seen, Target> targets = new HashMap<>();
            List<Integer> openEmpty = new ArrayList<>(empty);
            int openFree = free.get(kind);
            for (List<Seen> group : List.of(kindSource, kindExtra)) {
                List<Seen> candidates = new ArrayList<>(group.stream().filter(s -> !present.containsKey(s)).toList());
                while (!openEmpty.isEmpty() && !candidates.isEmpty()) {
                    Seen best = null;
                    Integer bestTeam = null;
                    long bestDiff = Long.MAX_VALUE;
                    for (Seen s : candidates) {
                        for (int index : openEmpty) {
                            long diff = Math.abs((long) s.power() - teams.get(index).power());
                            if (diff < bestDiff) {
                                bestDiff = diff;
                                best = s;
                                bestTeam = index;
                            }
                        }
                    }
                    targets.put(best, Target.fill(bestTeam));
                    candidates.remove(best);
                    openEmpty.remove(bestTeam);
                }
                for (Seen s : candidates) {
                    if (openFree > 0) {
                        targets.put(s, Target.NEW);
                        openFree--;
                    }
                }
            }
            for (Seen s : concat(kindSource, kindExtra)) {
                boolean fromSource = kindSource.contains(s);
                ProposalStatus status = present.containsKey(s) ? ProposalStatus.ALREADY_PRESENT
                        : targets.containsKey(s) ? ProposalStatus.AVAILABLE : ProposalStatus.OVER_LIMIT;
                List<Composition> offered = s.composition() != null ? List.of()
                        : known.stream().filter(c -> c.kind() == kind).toList();
                proposals.add(new Proposal(proposalId(member, s), member.id(), kind, s.power(), s.fortificationId(),
                        s.fortificationName(), s.position(), s.battle().battleId(), s.battle().date(), fromSource,
                        s.composition(), offered, status, present.getOrDefault(s, -1), targets.get(s),
                        fromSource && status == ProposalStatus.AVAILABLE));
            }
        }
        proposals.sort(Comparator.comparing((Proposal p) -> !p.fromSourceBattle())
                .thenComparing(Proposal::battleDate, Comparator.reverseOrder())
                .thenComparing(Proposal::kind));
        return new MemberPlan(member.id(), member.name(), new ArrayList<>(names), sourceDate, proposals,
                free.get(TeamKind.HERO), free.get(TeamKind.TITAN), emptyTeams.get(TeamKind.HERO),
                emptyTeams.get(TeamKind.TITAN));
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }

    private static String proposalId(GuildMember member, Seen s) {
        return member.id() + "|" + s.battle().battleId() + "|"
                + (s.fortificationId() != null ? s.fortificationId() : s.fortificationName()) + "|" + s.position();
    }

    /** A team with heroes/titans matches a seen team: same units, or power within the tolerance. */
    private static boolean matches(Team team, Seen s) {
        if (s.composition() != null && new HashSet<>(s.composition().unitIds()).equals(team.unitIds())) {
            return true;
        }
        return team.power() > 0 && closePower(team.power(), s.power());
    }

    // =====================================================================
    // apply
    // =====================================================================

    /**
     * Adds the selected proposals to the open guild - new teams at the next free index,
     * or filling the chosen empty team - with power, heroes/titans (if known), pet or
     * totems, {@code lastModified} = battle day and no war flag. Teams with heroes/titans
     * are never touched. Validates everything first; on any error nothing changes. On
     * success the guild is set via {@link GuildService} and is unsaved.
     */
    public TeamBuildResult apply(TeamBuildPlan plan, TeamBuildSelection selection) {
        Guild guild = context.guild();
        if (guild == null || !Objects.equals(plan.guildFile(), context.guildFilePath())) {
            return TeamBuildResult.failed(List.of(new TeamBuildResult.Error(
                    TeamBuildResult.Error.Kind.GUILD_CHANGED, null, null)));
        }
        Applied applied = applyTo(guild, plan, selection, context.catalog());
        if (!applied.errors.isEmpty()) {
            return TeamBuildResult.failed(applied.errors);
        }
        if (applied.created + applied.filled > 0) {
            guildService.updateGuild(applied.guild);
        }
        Logger.log("Team builder: " + applied.created + " team(s) added, " + applied.filled + " filled "
                + applied.bySource);
        if (applied.created + applied.filled > 0) {
            GuildLog.event(GuildLog.dirOf(plan.guildFile()), "guildLog.journalTeamsBuilt", applied.created + applied.filled);
        }
        return new TeamBuildResult(List.of(), applied.created, applied.filled, applied.bySource, applied.droppedPets,
                applied.droppedTotems);
    }

    /**
     * What {@link #apply} would reject for this selection on {@code guild} - empty if it can be
     * applied. For the dialog, to block "apply" early. Changes nothing.
     */
    public static List<TeamBuildResult.Error> check(Guild guild, TeamBuildPlan plan, TeamBuildSelection selection,
                                                    Catalog catalog) {
        if (guild == null) {
            return List.of(new TeamBuildResult.Error(TeamBuildResult.Error.Kind.GUILD_CHANGED, null, null));
        }
        return List.copyOf(applyTo(guild, plan, selection, catalog).errors);
    }

    /** What {@link #applyTo} produced. */
    static final class Applied {
        final List<TeamBuildResult.Error> errors = new ArrayList<>();
        final Map<CompositionSource, Integer> bySource = new EnumMap<>(CompositionSource.class);
        final List<TeamBuildResult.DroppedPet> droppedPets = new ArrayList<>();
        final List<TeamBuildResult.DroppedTotem> droppedTotems = new ArrayList<>();
        Guild guild;
        int created;
        int filled;
    }

    /** The guild with the selection applied (no side effects) - errors instead of a guild if invalid. */
    static Applied applyTo(Guild guild, TeamBuildPlan plan, TeamBuildSelection selection, Catalog catalog) {
        Applied result = new Applied();
        Map<String, List<Proposal>> byMember = new LinkedHashMap<>();
        for (Map.Entry<String, Target> e : selection.targets().entrySet()) {
            Optional<Proposal> p = plan.proposal(e.getKey());
            if (p.isEmpty() || !p.get().selectable()) {
                result.errors.add(new TeamBuildResult.Error(TeamBuildResult.Error.Kind.NOT_SELECTABLE, e.getKey(), null));
                continue;
            }
            byMember.computeIfAbsent(p.get().memberId(), k -> new ArrayList<>()).add(p.get());
        }
        Map<String, GuildMember> members = new LinkedHashMap<>();
        guild.members().forEach(m -> members.put(m.id(), m));
        for (Map.Entry<String, List<Proposal>> e : byMember.entrySet()) {
            GuildMember member = members.get(e.getKey());
            if (member == null) {
                result.errors.add(new TeamBuildResult.Error(TeamBuildResult.Error.Kind.UNKNOWN_MEMBER, null, e.getKey()));
                continue;
            }
            members.put(member.id(), applyToMember(member, e.getValue(), selection, catalog, result));
        }
        if (result.errors.isEmpty()) {
            result.guild = guild.withMembers(new ArrayList<>(members.values()));
        }
        return result;
    }

    private static GuildMember applyToMember(GuildMember member, List<Proposal> proposals, TeamBuildSelection selection,
                                             Catalog catalog, Applied result) {
        List<HeroTeam> heroTeams = new ArrayList<>(member.heroTeams());
        List<TitanTeam> titanTeams = new ArrayList<>(member.titanTeams());
        Set<Integer> filledHero = new HashSet<>();
        Set<Integer> filledTitan = new HashSet<>();
        Map<String, String> selectedPets = new HashMap<>();
        int errorsBefore = result.errors.size();
        for (Proposal p : proposals) {
            Target target = selection.targets().get(p.id());
            Composition composition = p.composition() != null ? p.composition() : selection.compositions().get(p.id());
            if (composition != null && composition.kind() != p.kind()) {
                error(result, TeamBuildResult.Error.Kind.INVALID_COMPOSITION, p);
                continue;
            }
            boolean hero = p.kind() == TeamKind.HERO;
            int size = hero ? heroTeams.size() : titanTeams.size();
            int index;
            if (target.kind() == Target.Kind.NEW) {
                index = size;
                if (index >= JournalTeams.maxTeams(p.kind())) {
                    error(result, TeamBuildResult.Error.Kind.TOO_MANY_TEAMS, p);
                    continue;
                }
            } else {
                index = target.teamIndex();
                Set<Integer> filled = hero ? filledHero : filledTitan;
                boolean empty = index >= 0 && index < size && (hero ? JournalTeams.isEmpty(heroTeams.get(index))
                        : titanTeams.get(index).titans().isEmpty());
                if (!empty || !filled.add(index) || index >= (hero ? member.heroTeams() : member.titanTeams()).size()) {
                    error(result, TeamBuildResult.Error.Kind.TARGET_TAKEN, p);
                    continue;
                }
            }
            if (hero && composition != null && composition.petId() != null) {
                String other = selectedPets.put(composition.petId(), p.id());
                if (other != null) {
                    error(result, TeamBuildResult.Error.Kind.DUPLICATE_PET, p);
                    continue;
                }
            }
            if (hero) {
                HeroTeam team = heroTeam(member, index, p, composition, catalog, result);
                if (team == null) {
                    continue;
                }
                put(heroTeams, index, team);
            } else {
                TitanTeam team = titanTeam(member, index, p, composition, catalog, result);
                if (team == null) {
                    continue;
                }
                put(titanTeams, index, team);
            }
            if (target.kind() == Target.Kind.NEW) {
                result.created++;
            } else {
                result.filled++;
            }
            result.bySource.merge(composition == null ? CompositionSource.POWER_ONLY : composition.source(), 1,
                    Integer::sum);
        }
        if (result.errors.size() > errorsBefore) {
            return member;
        }
        // a pet may only be used once per member: drop it from new/filled teams where a kept team has it
        Set<String> keptPets = new HashSet<>();
        for (HeroTeam t : heroTeams) {
            if (!isBuilt(t, member) && t.pet() != null) {
                keptPets.add(t.pet().id());
            }
        }
        for (int i = 0; i < heroTeams.size(); i++) {
            HeroTeam t = heroTeams.get(i);
            if (isBuilt(t, member) && t.pet() != null && keptPets.contains(t.pet().id())) {
                result.droppedPets.add(new TeamBuildResult.DroppedPet(member.id(), t.pet().id()));
                heroTeams.set(i, new HeroTeam(t.memberId(), t.index(), t.heroes(), null, null, t.totalPower(),
                        t.lastModified()));
            }
        }
        return new GuildMember(member.id(), member.name(), heroTeams, titanTeams);
    }

    /** True if the team at its index is new or was filled (differs from the member's original). */
    private static boolean isBuilt(HeroTeam team, GuildMember original) {
        return team.index() >= original.heroTeams().size() || original.heroTeams().get(team.index()) != team;
    }

    private static <T> void put(List<T> teams, int index, T team) {
        if (index == teams.size()) {
            teams.add(team);
        } else {
            teams.set(index, team);
        }
    }

    private static void error(Applied result, TeamBuildResult.Error.Kind kind, Proposal p) {
        result.errors.add(new TeamBuildResult.Error(kind, p.id(), p.memberId()));
    }

    private static HeroTeam heroTeam(GuildMember member, int index, Proposal p, Composition c, Catalog catalog,
                                     Applied result) {
        HeroTeam team = JournalTeams.heroTeam(member.id(), index, c == null ? List.of() : c.unitIds(),
                c == null ? null : c.petId(), null, p.power(), p.battleDate(), catalog,
                pet -> result.droppedPets.add(new TeamBuildResult.DroppedPet(member.id(), pet)));
        if (team == null) {
            error(result, TeamBuildResult.Error.Kind.UNKNOWN_CATALOG_ID, p);
        }
        return team;
    }

    private static TitanTeam titanTeam(GuildMember member, int index, Proposal p, Composition c, Catalog catalog,
                                       Applied result) {
        TitanTeam team = JournalTeams.titanTeam(member.id(), index, c == null ? List.of() : c.unitIds(),
                c == null ? Set.of() : c.totems(), p.power(), p.battleDate(), catalog,
                totem -> result.droppedTotems.add(new TeamBuildResult.DroppedTotem(member.id(), totem)));
        if (team == null) {
            error(result, TeamBuildResult.Error.Kind.UNKNOWN_CATALOG_ID, p);
        }
        return team;
    }
}
