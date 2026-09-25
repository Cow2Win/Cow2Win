package org.c2w.eval;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of every available {@link LineupAlgorithm}, split by side (since
 * 2026-09-24): {@link #HERO} only fills hero fortifications, {@link #TITAN}
 * only titan fortifications, so both parts of a lineup can use different
 * strategies - or one of them {@link ManualLineupAlgorithm manual picks}
 * only. {@link #runBoth} applies one algorithm per side to a lineup.
 *
 * <p>Also owns the format of the COMBINED {@link Lineup#algorithmName()}
 * ("Heroes: Best possible lineup; Titans: Balanced defense") - see {@link
 * #combinedAlgorithmName}/{@link #parseAlgorithmName}.
 */
public final class LineupAlgorithms {

    /** Every hero strategy, in the order the combo boxes should list them; the first entry is the default. */
    public static final List<LineupAlgorithm> HERO = forSide(TeamSide.HERO);

    /** Every titan strategy, in the order the combo boxes should list them; the first entry is the default. */
    public static final List<LineupAlgorithm> TITAN = forSide(TeamSide.TITAN);

    private static final String HERO_PREFIX = "Heroes:";
    private static final String TITAN_PREFIX = "Titans:";
    private static final String PART_SEPARATOR = ";";

    private LineupAlgorithms() {
        // Utility class, no instantiation
    }

    private static <T> List<LineupAlgorithm> forSide(TeamSide<T> side) {
        return List.of(
                new BestPossibleLineupAlgorithm<>(side),
                new BalancedDefenseAlgorithm<>(side),
                new CowScoreMaximizerAlgorithm<>(side),
                new ConsensusAlgorithm<>(side),
                new ManualLineupAlgorithm(side.teamType())
        );
    }

    /** {@link #HERO} or {@link #TITAN}, whichever matches {@code teamType}. */
    public static List<LineupAlgorithm> forType(Lineup.TeamType teamType) {
        return teamType == Lineup.TeamType.HERO ? HERO : TITAN;
    }

    /**
     * The algorithm of {@code teamType} whose {@link LineupAlgorithm#displayName()}
     * equals {@code displayName}, falling back to the first (default) entry of
     * that side if there is none - e.g. nothing configured yet, or an
     * algorithm that has been renamed/removed since it was saved.
     */
    public static LineupAlgorithm findOrDefault(Lineup.TeamType teamType, String displayName) {
        List<LineupAlgorithm> candidates = forType(teamType);
        return candidates.stream()
                .filter(algorithm -> algorithm.displayName().equals(displayName))
                .findFirst()
                .orElse(candidates.get(0));
    }

    /**
     * Runs {@code heroAlgorithm} and then {@code titanAlgorithm} on {@code
     * lineup}. The two never compete for the same slots, so the order does
     * not matter for the resulting entries.
     *
     * @throws IllegalArgumentException if an algorithm is passed for the wrong side
     */
    public static Lineup runBoth(LineupAlgorithm heroAlgorithm, LineupAlgorithm titanAlgorithm,
                                 Lineup lineup, Guild guild) {
        requireSide(heroAlgorithm, Lineup.TeamType.HERO);
        requireSide(titanAlgorithm, Lineup.TeamType.TITAN);
        Lineup afterHeroes = heroAlgorithm.run(lineup, guild);
        return titanAlgorithm.run(afterHeroes, guild);
    }

    private static void requireSide(LineupAlgorithm algorithm, Lineup.TeamType expected) {
        if (algorithm.teamType() != expected) {
            throw new IllegalArgumentException(algorithm.displayName() + " is a " + algorithm.teamType()
                    + " algorithm, expected " + expected);
        }
    }

    /**
     * The {@link Lineup#algorithmName()} to store after {@code algorithmName}
     * ran for {@code side} on a lineup whose name was {@code existingName}:
     * the part for {@code side} is replaced, the other side's part is kept.
     * A legacy (pre-2026-09-24, single-algorithm) name that cannot be parsed
     * is dropped, since it no longer describes either side reliably.
     */
    public static String combinedAlgorithmName(String existingName, Lineup.TeamType side, String algorithmName) {
        Map<Lineup.TeamType, String> parts = parseAlgorithmName(existingName);
        parts.put(side, algorithmName);
        List<String> formatted = new ArrayList<>();
        if (parts.containsKey(Lineup.TeamType.HERO)) {
            formatted.add(HERO_PREFIX + " " + parts.get(Lineup.TeamType.HERO));
        }
        if (parts.containsKey(Lineup.TeamType.TITAN)) {
            formatted.add(TITAN_PREFIX + " " + parts.get(Lineup.TeamType.TITAN));
        }
        return String.join(PART_SEPARATOR + " ", formatted);
    }

    /**
     * Splits a combined {@link Lineup#algorithmName()} (see {@link
     * #combinedAlgorithmName}) into its per-side algorithm names. Returns an
     * empty (mutable) map for a blank name or a legacy single-algorithm name.
     */
    public static Map<Lineup.TeamType, String> parseAlgorithmName(String name) {
        Map<Lineup.TeamType, String> parts = new EnumMap<>(Lineup.TeamType.class);
        if (name == null || name.isBlank()) {
            return parts;
        }
        for (String part : name.split(PART_SEPARATOR)) {
            String trimmed = part.trim();
            if (trimmed.startsWith(HERO_PREFIX)) {
                parts.put(Lineup.TeamType.HERO, trimmed.substring(HERO_PREFIX.length()).trim());
            } else if (trimmed.startsWith(TITAN_PREFIX)) {
                parts.put(Lineup.TeamType.TITAN, trimmed.substring(TITAN_PREFIX.length()).trim());
            }
        }
        return parts;
    }
}
