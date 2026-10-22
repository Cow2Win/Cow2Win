package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.guild.GuildHeroEntryDialog;
import org.c2w.gui.guild.GuildNoteDialog;
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
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private static final String ICON_GUILD_NOTE = "/images/app/note.png";

    /** Language file keys of {@link ActionId#SHOW_TEAMS} per fortification type; the icon is the same for both, colored by type. */
    private static final String KEY_HERO_TEAMS = "toolbar.heroTeams";
    private static final String KEY_TITAN_TEAMS = "toolbar.titanTeams";
    private static final String ICON_SHOW_TEAMS = "/images/app/table.png";

    private static final String ICON_GUILD_TEAM_ENTRY = "/images/app/pencil.png";
    private static final String ICON_GENERATE_REPORT = "/images/app/lineup-report.png";
    private static final String ICON_RUN_ALGORITHM = "/images/app/run.png";
    private static final String ICON_COMPARE_LINEUPS = "/images/app/compare.png";

    /** Icon of the "open in-game change plan" button - "box-arrow-down" reads as "produce a checklist/plan to apply". */
    private static final String ICON_OPEN_CHANGE_PLAN = "/images/app/box-arrow-down.png";

    private final AppContext appContext;
    private final GuildService guildService;
    private final LineupService lineupService;

    /** The algorithm per fortification type "Run assignment" uses - shared with the concept stage's combo box. */
    private final AlgorithmSelection algorithmSelection;

    /** "Save guild" action - error-colored icon and extended tooltip while the guild is dirty, see {@link #updateSaveButtons()}. */
    private final AppAction saveGuildAction;

    /** "Save lineup" action - error-colored icon and extended tooltip while the lineup is dirty, see {@link #updateSaveButtons()}. */
    private final AppAction saveLineupAction;

    /** "Edit guild note" - enabled only while the open guild has a folder, see {@link #updateGuildNoteAction()}. */
    private final AppAction editGuildNoteAction;

    /** Disabled while an algorithm run is in progress (see {@link #onRunAlgorithm()}). */
    private final AppAction runAlgorithmAction;

    /** "Compare lineups" - icon colored by the selected fortification type, see {@link #updateFortificationTypeActions()}. */
    private final AppAction compareLineupsAction;

    /** Team overview of the selected fortification type, see {@link #updateFortificationTypeActions()}. */
    private final AppAction showTeamsAction;

    /** The process bar shown by this bar, see {@link #buildBar}. */
    private ProcessBar processBar;

    /** Switches the main window's stage view, see {@link #setStageSwitcher}. */
    private Consumer<Stage> stageSwitcher;

    /** The open team assignment dialog (disposed once closed), see {@link #teamEntryDialog}. */
    private JDialog teamEntryDialog;

    /**
     * Registers this bar's actions in {@code actions} - the process bar itself is only added
     * by {@link #buildBar}, once every action of the main window exists.
     */
    public ActionBar(AppContext appContext, MainActions actions, AlgorithmSelection algorithmSelection) {
        super(new BorderLayout());
        // Transparent like the process bar inside it, so the background image shows through.
        setOpaque(false);
        if (appContext == null) {
            throw new IllegalArgumentException("ActionBar needs an AppContext");
        }
        if (actions == null) {
            throw new IllegalArgumentException("ActionBar needs the main actions");
        }
        if (algorithmSelection == null) {
            throw new IllegalArgumentException("ActionBar needs the algorithm selection");
        }
        this.appContext = appContext;
        this.algorithmSelection = algorithmSelection;
        this.guildService = new GuildService(appContext);
        this.lineupService = new LineupService(appContext);

        // The save actions are shown as buttons in the context bar only; every other action here
        // is in a menu or a stage's action list (see MainMenuBar) and gets its icon there (same
        // size as a button icon).
        saveGuildAction = actions.register(new AppAction(SAVE_GUILD, this::onSaveGuild));
        // In the "Guild" menu and as a button right after "save guild" in the context bar.
        editGuildNoteAction = actions.register(new AppAction(EDIT_GUILD_NOTE, this::onEditGuildNote)
                .withIcon(IconLoader.iconFor(ICON_GUILD_NOTE, TOOLBAR_ICON_SIZE, AppColors.ACCENT)));
        // "Maintain live lineup" - same text and icon for both fortification types.
        actions.register(new AppAction(OPEN_GUILD_TEAM_ENTRY, this::onOpenGuildTeamEntry)
                .withIcon(IconLoader.iconForButton(ICON_GUILD_TEAM_ENTRY)));
        saveLineupAction = actions.register(new AppAction(SAVE_LINEUP, this::onSaveLineup));
        actions.register(new AppAction(GENERATE_REPORT, this::onGenerateReport)
                .withIcon(IconLoader.iconForButton(ICON_GENERATE_REPORT)));
        // "Run assignment" always green; icons of "compare" and "show teams" follow the fortification type, see updateFortificationTypeActions().
        runAlgorithmAction = actions.register(new AppAction(RUN_ALGORITHM, this::onRunAlgorithm)
                .withIcon(IconLoader.iconFor(ICON_RUN_ALGORITHM, TOOLBAR_ICON_SIZE, AppColors.SUCCESS)));
        compareLineupsAction = actions.register(new AppAction(COMPARE_LINEUPS, this::onOpenLineupComparison));
        actions.register(new AppAction(OPEN_CHANGE_PLAN, this::onOpenChangePlan)
                .withIcon(IconLoader.iconForButton(ICON_OPEN_CHANGE_PLAN)));
        showTeamsAction = actions.register(new AppAction(SHOW_TEAMS, this::onShowTeams));

        // Shows on the two save buttons WHICH part (guild and/or lineup) has unsaved changes,
        // and lets the fortification type dependent actions follow the selected type.
        updateSaveButtons();
        updateFortificationTypeActions();
        updateGuildNoteAction();
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                updateGuildNoteAction();
            }

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
     * Gives {@link #showTeamsAction} the text and tooltip of the selected fortification type, so
     * menu entry and button always say what they open, and colors the icons of "compare"
     * and "show teams" by that type (see {@link IconLoader#iconForType}).
     */
    private void updateFortificationTypeActions() {
        FortificationType type = appContext.fortificationType();
        showTeamsAction.setText(type == FortificationType.HERO ? KEY_HERO_TEAMS : KEY_TITAN_TEAMS);
        showTeamsAction.withIcon(IconLoader.iconForType(ICON_SHOW_TEAMS, type));
        compareLineupsAction.withIcon(IconLoader.iconForType(ICON_COMPARE_LINEUPS, type));
    }

    /**
     * Colors the "save guild"/"save lineup" icons {@link AppColors#ERROR}
     * while {@link AppContext#isGuildDirty()}/{@link AppContext#isLineupDirty()}
     * respectively (otherwise {@link AppColors#ACCENT}) and appends
     * "unsaved changes" to their tooltips - set on the actions, which pass
     * them on to the bound buttons.
     */
    private void updateSaveButtons() {
        updateSaveButton(saveGuildAction, ICON_SAVE_GUILD, KEY_SAVE_GUILD, appContext.isGuildDirty());
        updateSaveButton(saveLineupAction, ICON_SAVE_LINEUP, KEY_SAVE_LINEUP, appContext.isLineupDirty());
    }

    private static void updateSaveButton(AppAction action, String iconPath, String tooltipKey, boolean dirty) {
        action.putValue(Action.LARGE_ICON_KEY,
                IconLoader.iconFor(iconPath, TOOLBAR_ICON_SIZE, dirty ? AppColors.ERROR : AppColors.ACCENT));
        String tooltip = LanguageService.displayName(tooltipKey);
        action.putValue(Action.SHORT_DESCRIPTION,
                dirty ? tooltip + " – " + LanguageService.displayName(KEY_UNSAVED_SUFFIX) : tooltip);
    }

    /**
     * Runs the algorithm chosen in {@link #algorithmSelection} for the selected
     * fortification type only and leaves the other type untouched. If that type
     * already has assignments, asks first whether to clear them and assign anew
     * ({@link LineupService.AssignmentMode#REPLACE}), to keep them and only
     * distribute the remaining teams ({@link LineupService.AssignmentMode#COMPLETE})
     * or to cancel; without assignments it runs right away.
     * Computes on a background thread, so the window stays responsive, and
     * applies the result on the Swing event thread once done (see
     * {@link LineupService#computeAlgorithm}/{@link LineupService#applyAlgorithmRun}).
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
        Lineup.TeamType teamType = AlgorithmSelection.teamTypeOf(appContext.fortificationType());
        LineupAlgorithm algorithm = algorithmSelection.get(teamType);
        long existing = lineup.entries().stream().filter(entry -> entry.teamType() == teamType).count();
        LineupService.AssignmentMode mode = existing > 0
                ? askAssignmentMode(algorithm, existing, teamType)
                : LineupService.AssignmentMode.REPLACE;
        if (mode == null) {
            return;
        }

        Window window = SwingUtilities.getWindowAncestor(this);
        runAlgorithmAction.setEnabled(false);
        window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<LineupService.AlgorithmRun, Void>() {
            @Override
            protected LineupService.AlgorithmRun doInBackground() {
                return LineupService.computeAlgorithm(lineup, guild, algorithm, mode);
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

    /**
     * Asks what to do with the {@code existing} assignments of {@code teamType}
     * before running {@code algorithm}: "Clear and reassign" (preselected),
     * "Only add" or "Cancel" - null for cancel or a closed window.
     */
    private LineupService.AssignmentMode askAssignmentMode(LineupAlgorithm algorithm, long existing,
                                                           Lineup.TeamType teamType) {
        String typeName = LineupService.teamTypeName(teamType);
        // English and Spanish write the type in lower case inside a sentence ("The heroes already have ...").
        String language = LanguageService.locale().getLanguage();
        if (language.equals(Locale.ENGLISH.getLanguage()) || language.equals("es")) {
            typeName = typeName.toLowerCase(LanguageService.locale());
        }
        String replace = LanguageService.displayName("toolbar.runAlgorithm.replace");
        String[] options = {replace, LanguageService.displayName("toolbar.runAlgorithm.complete"),
                LanguageService.displayName("common.cancel")};
        int answer = JOptionPane.showOptionDialog(dialogParent(),
                LanguageService.displayName("toolbar.runAlgorithm.confirmMode", algorithm.localizedName(), existing, typeName),
                LanguageService.displayName("teamsOverview.runAlgorithm"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, replace);
        return switch (answer) {
            case 0 -> LineupService.AssignmentMode.REPLACE;
            case 1 -> LineupService.AssignmentMode.COMPLETE;
            default -> null;
        };
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
     * Opens {@link LineupComparisonDialog} or brings the open one to front - purely a
     * read-only comparison (nothing there ever changes {@link #appContext}'s lineup).
     */
    /** Enables "edit guild note" only while the open guild has a folder to keep the note in. */
    private void updateGuildNoteAction() {
        editGuildNoteAction.setEnabled(GuildNoteDialog.guildDirOf(appContext) != null);
    }

    /** Opens the note of the open guild, or brings the open note dialog to front. */
    private void onEditGuildNote() {
        GuildNoteDialog.open((Frame) SwingUtilities.getWindowAncestor(this), appContext);
    }

    private void onOpenLineupComparison() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        LineupComparisonDialog.open(owner, appContext);
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
     * counterpart of the per-fortification {@code FortificationAssignmentDialog} of the concept stage.
     * Its save opens the guild's "Original" lineup, which the context bar
     * and the map pick up by themselves (see {@link AppContext.Listener}).
     * While a team assignment is open, that one is brought to front instead (see {@link #teamEntryDialog}).
     */
    private void onOpenGuildTeamEntry() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        // No conditional expression: both dialogs' common base class is not public.
        showTeamEntry(teamEntryDialog(() -> {
            if (appContext.fortificationType() == FortificationType.HERO) {
                return new GuildHeroEntryDialog(owner, appContext);
            }
            return new GuildTitanEntryDialog(owner, appContext);
        }));
    }

    /**
     * Opens the team assignment of the selected fortification type for the one member
     * {@code memberId} - its teams only, member, filter and search locked (see
     * {@code GuildTeamEntryDialog}). From the member overview of the input stage.
     * While a team assignment is open, that one is brought to front instead (see {@link #teamEntryDialog}).
     */
    public void openTeamEntryFor(String memberId) {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        // No conditional expression: both dialogs' common base class is not public.
        showTeamEntry(teamEntryDialog(() -> {
            if (appContext.fortificationType() == FortificationType.HERO) {
                return new GuildHeroEntryDialog(owner, appContext, memberId);
            }
            return new GuildTitanEntryDialog(owner, appContext, memberId);
        }));
    }

    /**
     * The one team assignment dialog: the open one if there is one - whatever its fortification
     * type or member - otherwise a new one from {@code factory}. Only one at a time, because each
     * dialog edits its own copy of the guild and saving a second one would overwrite the first
     * one's changes.
     */
    JDialog teamEntryDialog(Supplier<JDialog> factory) {
        if (teamEntryDialog == null || !teamEntryDialog.isDisplayable()) {
            teamEntryDialog = factory.get();
        }
        return teamEntryDialog;
    }

    private static void showTeamEntry(JDialog dialog) {
        dialog.setVisible(true);
        dialog.toFront();
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
