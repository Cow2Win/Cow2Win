package org.c2w.gui.guild;

import org.c2w.data.model.Hero;
import org.c2w.data.model.Pet;
import org.c2w.data.model.WarFlag;
import org.c2w.data.repository.Catalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TeamEditorPanel}'s team templates (see {@link TeamEditorPanel#enableTemplates}):
 * loading a template into the slots and the F-key bindings.
 */
class TeamEditorPanelTemplatesTest {

    /** Component indexes in a hero row with extras: power, war flag, pet, then the 5 slots. */
    private static final int FIRST_SLOT = 3;

    @TempDir
    Path workspace;

    private Catalog catalog;
    private List<Hero> heroes;

    @BeforeEach
    void loadCatalog() {
        System.setProperty("java.awt.headless", "true");
        catalog = new Catalog(workspace);
        heroes = catalog.heroes().findAll();
    }

    private TeamEditorPanel<Hero> panel(TeamDraft<Hero> draft, Runnable onChanged) {
        TeamExtras extras = new TeamExtras(catalog.pets().findAll(), catalog.warFlags().findAll(), null, null);
        TeamEditorPanel<Hero> panel = new TeamEditorPanel<>(heroes, Hero::id, null, null, draft, "-",
                Comparator.comparing(Hero::id), onChanged, extras);
        panel.enableTemplates(catalog.heroTemplates(), Hero::id);
        return panel;
    }

    private static List<Object> slotsOf(TeamEditorPanel<Hero> panel) {
        List<Object> slots = new ArrayList<>();
        for (int i = FIRST_SLOT; i < FIRST_SLOT + 5; i++) {
            slots.add(((JComboBox<?>) panel.getComponent(i)).getSelectedItem());
        }
        return slots;
    }

    private static List<String> ids(List<Hero> members) {
        return members.stream().map(Hero::id).toList();
    }

    /** A draft with members 0-4, power, pet and war flag set, last modified long ago. */
    private TeamDraft<Hero> filledDraft() {
        TeamDraft<Hero> draft = new TeamDraft<>();
        draft.members.addAll(heroes.subList(0, 5));
        draft.totalPower = 123_456;
        draft.pet = catalog.pets().findAll().get(0);
        draft.warFlag = catalog.warFlags().findAll().get(0);
        draft.lastModified = LocalDate.of(2020, 1, 1);
        return draft;
    }

    @Test
    @DisplayName("loading replaces all 5 slots in template order; power, pet and war flag stay; counts as a change")
    void loadsFullTemplate() throws Exception {
        List<String> template = ids(List.of(heroes.get(9), heroes.get(2), heroes.get(7), heroes.get(5), heroes.get(6)));
        catalog.heroTemplates().save(1, template);
        TeamDraft<Hero> draft = filledDraft();
        Pet pet = draft.pet;
        WarFlag warFlag = draft.warFlag;
        int[] changes = {0};
        TeamEditorPanel<Hero> panel = panel(draft, () -> changes[0]++);

        panel.loadTemplate(1);

        assertEquals(template, ids(draft.members));
        assertEquals(template, slotsOf(panel).stream().map(h -> ((Hero) h).id()).toList());
        assertEquals(123_456, draft.totalPower);
        assertSame(pet, draft.pet);
        assertSame(warFlag, draft.warFlag);
        assertEquals(LocalDate.now(), draft.lastModified);
        assertEquals(1, changes[0]);
        // the usual slot exclusion still applies: slot 1's hero is not selectable in slot 2
        JComboBox<?> secondSlot = (JComboBox<?>) panel.getComponent(FIRST_SLOT + 1);
        for (int i = 0; i < secondSlot.getItemCount(); i++) {
            assertNotEquals(heroes.get(9), secondSlot.getItemAt(i));
        }
    }

    @Test
    @DisplayName("a template with fewer entries empties the remaining slots; unknown ids are skipped")
    void loadsShortTemplateWithUnknownId() throws Exception {
        catalog.heroTemplates().save(2, List.of(heroes.get(8).id(), "no-such-hero", heroes.get(3).id()));
        TeamDraft<Hero> draft = filledDraft();
        TeamEditorPanel<Hero> panel = panel(draft, null);

        panel.loadTemplate(2);

        assertEquals(List.of(heroes.get(8).id(), heroes.get(3).id()), ids(draft.members));
        assertEquals(java.util.Arrays.asList(heroes.get(8), heroes.get(3), null, null, null), slotsOf(panel));
    }

    @Test
    @DisplayName("an empty template slot changes nothing and fires nothing")
    void emptySlotDoesNothing() {
        TeamDraft<Hero> draft = filledDraft();
        int[] changes = {0};
        TeamEditorPanel<Hero> panel = panel(draft, () -> changes[0]++);
        List<Hero> before = List.copyOf(draft.members);

        panel.loadTemplate(4);

        assertEquals(before, draft.members);
        assertEquals(LocalDate.of(2020, 1, 1), draft.lastModified);
        assertEquals(0, changes[0]);
    }

    @Test
    @DisplayName("F1-F5 / Shift+F1-F5 are bound WHEN_FOCUSED on the power field, extras and every slot, so they win over look-and-feel keys")
    void keysBoundOnEveryFocusablePart() {
        TeamEditorPanel<Hero> panel = panel(new TeamDraft<>(), null);

        for (int i = 0; i < FIRST_SLOT + 5; i++) {
            JComponent component = (JComponent) panel.getComponent(i);
            InputMap inputMap = component.getInputMap(JComponent.WHEN_FOCUSED);
            for (int slot = 1; slot <= 5; slot++) {
                Object load = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_F1 + slot - 1, 0));
                Object save = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_F1 + slot - 1, InputEvent.SHIFT_DOWN_MASK));
                assertNotNull(load, "component " + i + ", F" + slot);
                assertNotNull(save, "component " + i + ", Shift+F" + slot);
                assertNotNull(component.getActionMap().get(load));
                assertNotNull(component.getActionMap().get(save));
            }
        }
    }

    @Test
    @DisplayName("pressing F2 in a slot combo box loads template 2")
    void keyPressLoadsTemplate() throws Exception {
        catalog.heroTemplates().save(2, List.of(heroes.get(4).id()));
        TeamDraft<Hero> draft = filledDraft();
        TeamEditorPanel<Hero> panel = panel(draft, null);
        JComboBox<?> slot = (JComboBox<?>) panel.getComponent(FIRST_SLOT + 2);

        // dispatchEvent would be dropped headless (no focus owner) - resolve the key bindings directly,
        // exactly as Swing does for a focused component: WHEN_FOCUSED first, then the ancestors.
        assertTrue(SwingUtilities.processKeyBindings(new KeyEvent(slot, KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, KeyEvent.VK_F2, KeyEvent.CHAR_UNDEFINED)));

        assertEquals(List.of(heroes.get(4).id()), ids(draft.members));
    }

    @Test
    @DisplayName("without enableTemplates there are no template keys")
    void noTemplatesByDefault() {
        TeamEditorPanel<Hero> panel = new TeamEditorPanel<>(heroes, Hero::id, null, null, new TeamDraft<>(), "-",
                Comparator.comparing(Hero::id));
        JComponent slot = (JComponent) panel.getComponent(1);
        assertNull(slot.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)));
    }
}
