package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.ManualLineupAlgorithm;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.guild.GuildHeroEntryDialog;
import org.c2w.gui.guild.GuildTitanEntryDialog;
import org.c2w.gui.hero.HeroValueOverviewDialog;
import org.c2w.gui.titan.TitanValueOverviewDialog;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.report.ReportGenerator;
import org.c2w.service.AppContext;
import org.c2w.service.GuildLog;
import org.c2w.service.GuildService;
import org.c2w.service.LineupService;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.c2w.gui.action.ActionId.*;

/**
 * The lower row of the main window's top area (below {@link ContextBar}): shows the
 * {@link ProcessBar} - one tile per process stage (display only until the stage views of M3 exist).
 *
 * <p>Above all, owns the handlers of the input, concept and output actions (registered in the
 * constructor, see {@link MainActions}; the menus and the save buttons in
 * {@link ContextBar} use these very actions) and keeps the save actions' "unsaved" look and
 * the fortification type dependent actions up to date. It stays a component on purpose: the
 * handlers open their dialogs and messages relative to it (its window).
 */
public class ActionBar extends JPanel {

    static final String ILLEGAL_FILENAME_CHARS = "<>:\"/\\|?*";

    /** Package-visible (not {@code private}) so {@code Cow2Frame} can size its menu item icons to match. */
    static final int TOOLBAR_ICON_SIZE = 20;

    /** Language file key of the suffix appended to a save button's tooltip while there is something unsaved (see {@link #updateSaveButtons()}). */
    private static final String KEY_UNSAVED_SUFFIX = "toolbar.unsavedSuffix";

    /**
     * Language file key for the tooltip of the "save guild" button (see {@link #onSaveGuild()}).
     * Keeps its historical "teamsOverview.*" key name.
     */
    private static final String KEY_SAVE_GUILD = "teamsOverview.saveGuild";
    private static final String ICON_SAVE_GUILD = "/images/app/save.png";

    /** Language file key for the tooltip of the "save lineup" button (see {@link #onSaveLineup()}). */
    private static final String KEY_SAVE_LINEUP = "toolbar.saveLineup";
    private static final String ICON_SAVE_LINEUP = "/images/app/save.png";

    /** Language file keys and icons of {@link ActionId#SHOW_TEAMS} per fortification type. */
    private static final String KEY_HERO_TEAMS = "toolbar.heroTeams";
    private static final String KEY_TITAN_TEAMS = "toolbar.titanTeams";
    private static final String ICON_HERO_TEAMS = "/images/app/square.png";
    private static final String ICON_TITAN_TEAMS = "/images/app/hexagon.png";

    private static final String ICON_GENERATE_REPORT = "/images/app/lineup-report.png";
    private static final String ICON_RUN_ALGORITHM = "/images/app/run.png";

    /** Reused rather than a dedicated icon (none of the existing ones reads as "compare") - the "hexagon" family, told apart by shape (paired hexagons). */
    private static final String ICON_COMPARE_LINEUPS = "/images/app/hexagon-team.png";

    /** Icon of the "open in-game change plan" button - "box-arrow-down" reads as "produce a checklist/plan to apply". */
    private static final String ICON_OPEN_CHANGE_PLAN = "/images/app/box-arrow-down.png";

    private final AppContext appContext;
    private final GuildService guildService;
    private final LineupService lineupService;

    /** "Save guild" action - red icon and extended tooltip while the guild is dirty, see {@link #updateSaveButtons()}. */
    private final AppAction saveGuildAction;

    /** "Save lineup" action - red icon and extended tooltip while the lineup is dirty, see {@link #updateSaveButtons()}. */
    private final AppAction saveLineupAction;

    /** Disabled while an algorithm run is in progress (see {@link #onRunAlgorithm()}). */
    private final AppAction runAlgorithmAction;

    /** Team overview of the selected fortification type, see {@link #updateFortificationTypeActions()}. */
    private final AppAction showTeamsAction;

