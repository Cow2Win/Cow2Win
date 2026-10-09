package org.c2w.data.model;

import org.c2w.domain.TeamScoreCalculator;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/**
 * A titan team fielded by a guild member. Per the user's feedback, team size
 * does not differ from a hero team (i.e. also up to 5 titans) - contrary to
 * older community sources, which state 1-4 titans. totalPower, as with
 * HeroTeam, is the team's total strength.
 *
 * index: see {@link HeroTeam#index()} - identical concept, here for titan
 * teams (0..{@link #MAX_TEAMS_PER_MEMBER} - 1, up to 2 titan teams per
 * member, see {@link GuildMember}).
 *
 * lastModified: see {@link HeroTeam#lastModified()} - identical concept,
 * here for titan teams.
 *
 * totems: the team's totems - the titan-side counterpart of a hero team's
 * pet/war flag. There is exactly one totem per {@link TitanElement} (fire
 * totem = FIRE etc.), so a totem simply IS a TitanElement. Rules:
 * <ul>
 *     <li>a team fields 0 to {@link #MAX_TOTEMS} totems, each at most once
 *     (guaranteed by the set);</li>
 *     <li>a totem requires at least {@link #MIN_TITANS_PER_TOTEM} titans of
 *     its element in the team (see {@link #eligibleTotems(List)}) - e.g.
 *     2x fire + 3x water allows the fire and the water totem, five
 *     different elements allow none;</li>
 *     <li>unlike pets/war flags, there is no restriction across teams: both
 *     titan teams of a member may field the same totem.</li>
 * </ul>
 * The order is irrelevant - the set is kept in {@link TitanElement} order,
 * which is also the order totems are stored and displayed in. null means no
 * totems. Each totem adds a fortification-independent bonus to the team's
 * CowScore ({@link TeamScoreCalculator#bonuses()}, adjustable in the settings), see {@link
 * TeamScoreCalculator#scoreFor(TitanTeam, Fortification)} and {@link
 * #sortScore()}.
 */
