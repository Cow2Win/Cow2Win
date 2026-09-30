package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TeamEditorPanel}'s optional war flag/pet combo boxes (see
 * {@link TeamExtras}): row order, binding to the draft, blocked ids and
 * {@link TeamEditorPanel#clear()}.
 */
class TeamEditorPanelExtrasTest {

    /** The catalogs seed their CowScore workspace files here instead of the real workspace. */
    @TempDir
    Path workspace;

    private Catalog catalog;

    @BeforeEach
    void loadCatalog() {
        System.setProperty("java.awt.headless", "true");
        catalog = new Catalog(workspace);
    }

    private TeamExtras extras(Supplier<Set<String>> blockedPetIds, Supplier<Set<String>> blockedWarFlagIds) {
        return new TeamExtras(catalog.pets().findAll(), catalog.warFlags().findAll(), blockedPetIds, blockedWarFlagIds);
    }

    private TeamEditorPanel<Hero> panel(TeamDraft<Hero> draft, TeamExtras extras, Runnable onChanged) {
        return new TeamEditorPanel<>(catalog.heroes().findAll(), Hero::id, null, null, draft, "-",
                Comparator.comparing(Hero::id), onChanged, extras);
    }

    @SuppressWarnings("unchecked")
    private static <E> JComboBox<E> comboAt(TeamEditorPanel<?> panel, int index) {
        return (JComboBox<E>) panel.getComponent(index);
    }

    private static List<Object> itemsOf(JComboBox<?> combo) {
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            items.add(combo.getItemAt(i));
        }
        return items;
    }

    @Test
    @DisplayName("row order is power, war flag, pet, then the 5 member slots; without extras there is neither")
    void rowOrder() throws Exception {
        TeamEditorPanel<Hero> withExtras = panel(new TeamDraft<>(), extras(null, null), null);
        Component[] components = withExtras.getComponents();
        assertEquals(8, components.length);
        assertInstanceOf(JTextField.class, components[0]);
        assertTrue(itemsOf((JComboBox<?>) components[1]).stream().skip(1).allMatch(WarFlag.class::isInstance));
        assertTrue(itemsOf((JComboBox<?>) components[2]).stream().skip(1).allMatch(Pet.class::isInstance));
        for (int i = 3; i < 8; i++) {
            assertTrue(itemsOf((JComboBox<?>) components[i]).stream().skip(1).allMatch(Hero.class::isInstance));
        }

        TeamEditorPanel<Hero> withoutExtras = panel(new TeamDraft<>(), null, null);
        assertEquals(6, withoutExtras.getComponentCount());
    }

    @Test
    @DisplayName("an existing pet/war flag is preselected, a new selection is written to the draft and fires onChanged")
    void bindsToDraft() throws Exception {
        TeamDraft<Hero> draft = new TeamDraft<>();
        draft.pet = new Pet("albus"); // not the catalog instance - must still be matched by id
        draft.warFlag = catalog.warFlags().findById("flag-frost").orElseThrow();
        int[] changes = {0};
        TeamEditorPanel<Hero> panel = panel(draft, extras(null, null), () -> changes[0]++);

        JComboBox<WarFlag> warFlagCombo = comboAt(panel, 1);
        JComboBox<Pet> petCombo = comboAt(panel, 2);
        assertEquals("flag-frost", ((WarFlag) warFlagCombo.getSelectedItem()).id());
        assertEquals("albus", ((Pet) petCombo.getSelectedItem()).id());
        assertEquals(0, changes[0], "the initial population must not count as a change");

        Pet axel = catalog.pets().findById("axel").orElseThrow();
        petCombo.setSelectedItem(axel);
        assertEquals("axel", draft.pet.id());
        assertEquals(1, changes[0]);

        warFlagCombo.setSelectedItem(null);
        assertNull(draft.warFlag);
        assertEquals(2, changes[0]);
    }

    @Test
    @DisplayName("ids blocked by the member's other teams are hidden when the dropdown opens, the own selection stays")
    void hidesBlockedIds() throws Exception {
        TeamDraft<Hero> draft = new TeamDraft<>();
        draft.pet = catalog.pets().findById("albus").orElseThrow();
        TeamEditorPanel<Hero> panel = panel(draft,
                extras(() -> Set.of("albus", "axel"), () -> Set.of("flag-frost")), null);

        JComboBox<Pet> petCombo = comboAt(panel, 2);
        for (javax.swing.event.PopupMenuListener l : petCombo.getPopupMenuListeners()) {
            l.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(petCombo));
        }
        List<String> petIds = itemsOf(petCombo).stream().skip(1).map(p -> ((Pet) p).id()).toList();
        assertTrue(petIds.contains("albus"), "own selection must stay selectable");
        assertFalse(petIds.contains("axel"));
        assertEquals("albus", ((Pet) petCombo.getSelectedItem()).id());

        JComboBox<WarFlag> warFlagCombo = comboAt(panel, 1);
        for (javax.swing.event.PopupMenuListener l : warFlagCombo.getPopupMenuListeners()) {
            l.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(warFlagCombo));
        }
        assertFalse(itemsOf(warFlagCombo).stream().skip(1).anyMatch(f -> ((WarFlag) f).id().equals("flag-frost")));
    }

    @Test
    @DisplayName("clear() also resets war flag and pet")
    void clearResetsExtras() throws Exception {
        TeamDraft<Hero> draft = new TeamDraft<>();
        draft.pet = catalog.pets().findById("albus").orElseThrow();
        draft.warFlag = catalog.warFlags().findById("flag-frost").orElseThrow();
        TeamEditorPanel<Hero> panel = panel(draft, extras(null, null), null);

        panel.clear();

        assertNull(draft.pet);
        assertNull(draft.warFlag);
        assertNull(comboAt(panel, 1).getSelectedItem());
        assertNull(comboAt(panel, 2).getSelectedItem());
    }

    @Test
    @DisplayName("type-ahead: a pet by the start of its name, a war flag by the start of any word of its name")
    void typeAheadSelectsByDisplayName() throws Exception {
        TeamDraft<Hero> draft = new TeamDraft<>();
        TeamEditorPanel<Hero> panel = panel(draft, extras(null, null), null);

        JComboBox<Pet> petCombo = comboAt(panel, 2);
        assertTrue(petCombo.selectWithKeyChar('o'));
        assertEquals("oliver", draft.pet.id());

        // War flag names all start alike ("Kriegsflagge ...", "War Flag of ..."), so any word of the name
        // counts: the first letters of the frost flag's LAST word ("Frostes"/"Frost"/"Givre", depending on
        // the configured language), typed in quick succession, must select it.
        String[] words = org.c2w.i18n.LanguageService.displayName("flag-frost").split("\\s+");
        String typed = words[words.length - 1].substring(0, 3).toLowerCase();
        JComboBox<WarFlag> warFlagCombo = comboAt(panel, 1);
        for (char c : typed.toCharArray()) {
            assertTrue(warFlagCombo.selectWithKeyChar(c), typed);
        }
        assertEquals("flag-frost", draft.warFlag.id());
    }
}
