package org.c2w.service.journal;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.AssignmentStatus;
import org.c2w.data.journal.db.PlayerAssignment;
import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.service.journal.TeamBuildPlan.Composition;
import org.c2w.service.journal.TeamBuildPlan.CompositionSource;
import org.c2w.service.journal.TeamBuildPlan.SkipReason;
import org.c2w.service.journal.TeamBuildPlan.SkippedPlayer;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/**
 * Building blocks shared by the services that take teams from the journal's
 * DEFENSE logs into the guild ("build teams from logs" and the sync): which
 * defender is which guild member, the log teams per (fortification, position)
 * of a member, a reduced view of the member's stored teams, and building
 * {@link HeroTeam}/{@link TitanTeam} objects from catalog ids. No GUI, no state.
 */
public final class JournalTeams {

    /** A team power within this share counts as the same team (matching, not composition). */
    public static final double SAME_TEAM_POWER_TOLERANCE = 0.03;

    /** Units of a complete team in a log. */
    public static final int TEAM_SIZE = 5;

    private JournalTeams() {
    }

    // =====================================================================
    // defenders -> members
    // =====================================================================

    /**
     * Our defenders in some defense logs, linked to guild members.
     *
     * @param logNames member id -> the member's names in the logs (several after renames), in log order
     * @param skipped  defenders not linked to an existing member, by raw name
     */
    public record Defenders(Map<String, Set<String>> logNames, List<SkippedPlayer> skipped) {
    }

    /**
     * Links the defenders of {@code defenseLogs} (in the given order) to the members of
     * {@code guild}: only {@code ASSIGNED} players of an existing member count; every other
     * defender is skipped with its reason. Several log names of one member are merged.
     *
     * @param assignments own players' assignments by exact raw name
     */
    public static Defenders defenders(List<BattleLog> defenseLogs, Map<String, PlayerAssignment> assignments,
                                      Guild guild) {
        Set<String> memberIds = new HashSet<>();
        guild.members().forEach(m -> memberIds.add(m.id()));
        Map<String, Set<String>> logNames = new LinkedHashMap<>();
        Map<String, SkippedPlayer> skipped = new TreeMap<>();
        for (BattleLog log : defenseLogs) {
            for (Fight f : log.fights()) {
                String name = f.defender().playerName();
                PlayerAssignment a = assignments.get(name);
                if (a != null && a.status() == AssignmentStatus.ASSIGNED && memberIds.contains(a.memberId())) {
                    logNames.computeIfAbsent(a.memberId(), k -> new LinkedHashSet<>()).add(name);
                } else if (!skipped.containsKey(name)) {
                    skipped.put(name, skipped(name, a));
                }
            }
        }
        return new Defenders(logNames, new ArrayList<>(skipped.values()));
    }

    private static SkippedPlayer skipped(String name, PlayerAssignment a) {
        if (a == null || a.status() == AssignmentStatus.OPEN) {
            return new SkippedPlayer(name, SkipReason.NOT_ASSIGNED, null);
        }
        return switch (a.status()) {
            case NOT_IN_COW2WIN -> new SkippedPlayer(name, SkipReason.NOT_IN_COW2WIN, null);
            case FORMER -> new SkippedPlayer(name, SkipReason.FORMER, null);
            default -> new SkippedPlayer(name, SkipReason.MEMBER_MISSING, a.memberId());
        };
    }

    // =====================================================================
    // log teams
    // =====================================================================