public record TitanTeam(
        String memberId,
        int index,
        List<Titan> titans,
        int totalPower,
        LocalDate lastModified,
        Set<TitanElement> totems
) {
    /** Base weight per titan whose element matches the ElementBuff's element. */
    public static final int ELEMENT_MATCH_WEIGHT = 1;

    /** Per Clash of Worlds rules, at most 2 titan teams per member (see {@link GuildMember}) - so {@link #index} must be 0 or 1. */
    public static final int MAX_TEAMS_PER_MEMBER = 2;

    /** A titan team fields at most this many totems. */
    public static final int MAX_TOTEMS = 2;

    /** A totem requires at least this many titans of its element in the team. */
    public static final int MIN_TITANS_PER_TOTEM = 2;

    public TitanTeam {
        if (totalPower < 0) {
            throw new IllegalArgumentException("totalPower must not be negative");
        }
        if (index < 0 || index >= MAX_TEAMS_PER_MEMBER) {
            throw new IllegalArgumentException(
                    "index must be between 0 and " + (MAX_TEAMS_PER_MEMBER - 1) + ", was: " + index);
        }
        titans = titans == null ? List.of() : List.copyOf(titans);
        totems = copyOfTotems(totems);
        if (totems.size() > MAX_TOTEMS) {
            throw new IllegalArgumentException("at most " + MAX_TOTEMS + " totems allowed, was: " + totems);
        }
        Set<TitanElement> eligible = eligibleTotems(titans);
        for (TitanElement totem : totems) {
            if (!eligible.contains(totem)) {
                throw new IllegalArgumentException("totem " + totem + " requires at least " + MIN_TITANS_PER_TOTEM
                        + " titans of its element in the team");
            }
        }
    }

    /** Convenience constructor for titan teams without totems. */
    public TitanTeam(String memberId, int index, List<Titan> titans, int totalPower, LocalDate lastModified) {
        this(memberId, index, titans, totalPower, lastModified, null);
    }

    /** Convenience constructor for titan teams without lastModified and totems. */
    public TitanTeam(String memberId, int index, List<Titan> titans, int totalPower) {
        this(memberId, index, titans, totalPower, null, null);
    }

    /** Convenience constructor for an empty titan team at slot 0, without lastModified and totems. */
    public TitanTeam() {
        this(null, 0, null, 0, null, null);
    }

    /** An unmodifiable copy of {@code totems} in {@link TitanElement} order - null = empty, null entries are rejected. */
    private static Set<TitanElement> copyOfTotems(Set<TitanElement> totems) {
        if (totems == null || totems.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.noneOf(TitanElement.class));
        }
        // Not totems.contains(null): immutable sets (Set.of) throw on that.
        for (TitanElement totem : totems) {
            if (totem == null) {
                throw new IllegalArgumentException("totems must not contain null");
            }
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(totems));
    }

    /**
     * Every totem {@code titans} allow, i.e. every element at least
     * {@link #MIN_TITANS_PER_TOTEM} of them share, in {@link TitanElement}
     * order. null entries are ignored, null = no titans.
     */
    public static Set<TitanElement> eligibleTotems(List<Titan> titans) {
        Map<TitanElement, Integer> counts = new EnumMap<>(TitanElement.class);
        if (titans != null) {
            for (Titan titan : titans) {
                if (titan != null) {
                    counts.merge(titan.element(), 1, Integer::sum);
                }
            }
        }
        Set<TitanElement> eligible = EnumSet.noneOf(TitanElement.class);
        counts.forEach((element, count) -> {
            if (count >= MIN_TITANS_PER_TOTEM) {
                eligible.add(element);
            }
        });
        return eligible;
    }

    /**
     * The valid part of {@code requested} (in its iteration order) for a team
     * of {@code titans} - for totems from a source that may break the rules
     * (a guild file, an editing dialog's draft), so building the team never
     * fails: a null or duplicate entry, a totem {@code titans} don't allow
     * and every valid one beyond {@link #MAX_TOTEMS} are dropped, each
     * reported via {@code onDropped} (an English message; may be null).
     * Result in {@link TitanElement} order.
     */
    public static Set<TitanElement> validTotems(Iterable<TitanElement> requested, List<Titan> titans,
                                                Consumer<String> onDropped) {
        Consumer<String> report = onDropped == null ? message -> { } : onDropped;
        Set<TitanElement> result = EnumSet.noneOf(TitanElement.class);
        if (requested == null) {
            return result;
        }
        Set<TitanElement> eligible = eligibleTotems(titans);
        for (TitanElement totem : requested) {
            if (totem == null) {
                report.accept("Empty totem entry, dropping it");
            } else if (result.contains(totem)) {
                report.accept("Totem " + totem + " listed more than once, keeping it only once");
            } else if (!eligible.contains(totem)) {
                report.accept("Totem " + totem + " requires at least " + MIN_TITANS_PER_TOTEM
                        + " titans of its element in the team, dropping it");
            } else if (result.size() >= MAX_TOTEMS) {
                report.accept("More than " + MAX_TOTEMS + " totems, dropping " + totem);
            } else {
                result.add(totem);
            }
        }
        return result;
    }

    /**
     * Second comparison value besides totalPower: how much the given
     * {@link ElementBuff} helps this team. Every titan WITH the required
     * element contributes {@link #ELEMENT_MATCH_WEIGHT} points. Titans with a
     * different element do NOT contribute to the score (see
     * {@link ElementBuff}: "+bonusPercent% ... per titan WITH matching
     * element"). Totems do not count here.
     */
    public int buffFitScore(Buff buff) {
        if (!(buff instanceof ElementBuff elementBuff)) {
            throw new IllegalArgumentException("TitanTeam.buffFitScore expects an ElementBuff, was: " + buff);
        }
        int score = 0;
        for (Titan t : titans) {
            if (t.element() == elementBuff.element()) {
                score += ELEMENT_MATCH_WEIGHT;
            }
        }
        return score;
    }

    /**
     * The TITAN-side counterpart of {@link HeroTeam#sortScore()}, used the
     * same way to pick a team for a fortification without a buff and as the
     * general "how good is this team" measure independent of any specific
     * fortification: see {@link TeamScoreCalculator#sortScore(TitanTeam)}
     * (power / 100 000 x (1 + totem bonus + combo bonus)).
     */
    public double sortScore() {
        return TeamScoreCalculator.sortScore(this);
    }
}
