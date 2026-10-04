package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.LineupFiles;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.ManualLineupAlgorithm;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.guild.GuildHeroEntryDialog;
import org.c2w.gui.guild.GuildTitanEntryDialog;
import org.c2w.gui.hero.HeroValueOverviewDialog;
import org.c2w.gui.titan.TitanValueOverviewDialog;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.report.ReportGenerator;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.LineupService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ToolbarPanel extends JPanel {

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the label in front of the guild combo box. */
    private static final String KEY_GUILD_LABEL = "toolbar.guild";

    /**
     * File name suffix of a lineup file - stripped for display in the combo
     * box (see {@link #stripLineupSuffix}).
     */
    private static final String LINEUP_FILE_SUFFIX = LineupFiles.SUFFIX;

    static final String ILLEGAL_FILENAME_CHARS = "<>:\"/\\|?*";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the label in front of the lineup combo box. */
    private static final String KEY_LINEUP_LABEL = "toolbar.lineup";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "save lineup" button (see {@link #onSaveLineup()}). */
    private static final String KEY_SAVE_LINEUP = "toolbar.saveLineup";

    /** Language file key of the suffix appended to a save button's tooltip while there is something unsaved (see {@link #updateSaveButtons()}). */
    private static final String KEY_UNSAVED_SUFFIX = "toolbar.unsavedSuffix";

    private static final String ICON_SAVE_LINEUP = "/images/app/save.png";

    private static final String ICON_GENERATE_REPORT = "/images/app/lineup-report.png";

    private static final String ICON_HERO_TEAMS = "/images/app/square.png";

    private static final String ICON_TITAN_TEAMS = "/images/app/hexagon.png";

    /** Package-visible (not {@code private}) so {@code Cow2Frame} can size its "Guild" menu item icons to match. */
    static final int TOOLBAR_ICON_SIZE = 20;

    /**
     * Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of
     * the "save guild" button (see {@link #onSaveGuild()}). Keeps its
     * historical "teamsOverview.*" key name.
     */
    private static final String KEY_SAVE_GUILD = "teamsOverview.saveGuild";

    /** Classpath path of the "save guild" button's icon (see {@link IconLoader}). */
    private static final String ICON_SAVE_GUILD = "/images/app/save.png";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the label in front of the algorithm combo box. */
    private static final String KEY_ALGORITHM_LABEL = "teamsOverview.algorithm";

    /** Classpath path of the "run algorithm" button's icon (see {@link IconLoader}). */
    private static final String ICON_RUN_ALGORITHM = "/images/app/run.png";

    /** Reused rather than a dedicated icon (none of the existing ones reads as "compare") - same "hexagon" family already used for {@link #ICON_HERO_TEAMS}/{@link #ICON_TITAN_TEAMS}, told apart by shape (paired hexagons) instead of color. */
    private static final String ICON_COMPARE_LINEUPS = "/images/app/hexagon-team.png";

    /** Icon of the "open in-game change plan" button - the "box-arrow-down" icon reads as "produce a checklist/plan to apply", telling it apart from the paired-hexagon "compare" button next to it. */
    private static final String ICON_OPEN_CHANGE_PLAN = "/images/app/box-arrow-down.png";

    /** Reused rather than a dedicated icon - same "square" (HERO fortification) icon as {@link #ICON_HERO_TEAMS}, since this button opens the guild-wide hero counterpart of a single fortification's own team-entry dialog. */
    private static final String ICON_OPEN_GUILD_HERO_ENTRY = "/images/app/square-plus.png";;

    /** Reused rather than a dedicated icon - same "hexagon" (TITAN fortification) icon as {@link #ICON_TITAN_TEAMS}, since this button opens the guild-wide titan counterpart of a single fortification's own team-entry dialog. */
    private static final String ICON_OPEN_GUILD_TITAN_ENTRY = "/images/app/hexagon-plus.png";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the "show heroes" checkbox label. */
    private static final String KEY_SHOW_HEROES = "fortificationMap.showHeroes";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the "show titans" checkbox label. */
    private static final String KEY_SHOW_TITANS = "fortificationMap.showTitans";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the "changes" checkbox label. */
    private static final String KEY_SHOW_CHANGES = "fortificationMap.showChanges";

    private final AppContext appContext;
    private final GuildService guildService;
    private final LineupService lineupService;
    private final FortificationMapPanel fortificationMapPanel;

    /** Guild file {@link #guildCombo} was last populated for - it is only rebuilt when {@link AppContext#guildFilePath()} moves away from this. */
    private Path shownGuildFilePath;

    /** Lineup file {@link #lineupCombo} was last populated for - it is only rebuilt when {@link AppContext#lineupFilePath()} moves away from this. */
    private Path shownLineupFilePath;

    private final JComboBox<String> lineupCombo = new JComboBox<>();

    /** Combo box listing every guild folder under workspace/ (see {@link #populateGuildCombo()}) - sits before {@link #lineupCombo}, separated from it by a {@link JSeparator} (see constructor). */
    private final JComboBox<String> guildCombo = new JComboBox<>();

    /** "Save guild" action - red icon and extended tooltip while the guild is dirty, see {@link #updateSaveButtons()}. */
    private final AppAction saveGuildAction;

    /** "Save lineup" action - red icon and extended tooltip while the lineup is dirty, see {@link #updateSaveButtons()}. */
    private final AppAction saveLineupAction;

    /** Disabled while an algorithm run is in progress (see {@link #onRunAlgorithm()}). */
    private final AppAction runAlgorithmAction;

    /** The registry this panel's actions are registered in and its buttons are built from (see {@link #buildToolbar()}). */
    private final MainActions actions;

    /** Reports the outcome of the last algorithm run (see {@link #onRunAlgorithm()}). */
    private final JLabel statusLabel = new JLabel(" ");

    /**
     * True while {@link #populateLineupCombo()} is (re)building the combo
     * box's model/selection - {@link JComboBox#setModel}/{@code setSelectedItem}
     * both fire the same action event a user pick would, so the selection
     * handler (see {@link #onLineupSelected()}) checks this flag first and
     * does nothing while it is true, to avoid reloading the lineup that is
     * already open merely because the combo box was (re)populated.
     */
    private boolean populatingCombo = false;

    /** {@link #guildCombo} counterpart of {@link #populatingCombo} - guards {@link #onGuildSelected()} the same way, see its Javadoc. */
    private boolean populatingGuildCombo = false;

    /**
     * Registers this panel's actions in {@code actions} - the components themselves are only
     * added by {@link #buildToolbar()}, once every action of the main window exists.
     */
    public ToolbarPanel(AppContext appContext, FortificationMapPanel fortificationMapPanel, MainActions actions) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 4));
        if (appContext == null) {
            throw new IllegalArgumentException("ToolbarPanel needs a guildContext");
        }
        if (fortificationMapPanel == null) {
            throw new IllegalArgumentException("ToolbarPanel needs a fortificationMapPanel");
        }
        if (actions == null) {
            throw new IllegalArgumentException("ToolbarPanel needs the main actions");
        }
        this.appContext = appContext;
        this.guildService = new GuildService(appContext);
        this.lineupService = new LineupService(appContext);
        this.fortificationMapPanel = fortificationMapPanel;
        this.actions = actions;

        saveGuildAction = actions.register(new AppAction(ActionId.SAVE_GUILD, this::onSaveGuild));
        actions.register(new AppAction(ActionId.OPEN_GUILD_HERO_ENTRY, this::onOpenGuildHeroEntry)
                .withLargeIcon(IconLoader.iconForButton(ICON_OPEN_GUILD_HERO_ENTRY)));
        actions.register(new AppAction(ActionId.OPEN_GUILD_TITAN_ENTRY, this::onOpenGuildTitanEntry)
                .withLargeIcon(IconLoader.iconForButton(ICON_OPEN_GUILD_TITAN_ENTRY)));
        saveLineupAction = actions.register(new AppAction(ActionId.SAVE_LINEUP, this::onSaveLineup));
        actions.register(new AppAction(ActionId.GENERATE_REPORT, this::onGenerateReport)
                .withLargeIcon(IconLoader.iconForButton(ICON_GENERATE_REPORT)));
        runAlgorithmAction = actions.register(new AppAction(ActionId.RUN_ALGORITHM, this::onRunAlgorithm)
                .withLargeIcon(IconLoader.iconForButton(ICON_RUN_ALGORITHM)));
        actions.register(new AppAction(ActionId.COMPARE_LINEUPS, this::onOpenLineupComparison)
                .withLargeIcon(IconLoader.iconForButton(ICON_COMPARE_LINEUPS)));
        actions.register(new AppAction(ActionId.OPEN_CHANGE_PLAN, this::onOpenChangePlan)
                .withLargeIcon(IconLoader.iconForButton(ICON_OPEN_CHANGE_PLAN)));
        actions.register(new AppAction(ActionId.SHOW_HERO_TEAMS, this::onOpenHeroTeams)
                .withLargeIcon(IconLoader.iconForButton(ICON_HERO_TEAMS)));
        actions.register(new AppAction(ActionId.SHOW_TITAN_TEAMS, this::onOpenTitanTeams)
                .withLargeIcon(IconLoader.iconFor(ICON_TITAN_TEAMS, TOOLBAR_ICON_SIZE,
                        FortificationTypeStyle.color(FortificationType.TITAN))));

        // Shows on the two save buttons WHICH part (guild and/or lineup) has unsaved changes.
        updateSaveButtons();
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void dirtyStateChanged() {
                updateSaveButtons();
            }
        });
    }

    /**
     * Adds the combo boxes, the buttons (bound to the actions registered in the
     * constructor, see {@link FlatButton#forAction}) and the map filter checkboxes.
     * Called once by {@code Cow2Frame} after all actions of the main window are registered.
     */
    void buildToolbar() {
        add(new JLabel(LanguageService.displayName(KEY_GUILD_LABEL)));
        add(guildCombo);
        populateGuildCombo();
        guildCombo.addActionListener(e -> {
            if (!populatingGuildCombo) {
                onGuildSelected();
            }
        });

        add(FlatButton.forAction(actions.get(ActionId.SAVE_GUILD)));
        add(FlatButton.forAction(actions.get(ActionId.OPEN_GUILD_HERO_ENTRY)));
        add(FlatButton.forAction(actions.get(ActionId.OPEN_GUILD_TITAN_ENTRY)));

        JSeparator guildLineupSeparator = new JSeparator(SwingConstants.VERTICAL);
        guildLineupSeparator.setPreferredSize(new Dimension(2, TOOLBAR_ICON_SIZE + 8));
        add(guildLineupSeparator);

        add(new JLabel(LanguageService.displayName(KEY_LINEUP_LABEL)));
        lineupCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String fileName) {
                    setText(stripLineupSuffix(fileName));
                }
                return this;
            }
        });
        add(lineupCombo);
        populateLineupCombo();
        lineupCombo.addActionListener(e -> {
            if (!populatingCombo) {
                onLineupSelected();
            }
        });

        add(FlatButton.forAction(actions.get(ActionId.SAVE_LINEUP)));
        add(FlatButton.forAction(actions.get(ActionId.GENERATE_REPORT)));
        add(FlatButton.forAction(actions.get(ActionId.RUN_ALGORITHM)));
        add(FlatButton.forAction(actions.get(ActionId.COMPARE_LINEUPS)));
        add(FlatButton.forAction(actions.get(ActionId.OPEN_CHANGE_PLAN)));

        JSeparator filterSeparator = new JSeparator(SwingConstants.VERTICAL);
        filterSeparator.setPreferredSize(new Dimension(2, TOOLBAR_ICON_SIZE + 8));
        add(filterSeparator);

        addMapFilterCheckboxes();

        add(statusLabel);

        // Keeps both combo boxes in sync with whichever guild/lineup file is
        // open, no matter who opened it (this panel, the menus in Cow2Frame,
        // the guild team-entry dialogs, ...).
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                if (!appContext.guildFilePath().equals(shownGuildFilePath)) {
                    populateGuildCombo();
                }
            }

            @Override
            public void lineupChanged() {
                if (!appContext.lineupFilePath().equals(shownLineupFilePath)) {
                    populateLineupCombo();
                }
            }
        });
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
     * Adds the "show heroes"/"show titans"/"changes" checkboxes that used to
     * sit in the top-right cell of {@link FortificationMapPanel} - they only
     * forward their state to that panel, which still owns it.
     */
    private void addMapFilterCheckboxes() {
        JCheckBox showHeroesCheckbox = new JCheckBox(LanguageService.displayName(KEY_SHOW_HEROES),
                fortificationMapPanel.isShowHeroFortifications());
        showHeroesCheckbox.setOpaque(false);
        showHeroesCheckbox.setForeground(FortificationTypeStyle.color(FortificationType.HERO));
        showHeroesCheckbox.addActionListener(e ->
                fortificationMapPanel.setShowHeroFortifications(showHeroesCheckbox.isSelected()));
        add(showHeroesCheckbox);

        add(FlatButton.forAction(actions.get(ActionId.SHOW_HERO_TEAMS)));

        JSeparator filterSeparator = new JSeparator(SwingConstants.VERTICAL);
        filterSeparator.setPreferredSize(new Dimension(2, TOOLBAR_ICON_SIZE + 8));
        add(filterSeparator);

        JCheckBox showTitansCheckbox = new JCheckBox(LanguageService.displayName(KEY_SHOW_TITANS),
                fortificationMapPanel.isShowTitanFortifications());
        showTitansCheckbox.setOpaque(false);
        showTitansCheckbox.setForeground(FortificationTypeStyle.color(FortificationType.TITAN));
        showTitansCheckbox.addActionListener(e ->
                fortificationMapPanel.setShowTitanFortifications(showTitansCheckbox.isSelected()));
        add(showTitansCheckbox);


        add(FlatButton.forAction(actions.get(ActionId.SHOW_TITAN_TEAMS)));

        JCheckBox changesCheckbox = new JCheckBox(LanguageService.displayName(KEY_SHOW_CHANGES),
                fortificationMapPanel.isShowChanges());
        changesCheckbox.setOpaque(false);
        changesCheckbox.addActionListener(e ->
                fortificationMapPanel.setShowChanges(changesCheckbox.isSelected()));
        add(changesCheckbox);
    }


    /**
     * Runs the hero and the titan algorithm configured as defaults in the
     * Settings dialog on a background thread, so the window stays responsive,
     * and applies the result on the Swing event thread once done (see
     * {@link LineupService#computeAlgorithms}/{@link LineupService#applyAlgorithmRun}).
     * A side configured as "Manual" (see {@link ManualLineupAlgorithm}) is left
     * untouched.
     */
    private void onRunAlgorithm() {
        if (lineupService.isOriginalOpen()) {
            JOptionPane.showMessageDialog(this,
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
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.saveGuildError") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }


    private void populateGuildCombo() {
        populatingGuildCombo = true;
        try {
            List<String> folderNames = guildService.listGuildFolderNames();
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            folderNames.forEach(model::addElement);
            guildCombo.setModel(model);

            String currentFolderName = guildService.currentGuildFolderName();
            if (currentFolderName != null && folderNames.contains(currentFolderName)) {
                guildCombo.setSelectedItem(currentFolderName);
            }
            shownGuildFilePath = appContext.guildFilePath();
        } finally {
            populatingGuildCombo = false;
        }
    }


    /**
     * Asks whether unsaved guild/lineup changes may be discarded - true
     * right away if there are none. Package-visible (not {@code private}) so
     * {@code Cow2Frame#onNewGuild()} can reuse it.
     */
    boolean confirmDiscardUnsavedChanges() {
        if (!appContext.hasUnsavedChanges()) {
            return true;
        }
        boolean guildDirty = appContext.isGuildDirty();
        boolean lineupDirty = appContext.isLineupDirty();
        String messageKey = guildDirty && lineupDirty ? "toolbar.unsaved.switchGuildAndLineup"
                : guildDirty ? "toolbar.unsaved.switchGuild" : "toolbar.unsaved.switchLineup";
        int choice = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName(messageKey),
                LanguageService.displayName("common.unsavedChangesTitle"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * Switches to the guild in the given workspace folder (see {@link
     * GuildService#switchToGuild}), showing an error dialog and restoring
     * {@link #guildCombo}'s selection if that fails. Package-visible (not
     * {@code private}) so {@code Cow2Frame#onNewGuild()}/{@code onRemoveGuild()}
     * can reuse it.
     */
    boolean switchToGuild(String folderName) {
        try {
            guildService.switchToGuild(folderName);
            return true;
        } catch (GuildService.LineupLoadException e) {
            showGuildLoadError("toolbar.loadGuildLineupError", e);
        } catch (IOException e) {
            showGuildLoadError("toolbar.loadGuildError", e);
        }
        populateGuildCombo();
        return false;
    }

    private void showGuildLoadError(String messageKey, IOException e) {
        JOptionPane.showMessageDialog(this, LanguageService.displayName(messageKey) + "\n" + e.getMessage(),
                LanguageService.displayName("common.loadGuildErrorTitle"), JOptionPane.ERROR_MESSAGE);
    }


    private void onGuildSelected() {
        String folderName = (String) guildCombo.getSelectedItem();
        if (folderName == null || folderName.equals(guildService.currentGuildFolderName())) {
            return;
        }
        if (!confirmDiscardUnsavedChanges()) {
            populateGuildCombo();
            return;
        }
        switchToGuild(folderName);
    }

    private void populateLineupCombo() {
        populatingCombo = true;
        try {
            List<String> fileNames = lineupService.listLineupFileNames();
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            fileNames.forEach(model::addElement);
            lineupCombo.setModel(model);

            String currentFileName = appContext.lineupFilePath().getFileName().toString();
            if (fileNames.contains(currentFileName)) {
                lineupCombo.setSelectedItem(currentFileName);
            }
            shownLineupFilePath = appContext.lineupFilePath();
        } finally {
            populatingCombo = false;
        }
    }

    /**
     * Loads the newly picked ".lineup" file and opens it - or shows an error
     * dialog and leaves everything unchanged if it can't be read.
     */
    private void onLineupSelected() {
        String fileName = (String) lineupCombo.getSelectedItem();
        if (fileName == null) {
            return;
        }
        try {
            lineupService.selectLineup(fileName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.loadLineupError") + "\n" + e.getMessage(),
                    LanguageService.displayName("common.loadLineupErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSaveLineup() {
        if (lineupService.isOriginalOpen()) {
            JOptionPane.showMessageDialog(this,
                    LanguageService.displayName("toolbar.saveLineup.originalMessage"),
                    LanguageService.displayName("common.originalReadOnlyTitle"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            lineupService.saveLineup();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.saveLineupError") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Display text for a lineup file name in the combo box - the file name
     * without its {@value #LINEUP_FILE_SUFFIX} suffix (purely cosmetic, see
     * class Javadoc). Package-visible (not {@code private}) so {@code
     * Cow2Frame#onRemoveLineup()} can reuse it in its confirmation dialog.
     */
    static String stripLineupSuffix(String fileName) {
        return fileName.endsWith(LINEUP_FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - LINEUP_FILE_SUFFIX.length())
                : fileName;
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
        new ReportViewerDialog(owner, reportHtml, suggestedFileName).setVisible(true);
    }


    /** Opens {@link HeroValueOverviewDialog} - one row per hero team, with a combo box to switch which value the per-fortification columns show. */
    private void onOpenHeroTeams() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new HeroValueOverviewDialog(owner, appContext).setVisible(true);
    }

    /** Opens {@link TitanValueOverviewDialog} - the TITAN counterpart of {@link #onOpenHeroTeams()}. */
    private void onOpenTitanTeams() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new TitanValueOverviewDialog(owner, appContext).setVisible(true);
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
     * Opens {@link LineupChangePlanDialog} - the step-by-step guide for turning
     * the guild's fixed "Original" lineup (the actual in-game deployment) into
     * a chosen target lineup, i.e. exactly which teams to re-arrange on the
     * Hero Wars side. Read-only like {@link #onOpenLineupComparison()}.
     */
    private void onOpenChangePlan() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new LineupChangePlanDialog(owner, appContext).setVisible(true);
    }

    /**
     * Opens {@link GuildHeroEntryDialog} - the guild-wide hero counterpart of
     * {@code FortificationPanel#openEntryDialog}'s {@code FortificationEntryDialog}.
     * Its save opens the guild's "Original" lineup, which the combo boxes
     * and the map pick up by themselves (see {@link AppContext.Listener}).
     */
    private void onOpenGuildHeroEntry() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new GuildHeroEntryDialog(owner, appContext).setVisible(true);
    }

    /** The TITAN-side counterpart of {@link #onOpenGuildHeroEntry()} - opens {@link GuildTitanEntryDialog}. */
    private void onOpenGuildTitanEntry() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new GuildTitanEntryDialog(owner, appContext).setVisible(true);
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

