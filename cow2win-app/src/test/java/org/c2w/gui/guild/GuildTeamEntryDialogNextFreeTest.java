package org.c2w.gui.guild;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link GuildTeamEntryDialog#nextFree}: the next fortification with room, GUI-free. */
class GuildTeamEntryDialogNextFreeTest {

    private static final Fortification A = fortification("a", 3);
    private static final Fortification B = fortification("b", 5);
    private static final Fortification C = fortification("c", 2);
    private static final Fortification D = fortification("d", 4);
    private static final List<Fortification> ORDER = List.of(A, B, C, D);

    @Test
    @DisplayName("The next fortification has room: that one")
    void nextHasRoom() {
        assertSame(C, next(B, Map.of(A, 3, B, 5, C, 1, D, 0)));
    }

    @Test
    @DisplayName("The next one is full too: the one after it")
    void skipsFullFortifications() {
        assertSame(D, next(B, Map.of(A, 0, B, 5, C, 2, D, 3)));
    }

    @Test
    @DisplayName("The full fortification is the last one: wraps around to the first free one")
    void wrapsAround() {
        assertSame(B, next(D, Map.of(A, 3, B, 4, C, 0, D, 4)));
    }

    @Test
    @DisplayName("Every fortification full: null")
    void allFull() {
        assertNull(next(B, Map.of(A, 3, B, 5, C, 2, D, 4)));
    }

    @Test
    @DisplayName("An overfull fortification counts as full")
    void overfullCountsAsFull() {
        assertSame(D, next(A, Map.of(A, 4, B, 7, C, 3, D, 0)));
        assertNull(next(A, Map.of(A, 4, B, 6, C, 2, D, 9)));
    }

    private static Fortification next(Fortification full, Map<Fortification, Integer> occupancy) {
        return GuildTeamEntryDialog.nextFree(ORDER, full, occupancy::get);
    }

    private static Fortification fortification(String id, int capacity) {
        return new Fortification(id, FortificationType.HERO, capacity, 0, 0, 0, null, List.of(), 0);
    }
}
