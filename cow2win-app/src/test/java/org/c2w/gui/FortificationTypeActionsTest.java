package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.RecentFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The actions and the fortification type: the team overview follows the selected type, the
 * icons of "compare" and "show teams" are colored by it, "run" (green) and "maintain live lineup" stay
 * the same - headless, nothing is shown.
 */
class FortificationTypeActionsTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private MainActions actions;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        GuildService guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
        actions = new MainActions();
        new ActionBar(context, actions, new AlgorithmSelection());
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("\"Maintain live lineup\" keeps its text for both types, the team overview shows the text and tooltip of the selected type")
    void textsFollowFortificationType() {
        assertTexts("toolbar.heroTeams");

        context.setFortificationType(FortificationType.TITAN);
        assertTexts("toolbar.titanTeams");

        context.setFortificationType(FortificationType.HERO);
        assertTexts("toolbar.heroTeams");
    }

    @Test
    @DisplayName("The icons of \"compare\" and the team overview change with the fortification type and back")
    void iconsFollowFortificationType() {
        for (ActionId id : List.of(ActionId.COMPARE_LINEUPS, ActionId.SHOW_TEAMS)) {
            context.setFortificationType(FortificationType.HERO);
            Object heroIcon = actions.get(id).getValue(Action.SMALL_ICON);
            assertNotNull(heroIcon, id + " hero icon");
            assertSame(heroIcon, actions.get(id).getValue(Action.LARGE_ICON_KEY), id + " button icon");

            context.setFortificationType(FortificationType.TITAN);
            Object titanIcon = actions.get(id).getValue(Action.SMALL_ICON);
            assertNotNull(titanIcon, id + " titan icon");
            assertNotSame(heroIcon, titanIcon, id.toString());
            assertSame(titanIcon, actions.get(id).getValue(Action.LARGE_ICON_KEY), id + " button icon");

            context.setFortificationType(FortificationType.HERO);
            assertSame(heroIcon, actions.get(id).getValue(Action.SMALL_ICON), id + " back to heroes");
        }
    }

    @Test
    @DisplayName("\"Run assignment\" has the same green icon for heroes and titans")
    void runAlgorithmIcon() {
        Object icon = actions.get(ActionId.RUN_ALGORITHM).getValue(Action.SMALL_ICON);
        assertSame(IconLoader.iconFor("/images/app/run.png", ActionBar.TOOLBAR_ICON_SIZE, AppColors.SUCCESS), icon);
        assertSame(icon, actions.get(ActionId.RUN_ALGORITHM).getValue(Action.LARGE_ICON_KEY));

        context.setFortificationType(FortificationType.TITAN);
        assertSame(icon, actions.get(ActionId.RUN_ALGORITHM).getValue(Action.SMALL_ICON));
    }

    @Test
    @DisplayName("\"Maintain live lineup\" has the same icon for heroes and titans")
    void teamEntryIcon() {
        Object icon = actions.get(ActionId.OPEN_GUILD_TEAM_ENTRY).getValue(Action.SMALL_ICON);
        assertNotNull(icon);
        assertSame(icon, actions.get(ActionId.OPEN_GUILD_TEAM_ENTRY).getValue(Action.LARGE_ICON_KEY));

        context.setFortificationType(FortificationType.TITAN);
        assertSame(icon, actions.get(ActionId.OPEN_GUILD_TEAM_ENTRY).getValue(Action.SMALL_ICON));
    }

    private void assertTexts(String showTeamsKey) {
        assertText(ActionId.OPEN_GUILD_TEAM_ENTRY, "toolbar.openGuildTeamEntry");
        assertText(ActionId.SHOW_TEAMS, showTeamsKey);
    }

    private void assertText(ActionId id, String textKey) {
        AppAction action = actions.get(id);
        assertEquals(AppAction.menuText(id, textKey), action.getValue(Action.NAME), id + " name");
        assertEquals(LanguageService.displayName(textKey), action.getValue(Action.SHORT_DESCRIPTION), id + " tooltip");
    }
}
