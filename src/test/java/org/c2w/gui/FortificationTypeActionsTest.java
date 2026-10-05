package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.cowscore.CowScoreTab;
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
 * The actions that follow the selected fortification type (team assignment, team overview)
 * and the CowScore tab per type - headless, nothing is shown.
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
    @DisplayName("Team assignment and team overview show the text and tooltip of the selected fortification type")
    void textsFollowFortificationType() {
        assertTexts("toolbar.openGuildHeroEntry", "toolbar.heroTeams");

        context.setFortificationType(FortificationType.TITAN);
        assertTexts("toolbar.openGuildTitanEntry", "toolbar.titanTeams");

        context.setFortificationType(FortificationType.HERO);
        assertTexts("toolbar.openGuildHeroEntry", "toolbar.heroTeams");
    }

    @Test
    @DisplayName("The icon of both actions changes with the fortification type")
    void iconsFollowFortificationType() {
        Object heroEntryIcon = actions.get(ActionId.OPEN_GUILD_TEAM_ENTRY).getValue(Action.LARGE_ICON_KEY);
        Object heroTeamsIcon = actions.get(ActionId.SHOW_TEAMS).getValue(Action.SMALL_ICON);

        context.setFortificationType(FortificationType.TITAN);

        assertNotSame(heroEntryIcon, actions.get(ActionId.OPEN_GUILD_TEAM_ENTRY).getValue(Action.LARGE_ICON_KEY));
        assertNotSame(heroTeamsIcon, actions.get(ActionId.SHOW_TEAMS).getValue(Action.SMALL_ICON));
    }

    @Test
    @DisplayName("CowScore opens on the tab of the selected fortification type")
    void cowScoreTabPerFortificationType() {
        assertEquals(CowScoreTab.HEROES, CowScoreTab.forFortificationType(FortificationType.HERO));
        assertEquals(CowScoreTab.TITANS, CowScoreTab.forFortificationType(FortificationType.TITAN));
    }

    private void assertTexts(String teamEntryKey, String showTeamsKey) {
        assertText(ActionId.OPEN_GUILD_TEAM_ENTRY, teamEntryKey);
        assertText(ActionId.SHOW_TEAMS, showTeamsKey);
    }

    private void assertText(ActionId id, String textKey) {
        AppAction action = actions.get(id);
        assertEquals(AppAction.menuText(id, textKey), action.getValue(Action.NAME), id + " name");
        assertEquals(LanguageService.displayName(textKey), action.getValue(Action.SHORT_DESCRIPTION), id + " tooltip");
    }
}
