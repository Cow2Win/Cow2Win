package org.c2w.gui;

import org.c2w.C2WApp;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.fort.FortificationMapPanel;
import org.c2w.gui.guild.GuildEntryDialog;
import org.c2w.util.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

public class ToolbarPanel extends JPanel {

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the label in front of the guild combo box. */
    private static final String KEY_GUILD_LABEL = "toolbar.guild";

    /** Glob pattern (see {@link Files#newDirectoryStream(Path, String)}) matching lineup files in the guild folder. */
    private static final String LINEUP_FILE_GLOB = "*.lineup";

    /**
     * File name suffix of a lineup file - stripped for display in the combo
     * box (see {@link #stripLineupSuffix}) and appended when creating one
     * (see {@code Cow2Frame#onNewLineup()}). Package-visible (not
     * {@code private}) so {@code Cow2Frame} can reuse it there, since the
     * "new/remove/clear lineup" logic moved to that class's own "Lineup"
     * menu (see {@link #stripLineupSuffix}).
     */
    static final String LINEUP_FILE_SUFFIX = ".lineup";

    /**
     * Characters not allowed in a Windows file name - rejected in
     * {@link #onNewLineup()} and {@code Cow2Frame#onNewGuild()}. Package-visible
     * (not {@code private}) so {@code Cow2Frame} can reuse it there instead of
     * duplicating it, since the "new guild" logic moved to that class's own
     * "Guild" menu (see {@link #containsIllegalFilenameChar}).
     */
    static final String ILLEGAL_FILENAME_CHARS = "<>:\"/\\|?*";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the label in front of the lineup combo box. */
    private static final String KEY_LINEUP_LABEL = "toolbar.lineup";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "save lineup" button (see {@link #onSaveLineup()}). */
    private static final String KEY_SAVE_LINEUP = "toolbar.saveLineup";

    private static final String ICON_SAVE_LINEUP = "/images/app/save.png";

    private static final String KEY_GENERATE_REPORT = "toolbar.generateReport";

    private static final String ICON_GENERATE_REPORT = "/images/app/lineup-report.png";

    private static final String KEY_ALL_TEAMS = "toolbar.allTeams";

    private static final String ICON_ALL_TEAMS = "/images/app/hexagon.png";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "all team scores" button (see {@link #onOpenAllTeamScores()}). */
    private static final String KEY_ALL_TEAMS_SCORES = "toolbar.allTeamsScores";

    private static final String ICON_ALL_TEAMS_SCORES = ICON_ALL_TEAMS;

    /** Package-visible (not {@code private}) so {@code Cow2Frame} can size its "Guild" menu item icons to match (see class Javadoc on the removed guild buttons). */
    static final int TOOLBAR_ICON_SIZE = 20;

    /**
     * Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of
     * the "save guild" button (see {@link #onSaveGuild()}). Moved here
     * (2026-09-09) from {@code TeamsOverviewPanel} together with every other
     * toolbar control that used to live in that class's own toolbar - kept
     * its "teamsOverview.*" key naming (no language file changes needed).
     */
    private static final String KEY_SAVE_GUILD = "teamsOverview.saveGuild";

    /** Classpath path of the "save guild" button's icon (see {@link IconLoader}). */
    private static final String ICON_SAVE_GUILD = "/images/app/save.png";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the label in front of the algorithm combo box. */
    private static final String KEY_ALGORITHM_LABEL = "teamsOverview.algorithm";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "run algorithm" button (see {@link #onRunAlgorithm()}). */
    private static final String KEY_RUN_ALGORITHM = "teamsOverview.runAlgorithm";

    /** Classpath path of the "run algorithm" button's icon (see {@link IconLoader}). */
    private static final String ICON_RUN_ALGORITHM = "/images/app/run.png";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "compare lineups" button (see {@link #onOpenLineupComparison()}). Added 2026-09-13. */
    private static final String KEY_COMPARE_LINEUPS = "toolbar.compareLineups";

    /** Reused rather than a dedicated icon (none of the existing ones reads as "compare") - same "hexagon" family already used for {@link #ICON_ALL_TEAMS}/{@link #ICON_ALL_TEAMS_SCORES}, told apart by shape (paired hexagons) instead of color. */
    private static final String ICON_COMPARE_LINEUPS = "/images/app/hexagon-team.png";

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for the tooltip of the "open guild-wide team assignment" button (see {@link #onOpenGuildEntry()}). */
    private static final String KEY_OPEN_GUILD_ENTRY = "toolbar.openGuildEntry";

    /** Reused rather than a dedicated icon - same "square" (HERO fortification) icon {@link org.c2w.data.model.FortificationType#HERO} itself uses, since this button opens the guild-wide counterpart of a single fortification's own team-entry dialog. */
    private static final String ICON_OPEN_GUILD_ENTRY = "/images/app/square.png";

    private final AppContext appContext;
    private final FortificationMapPanel fortificationMapPanel;

    /** Runs {@code Cow2Frame#onGuildSwitched()} after a successful guild switch/creation (see {@link #onGuildSelected()}/{@code Cow2Frame#onNewGuild()}) - passed in from the outside since this panel has no reference to {@code TeamsOverviewPanel}'s window title, which also needs refreshing. */
    private final Runnable onGuildSwitched;

    /**
     * The panel whose "save guild"/"run algorithm" actions the
     * corresponding buttons below trigger - this panel owns the guild/team
     * data those actions work on (see {@link TeamsOverviewPanel#saveGuild()}/
     * {@link TeamsOverviewPanel#runAlgorithm}), so unlike every other
     * control in this toolbar, those two need a direct reference to it
     * rather than just a callback.
     */
    private final TeamsOverviewPanel teamsOverviewPanel;

    private final JComboBox<String> lineupCombo = new JComboBox<>();

    /** Combo box listing every guild folder under workspace/ (see {@link #populateGuildCombo()}) - added 2026-09-05, sits before {@link #lineupCombo}, separated from it by a {@link JSeparator} (see constructor). */
    private final JComboBox<String> guildCombo = new JComboBox<>();

    /** Lists the available lineup algorithms (see {@link #onRunAlgorithm()}) - moved here 2026-09-09 from {@code TeamsOverviewPanel}, see {@link #KEY_SAVE_GUILD}. */
    private final JComboBox<LineupAlgorithm> algorithmCombo = new JComboBox<>();

    /** Reports the outcome of the last algorithm run (see {@link #onRunAlgorithm()}) - moved here 2026-09-09, see {@link #KEY_SAVE_GUILD}. */
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

    public ToolbarPanel(AppContext appContext, FortificationMapPanel fortificationMapPanel,
                        TeamsOverviewPanel teamsOverviewPanel, Runnable onGuildSwitched) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 4));
        if (appContext == null) {
            throw new IllegalArgumentException("ToolbarPanel needs a guildContext");
        }
        if (fortificationMapPanel == null) {
            throw new IllegalArgumentException("ToolbarPanel needs a fortificationMapPanel");
        }
        if (teamsOverviewPanel == null) {
            throw new IllegalArgumentException("ToolbarPanel needs a teamsOverviewPanel");
        }
        if (onGuildSwitched == null) {
            throw new IllegalArgumentException("ToolbarPanel needs an onGuildSwitched callback");
        }
        this.appContext = appContext;
        this.fortificationMapPanel = fortificationMapPanel;
        this.teamsOverviewPanel = teamsOverviewPanel;
        this.onGuildSwitched = onGuildSwitched;

        add(new JLabel(LanguageService.displayName(KEY_GUILD_LABEL)));
        add(guildCombo);
        populateGuildCombo();
        guildCombo.addActionListener(e -> {
            if (!populatingGuildCombo) {
                onGuildSelected();
            }
        });


        FlatButton saveGuildButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_GUILD, TOOLBAR_ICON_SIZE,IconLoader.BLUE));
        saveGuildButton.setToolTipText(LanguageService.displayName(KEY_SAVE_GUILD));
        saveGuildButton.addActionListener(e -> onSaveGuild());
        add(saveGuildButton);

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

        FlatButton saveLineupButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_LINEUP, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        saveLineupButton.setToolTipText(LanguageService.displayName(KEY_SAVE_LINEUP));
        saveLineupButton.addActionListener(e -> onSaveLineup());
        add(saveLineupButton);

        FlatButton generateReportButton = new FlatButton(IconLoader.iconFor(ICON_GENERATE_REPORT, TOOLBAR_ICON_SIZE));
        generateReportButton.setToolTipText(LanguageService.displayName(KEY_GENERATE_REPORT));
        generateReportButton.addActionListener(e -> onGenerateReport());
        add(generateReportButton);

        FlatButton allTeamsButton = new FlatButton(IconLoader.iconFor(ICON_ALL_TEAMS, TOOLBAR_ICON_SIZE));
        allTeamsButton.setToolTipText(LanguageService.displayName(KEY_ALL_TEAMS));
        allTeamsButton.addActionListener(e -> onOpenAllTeams());
        add(allTeamsButton);

        FlatButton allTeamsScoresButton =
                new FlatButton(IconLoader.iconFor(ICON_ALL_TEAMS_SCORES, TOOLBAR_ICON_SIZE, Color.WHITE));
        allTeamsScoresButton.setToolTipText(LanguageService.displayName(KEY_ALL_TEAMS_SCORES));
        allTeamsScoresButton.addActionListener(e -> onOpenAllTeamScores());
        add(allTeamsScoresButton);

        FlatButton runAlgorithmButton = new FlatButton(IconLoader.iconFor(ICON_RUN_ALGORITHM, TOOLBAR_ICON_SIZE));
        runAlgorithmButton.setToolTipText(LanguageService.displayName(KEY_RUN_ALGORITHM));
        runAlgorithmButton.addActionListener(e -> onRunAlgorithm());
        add(runAlgorithmButton);

        FlatButton compareLineupsButton = new FlatButton(IconLoader.iconFor(ICON_COMPARE_LINEUPS, TOOLBAR_ICON_SIZE));
        compareLineupsButton.setToolTipText(LanguageService.displayName(KEY_COMPARE_LINEUPS));
        compareLineupsButton.addActionListener(e -> onOpenLineupComparison());
        add(compareLineupsButton);

        FlatButton openGuildEntryButton = new FlatButton(IconLoader.iconFor(ICON_OPEN_GUILD_ENTRY, TOOLBAR_ICON_SIZE));
        openGuildEntryButton.setToolTipText(LanguageService.displayName(KEY_OPEN_GUILD_ENTRY));
        openGuildEntryButton.addActionListener(e -> onOpenGuildEntry());
        add(openGuildEntryButton);

        add(statusLabel);
    }


    /**
     * Runs the algorithm configured as the default in the Settings dialog
     * (see {@link Config#getDefaultAlgorithm()}/{@code SettingsDialog}, added
     * 2026-09-15 - Cow2Win todos 3.4), matched against {@link
     * LineupAlgorithms#ALL} by {@link LineupAlgorithm#displayName()}. Falls
     * back to the first entry in {@link LineupAlgorithms#ALL} if nothing is
     * configured yet, or if a previously configured algorithm no longer
     * exists (e.g. renamed/removed) - same "just use the first one" behavior
     * this method always had before this became configurable.
     */
    private void onRunAlgorithm() {
        String configuredAlgorithm = Config.getDefaultAlgorithm();
        LineupAlgorithm algorithm = LineupAlgorithms.ALL.stream()
                .filter(a -> a.displayName().equals(configuredAlgorithm))
                .findFirst()
                .orElseGet(() -> LineupAlgorithms.ALL.isEmpty() ? null : LineupAlgorithms.ALL.get(0));
        if (algorithm == null) {
            return;
        }
        int assigned = teamsOverviewPanel.runAlgorithm(algorithm);
        Logger.log(algorithm.displayName() + ": " + assigned + " team(s) newly assigned.");
    }


    private void onSaveGuild() {
        teamsOverviewPanel.saveGuild();
    }


    /** Package-visible (not {@code private}) so {@code Cow2Frame#onNewGuild()}/{@code onRemoveGuild()} can reuse it - see the class-level note on the removed guild buttons. */
    Path workspaceDir() {
        Path guildDir = appContext.guildFilePath().getParent();
        Path workspace = guildDir == null ? null : guildDir.getParent();
        return workspace == null ? Config.getWorkspaceDir() : workspace;
    }


    /** Package-visible (not {@code private}) - see {@link #workspaceDir()}. */
    java.util.List<String> listGuildFolderNames() {
        java.util.List<String> result = new ArrayList<>();
        Path workspaceDir = workspaceDir();
        if (!Files.isDirectory(workspaceDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workspaceDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && Files.isRegularFile(path.resolve(C2WApp.GUILD_FILE_NAME))) {
                    result.add(path.getFileName().toString());
                }
            }
        } catch (IOException e) {
            Logger.logException("Could not list guild folders in " + workspaceDir, e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private void populateGuildCombo() {
        populatingGuildCombo = true;
        try {
            java.util.List<String> folderNames = listGuildFolderNames();
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            folderNames.forEach(model::addElement);
            guildCombo.setModel(model);

            Path currentGuildDir = appContext.guildFilePath().getParent();
            String currentFolderName = currentGuildDir == null ? null : currentGuildDir.getFileName().toString();
            if (currentFolderName != null && folderNames.contains(currentFolderName)) {
                guildCombo.setSelectedItem(currentFolderName);
            }
        } finally {
            populatingGuildCombo = false;
        }
    }


    /** Package-visible (not {@code private}) - see {@link #workspaceDir()}. */
    boolean confirmDiscardUnsavedChanges() {
        if (!GuiUtils.editedGuild && !GuiUtils.editedLineup) {
            return true;
        }
        String what = GuiUtils.editedGuild && GuiUtils.editedLineup ? "guild and lineup"
                : GuiUtils.editedGuild ? "guild" : "lineup";
        int choice = JOptionPane.showConfirmDialog(this,
                "There are unsaved " + what + " changes. Switch guild anyway?",
                "Unsaved changes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /** Package-visible (not {@code private}) - see {@link #workspaceDir()}. */
    boolean switchToGuild(Path guildDir, Path guildFilePath) {
        Guild guild;
        try {
            guild = GuildRepository.load(guildFilePath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not load guild:\n" + e.getMessage(),
                    "Error while loading guild", JOptionPane.ERROR_MESSAGE);
            populateGuildCombo();
            return false;
        }

        java.util.List<String> lineupFileNames = listLineupFileNames(guildDir);
        Path lineupPath;
        Lineup lineup;
        try {
            lineupPath = lineupFileNames.isEmpty()
                    ? C2WApp.createInitialLineupFile(guild.name(), guildDir)
                    : guildDir.resolve(lineupFileNames.get(0));
            lineup = LineupRepository.load(lineupPath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not load a lineup for this guild:\n" + e.getMessage(),
                    "Error while loading guild", JOptionPane.ERROR_MESSAGE);
            populateGuildCombo();
            return false;
        }

         appContext.set(guild, guildFilePath);
         appContext.set(lineup, lineupPath);
        Config.setLastGuildPath(guildFilePath.toString());
        Config.setLastLineUpPath(lineupPath.toString());
        Config.save();
        GuiUtils.editedLineup = false;
        fortificationMapPanel.refresh(lineup, guild);
        populateGuildCombo();
        populateLineupCombo();
        onGuildSwitched.run();
        return true;
    }


    private void onGuildSelected() {
        String folderName = (String) guildCombo.getSelectedItem();
        if (folderName == null) {
            return;
        }
        Path guildDir = workspaceDir().resolve(folderName);
        Path guildFilePath = guildDir.resolve(C2WApp.GUILD_FILE_NAME);
        if (guildFilePath.equals(appContext.guildFilePath())) {
            return;
        }
        if (!confirmDiscardUnsavedChanges()) {
            populateGuildCombo();
            return;
        }
        if (switchToGuild(guildDir, guildFilePath)) {
            Logger.log("Switched to guild: " + guildFilePath);
        }
    }

    /**
     * The folder name currently selected in {@link #guildCombo} (or
     * {@code null} if nothing is selected) - package-visible (not
     * {@code private}) so {@code Cow2Frame#onRemoveGuild()} can read it now
     * that the "remove guild" logic (and the "new guild" logic alongside it)
     * moved there into the new "Guild" menu, taking the {@code FlatButton}s
     * that used to trigger them with it. {@link #guildCombo}'s selection
     * always mirrors the currently open guild (picking a different entry
     * already triggers an immediate {@link #onGuildSelected()} switch or
     * reverts the selection), so the selected item can be removed without
     * first checking it against {@link #appContext}.
     */
    String selectedGuildFolderName() {
        return (String) guildCombo.getSelectedItem();
    }

    /**
     * Package-visible (not {@code private}) so {@code Cow2Frame#onNewLineup()}/
     * {@code onRemoveLineup()} can refresh {@link #lineupCombo} after
     * changing which ".lineup" files exist, now that logic moved there into
     * the new "Lineup" menu, see {@link #selectedLineupFileName()}.
     */
    void populateLineupCombo() {
        populatingCombo = true;
        try {
            java.util.List<String> fileNames = listLineupFileNames();
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            fileNames.forEach(model::addElement);
            lineupCombo.setModel(model);

            String currentFileName = appContext.lineupFilePath().getFileName().toString();
            if (fileNames.contains(currentFileName)) {
                lineupCombo.setSelectedItem(currentFileName);
            }
        } finally {
            populatingCombo = false;
        }
    }

    /** Package-visible (not {@code private}) - see {@link #populateLineupCombo()}. */
    java.util.List<String> listLineupFileNames() {
        return listLineupFileNames(appContext.guildFilePath().getParent());
    }

    /**
     * The file name currently selected in {@link #lineupCombo} (or
     * {@code null} if nothing is selected) - package-visible (not
     * {@code private}) so {@code Cow2Frame#onRemoveLineup()} can read it,
     * see {@link #populateLineupCombo()}. Mirrors {@link #selectedGuildFolderName()}.
     */
    String selectedLineupFileName() {
        return (String) lineupCombo.getSelectedItem();
    }

    /**
     * Lists the file names (not full paths) of every ".lineup" file
     * directly inside the given guild folder, sorted alphabetically. Empty
     * if the folder is null, does not exist, or cannot be read - logged,
     * not shown as a dialog, since this runs during construction too (via
     * {@link #listLineupFileNames()}).
     */
    private java.util.List<String> listLineupFileNames(Path guildDir) {
        java.util.List<String> result = new ArrayList<>();
        if (guildDir == null || !Files.isDirectory(guildDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(guildDir, LINEUP_FILE_GLOB)) {
            for (Path path : stream) {
                result.add(path.getFileName().toString());
            }
        } catch (IOException e) {
            Logger.logException("Could not list lineup files in " + guildDir, e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /**
     * Loads the newly picked ".lineup" file and applies it everywhere it
     * needs to take effect (see class Javadoc) - or shows an error dialog
     * and leaves everything unchanged if it can't be read.
     */
    private void onLineupSelected() {
        String fileName = (String) lineupCombo.getSelectedItem();
        if (fileName == null) {
            return;
        }
        Path guildDir = appContext.guildFilePath().getParent();
        Path lineupPath = guildDir.resolve(fileName);
        try {
            Lineup lineup = LineupRepository.load(lineupPath);
            appContext.set(lineup, lineupPath);
            Config.setLastLineUpPath(lineupPath.toString());
            Config.save();
            GuiUtils.editedLineup = false;
            fortificationMapPanel.refresh(lineup);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not load lineup:\n" + e.getMessage(),
                    "Error while loading lineup", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSaveLineup() {
        try {
            LineupRepository.save(appContext.lineup(), appContext.lineupFilePath());
            GuiUtils.editedLineup = false;
            Logger.log("Saved: " + appContext.lineupFilePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save lineup:\n" + ex.getMessage(),
                    "Error while saving", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Display text for a lineup file name in the combo box - the file name
     * without its {@value #LINEUP_FILE_SUFFIX} suffix (purely cosmetic, see
     * class Javadoc). Package-visible (not {@code private}) so {@code
     * Cow2Frame#onRemoveLineup()} can reuse it in its confirmation dialog,
     * see {@link #populateLineupCombo()}.
     */
    static String stripLineupSuffix(String fileName) {
        return fileName.endsWith(LINEUP_FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - LINEUP_FILE_SUFFIX.length())
                : fileName;
    }

    private void onGenerateReport() {
        try {
            Path reportPath = ReportGenerator.generate(
                    appContext.lineup(), appContext.guild(), appContext.lineupFilePath());
            Logger.log("Report generated: " + reportPath);
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new ReportViewerDialog(owner, reportPath).setVisible(true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not generate report:\n" + e.getMessage(),
                    "Error while generating report", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void onOpenAllTeams() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new AllTeamsOverviewDialog(owner, appContext, fortificationMapPanel).setVisible(true);
    }

    private void onOpenAllTeamScores() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new AllTeamsScoreOverviewDialog(owner, appContext, fortificationMapPanel).setVisible(true);
    }

    /**
     * Opens {@link LineupComparisonDialog} (added 2026-09-13) - purely a
     * read-only preview/comparison, so unlike {@link #onOpenAllTeamScores()}
     * it needs no {@link #fortificationMapPanel} reference (nothing here
     * ever changes {@link #appContext}'s lineup).
     */
    private void onOpenLineupComparison() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new LineupComparisonDialog(owner, appContext).setVisible(true);
    }

    /**
     * Opens {@link GuildEntryDialog} - the guild-wide counterpart of
     * {@code FortificationPanel#openEntryDialog}'s {@code FortificationEntryDialog},
     * refreshed the same way on save via {@link FortificationMapPanel#refreshAfterExternalSave()}.
     */
    private void onOpenGuildEntry() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new GuildEntryDialog(owner, appContext, fortificationMapPanel::refreshAfterExternalSave).setVisible(true);
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

