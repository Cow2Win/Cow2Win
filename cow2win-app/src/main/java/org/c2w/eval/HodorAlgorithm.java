package org.c2w.eval;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.HodorSettings;

import java.util.*;
import java.util.function.Supplier;

/**
 * Lineup strategies "HODOR" and "HODOR (CowScore)" - "Hold the door": the gates (bridges,
 * bastions, temples, see {@link HodorGates}) come first. Instead of picking team by team, HODOR
 * rates a WHOLE lineup of this side with {@link HodorEvaluation} and improves
 * it until the rating no longer rises:
 * <ol>
 *     <li>Existing entries are kept and count in the rating, but are never moved (additive).</li>
 *     <li>Pool: the still-unassigned teams of this side by base strength (HODOR: totalPower,
 *     HODOR (CowScore): sortScore) - as many as there are free slots plus
 *     {@link HodorSettings#reserve()}.</li>
 *     <li>Start distribution: the strongest teams on the gate fortifications (gates by points behind,
 *     every gate fortification gets one team before any gets a second, OR-groups round-robin), then
 *     the buffed fortifications with the teams with the most matching units, then the rest by
 *     capture bonus.</li>
 *     <li>Improvement: swap two placed teams of different fortifications, swap a placed team with a
 *     reserve team, move a placed team to a free slot - always in the same order, taking the first
 *     improvement, so the same data always gives the same result.</li>
 * </ol>
 * The reserve teams left over stay out. HODOR rates a team by power and the real fortification buff
 * (weighted per {@link BuffEffect}, see {@link HodorSettings}); HODOR (CowScore) by its CowScore,
 * which replaces the real buff there. The search itself is {@link HodorSearch}.
 *
 * @param <T> {@link HeroTeam} or {@link TitanTeam}
 */
public class HodorAlgorithm<T> extends AbstractLineupAlgorithm<T> {

    /** The two variants - {@link #displayName()} is the stable algorithm ID. */
    public enum Variant {
        POWER("HODOR"),
        COW_SCORE("HODOR (CowScore)");

        private final String displayName;

        Variant(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** The values set in the settings - see {@link #setSettings}. */
    private static volatile HodorSettings registeredSettings = HodorSettings.DEFAULTS;

    private final Variant variant;
    private final Supplier<HodorSettings> settings;

    public HodorAlgorithm(TeamSide<T> side, Variant variant) {
        this(side, variant, HodorAlgorithm::settings);
    }

    /** With its own settings - for tests. */
    HodorAlgorithm(TeamSide<T> side, Variant variant, Supplier<HodorSettings> settings) {
        super(side);
        this.variant = Objects.requireNonNull(variant, "variant");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Registers the values of the settings - at startup and whenever they are saved. */
    public static void setSettings(HodorSettings newSettings) {
        registeredSettings = newSettings.normalized();
    }

    /** The values currently registered via {@link #setSettings}. */
    public static HodorSettings settings() {
        return registeredSettings;
    }

    @Override
    public String displayName() {
        return variant.displayName();
    }

    /** Both variants use the reserve; HODOR the buff weights, HODOR (CowScore) the CowScore bonuses instead. */
    @Override
    public Set<AlgorithmSetting> usedSettings() {
        return switch (variant) {
            case POWER -> EnumSet.of(AlgorithmSetting.RESERVE, AlgorithmSetting.BUFF_WEIGHTS);
            case COW_SCORE -> EnumSet.of(AlgorithmSetting.RESERVE, AlgorithmSetting.COW_SCORE_BONUSES);
        };
    }

    @Override
    protected void fillFortifications(List<Fortification> fortifications, List<Candidate<T>> pool,
                                      List<Lineup.Entry> updatedEntries, Guild guild) {
        if (fortifications.isEmpty()) {
            return;
        }
        List<HodorGates.Gate> gates = HodorGates.fromCatalog(FortificationRepository.findAll()).stream()
                .filter(gate -> gate.type() == side().fortificationType())
                .toList();
        HodorSettings current = settings.get();
        new HodorSearch<>(side(), displayName(), variant, fortifications, gates, current, pool, current.reserve(),
                updatedEntries, guild).run();
    }
}
