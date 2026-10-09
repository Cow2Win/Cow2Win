package org.c2w.domain;

import org.c2w.data.model.TeamTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link TeamTemplates} - turning a template into slot contents and a row back into template ids, without Swing. */
class TeamTemplatesTest {

    private record Entry(String id) {
    }

    private static final List<Entry> CATALOG = List.of(new Entry("a"), new Entry("b"), new Entry("c"),
            new Entry("d"), new Entry("e"), new Entry("f"));

    private static List<Entry> slots(String... templateIds) {
        return TeamTemplates.toSlots(new TeamTemplate(1, List.of(templateIds)), CATALOG, Entry::id, 5);
    }

    private static List<Entry> entries(String... ids) {
        return Arrays.stream(ids).map(id -> id == null ? null : new Entry(id)).toList();
    }

    @Test
    @DisplayName("a full template fills all 5 slots in template order")
    void fullTemplate() {
        assertEquals(entries("e", "a", "d", "b", "f"), slots("e", "a", "d", "b", "f"));
    }

    @Test
    @DisplayName("a template with fewer than 5 entries leaves the remaining slots empty")
    void shortTemplate() {
        assertEquals(entries("c", "a", null, null, null), slots("c", "a"));
    }

    @Test
    @DisplayName("an id not in the catalog is skipped, the rest of the template is still applied")
    void unknownIdSkipped() {
        assertEquals(entries("a", "c", "d", null, null), slots("a", "gone", "c", "d"));
        assertEquals(entries(null, null, null, null, null), slots("gone"));
    }

    @Test
    @DisplayName("memberIds: the ids of the non-empty slots, in slot order")
    void memberIds() {
        assertEquals(List.of("d", "a"), TeamTemplates.memberIds(entries(null, "d", null, "a", null), Entry::id));
        assertEquals(List.of(), TeamTemplates.memberIds(entries(null, null), Entry::id));
    }
}
