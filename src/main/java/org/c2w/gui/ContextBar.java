package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.LineupFiles;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.HintStyle;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.LineupService;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The upper row of the main window's top area: what is selected here applies everywhere.
 * From left to right: the guild with its save button, the {@linkplain AppContext#fortificationType()
 * fortification type} (heroes | titans), the lineup with its save button, a hint while something
 * is unsaved, and - right-aligned - how old the guild's "Original" lineup (the one currently set
 * up in the game) is.
 *
 * <p>Everything follows {@link AppContext} by itself (see {@link AppContext.Listener}), no
 * matter who changed it (this bar, the menus in {@code Cow2Frame}, the team-entry dialogs, ...).
 */
public class ContextBar extends JPanel {

    private static final String KEY_GUILD_LABEL = "toolbar.guild";
    private static final String KEY_LINEUP_LABEL = "toolbar.lineup";
    private static final String KEY_FORTIFICATION_TYPE_LABEL = "context.fortificationType";
    private static final String KEY_UNSAVED = "toolbar.unsavedSuffix";
    private static final String KEY_UNSAVED_TOOLTIP_GUILD = "context.unsavedTooltip.guild";
    private static final String KEY_UNSAVED_TOOLTIP_LINEUP = "context.unsavedTooltip.lineup";
    private static final String KEY_UNSAVED_TOOLTIP_BOTH = "context.unsavedTooltip.both";
    private static final String KEY_ORIGINAL = "context.original";
    private static final String KEY_ORIGINAL_MISSING = "context.originalMissing";
    private static final String KEY_ORIGINAL_TOOLTIP = "context.originalTooltip";

    /** Language file keys of the fortification type buttons - the labels of the former map filter checkboxes. */
    private static final Map<FortificationType, String> FORTIFICATION_TYPE_KEYS = Map.of(
            FortificationType.HERO, "fortificationMap.showHeroes",
            FortificationType.TITAN, "fortificationMap.showTitans");

    /** Orange of the "unsaved changes" hint - package-visible, also the "attention" light of {@link StageStatus}. */
    static final Color UNSAVED_COLOR = new Color(0xF0, 0xA0, 0x30);

    /** Teal of the "Original" lineup status - shared with other hints, see {@link HintStyle#TEAL}. */
    private static final Color ORIGINAL_COLOR = HintStyle.TEAL;

    /** Background of the selected fortification type segment, and the line around the switch. */
    private static final Color SWITCH_SELECTED_BACKGROUND = new Color(0x5A, 0x6A, 0x72);
    private static final Color SWITCH_BORDER_COLOR = new Color(255, 255, 255, 70);

    /** Darkens the panel background a little (see {@link #barBackground()}), so the bar stands apart from the action bar below it. */
    private static final Color BAR_BACKGROUND = new Color(0, 0, 0, 70);

    private final AppContext appContext;
    private final GuildService guildService;
    private final LineupService lineupService;

    private final JComboBox<String> guildCombo = new JComboBox<>();
    private final JComboBox<String> lineupCombo = new JComboBox<>();
    private final Map<FortificationType, JToggleButton> fortificationTypeButtons = new EnumMap<>(FortificationType.class);
    private final PillLabel unsavedLabel = new PillLabel(UNSAVED_COLOR);
    private final PillLabel originalLabel = new PillLabel(ORIGINAL_COLOR);

    /** Guild file {@link #guildCombo} was last populated for - it is only rebuilt when {@link AppContext#guildFilePath()} moves away from this. */
    private Path shownGuildFilePath;

    /** Lineup file {@link #lineupCombo} was last populated for - it is only rebuilt when {@link AppContext#lineupFilePath()} moves away from this. */
    private Path shownLineupFilePath;

    /**
     * True while {@link #populateLineupCombo()} is (re)building the combo
     * box's model/selection - {@link JComboBox#setModel}/{@code setSelectedItem}
     * both fire the same action event a user pick would, so the selection
     * handler (see {@link #onLineupSelected()}) checks this flag first and
     * does nothing while it is true, to avoid reloading the lineup that is
     * already open merely because the combo box was (re)populated.
     */
    private boolean populatingLineupCombo = false;

    /** {@link #guildCombo} counterpart of {@link #populatingLineupCombo} - guards {@link #onGuildSelected()} the same way. */
    private boolean populatingGuildCombo = false;

