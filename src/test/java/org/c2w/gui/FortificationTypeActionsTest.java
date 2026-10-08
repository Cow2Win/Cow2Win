package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The actions and the fortification type: the team overview follows the selected type,
 * "maintain live lineup" stays the same - headless, nothing is shown.
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
        guildService.createGuild("Alpha");
        guildService.switchToGuild("Alpha");
        actions = new MainActions();
        new ActionBar(context, actions);
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
    @DisplayName("The icon of the team overview changes with the fortification type")
    void iconsFollowFortificationType() {
        Object heroTeamsIcon = actions.get(ActionId.SHOW_TEAMS).getValue(Action.SMALL_ICON);

        context.setFortificationType(FortificationType.TITAN);

        assertNotSame(heroTeamsIcon, actions.get(ActionId.SHOW_TEAMS).getValue(Action.SMALL_ICON));
    }

    @Test
    @DisplayName("\"Maintain live lineup\" has no icon, neither for heroes nor for titans")
    void teamEntryHasNoIcon() {
        assertNoIcon(ActionId.OPEN_GUILD_TEAM_ENTRY);

        context.setFortificationType(FortificationType.TITAN);
        assertNoIcon(ActionId.OPEN_GUILD_TEAM_ENTRY);

        context.setFortificationType(FortificationType.HERO);
        assertNoIcon(ActionId.OPEN_GUILD_TEAM_ENTRY);
    }

    private void assertTexts(String showTeamsKey) {
        assertText(ActionId.OPEN_GUILD_TEAM_ENTRY, "toolbar.openGuildTeamEntry");
        assertText(ActionId.SHOW_TEAMS, showTeamsKey);
    }

    private void assertNoIcon(ActionId id) {
        AppAction action = actions.get(id);
        assertNull(action.getValue(Action.SMALL_ICON), id + " small icon");
        assertNull(action.getValue(Action.LARGE_ICON_KEY), id + " large icon");
    }

    private void assertText(ActionId id, String textKey) {
        AppAction action = actions.get(id);
        assertEquals(AppAction.menuText(id, textKey), action.getValue(Action.NAME), id + " name");
        assertEquals(LanguageService.displayName(textKey), action.getValue(Action.SHORT_DESCRIPTION), id + " tooltip");
    }
}
