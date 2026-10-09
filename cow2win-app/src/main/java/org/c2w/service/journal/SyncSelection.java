package org.c2w.service.journal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What to take over from a {@link SyncPlan}: per row the target team and whether
 * its power and/or its units are taken over. Rows without an entry, or with
 * neither, change nothing.
 *
 * @param choices row id -> choice
 */
public record SyncSelection(Map<String, Choice> choices) {

    public SyncSelection {
        choices = Collections.unmodifiableMap(new LinkedHashMap<>(choices));
    }

    /**
     * The choice for one row.
     *
     * @param teamIndex the stored team that is changed
     * @param power     take over the power
     * @param units     take over heroes/titans with pet or totems
     */
    public record Choice(int teamIndex, boolean power, boolean units) {

        /** True if anything is taken over. */
        public boolean any() {
            return power || units;
        }
    }

    /** Nothing selected. */
    public static SyncSelection none() {
        return new SyncSelection(Map.of());
    }

    /** Every row with its matched team; power where {@link SyncRow#preselectPower()}, units never. */
    public static SyncSelection preselected(SyncPlan plan) {
        Map<String, Choice> choices = new LinkedHashMap<>();
        for (SyncRow row : plan.rows()) {
            choices.put(row.id(), new Choice(row.suggestedIndex(), row.preselectPower(), false));
        }
        return new SyncSelection(choices);
    }

    /** This selection with {@code choice} for {@code rowId}. */
    public SyncSelection with(String rowId, Choice choice) {
        Map<String, Choice> copy = new LinkedHashMap<>(choices);
        copy.put(rowId, Objects.requireNonNull(choice));
        return new SyncSelection(copy);
    }

    /** Number of rows that take over anything. */
    public long selectedCount() {
        return choices.values().stream().filter(Choice::any).count();
    }
}
