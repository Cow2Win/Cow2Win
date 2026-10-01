package org.c2w.data.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * A team combo: {@value #MIN_MEMBERS} to {@value #MAX_MEMBERS} heroes (or,
 * later, titans) that have synergy effects in the game which are not
 * visible in the team's power. A combo {@link #matches} a team if ALL of its
 * members are part of that team - see {@code TeamScoreCalculator} for the
 * bonus this gives.
 *
 * <p>Deliberately not hero-specific - {@link #memberIds()} are plain catalog
 * ids, so the same type can be reused for titan combos.
 *
 * @param id          unique key, also used to match a shipped default with its workspace copy
 * @param name        optional custom label chosen by the user, shown as is (untranslated) - null if the
 *                    combo is displayed by its heroes' localized names, see {@code ComboTexts}
 * @param memberIds   the ids of the combo's members, {@value #MIN_MEMBERS}..{@value #MAX_MEMBERS}, no duplicates
 * @param source      shipped ({@link ComboSource#C2W}) or user-maintained ({@link ComboSource#USER})
 * @param deactivated the date since when the combo is switched off, null if it is active -
 *                    any date counts, including one in the future (it only documents "since when")
 */
public record TeamCombo(String id, String name, List<String> memberIds, ComboSource source, LocalDate deactivated) {

    /** Minimum number of members of a combo. */
    public static final int MIN_MEMBERS = 2;

    /** Maximum number of members of a combo (a full team). */
    public static final int MAX_MEMBERS = 5;

    public TeamCombo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("TeamCombo needs an id");
        }
        if (memberIds == null || memberIds.size() < MIN_MEMBERS || memberIds.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("TeamCombo '" + id + "' needs " + MIN_MEMBERS + " to " + MAX_MEMBERS
                    + " members, was: " + memberIds);
        }
        if (new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new IllegalArgumentException("TeamCombo '" + id + "' lists a member twice: " + memberIds);
        }
        memberIds = List.copyOf(memberIds);
        name = (name == null || name.isBlank()) ? null : name.trim();
        source = source == null ? ComboSource.USER : source;
    }

    /** True if the user gave this combo its own label (see {@link #name()}). */
    public boolean hasCustomName() {
        return name != null;
    }

    /** True unless a {@link #deactivated()} date is set. */
    public boolean isActive() {
        return deactivated == null;
    }

    /** True if every member of this combo is contained in {@code teamMemberIds} - regardless of {@link #isActive()}. */
    public boolean matches(Collection<String> teamMemberIds) {
        return teamMemberIds != null && teamMemberIds.containsAll(memberIds);
    }
}