    /**
     * One team of a member in a defense log: everything at one (fortification, position).
     *
     * @param fortificationId   catalog id of the fortification ({@code null} if unknown)
     * @param fortificationName raw fortification name
     * @param position          position in the fortification
     * @param kind              hero or titan team (from the units, else the fortification type)
     * @param fights            the fights against it, in log order (at least one)
     */
    public record LogTeam(String fortificationId, String fortificationName, int position, TeamKind kind,
                          List<Fight> fights) {
        public LogTeam {
            fights = List.copyOf(fights);
        }

        /** The defender's name in the first fight. */
        public String logName() {
            return fights.get(0).defender().playerName();
        }

        /** Team power in the first fight. */
        public int firstPower() {
            return fights.get(0).defender().teamPower();
        }

        /** Team power in the last fight. */
        public int lastPower() {
            return fights.get(fights.size() - 1).defender().teamPower();
        }

        /** The distinct team powers of the fights, in log order - more than one should not happen. */
        public List<Integer> powers() {
            return fights.stream().map(f -> f.defender().teamPower()).distinct().toList();
        }

        /** The defender's units of the first fight that has any - empty if the log has none. */
        public List<FightUnit> units() {
            return fights.stream().map(f -> f.defender().units()).filter(u -> !u.isEmpty()).findFirst()
                    .orElse(List.of());
        }

        /** A key for the slot: fortification (id, else raw name) and position. */
        public String slotKey() {
            return slotKey(fortificationId, fortificationName, position);
        }

        static String slotKey(String fortificationId, String fortificationName, int position) {
            return (fortificationId != null ? fortificationId : "?" + fortificationName) + "#" + position;
        }
    }

    /**
     * The teams of the defenders named {@code names} in {@code defense}: one per
     * (fortification, position), in order of first appearance. Slots whose team kind is
     * unknown are left out and reported via {@code onUnknownKind} (English, for the log).
     */
    public static List<LogTeam> logTeams(BattleLog defense, Set<String> names, Consumer<String> onUnknownKind) {
        Map<String, List<Fight>> slots = new LinkedHashMap<>();
        for (Fight f : defense.fights()) {
            if (names.contains(f.defender().playerName())) {
                slots.computeIfAbsent(LogTeam.slotKey(f.fortificationId(), f.fortificationName(), f.position()),
                        k -> new ArrayList<>()).add(f);
            }
        }
        List<LogTeam> teams = new ArrayList<>();
        for (List<Fight> fights : slots.values()) {
            Fight first = fights.get(0);
            TeamKind kind = teamKind(first);
            if (kind == null) {
                if (onUnknownKind != null) {
                    onUnknownKind.accept("unknown team kind at " + first.fortificationName() + " - skipped");
                }
                continue;
            }
            teams.add(new LogTeam(first.fortificationId(), first.fortificationName(), first.position(), kind, fights));
        }
        return teams;
    }

    /** The team kind of a fight: from the units if the log has them, else from the fortification type. */
    public static TeamKind teamKind(Fight fight) {
        if (fight.teamKind() != null) {
            return fight.teamKind();
        }
        if (fight.fortificationId() == null) {
            return null;
        }
        return FortificationRepository.findById(fight.fortificationId())
                .map(f -> f.type() == FortificationType.HERO ? TeamKind.HERO : TeamKind.TITAN).orElse(null);
    }

    /** A composition from log units: exactly 5 heroes or titans of {@code kind}, all with catalog id; else null. */
    public static Composition fromUnits(List<FightUnit> units, TeamKind kind, CompositionSource source, LocalDate date,
                                        int power) {
        UnitKind main = kind == TeamKind.HERO ? UnitKind.HERO : UnitKind.TITAN;
        List<String> ids = new ArrayList<>();
        String pet = null;
        Set<TitanElement> totems = EnumSet.noneOf(TitanElement.class);
        for (FightUnit u : units) {
            if (u.kind() == main) {
                if (u.catalogId() == null) {
                    return null;
                }
                ids.add(u.catalogId());
            } else if (u.kind() == UnitKind.PET && kind == TeamKind.HERO && u.catalogId() != null) {
                pet = u.catalogId();
            } else if (u.kind() == UnitKind.TOTEM && kind == TeamKind.TITAN && u.totemElement() != null) {
                totems.add(u.totemElement());
            } else if (u.kind() == UnitKind.HERO || u.kind() == UnitKind.TITAN) {
                return null; // a team of the other kind
            }
        }
        if (ids.size() != TEAM_SIZE) {
            return null;
        }
        return new Composition(kind, ids, pet, totems, source, date, power);
    }

    /** True if every unit of the list has a catalog id (totems: an element). */
    public static boolean allResolved(List<FightUnit> units) {
        return units.stream().allMatch(FightUnit::isResolved);
    }

