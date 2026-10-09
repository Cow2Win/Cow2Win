package org.c2w.gui.guild;

import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.data.repository.Catalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.c2w.data.model.TitanElement.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TeamEditorPanel}'s optional totem combo boxes (see
 * {@link TitanTeamExtras}): row order, only allowed totems selectable, no
 * totem twice, automatic removal when the titans no longer allow a totem.
 */
class TeamEditorPanelTotemsTest {

    /** Component indexes in a titan row with totems: power, the 5 slots, then totem 1 and totem 2. */
    private static final int TOTEM_1 = 6;
    private static final int TOTEM_2 = 7;
    private static final int FIRST_SLOT = 1;

    @TempDir
    Path workspace;

    private Catalog catalog;

    @BeforeEach
    void loadCatalog() {
        System.setProperty("java.awt.headless", "true");
        catalog = new Catalog(workspace);
    }

    private Titan titan(String id) {
        return catalog.titans().findById(id).orElseThrow();
    }

    /** ignis + vulcan (fire), nova + sigurd (water), eden (earth). */
    private TeamDraft<Titan> draft(TitanElement... totems) {
        TeamDraft<Titan> draft = new TeamDraft<>();
        draft.members.addAll(List.of(titan("ignis"), titan("vulcan"), titan("nova"), titan("sigurd"), titan("eden")));
        draft.totalPower = 1000;
        draft.totems.addAll(List.of(totems));
        draft.lastModified = LocalDate.of(2020, 1, 1);
        return draft;
    }

    private TeamEditorPanel<Titan> panel(TeamDraft<Titan> draft, Runnable onChanged) {
        TeamEditorPanel<Titan> panel = new TeamEditorPanel<>(catalog.titans().findAll(), Titan::id, null, null, draft,
                "-", Comparator.comparing(Titan::id), onChanged, null, TitanTeamExtras.ALL);
        panel.enableTemplates(catalog.titanTemplates(), Titan::id);
        return panel;
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<TitanElement> totemCombo(TeamEditorPanel<?> panel, int index) {
        return (JComboBox<TitanElement>) panel.getComponent(index);
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Titan> slot(TeamEditorPanel<?> panel, int slot) {
        return (JComboBox<Titan>) panel.getComponent(FIRST_SLOT + slot);
    }

    /** Opens the dropdown's model refresh (as the popup would) and returns its entries after "no totem". */
    private static List<TitanElement> offered(TeamEditorPanel<Titan> panel, int index) {
        JComboBox<TitanElement> combo = totemCombo(panel, index);
        for (javax.swing.event.PopupMenuListener l : combo.getPopupMenuListeners()) {
            l.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(combo));
        }
        List<TitanElement> items = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            items.add(combo.getItemAt(i));
        }
        assertNull(items.get(0), "the first entry is always \"no totem\"");
        return items.subList(1, items.size());
    }

    @Test
    @DisplayName("row order is power, the 5 member slots, then totem 1 and totem 2 (titans before totems in focus order)")
    void rowOrder() {
        TeamEditorPanel<Titan> panel = panel(draft(), null);
        assertEquals(8, panel.getComponentCount());
        assertInstanceOf(JTextField.class, panel.getComponent(0));
        for (int i = FIRST_SLOT; i < FIRST_SLOT + 5; i++) {
            assertInstanceOf(Titan.class, ((JComboBox<?>) panel.getComponent(i)).getSelectedItem());
        }
        assertEquals(List.of(FIRE, WATER), offered(panel, TOTEM_1), "the last two components are the totem combos");
        assertEquals(List.of(FIRE, WATER), offered(panel, TOTEM_2));

        TeamEditorPanel<Titan> withoutTotems = new TeamEditorPanel<>(catalog.titans().findAll(), Titan::id, null, null,
                draft(), "-", Comparator.comparing(Titan::id));
        assertEquals(6, withoutTotems.getComponentCount());
    }

    @Test
    @DisplayName("existing totems are preselected; only allowed totems are offered, the other field's choice is not")
    void onlyAllowedTotemsSelectable() {
        TeamDraft<Titan> draft = draft(FIRE);
        TeamEditorPanel<Titan> panel = panel(draft, null);

        assertEquals(FIRE, totemCombo(panel, TOTEM_1).getSelectedItem());
        assertNull(totemCombo(panel, TOTEM_2).getSelectedItem());
        assertEquals(List.of(FIRE, WATER), offered(panel, TOTEM_1), "own selection stays, earth has only 1 titan");
        assertEquals(List.of(WATER), offered(panel, TOTEM_2), "fire is already picked in totem 1");
    }

    @Test
    @DisplayName("a totem selection is written to the draft, touches lastModified and fires onChanged")
    void bindsToDraft() {
        TeamDraft<Titan> draft = draft();
        int[] changes = {0};
        TeamEditorPanel<Titan> panel = panel(draft, () -> changes[0]++);
        assertEquals(0, changes[0], "the initial population must not count as a change");

        offered(panel, TOTEM_2);
        totemCombo(panel, TOTEM_2).setSelectedItem(WATER);
        assertEquals(Set.of(WATER), draft.totems);
        assertEquals(LocalDate.now(), draft.lastModified);
        assertEquals(1, changes[0]);

        totemCombo(panel, TOTEM_2).setSelectedItem(null);
        assertEquals(Set.of(), draft.totems);
        assertEquals(2, changes[0]);
    }

