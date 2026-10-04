package org.c2w.service.journal;

import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.TitanElement;

import java.util.List;

/**
 * Result of {@code JournalSyncService#apply}: data only (enums, ids, numbers) -
 * the GUI turns them into text.
 *
 * @param errors        why nothing was applied (empty on success)
 * @param teamsChanged  teams changed (power and/or units)
 * @param powerChanged  teams whose power was taken over
 * @param unitsChanged  teams whose heroes/titans (with pet/totems) were taken over
 * @param droppedPets   pets not taken over because another hero team of the member has them
 * @param droppedTotems totems not taken over because the titans do not allow them
 */
public record SyncResult(List<Error> errors, int teamsChanged, int powerChanged, int unitsChanged,
                         List<DroppedPet> droppedPets, List<DroppedTotem> droppedTotems) {

    public SyncResult {
        errors = List.copyOf(errors);
        droppedPets = List.copyOf(droppedPets);
        droppedTotems = List.copyOf(droppedTotems);
    }

    /** A failed apply - nothing was changed. */
    public static SyncResult failed(List<Error> errors) {
        return new SyncResult(errors, 0, 0, 0, List.of(), List.of());
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * Why the selection could not be applied.
     *
     * @param kind     what is wrong
     * @param rowId    the row concerned, {@code null} if none
     * @param memberId the member concerned, {@code null} if none
     */
    public record Error(Kind kind, String rowId, String memberId) {

        public enum Kind {
            /** No guild is open, or another guild than the plan's. */
            GUILD_CHANGED,
            /** The row is not in the plan. */
            UNKNOWN_ROW,
            /** The member no longer exists. */
            UNKNOWN_MEMBER,
            /** The target team does not exist (any more). */
            UNKNOWN_TEAM,
            /** Two selected rows change the same team. */
            TARGET_TWICE,
            /** The units of the row cannot be taken over for this team. */
            UNITS_NOT_SELECTABLE,
            /** A hero/titan id is not in the catalog. */
            UNKNOWN_CATALOG_ID
        }
    }

    /**
     * A pet not taken over (the team keeps its previous pet if possible).
     *
     * @param memberId the member
     * @param kind     always {@link TeamKind#HERO}
     * @param index    the team
     * @param petId    the pet of the log
     */
    public record DroppedPet(String memberId, TeamKind kind, int index, String petId) {
    }

    /** A totem of the log not taken over. */
    public record DroppedTotem(String memberId, TeamKind kind, int index, TitanElement totem) {
    }
}
