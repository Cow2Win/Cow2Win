package org.c2w.service.journal;

import org.c2w.data.model.TitanElement;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Result of {@code JournalTeamBuilderService#apply}: data only (enums, ids,
 * numbers) - the GUI turns them into text.
 *
 * @param errors        why nothing was applied (empty on success)
 * @param created       teams added
 * @param filled        empty teams filled
 * @param bySource      applied teams per composition source (incl. chosen known compositions)
 * @param droppedPets   pets left out because the member already uses them in another hero team
 * @param droppedTotems totems left out because the titans do not allow them
 */
public record TeamBuildResult(List<Error> errors, int created, int filled,
                              Map<TeamBuildPlan.CompositionSource, Integer> bySource,
                              List<DroppedPet> droppedPets, List<DroppedTotem> droppedTotems) {

    public TeamBuildResult {
        errors = List.copyOf(errors);
        bySource = Map.copyOf(bySource);
        droppedPets = List.copyOf(droppedPets);
        droppedTotems = List.copyOf(droppedTotems);
    }

    /** A failed apply - nothing was changed. */
    public static TeamBuildResult failed(List<Error> errors) {
        return new TeamBuildResult(errors, 0, 0, new EnumMap<>(TeamBuildPlan.CompositionSource.class), List.of(),
                List.of());
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /** Teams applied with heroes/titans (any source but POWER_ONLY). */
    public int withComposition() {
        return bySource.entrySet().stream().filter(e -> e.getKey() != TeamBuildPlan.CompositionSource.POWER_ONLY)
                .mapToInt(Map.Entry::getValue).sum();
    }

    /**
     * Why the selection could not be applied.
     *
     * @param kind       what is wrong
     * @param proposalId the proposal concerned, {@code null} if none
     * @param memberId   the member concerned, {@code null} if none
     */
    public record Error(Kind kind, String proposalId, String memberId) {

        public enum Kind {
            /** No guild is open, or another guild than the plan's. */
            GUILD_CHANGED,
            /** The proposal is not in the plan, or not selectable. */
            NOT_SELECTABLE,
            /** The member no longer exists. */
            UNKNOWN_MEMBER,
            /** The target is not an empty team of the member (any more), or two proposals use it. */
            TARGET_TAKEN,
            /** More teams of a kind than a member may have. */
            TOO_MANY_TEAMS,
            /** Two selected teams of a member have the same pet. */
            DUPLICATE_PET,
            /** A chosen known composition does not fit the proposal (other kind). */
            INVALID_COMPOSITION,
            /** A hero/titan/pet id is not in the catalog. */
            UNKNOWN_CATALOG_ID
        }
    }

    /** A pet left out of a new/filled team. */
    public record DroppedPet(String memberId, String petId) {
    }

    /** A totem left out of a new/filled team. */
    public record DroppedTotem(String memberId, TitanElement totem) {
    }
}