    /**
     * @param actions the main window's actions - the save buttons right after the guild and
     *                the lineup combo box are bound to {@link ActionId#SAVE_GUILD} and
     *                {@link ActionId#SAVE_LINEUP} (registered by {@link ActionBar})
     */
    public ContextBar(AppContext appContext, MainActions actions) {
        super(new BorderLayout());
        if (appContext == null) {
            throw new IllegalArgumentException("ContextBar needs an AppContext");
        }
        if (actions == null) {
            throw new IllegalArgumentException("ContextBar needs the main actions");
        }
        this.appContext = appContext;
        this.guildService = new GuildService(appContext);
        this.lineupService = new LineupService(appContext);
        // Opaque like the action bar below it (the toolbar always was), but slightly darker
        // with a line underneath, so the two rows stand apart.
        setOpaque(true);
        setBackground(barBackground());
        setBorder(new MatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 40)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        left.setOpaque(false);
        left.add(new JLabel(LanguageService.displayName(KEY_GUILD_LABEL)));
        left.add(guildCombo);
        left.add(FlatButton.forAction(actions.get(ActionId.SAVE_GUILD)));
        left.add(Box.createHorizontalStrut(8));
        left.add(new JLabel(LanguageService.displayName(KEY_FORTIFICATION_TYPE_LABEL)));
        left.add(buildFortificationTypeSwitch());
        left.add(Box.createHorizontalStrut(8));
        left.add(new JLabel(LanguageService.displayName(KEY_LINEUP_LABEL)));
        lineupCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String fileName) {
                    setText(LineupFiles.displayName(fileName));
                }
                return this;
            }
        });
        left.add(lineupCombo);
        left.add(FlatButton.forAction(actions.get(ActionId.SAVE_LINEUP)));
        unsavedLabel.setText(LanguageService.displayName(KEY_UNSAVED));
        left.add(unsavedLabel);
        add(left, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 6));
        right.setOpaque(false);
        originalLabel.setToolTipText(LanguageService.displayName(KEY_ORIGINAL_TOOLTIP));
        right.add(originalLabel);
        add(right, BorderLayout.EAST);

        populateGuildCombo();
        guildCombo.addActionListener(e -> {
            if (!populatingGuildCombo) {
                onGuildSelected();
            }
        });
        populateLineupCombo();
        lineupCombo.addActionListener(e -> {
            if (!populatingLineupCombo) {
                onLineupSelected();
            }
        });
        updateFortificationTypeSwitch();
        updateUnsavedHint();
        updateOriginalStatus();

        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                if (!appContext.guildFilePath().equals(shownGuildFilePath)) {
                    populateGuildCombo();
                }
                updateOriginalStatus();
            }

            @Override
            public void lineupChanged() {
                if (!appContext.lineupFilePath().equals(shownLineupFilePath)) {
                    populateLineupCombo();
                }
                // Saving the "Original" lineup (team-entry dialogs) reopens it - its date changes.
                updateOriginalStatus();
            }

            @Override
            public void dirtyStateChanged() {
                updateUnsavedHint();
            }

            @Override
            public void fortificationTypeChanged() {
                updateFortificationTypeSwitch();
            }
        });
    }

    /** The look and feel's panel background, mixed with {@link #BAR_BACKGROUND} - a little darker. */
    static Color barBackground() {
        Color panel = UIManager.getColor("Panel.background");
        if (panel == null) {
            panel = Color.DARK_GRAY;
        }
        float alpha = BAR_BACKGROUND.getAlpha() / 255f;
        return new Color(
                Math.round(panel.getRed() * (1 - alpha) + BAR_BACKGROUND.getRed() * alpha),
                Math.round(panel.getGreen() * (1 - alpha) + BAR_BACKGROUND.getGreen() * alpha),
                Math.round(panel.getBlue() * (1 - alpha) + BAR_BACKGROUND.getBlue() * alpha));
    }

    // --- fortification type ---

    /**
     * Two toggle buttons "Heroes | Titans" in one group - a segmented switch, each segment in
     * its fortification type's color. The plain basic button UI is used on purpose: the look
     * and feel would draw a toggle button as a sliding switch.
     */
    private JComponent buildFortificationTypeSwitch() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 0, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createLineBorder(SWITCH_BORDER_COLOR));
        ButtonGroup group = new ButtonGroup();
        for (FortificationType type : FortificationType.values()) {
            JToggleButton button = new JToggleButton(LanguageService.displayName(FORTIFICATION_TYPE_KEYS.get(type)));
            button.setUI(new BasicToggleButtonUI());
            button.setForeground(FortificationTypeStyle.color(type));
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(true);
            button.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> appContext.setFortificationType(type));
            group.add(button);
            fortificationTypeButtons.put(type, button);
            panel.add(button);
        }
        return panel;
    }

    /** Selects the segment of the current fortification type: bold on a light background, the other one plain. */
    private void updateFortificationTypeSwitch() {
        fortificationTypeButtons.forEach((type, button) -> {
            boolean selected = type == appContext.fortificationType();
            button.setSelected(selected);
            button.setFont(button.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
            button.setBackground(selected ? SWITCH_SELECTED_BACKGROUND : getBackground());
        });
    }

    // --- unsaved hint ---

    /** Shows the hint only while something is unsaved; its tooltip says what. */
    private void updateUnsavedHint() {
        String tooltipKey = unsavedTooltipKey(appContext.isGuildDirty(), appContext.isLineupDirty());
        unsavedLabel.setVisible(tooltipKey != null);
        unsavedLabel.setToolTipText(tooltipKey == null ? null : LanguageService.displayName(tooltipKey));
    }

    /** Language file key of the "unsaved" hint's tooltip - guild, lineup or both - or null if nothing is unsaved. */
    static String unsavedTooltipKey(boolean guildDirty, boolean lineupDirty) {
        if (guildDirty && lineupDirty) {
            return KEY_UNSAVED_TOOLTIP_BOTH;
        }
        if (guildDirty) {
            return KEY_UNSAVED_TOOLTIP_GUILD;
        }
        return lineupDirty ? KEY_UNSAVED_TOOLTIP_LINEUP : null;
    }

    // --- "Original" lineup status ---

    /** Shows the date of the live lineup again - after "apply to live" changed it without a lineup event. */
    public void refreshLiveStatus() {
        updateOriginalStatus();
    }

    private void updateOriginalStatus() {
        Path guildFilePath = appContext.guildFilePath();
        Path originalFile = guildFilePath == null || guildFilePath.getParent() == null ? null
                : LineupFiles.originalPathFor(guildFilePath.getParent());
        originalLabel.setText(originalStatusText(originalFile, JournalTexts.locale()));
    }

    /**
     * "Live, as of 28/09/2026" - the last-modified date of {@code originalFile}
     * in {@code locale}'s short date format - or "In game: no Original lineup yet" if there is
     * no such file (or it is null). Public: also shown in the info panel of the output stage view.
     */
    public static String originalStatusText(Path originalFile, Locale locale) {
        if (originalFile == null || !Files.isRegularFile(originalFile)) {
            return LanguageService.displayName(KEY_ORIGINAL_MISSING);
        }
        try {
            LocalDate date = LocalDate.ofInstant(Files.getLastModifiedTime(originalFile).toInstant(), ZoneId.systemDefault());
            return LanguageService.displayName(KEY_ORIGINAL,
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(date));
        } catch (IOException e) {
            Logger.logException("Could not read the date of " + originalFile, e);
            return LanguageService.displayName(KEY_ORIGINAL_MISSING);
        }
    }

    // --- guild ---

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
     * {@code Cow2Frame} can reuse it (new guild, journal guild switch).
     */
    boolean confirmDiscardUnsavedChanges() {
        if (!appContext.hasUnsavedChanges()) {
            return true;
        }
        boolean guildDirty = appContext.isGuildDirty();
        boolean lineupDirty = appContext.isLineupDirty();
        String messageKey = guildDirty && lineupDirty ? "toolbar.unsaved.switchGuildAndLineup"
                : guildDirty ? "toolbar.unsaved.switchGuild" : "toolbar.unsaved.switchLineup";
        int choice = JOptionPane.showConfirmDialog(dialogParent(),
                LanguageService.displayName(messageKey),
                LanguageService.displayName("common.unsavedChangesTitle"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * Switches to the guild in the given workspace folder (see {@link
     * GuildService#switchToGuild}), showing an error dialog and restoring
     * {@link #guildCombo}'s selection if that fails. Package-visible (not
     * {@code private}) so {@code Cow2Frame} (new/remove guild, journal guild switch)
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
        JOptionPane.showMessageDialog(dialogParent(), LanguageService.displayName(messageKey) + "\n" + e.getMessage(),
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

    // --- lineup ---

    private void populateLineupCombo() {
        populatingLineupCombo = true;
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
            populatingLineupCombo = false;
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
            JOptionPane.showMessageDialog(dialogParent(), LanguageService.displayName("common.loadLineupError") + "\n" + e.getMessage(),
                    LanguageService.displayName("common.loadLineupErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Parent of this bar's message dialogs: the main window, so they appear centered on it. */
    private Component dialogParent() {
        Window window = SwingUtilities.getWindowAncestor(this);
        return window != null ? window : this;
    }

    /**
     * A small label on a rounded, translucent background with a border in its own color, with fully
     * rounded ends - for the hints in this bar. Painted by {@link HintStyle#paintHintBackground}.
     */
    private static final class PillLabel extends JLabel {

        private final Color color;

        PillLabel(Color color) {
            this.color = color;
            setForeground(color);
            setFont(getFont().deriveFont(Font.BOLD));
            setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            HintStyle.paintHintBackground(g, this, color, getHeight());
            super.paintComponent(g);
        }
    }
}
