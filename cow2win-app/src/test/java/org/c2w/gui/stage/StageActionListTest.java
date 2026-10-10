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
    @DisplayName("For the concept stage: the lineup group with three entries, then the other actions, separators as lines")
    void rowsFollowTheMenu() {
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.CONCEPT), allActions());

        String lineupHeading = LanguageService.displayName("menu.lineup").toUpperCase(LanguageService.locale());
        assertEquals(List.of(
                "#" + lineupHeading, "NEW_LINEUP", "REMOVE_LINEUP", "CLEAR_LINEUP",
                "---", "RUN_ALGORITHM", "COMPARE_LINEUPS",
                "---", "SHOW_TEAMS"), describe(list));
        assertEquals(MainMenuBar.menuFor(Stage.CONCEPT).allActionIds(),
                list.rows().stream().map(row -> ((AppAction) row.action()).id()).toList());
    }

    @Test
    @DisplayName("For the input stage: no guild group and no journal - add / delete member, then the team assignment")
    void inputStageHasNoGuildGroup() {
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.INPUT), allActions());

        assertEquals(List.of(
                "ADD_MEMBER", "REMOVE_MEMBER",
                "---", "OPEN_GUILD_TEAM_ENTRY"), describe(list));
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
    @DisplayName("A stage view can append its own rows")
    void extraRows() {
        StageActionList list = new StageActionList(MainMenuBar.menuFor(Stage.CONCEPT), allActions());
        JCheckBox toggle = new JCheckBox("toggle");
        list.addExtra(toggle);
        Component[] components = list.getComponents();
        assertSame(toggle, components[components.length - 1]);
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
