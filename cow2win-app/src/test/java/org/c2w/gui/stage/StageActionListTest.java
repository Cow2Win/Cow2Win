package org.c2w.gui.stage;

import org.c2w.gui.MainMenuBar;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** {@link StageActionList} built from the stage's menu - headless, never shown. */
class StageActionListTest {

    private static MainActions allActions() {
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
        return actions;
    }

    @Test
    @DisplayName("For the concept stage: the groups lineup (five entries), algorithm and teams - headings, no separators")
    void rowsFollowTheMenu() {
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.CONCEPT), allActions());

        assertEquals(List.of(
                heading("menu.lineup"), "NEW_LINEUP", "REMOVE_LINEUP", "CLEAR_LINEUP", "COPY_LINEUP", "COMPARE_LINEUPS",
                heading("actionGroup.algorithm"), "RUN_ALGORITHM",
                heading("actionGroup.teams"), "SHOW_TEAMS"), describe(list));
        assertEquals(MainMenuBar.menuFor(Stage.CONCEPT).allActionIds(),
                list.rows().stream().map(row -> ((AppAction) row.action()).id()).toList());
    }

    @Test
    @DisplayName("For the input stage: no guild group and no journal - the groups members and live lineup")
    void inputStageHasNoGuildGroup() {
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.INPUT), allActions());

        assertEquals(List.of(
                heading("actionGroup.members"), "ADD_MEMBER", "REMOVE_MEMBER",
                heading("actionGroup.liveLineup"), "OPEN_GUILD_TEAM_ENTRY"), describe(list));
    }

    @Test
    @DisplayName("The journal is no stage with an action list - no stage view uses its menu")
    void journalHasNoActionList() {
        assertNull(Stage.JOURNAL.stageTextKey());
        for (Stage stage : List.of(Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT)) {
            StageActionList list = new StageActionList(MainMenuBar.menuFor(stage), allActions());
            assertTrue(list.rows().stream().noneMatch(row -> ((AppAction) row.action()).id().stage() == Stage.JOURNAL),
                    stage.name());
        }
    }

    @Test
    @DisplayName("A row follows its action: disabled, new name and icon; a click performs it only while enabled")
    void rowFollowsItsAction() {
        MainActions actions = new MainActions();
        for (ActionId id : ActionId.values()) {
            actions.register(new AppAction(id, () -> { }));
        }
        AtomicInteger runs = new AtomicInteger();
        MainActions countingActions = new MainActions();
        for (ActionId id : ActionId.values()) {
            countingActions.register(id == ActionId.RUN_ALGORITHM
                    ? new AppAction(id, runs::incrementAndGet) : actions.get(id));
        }
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.CONCEPT), countingActions);
        StageActionList.ActionRow row = list.rows().stream()
                .filter(r -> ((AppAction) r.action()).id() == ActionId.RUN_ALGORITHM).findFirst().orElseThrow();
        AppAction action = countingActions.get(ActionId.RUN_ALGORITHM);

        assertTrue(row.isEnabled());
        assertEquals(Cursor.HAND_CURSOR, row.getCursor().getType());
        row.perform();
        assertEquals(1, runs.get());

        action.setEnabled(false);
        assertFalse(row.isEnabled());
        assertEquals(Cursor.DEFAULT_CURSOR, row.getCursor().getType());
        row.perform();
        assertEquals(1, runs.get(), "a disabled row does nothing");

        action.putValue(Action.NAME, "Renamed");
        assertEquals("Renamed", row.getText());
    }

    @Test
    @DisplayName("A stage view can insert a row directly after an action's row, indented like it; an unknown action is refused")
    void extraRowAfterAction() {
        MainActions actions = allActions();
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.CONCEPT), actions);
        JComboBox<String> combo = new JComboBox<>();

        list.addExtraAfter(actions.get(ActionId.RUN_ALGORITHM), combo);

        List<Component> components = List.of(list.getComponents());
        int index = components.indexOf(combo);
        StageActionList.ActionRow row = (StageActionList.ActionRow) components.get(index - 1);
        assertSame(actions.get(ActionId.RUN_ALGORITHM), row.action());
        assertEquals(heading("actionGroup.teams"), "#" + ((JLabel) components.get(index + 1)).getText());
        assertEquals(row.getInsets().left - 4, combo.getInsets().left, "indented like the row, minus the combo box's own border");

        assertThrows(IllegalArgumentException.class,
                () -> list.addExtraAfter(new AppAction(ActionId.RUN_ALGORITHM, () -> { }), new JLabel()));
    }

    @Test
    @DisplayName("A stage view can insert an action directly after an action's row - same level, also in rows(); an unknown anchor is refused")
    void extraActionAfterAction() {
        MainActions actions = allActions();
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.CONCEPT), actions);
        Action extra = new AbstractAction("extra") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
            }
        };

        list.addExtraActionAfter(actions.get(ActionId.NEW_LINEUP), extra);

        List<Action> rowActions = list.rows().stream().map(StageActionList.ActionRow::action).toList();
        assertEquals(List.of(actions.get(ActionId.NEW_LINEUP), extra, actions.get(ActionId.REMOVE_LINEUP)),
                rowActions.subList(0, 3));
        List<Component> components = List.of(list.getComponents());
        StageActionList.ActionRow extraRow = list.rows().get(1);
        assertSame(list.rows().get(0), components.get(components.indexOf(extraRow) - 1));
        assertSame(list.rows().get(2), components.get(components.indexOf(extraRow) + 1));
        assertEquals(list.rows().get(0).level(), extraRow.level());
        assertEquals(1, extraRow.level());

        assertThrows(IllegalArgumentException.class,
                () -> list.addExtraActionAfter(new AppAction(ActionId.NEW_LINEUP, () -> { }), extra));
    }

    @Test
    @DisplayName("A stage view can append a group of its own: heading in capitals, then the component, indented like a group entry")
    void extraGroup() {
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.CONCEPT), allActions());
        JCheckBox toggle = new JCheckBox("toggle");

        list.addExtraGroup("Extra group", toggle);

        Component[] components = list.getComponents();
        assertSame(toggle, components[components.length - 1]);
        StageActionList.GroupHeading heading = (StageActionList.GroupHeading) components[components.length - 2];
        assertEquals("EXTRA GROUP", heading.getText());
        StageActionList.ActionRow groupEntry = list.rows().getLast();
        assertEquals(groupEntry.getInsets().left - 4, toggle.getInsets().left);
    }

    /** The heading text of the group {@code titleKey}, as {@link #describe} writes it. */
    private static String heading(String titleKey) {
        return "#" + LanguageService.displayName(titleKey).toUpperCase(LanguageService.locale());
    }

    /** Headings as "#TITLE", separators as "---", rows as their action id. */
    private static List<String> describe(StageActionList list) {
        List<String> entries = new ArrayList<>();
        for (Component component : list.getComponents()) {
            if (component instanceof StageActionList.GroupHeading heading) {
                entries.add("#" + heading.getText());
            } else if (component instanceof StageActionList.SeparatorRow) {
                entries.add("---");
            } else if (component instanceof StageActionList.ActionRow row) {
                entries.add(((AppAction) row.action()).id().name());
            }
        }
        return entries;
    }
}
