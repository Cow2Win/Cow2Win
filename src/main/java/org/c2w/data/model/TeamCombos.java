package org.c2w.data.model;

import java.util.Collection;
import java.util.List;

/**
 * All known {@link TeamCombo}s of one kind (currently: hero combos, see
 * {@code HeroComboRepository}), active and deactivated ones.
 */
public record TeamCombos(List<TeamCombo> combos) {

    /** No combos at all. */
    public static final TeamCombos NONE = new TeamCombos(List.of());

    public TeamCombos {
        combos = combos == null ? List.of() : List.copyOf(combos);
    }

    /** The combos that count - those without a deactivation date. */
    public List<TeamCombo> active() {
        return combos.stream().filter(TeamCombo::isActive).toList();
    }

    /** The active combos all of whose members are contained in {@code teamMemberIds}, in file order. */
    public List<TeamCombo> matching(Collection<String> teamMemberIds) {
        return combos.stream()
                .filter(TeamCombo::isActive)
                .filter(combo -> combo.matches(teamMemberIds))
                .toList();
    }
}
