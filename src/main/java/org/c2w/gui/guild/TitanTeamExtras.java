package org.c2w.gui.guild;

import org.c2w.data.model.TitanElement;

import java.util.List;

/**
 * Turns on a {@link TeamEditorPanel}'s two totem combo boxes ("Totem 1",
 * "Totem 2", after the member slots, so the titans are entered first) - the titan-side
 * counterpart of {@link TeamExtras}, titan teams only. {@code totems} is
 * what the combo boxes can offer at most (in this order); each dropdown
 * only shows those currently allowed for the row (at least
 * {@code TitanTeam#MIN_TITANS_PER_TOTEM} titans of the element, see
 * {@code TitanTeam#eligibleTotems}) and not already picked in the other
 * totem combo box of the same row. Unlike pets/war flags, other teams of
 * the member do not matter.
 *
 * <p>A {@link TeamEditorPanel} with these extras must edit {@code Titan}s.
 */
public record TitanTeamExtras(List<TitanElement> totems) {

    /** Every totem, in {@link TitanElement} order - what every dialog uses. */
    public static final TitanTeamExtras ALL = new TitanTeamExtras(List.of(TitanElement.values()));

    public TitanTeamExtras {
        totems = totems == null ? List.of() : List.copyOf(totems);
    }
}