    @Test
    @DisplayName("without any element twice, only \"no totem\" is offered")
    void noEligibleTotem() {
        TeamDraft<Titan> draft = new TeamDraft<>();
        draft.members.addAll(List.of(titan("ignis"), titan("nova"), titan("eden")));
        TeamEditorPanel<Titan> panel = panel(draft, null);
        assertEquals(List.of(), offered(panel, TOTEM_1));
    }

    @Test
    @DisplayName("a slot change that makes a totem unallowed removes it; it does not come back by itself")
    void slotChangeRemovesTotem() {
        TeamDraft<Titan> draft = draft(FIRE, WATER);
        TeamEditorPanel<Titan> panel = panel(draft, null);

        slot(panel, 0).setSelectedItem(null); // ignis out - only vulcan is left of fire

        assertNull(totemCombo(panel, TOTEM_1).getSelectedItem());
        assertEquals(WATER, totemCombo(panel, TOTEM_2).getSelectedItem());
        assertEquals(Set.of(WATER), draft.totems);

        slot(panel, 0).setSelectedItem(titan("ignis")); // fire would be allowed again
        assertEquals(Set.of(WATER), draft.totems);
        assertNull(totemCombo(panel, TOTEM_1).getSelectedItem());
    }

    @Test
    @DisplayName("loading a template keeps totems the new titans still allow and removes the others")
    void templateLoad() throws Exception {
        catalog.titanTemplates().save(1, List.of("nova", "sigurd", "eden", "angus", "ignis"));
        TeamDraft<Titan> draft = draft(FIRE, WATER);
        TeamEditorPanel<Titan> panel = panel(draft, null);

        panel.loadTemplate(1);

        assertEquals(Set.of(WATER), draft.totems);
        assertNull(totemCombo(panel, TOTEM_1).getSelectedItem());
        assertEquals(WATER, totemCombo(panel, TOTEM_2).getSelectedItem());
        assertEquals(1000, draft.totalPower, "power stays");
    }

    @Test
    @DisplayName("the totem combo boxes are as high as the member slots")
    void heightMatchesSlots() {
        TeamEditorPanel<Titan> panel = panel(draft(), null);
        int slotHeight = slot(panel, 0).getPreferredSize().height;
        assertEquals(slotHeight, totemCombo(panel, TOTEM_1).getPreferredSize().height);
        assertEquals(slotHeight, totemCombo(panel, TOTEM_2).getPreferredSize().height);
    }

    @Test
    @DisplayName("type-ahead without opening the dropdown: the first letter selects an allowed totem")
    void typeAheadSelectsTotem() {
        TeamDraft<Titan> draft = new TeamDraft<>();
        draft.members.addAll(List.of(titan("eden"), titan("angus"), titan("ignis"), titan("vulcan")));
        TeamEditorPanel<Titan> panel = panel(draft, null);

        char earthKey = Character.toLowerCase(org.c2w.i18n.TotemTexts.name(EARTH).charAt(0)); // "e" for Erde/Earth
        assertTrue(totemCombo(panel, TOTEM_1).selectWithKeyChar(earthKey));
        assertEquals(EARTH, totemCombo(panel, TOTEM_1).getSelectedItem());
        assertEquals(Set.of(EARTH), draft.totems);

        // Earth is now taken by totem 1 - totem 2 can't pick it, but fire.
        assertFalse(totemCombo(panel, TOTEM_2).selectWithKeyChar(earthKey));
        char fireKey = Character.toLowerCase(org.c2w.i18n.TotemTexts.name(FIRE).charAt(0));
        assertTrue(totemCombo(panel, TOTEM_2).selectWithKeyChar(fireKey));
        assertEquals(Set.of(FIRE, EARTH), draft.totems);
    }

    @Test
    @DisplayName("a titan change makes a newly allowed totem selectable by key right away")
    void slotChangeUpdatesClosedModel() {
        TeamDraft<Titan> draft = new TeamDraft<>();
        draft.members.add(titan("eden"));
        TeamEditorPanel<Titan> panel = panel(draft, null);
        char earthKey = Character.toLowerCase(org.c2w.i18n.TotemTexts.name(EARTH).charAt(0));
        assertFalse(totemCombo(panel, TOTEM_1).selectWithKeyChar(earthKey), "only one earth titan");

        slot(panel, 1).setSelectedItem(titan("angus"));

        assertTrue(totemCombo(panel, TOTEM_1).selectWithKeyChar(earthKey));
        assertEquals(Set.of(EARTH), draft.totems);
    }

    @Test
    @DisplayName("clear() also removes the totems")
    void clearResetsTotems() {
        TeamDraft<Titan> draft = draft(FIRE, WATER);
        TeamEditorPanel<Titan> panel = panel(draft, null);

        panel.clear();

        assertEquals(Set.of(), draft.totems);
        assertNull(totemCombo(panel, TOTEM_1).getSelectedItem());
        assertNull(totemCombo(panel, TOTEM_2).getSelectedItem());
    }
}
