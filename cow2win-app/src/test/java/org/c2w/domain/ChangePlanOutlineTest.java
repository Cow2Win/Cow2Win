package org.c2w.domain;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.domain.ChangePlanOutline.Item;
import org.c2w.domain.ChangePlanOutline.Kind;
import org.c2w.domain.ChangePlanOutline.Section;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ChangePlanOutline}: order, grouping, fortification changes and stable ids. */
class ChangePlanOutlineTest {

    private static final Guild GUILD = new Guild("alpha", "Alpha", List.of(
            new GuildMember("m1", "Zora", List.of(), List.of()),
            new GuildMember("m2", "Anna", List.of(), List.of()),
            new GuildMember("m3", "Bert", List.of(), List.of())));

    private static TeamKey hero(String memberId, int index) {
        return new TeamKey(memberId, Lineup.TeamType.HERO, index);
    }

    private static TeamKey titan(String memberId, int index) {
        return new TeamKey(memberId, Lineup.TeamType.TITAN, index);
    }

    private static ChangeStep remove(TeamKey key, String from) {
        return new ChangeStep(ChangeType.REMOVE, key, from, null);
    }

    private static ChangeStep place(TeamKey key, String to) {
        return new ChangeStep(ChangeType.PLACE, key, null, to);
    }

    private static ChangeStep move(TeamKey key, String from, String to) {
        return new ChangeStep(ChangeType.MOVE, key, from, to);
    }

    private static List<String> fortifications(Section section) {
        return section.fortifications().stream().map(ChangePlanOutline.FortificationGroup::fortificationId).toList();
    }

    @Test
    @DisplayName("Heroes before titans, removals before additions, fortifications in map order (citadel first)")
    void order() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(
                place(titan("m1", 0), "sun-temple"),
                remove(hero("m1", 0), "barracks"),
                remove(hero("m2", 0), "citadel"),
                place(hero("m3", 0), "bastion"),
                remove(hero("m3", 1), "city-hall"),
                place(hero("m2", 1), "alchemy-tower")), GUILD);

        assertEquals(List.of(FortificationType.HERO, FortificationType.TITAN),
                outline.types().stream().map(ChangePlanOutline.TypeGroup::type).toList());
        List<Section> heroSections = outline.types().get(0).sections();
        assertEquals(List.of(Kind.REMOVE, Kind.ADD), heroSections.stream().map(Section::kind).toList());
        assertEquals(List.of("citadel", "city-hall", "barracks"), fortifications(heroSections.get(0)));
        assertEquals(List.of("alchemy-tower", "bastion"), fortifications(heroSections.get(1)));
        assertEquals(3, heroSections.get(0).itemCount());

        List<Section> titanSections = outline.types().get(1).sections();
        assertEquals(List.of(Kind.ADD), titanSections.stream().map(Section::kind).toList(), "no empty removal section");
    }

    @Test
    @DisplayName("Only fortifications with entries; a type without changes is left out; teams by member name, then number")
    void onlyChanges() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(
                remove(hero("m1", 0), "bastion"),
                remove(hero("m2", 2), "bastion"),
                remove(hero("m2", 0), "bastion")), GUILD);

        assertEquals(1, outline.types().size());
        assertEquals(FortificationType.HERO, outline.types().get(0).type());
        assertEquals(List.of("bastion"), fortifications(outline.types().get(0).sections().get(0)));
        assertEquals(List.of(hero("m2", 0), hero("m2", 2), hero("m1", 0)),
                outline.items().stream().map(Item::teamKey).toList(), "Anna before Zora");

        assertTrue(ChangePlanOutline.of(List.of(), GUILD).isEmpty());
        assertEquals(List.of(), ChangePlanOutline.of(List.of(), GUILD).types());
    }

    @Test
    @DisplayName("A fortification change gives a removal at the old and an addition at the new fortification, knowing each other")
    void move() {
        ChangePlanOutline outline = ChangePlanOutline.of(List.of(move(hero("m1", 0), "bastion", "citadel")), GUILD);

        assertEquals(2, outline.items().size());
        Item removal = outline.items().get(0);
        Item addition = outline.items().get(1);
        assertEquals(Kind.REMOVE, removal.kind());
        assertEquals("bastion", removal.fortificationId());
        assertEquals("citadel", removal.otherFortificationId());
        assertEquals(Kind.ADD, addition.kind());
        assertEquals("citadel", addition.fortificationId());
        assertEquals("bastion", addition.otherFortificationId());
        assertEquals(addition.id(), removal.partnerId());
        assertEquals(removal.id(), addition.partnerId());
        assertTrue(removal.isMovePart() && addition.isMovePart());
        assertSame(addition, outline.item(removal.partnerId()).orElseThrow());
    }

    @Test
    @DisplayName("Ids are stable: the same plan gives the same ids")
    void stableIds() {
        List<ChangeStep> steps = List.of(remove(hero("m1", 0), "citadel"), place(titan("m2", 1), "bridge"),
                move(hero("m3", 2), "bastion", "foundry"));
        List<String> first = ChangePlanOutline.of(steps, GUILD).items().stream().map(Item::id).toList();
        List<String> second = ChangePlanOutline.of(List.copyOf(steps), GUILD).items().stream().map(Item::id).toList();
        assertEquals(first, second);
        assertEquals(4, first.stream().distinct().count());
        assertTrue(first.contains("REMOVE|HERO|m1|0|citadel"), first.toString());
    }
}