    /** The process bar shown by this bar, see {@link #buildBar}. */
    private ProcessBar processBar;

    /** Switches the main window's stage view, see {@link #setStageSwitcher}. */
    private Consumer<Stage> stageSwitcher;

    /**
     * Registers this bar's actions in {@code actions} - the process bar itself is only added
     * by {@link #buildBar}, once every action of the main window exists.
     */
    public ActionBar(AppContext appContext, MainActions actions) {
        super(new BorderLayout());
        // Transparent like the process bar inside it, so the background image shows through.
        setOpaque(false);
        if (appContext == null) {
            throw new IllegalArgumentException("ActionBar needs an AppContext");
        }
        if (actions == null) {
            throw new IllegalArgumentException("ActionBar needs the main actions");
        }
        this.appContext = appContext;
        this.guildService = new GuildService(appContext);
        this.lineupService = new LineupService(appContext);

        // The save actions are shown as buttons in the context bar only; every other action here
        // is in a menu (see MainMenuBar) and gets its menu icon (same size as a button icon).
        saveGuildAction = actions.register(new AppAction(SAVE_GUILD, this::onSaveGuild));
        // "Maintain live lineup" has no icon - same text for both fortification types.
        actions.register(new AppAction(OPEN_GUILD_TEAM_ENTRY, this::onOpenGuildTeamEntry));
        saveLineupAction = actions.register(new AppAction(SAVE_LINEUP, this::onSaveLineup));
        actions.register(new AppAction(GENERATE_REPORT, this::onGenerateReport)
                .withIcon(IconLoader.iconForButton(ICON_GENERATE_REPORT)));
        runAlgorithmAction = actions.register(new AppAction(RUN_ALGORITHM, this::onRunAlgorithm)
                .withIcon(IconLoader.iconForButton(ICON_RUN_ALGORITHM)));
        actions.register(new AppAction(COMPARE_LINEUPS, this::onOpenLineupComparison)
                .withIcon(IconLoader.iconForButton(ICON_COMPARE_LINEUPS)));
        actions.register(new AppAction(OPEN_CHANGE_PLAN, this::onOpenChangePlan)
                .withIcon(IconLoader.iconForButton(ICON_OPEN_CHANGE_PLAN)));
        showTeamsAction = actions.register(new AppAction(SHOW_TEAMS, this::onShowTeams));

        // Shows on the two save buttons WHICH part (guild and/or lineup) has unsaved changes,
        // and lets the fortification type dependent actions follow the selected type.
        updateSaveButtons();
        updateFortificationTypeActions();
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void dirtyStateChanged() {
                updateSaveButtons();
            }

            @Override
            public void fortificationTypeChanged() {
                updateFortificationTypeActions();
            }
        });
    }

    /** Shows the {@link ProcessBar}. Called once by {@code Cow2Frame}. */
    void buildBar() {
        processBar = new ProcessBar();
        add(processBar, BorderLayout.CENTER);
    }

    /** The process bar this bar shows - null before {@link #buildBar}. */
    ProcessBar processBar() {
        return processBar;
    }

    /**
     * Gives {@link #showTeamsAction} the text, tooltip and icon of the selected fortification
     * type, so menu entry and button always say what they open (the titan team overview icon
     * keeps its titan color).
     */
    private void updateFortificationTypeActions() {
        boolean heroes = appContext.fortificationType() == FortificationType.HERO;
        showTeamsAction.setText(heroes ? KEY_HERO_TEAMS : KEY_TITAN_TEAMS);
        showTeamsAction.withIcon(heroes ? IconLoader.iconForButton(ICON_HERO_TEAMS)
                : IconLoader.iconFor(ICON_TITAN_TEAMS, TOOLBAR_ICON_SIZE, FortificationTypeStyle.color(FortificationType.TITAN)));
    }

    /**
     * Colors the "save guild"/"save lineup" icons {@link IconLoader#RED}
     * while {@link AppContext#isGuildDirty()}/{@link AppContext#isLineupDirty()}
     * respectively (otherwise {@link IconLoader#BLUE}) and appends
     * "unsaved changes" to their tooltips - set on the actions, which pass
     * them on to the bound buttons.
     */
    private void updateSaveButtons() {
        updateSaveButton(saveGuildAction, ICON_SAVE_GUILD, KEY_SAVE_GUILD, appContext.isGuildDirty());
        updateSaveButton(saveLineupAction, ICON_SAVE_LINEUP, KEY_SAVE_LINEUP, appContext.isLineupDirty());
    }

    private static void updateSaveButton(AppAction action, String iconPath, String tooltipKey, boolean dirty) {
        action.putValue(Action.LARGE_ICON_KEY,
                IconLoader.iconFor(iconPath, TOOLBAR_ICON_SIZE, dirty ? IconLoader.RED : IconLoader.BLUE));
        String tooltip = LanguageService.displayName(tooltipKey);
        action.putValue(Action.SHORT_DESCRIPTION,
                dirty ? tooltip + " – " + LanguageService.displayName(KEY_UNSAVED_SUFFIX) : tooltip);
    }

    /**
     * Runs the hero and the titan algorithm configured as defaults in the
     * Settings dialog on a background thread, so the window stays responsive,
     * and applies the result on the Swing event thread once done (see
     * {@link LineupService#computeAlgorithms}/{@link LineupService#applyAlgorithmRun}).
     * A fortification type configured as "Manual" (see {@link ManualLineupAlgorithm})
     * is left untouched.
     */
    private void onRunAlgorithm() {
        if (lineupService.isOriginalOpen()) {
            JOptionPane.showMessageDialog(dialogParent(),
                    LanguageService.displayName("toolbar.runAlgorithm.originalMessage"),
                    LanguageService.displayName("common.originalReadOnlyTitle"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Lineup lineup = appContext.lineup();
        Guild guild = appContext.guild();
        LineupAlgorithm heroAlgorithm = LineupService.defaultHeroAlgorithm();
        LineupAlgorithm titanAlgorithm = LineupService.defaultTitanAlgorithm();

        Window window = SwingUtilities.getWindowAncestor(this);
        runAlgorithmAction.setEnabled(false);
        window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<LineupService.AlgorithmRun, Void>() {
            @Override
            protected LineupService.AlgorithmRun doInBackground() {
                return LineupService.computeAlgorithms(lineup, guild, heroAlgorithm, titanAlgorithm);
            }

            @Override
            protected void done() {
                runAlgorithmAction.setEnabled(true);
                window.setCursor(Cursor.getDefaultCursor());
                try {
                    lineupService.applyAlgorithmRun(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Logger.logException("Algorithm run failed", e.getCause());
                }
            }
        }.execute();
    }

    /** Saves the current guild to disk as-is - see {@link GuildService#saveGuild()}. */
    private void onSaveGuild() {
        try {
            guildService.saveGuild();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialogParent(), LanguageService.displayName("common.saveGuildError") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSaveLineup() {
        if (lineupService.isOriginalOpen()) {
            JOptionPane.showMessageDialog(dialogParent(),
                    LanguageService.displayName("toolbar.saveLineup.originalMessage"),
                    LanguageService.displayName("common.originalReadOnlyTitle"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            lineupService.saveLineup();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialogParent(), LanguageService.displayName("common.saveLineupError") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Builds the report HTML in memory and shows it in {@link
     * ReportViewerDialog}. Nothing is written to disk here -
     * the dialog's own "Save report..." button lets the user choose where to
     * save it (see {@link ReportGenerator#buildReportHtml}).
     */
    private void onGenerateReport() {
        Path lineupFilePath = appContext.lineupFilePath();
        String suggestedFileName = ReportGenerator.suggestedReportFileName(lineupFilePath);
        String reportHtml = ReportGenerator.buildReportHtml(
                appContext.lineup(), appContext.guild(), suggestedFileName);
        Logger.log("Report generated for: " + lineupFilePath);
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new ReportViewerDialog(owner, reportHtml, suggestedFileName, GuildLog.dirOf(appContext.guildFilePath())).setVisible(true);
    }

    /**
     * Opens the team overview of the selected fortification type: {@link HeroValueOverviewDialog}
     * or {@link TitanValueOverviewDialog} - one row per team, with a combo box to switch which
     * value the per-fortification columns show.
     */
    private void onShowTeams() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        JDialog dialog = appContext.fortificationType() == FortificationType.HERO
                ? new HeroValueOverviewDialog(owner, appContext)
                : new TitanValueOverviewDialog(owner, appContext);
        dialog.setVisible(true);
    }

    /**
     * Opens {@link LineupComparisonDialog} - purely a
     * read-only preview/comparison (nothing here ever changes
     * {@link #appContext}'s lineup).
     */
    private void onOpenLineupComparison() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new LineupComparisonDialog(owner, appContext).setVisible(true);
    }

    /**
     * Switches to the output stage view - the change plan from the guild's fixed "Original"
     * lineup (the actual in-game deployment) to a target lineup, i.e. exactly which teams to
     * re-arrange in Hero Wars. Does nothing until {@link #setStageSwitcher} was called.
     */
    private void onOpenChangePlan() {
        if (stageSwitcher != null) {
            stageSwitcher.accept(Stage.OUTPUT);
        }
    }

    /** How this bar switches the main window's stage view (e.g. "change plan" shows the output stage). */
    void setStageSwitcher(Consumer<Stage> stageSwitcher) {
        this.stageSwitcher = stageSwitcher;
    }

    /**
     * Opens the guild-wide team assignment of the selected fortification type:
     * {@link GuildHeroEntryDialog} or {@link GuildTitanEntryDialog} - the guild-wide
     * counterpart of {@code FortificationPanel#openEntryDialog}'s {@code FortificationEntryDialog}.
     * Its save opens the guild's "Original" lineup, which the context bar
     * and the map pick up by themselves (see {@link AppContext.Listener}).
     */
    private void onOpenGuildTeamEntry() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        // No conditional expression: both dialogs' common base class is not public.
        if (appContext.fortificationType() == FortificationType.HERO) {
            new GuildHeroEntryDialog(owner, appContext).setVisible(true);
        } else {
            new GuildTitanEntryDialog(owner, appContext).setVisible(true);
        }
    }

    /**
     * Opens the team assignment of the selected fortification type for the one member
     * {@code memberId} - its teams only, member, filter and search locked (see
     * {@code GuildTeamEntryDialog}). From the member overview of the input stage.
     */
    public void openTeamEntryFor(String memberId) {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        // No conditional expression: both dialogs' common base class is not public.
        if (appContext.fortificationType() == FortificationType.HERO) {
            new GuildHeroEntryDialog(owner, appContext, memberId).setVisible(true);
        } else {
            new GuildTitanEntryDialog(owner, appContext, memberId).setVisible(true);
        }
    }

    /**
     * Parent of this bar's message and confirmation dialogs: the main window, so they
     * appear centered on it - with this bar itself as parent they would sit on the
     * strip at the top. Falls back to this bar while it is not in a window yet.
     */
    private Component dialogParent() {
        Window window = SwingUtilities.getWindowAncestor(this);
        return window != null ? window : this;
    }

    /**
     * True if name contains any character listed in {@link #ILLEGAL_FILENAME_CHARS}.
     * Package-visible (not {@code private}) so {@code Cow2Frame#onNewGuild()}
     * can reuse it - see {@link #ILLEGAL_FILENAME_CHARS}.
     */
    static boolean containsIllegalFilenameChar(String name) {
        for (int i = 0; i < ILLEGAL_FILENAME_CHARS.length(); i++) {
            if (name.indexOf(ILLEGAL_FILENAME_CHARS.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
