package org.c2w.domain;

import org.c2w.data.model.TeamTemplate;
import org.c2w.infra.Logger;

import java.util.*;
import java.util.function.Function;

/**
 * GUI-free logic behind the team templates (F1-F5 / Shift+F1-F5 in a team
 * row, see {@code org.c2w.gui.guild.TeamEditorPanel}): turning a stored
 * {@link TeamTemplate} into the content of a team row's slots, and a row's
 * members back into template ids.
 */
public final class TeamTemplates {

    private TeamTemplates() {
        // Utility class, no instantiation
    }

    /**
     * The slot contents for applying {@code template} to a team row with
     * {@code slotCount} slots: the template's members looked up in {@code
     * catalog} (by {@code idOf}), in template order, padded with {@code null}
     * (= empty slot) up to {@code slotCount}. An id that is not in the
     * catalog (any more) is skipped and logged; the remaining members move
     * up. Members beyond {@code slotCount} are dropped (cannot happen with
     * {@link TeamTemplate#MAX_MEMBERS} == slot count).
     *
     * @param catalog the entries selectable in the row, e.g. all heroes
     * @return a mutable list of exactly {@code slotCount} entries
     */
    public static <T> List<T> toSlots(TeamTemplate template, Collection<T> catalog, Function<T, String> idOf,
                                      int slotCount) {
        Map<String, T> byId = new HashMap<>();
        for (T entry : catalog) {
            byId.putIfAbsent(idOf.apply(entry), entry);
        }
        List<T> slots = new ArrayList<>(slotCount);
        for (String id : template.memberIds()) {
            T entry = byId.get(id);
            if (entry == null) {
                Logger.log("Team template " + template.slot() + ": unknown id '" + id + "', skipping it");
            } else if (slots.size() < slotCount) {
                slots.add(entry);
            }
        }
        while (slots.size() < slotCount) {
            slots.add(null);
        }
        return slots;
    }

    /** The ids of a team row's non-empty slots, in slot order - what Shift+F1-F5 saves as a template. */
    public static <T> List<String> memberIds(List<T> slots, Function<T, String> idOf) {
        return slots.stream()
                .filter(Objects::nonNull)
                .map(idOf)
                .toList();
    }
}