    /** Same team by power: {@code a} and {@code b} differ by at most {@link #SAME_TEAM_POWER_TOLERANCE} of the larger. */
    public static boolean closePower(int a, int b) {
        return Math.abs(a - b) <= SAME_TEAM_POWER_TOLERANCE * Math.max(a, b);
    }

    // =====================================================================
    // stored teams
    // =====================================================================

    /**
     * A stored team of a member, reduced to what matching needs.
     *
     * @param index        team index
     * @param empty        true if it has no heroes/titans
     * @param power        team power
     * @param unitIds      hero/titan ids (order does not matter)
     * @param lastModified last change, {@code null} if unknown
     */
    public record Team(int index, boolean empty, int power, Set<String> unitIds, LocalDate lastModified) {
        public Team {
            unitIds = Set.copyOf(unitIds);
        }
    }

    /** The member's teams of {@code kind}, by index. */
    public static List<Team> teams(GuildMember member, TeamKind kind) {
        List<Team> teams = new ArrayList<>();
        if (kind == TeamKind.HERO) {
            for (HeroTeam t : member.heroTeams()) {
                Set<String> ids = new HashSet<>();
                if (t.heroes() != null) {
                    t.heroes().stream().filter(Objects::nonNull).forEach(h -> ids.add(h.id()));
                }
                teams.add(new Team(t.index(), ids.isEmpty(), t.totalPower(), ids, t.lastModified()));
            }
        } else {
            for (TitanTeam t : member.titanTeams()) {
                Set<String> ids = new HashSet<>();
                t.titans().stream().filter(Objects::nonNull).forEach(x -> ids.add(x.id()));
                teams.add(new Team(t.index(), ids.isEmpty(), t.totalPower(), ids, t.lastModified()));
            }
        }
        return teams;
    }

    /** At most this many teams of {@code kind} per member. */
    public static int maxTeams(TeamKind kind) {
        return kind == TeamKind.HERO ? HeroTeam.MAX_TEAMS_PER_MEMBER : TitanTeam.MAX_TEAMS_PER_MEMBER;
    }

    /** True if the hero team has no heroes. */
    public static boolean isEmpty(HeroTeam team) {
        return team.heroes() == null || team.heroes().stream().noneMatch(Objects::nonNull);
    }

    // =====================================================================
    // building teams
    // =====================================================================

    /**
     * A hero team from catalog ids. An unknown pet is left out and reported via
     * {@code onDroppedPet}.
     *
     * @return the team, or {@code null} if a hero id is not in the catalog
     */
    public static HeroTeam heroTeam(String memberId, int index, List<String> heroIds, String petId, WarFlag warFlag,
                                    int power, LocalDate lastModified, Catalog catalog, Consumer<String> onDroppedPet) {
        List<Hero> heroes = new ArrayList<>();
        for (String id : heroIds) {
            Optional<Hero> hero = catalog.heroes().findById(id);
            if (hero.isEmpty()) {
                return null;
            }
            heroes.add(hero.get());
        }
        Pet pet = null;
        if (petId != null) {
            pet = catalog.pets().findById(petId).orElse(null);
            if (pet == null) {
                onDroppedPet.accept(petId);
            }
        }
        return new HeroTeam(memberId, index, heroes, pet, warFlag, power, lastModified);
    }

    /**
     * A titan team from catalog ids. Totems the titans do not allow (see
     * {@link TitanTeam#validTotems}) are left out and reported via {@code onDroppedTotem}.
     *
     * @return the team, or {@code null} if a titan id is not in the catalog
     */
    public static TitanTeam titanTeam(String memberId, int index, List<String> titanIds, Set<TitanElement> totems,
                                      int power, LocalDate lastModified, Catalog catalog,
                                      Consumer<TitanElement> onDroppedTotem) {
        List<Titan> titans = new ArrayList<>();
        for (String id : titanIds) {
            Optional<Titan> titan = catalog.titans().findById(id);
            if (titan.isEmpty()) {
                return null;
            }
            titans.add(titan.get());
        }
        Set<TitanElement> valid = TitanTeam.validTotems(totems, titans, message -> {
        });
        for (TitanElement totem : totems) {
            if (!valid.contains(totem)) {
                onDroppedTotem.accept(totem);
            }
        }
        return new TitanTeam(memberId, index, titans, power, lastModified, valid);
    }
}
