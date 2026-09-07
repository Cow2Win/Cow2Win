package org.c2w.data.model;

import java.util.List;

/**
 * A fortification type (catalog entry, like Hero/Titan - identical across all guilds).
 *
 * row/column: position of this fortification in the map overview grid (see
 * {@link org.tdi.cow2.gui.GridPanel}), 0-based. Purely a layout concern
 * (which grid cell this fortification occupies) - unrelated to
 * prerequisites/route, maintained manually by the user.
 *
 * prerequisites: ids of other Fortifications - capturing ANY ONE of these
 * fortifications (OR-relation, not AND) unlocks this one. Empty list =
 * attackable from the start. Example from the user's route description:
 * "heros-bridge" has prerequisites [bastion-of-fire, gates-of-nature,
 * bastion-of-ice] - capturing any one of the three titan bastions is enough
 * to unlock the Hero's Bridge.
 *
 * strategicImportance: user assessment (catalog value, not computed) of how
 * much this fortification is a strategic hub on the map - higher = more
 * important. Rough scale 1 (low) to 10 (very high). Mainly determined by how
 * many other fortifications only get unlocked by capturing THIS fortification
 * (see the prerequisites graph): "bridge" has the highest value because, as
 * the only crossing, it unlocks practically the entire rest of the map; the
 * three titan bastions (bastion-of-fire, gates-of-nature, bastion-of-ice)
 * have a somewhat lower value because, while they too unlock many follow-up
 * fortifications, they act as OR-alternatives to each other (capturing only
 * ONE of the three suffices). Dead-end fortifications like "city-hall", which
 * themselves unlock nothing and are also only reachable late (via
 * heros-bridge), get the lowest value.
 */
public record Fortification(
        String id,
        FortificationType type,
        int capacity,       // number of teams that can defend here at the same time ("Teams" column, 3-8)
        int captureBonus,   // points awarded on capture ("Capture Bonus" column)
        int row,            // row of this fortification in the map grid (0-based)
        int column,         // column of this fortification in the map grid (0-based)
        Buff buff,          // this fortification's defender bonus, or null if it has none
        List<String> prerequisites,
        int strategicImportance   // how important as a hub, rough scale 1 (low) - 10 (very high)
) {
    public Fortification {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Fortification needs an id");
        }
        if (type == null) {
            throw new IllegalArgumentException("Fortification '" + id + "' needs a type");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Fortification '" + id + "' needs a positive capacity");
        }
        if (captureBonus < 0) {
            throw new IllegalArgumentException("captureBonus must not be negative");
        }
        if (row < 0) {
            throw new IllegalArgumentException("Fortification '" + id + "': row must not be negative");
        }
        if (column < 0) {
            throw new IllegalArgumentException("Fortification '" + id + "': column must not be negative");
        }
        if (strategicImportance < 0) {
            throw new IllegalArgumentException(
                    "Fortification '" + id + "': strategicImportance must not be negative");
        }
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }

    /** True if this fortification is attackable from the start (no prerequisite). */
    public boolean isInitiallyAttackable() {
        return prerequisites.isEmpty();
    }
}
