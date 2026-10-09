package org.c2w.gui.cowscore;

import org.c2w.data.repository.Catalog;
import org.c2w.domain.CowScoreBonuses;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.HintStyle;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.flag.WarFlagCowScorePanel;
import org.c2w.gui.hero.HeroComboPanel;
import org.c2w.gui.hero.HeroCowScorePanel;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.gui.pet.PetCowScorePanel;
import org.c2w.gui.titan.TitanCowScorePanel;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Non-modal dialog for maintaining the CowScore marks of heroes, titans, pets and war
 * flags and the hero combos - one {@link CowScorePanel} per {@link CowScoreTab}. Opened from
 * the "File" menu, independent of the currently open guild/lineup, since the catalogs are
 * shared across every guild. Only one instance exists at a time, see {@link #open}.
 *
 * <p>Every tab has the same layout: the list on the left is equally wide in all tabs (wide
 * enough for the longest entry, see {@link CowScoreLayout#listWidth}), and the dialog is high
 * enough to show every hero fortification without scrolling (see {@link #sizeToContent}). At the top
 * of every tab, an information area says what the controls of that tab do for the CowScore
 * (see {@link #infoText(CowScoreTab)}), with the percentages of the settings - refreshed when
 * they are saved (see {@link #refreshInfoTexts()}).
 *
 * <p>In front of those tabs, the "Info" tab explains what the CowScore is and how it is made up, with
 * an example (HTML from {@code language/<language>/cowScoreInfo.html}, see {@link CowScoreInfoHtml}).
 * It edits nothing, so it has no unsaved state, and the dialog opens on it unless a {@link CowScoreTab}
 * is asked for (see {@link #open(Frame, Catalog)}). Tab indices therefore go through {@link #tabIndex}.
 *
 * <p>The shared toolbar saves every tab with unsaved changes and restores the defaults of
 * the active tab only (disabled on "Info"). A tab with unsaved changes shows a {@code *} in front of its title,
 * and the save button turns red like the main window's save buttons. Closing with unsaved
 * changes asks whether to save, discard or cancel.
 */
public final class CowScoreDialog extends JDialog {

    private static final String KEY_TITLE = "cowScore.title";
    private static final String KEY_SAVE_SCORES = "heroBuffFitScores.saveScores";
    private static final String KEY_SAVE_ERROR = "heroBuffFitScores.saveError";
    private static final String KEY_SAVE_ERROR_TAB = "cowScore.saveErrorTab";
    private static final String KEY_RESTORE_DEFAULTS = "heroBuffFitScores.restoreDefaults";
    private static final String KEY_UNSAVED_SUFFIX = "toolbar.unsavedSuffix";
    private static final String KEY_UNSAVED_QUESTION = "cowScore.unsavedQuestion";
    private static final String KEY_INFO_TAB = "cowScore.tab.info";

    /** The "Info" tab comes first; the {@link CowScoreTab}s follow, see {@link #tabIndex}. */
    private static final int INFO_TAB_INDEX = 0;

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";
    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** Width with the list at {@link CowScoreLayout#MIN_LIST_WIDTH}, and the minimum height - see {@link #sizeToContent}. */
    private static final int BASE_WIDTH = 740;
    private static final int MIN_HEIGHT = 560;

    /** The open dialog, or null - see {@link #open}. */
    private static CowScoreDialog instance;

    private final Map<CowScoreTab, CowScorePanel> panels = new EnumMap<>(CowScoreTab.class);
    private final JTabbedPane tabs = new JTabbedPane();
    /** Per tab, what its controls do - shown at the top of the tab, see {@link #infoText(CowScoreTab)}. */
    private final Map<CowScoreTab, InfoArea> infoAreas = new EnumMap<>(CowScoreTab.class);
    private final FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_SCORES, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
    private final FlatButton restoreDefaultsButton = new FlatButton(IconLoader.iconFor(ICON_RESTORE_DEFAULTS, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
    /** The explanation in the "Info" tab - see {@link #refreshInfoHtml()}. */
    private final JEditorPane infoPane = new JEditorPane();
    /** The HTML currently shown in {@link #infoPane}, as built (before Swing's own parsing). */
    private String infoHtml;
    /** Runs after a save that wrote at least one tab, may be null - see {@link #open(Frame, Catalog, CowScoreTab, Runnable)}. */
    private Runnable onSaved;

    /** Shows the CowScore dialog on the "Info" tab - like {@link #open(Frame, Catalog, Runnable)} without callback. */
    public static CowScoreDialog open(Frame owner, Catalog catalog) {
        return open(owner, catalog, (Runnable) null);
    }

    /**
     * Shows the CowScore dialog on the "Info" tab: brings the open one to front and switches to
     * "Info", or creates it if none is open. {@code onSaved} as in {@link #open(Frame, Catalog, CowScoreTab, Runnable)}.
     */
    public static CowScoreDialog open(Frame owner, Catalog catalog, Runnable onSaved) {
        CowScoreDialog dialog = show(owner, catalog, onSaved);
        dialog.selectInfoTab();
        return dialog;
    }

    /**
     * Shows the CowScore dialog on {@code initialTab}: brings the open one to front and
     * switches its tab, or creates it if none is open.
     *
     * @return the (single) dialog
     */
    public static CowScoreDialog open(Frame owner, Catalog catalog, CowScoreTab initialTab) {
        return open(owner, catalog, initialTab, null);
    }

    /**
     * Like {@link #open(Frame, Catalog, CowScoreTab)}; {@code onSaved} runs after every save that
     * wrote at least one tab (e.g. to evaluate the data status again) - it replaces the one given before.
     */
    public static CowScoreDialog open(Frame owner, Catalog catalog, CowScoreTab initialTab, Runnable onSaved) {
        CowScoreDialog dialog = show(owner, catalog, onSaved);
        dialog.selectTab(initialTab);
        return dialog;
    }

    /** Creates the single dialog if none is open, sets {@code onSaved} and brings it to front. */
    private static CowScoreDialog show(Frame owner, Catalog catalog, Runnable onSaved) {
        if (instance == null) {
            instance = new CowScoreDialog(owner, catalog);
        }
        instance.onSaved = onSaved;
        instance.setVisible(true);
        instance.toFront();
        return instance;
    }

    private CowScoreDialog(Frame owner, Catalog catalog) {
        super(owner, LanguageService.displayName(KEY_TITLE), false);
        panels.put(CowScoreTab.HEROES, new HeroCowScorePanel(catalog.heroes()));
        panels.put(CowScoreTab.TITANS, new TitanCowScorePanel(catalog.titans()));
        panels.put(CowScoreTab.PETS, new PetCowScorePanel(catalog.pets()));
        panels.put(CowScoreTab.WAR_FLAGS, new WarFlagCowScorePanel(catalog.warFlags()));
        panels.put(CowScoreTab.HERO_COMBOS, new HeroComboPanel(catalog.heroCombos(), catalog.heroes()));
        // The list on the left is equally wide in every tab - wide enough for the longest entry of all tabs.
        int listWidth = CowScoreLayout.listWidth(panels.values().stream().flatMap(p -> p.listLabels().stream()).toList());
        panels.values().forEach(panel -> panel.setListWidth(listWidth));
        tabs.addTab(LanguageService.displayName(KEY_INFO_TAB), buildInfoTab());
        // In CowScoreTab order (EnumMap), so each tab lands at tabIndex(tab).
        panels.forEach((tab, panel) -> {
            InfoArea info = new InfoArea();
            info.setText(infoText(tab));
            infoAreas.put(tab, info);
            // Space between the hint frame and the tab - the frame itself is painted by InfoArea.
            JPanel infoHolder = new JPanel(new BorderLayout());
            infoHolder.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
            infoHolder.add(info, BorderLayout.CENTER);
            JPanel content = new JPanel(new BorderLayout());
            content.add(infoHolder, BorderLayout.NORTH);
            content.add(panel.component(), BorderLayout.CENTER);
            tabs.addTab(LanguageService.displayName(tab.textKey()), content);
            panel.addChangeListener(e -> updateUnsavedState());
        });

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        setLayout(new BorderLayout());
        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        tabs.addChangeListener(e -> restoreDefaultsButton.setEnabled(!isInfoTabSelected()));
        restoreDefaultsButton.setEnabled(!isInfoTabSelected());
        updateUnsavedState();
        sizeToContent(owner, listWidth);
        setLocationRelativeTo(owner);
    }

    /**
     * Width: {@link #BASE_WIDTH} plus whatever the list is wider than {@link CowScoreLayout#MIN_LIST_WIDTH}
     * (the detail area keeps its width). Height: the packed height - the lists are kept small, so the
     * tallest detail area decides, and all hero fortifications fit without scrolling. At least
     * {@link #MIN_HEIGHT}, at most the usable screen (then the detail area scrolls).
     */
    private void sizeToContent(Window owner, int listWidth) {
        pack();
        GraphicsConfiguration screen = owner != null ? owner.getGraphicsConfiguration() : getGraphicsConfiguration();
        Rectangle bounds = screen.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        int usableWidth = bounds.width - screenInsets.left - screenInsets.right;
        int usableHeight = bounds.height - screenInsets.top - screenInsets.bottom;
        int width = Math.max(BASE_WIDTH + listWidth - CowScoreLayout.MIN_LIST_WIDTH, getWidth());
        int height = Math.max(MIN_HEIGHT, getHeight());
        setSize(Math.min(width, usableWidth), Math.min(height, usableHeight));
    }

    @Override
    public void dispose() {
        if (instance == this) {
            instance = null;
        }
        super.dispose();
    }

    /**
     * What the controls of {@code tab} do for the CowScore, with the percentages currently
     * registered in {@link TeamScoreCalculator#bonuses()} (language file key {@code <tab text key>.info}).
     */
    static String infoText(CowScoreTab tab) {
        String key = tab.textKey() + ".info";
        CowScoreBonuses b = TeamScoreCalculator.bonuses();
        return switch (tab) {
            case HEROES -> LanguageService.displayName(key, percent(b.relationPercent()),
                    percent(b.rolePercent()));
            case TITANS -> LanguageService.displayName(key, percent(b.relationPercent()),
                    percent(b.elementPercent()));
            case PETS -> LanguageService.displayName(key, percent(b.petPercent()));
            case WAR_FLAGS -> LanguageService.displayName(key, percent(b.warFlagPercent()),
                    percent(TeamScoreCalculator.WAR_FLAG_PRESENT_PERCENT));
            case HERO_COMBOS -> LanguageService.displayName(key, percent(b.comboPercent()));
        };
    }

    /** "1,25" / "1.25" in the configured language. */
    private static String percent(double value) {
        return CowScoreInfoValues.percent(JournalTexts.locale(), value);
    }

    /**
     * Shows the information texts and the "Info" tab again with the currently registered
     * percentages - after the CowScore bonuses were changed in the settings.
     */
    public void refreshInfoTexts() {
        infoAreas.forEach((tab, info) -> info.setText(infoText(tab)));
        refreshInfoHtml();
    }

    /**
     * The "Info" tab: read-only HTML, white text in the interface font on the dialog's background,
     * scrolling vertically only (the text wraps).
     */
    private JComponent buildInfoTab() {
        infoPane.setContentType("text/html");
        infoPane.setEditable(false);
        infoPane.setFocusable(false);
        infoPane.setOpaque(false);
        // The interface font instead of JEditorPane's default serif font.
        infoPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        infoPane.setFont(UIManager.getFont("Label.font"));
        infoPane.setForeground(Color.WHITE);
        refreshInfoHtml();
        JScrollPane scroll = new JScrollPane(infoPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            /**
             * Tiny, like the information areas: the HTML (unwrapped as wide as its longest paragraph)
             * must not decide the dialog's size - the editable tabs do, the text wraps and scrolls.
             */
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, 1);
            }
        };
        // Same distance to the tab's edge as the information areas of the other tabs.
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    /** Builds the "Info" HTML again (in a fresh document with the tab's style rules) and scrolls to the top. */
    private void refreshInfoHtml() {
        infoHtml = CowScoreInfoHtml.build();
        HTMLDocument document = (HTMLDocument) infoPane.getEditorKit().createDefaultDocument();
        int size = infoPane.getFont().getSize();
        StyleSheet styles = document.getStyleSheet();
        styles.addRule("body { color: #ffffff; margin: 0; }");
        styles.addRule("h2 { color: #ffffff; font-size: " + (size + 3) + "pt; font-weight: bold;"
                + " margin-top: 10px; margin-bottom: 2px; }");
        styles.addRule("p { margin-top: 4px; margin-bottom: 4px; }");
        styles.addRule("ul { margin-top: 2px; margin-bottom: 4px; margin-left: 18px; }");
        styles.addRule(".formula { font-family: Monospaced; font-size: " + (size + 1) + "pt;"
                + " margin-left: 14px; margin-top: 6px; margin-bottom: 6px; }");
        // The two examples side by side - some space between the columns.
        styles.addRule(".examples td { color: #ffffff; padding-right: 16px; }");
        infoPane.setDocument(document);
        infoPane.setText(infoHtml);
        infoPane.setCaretPosition(0);
    }

    /** The HTML shown in the "Info" tab (filled, as built) - package-visible for tests. */
    String infoHtml() {
        return infoHtml;
    }

    /** The open dialog, if any - e.g. to refresh it after the settings changed. */
    public static CowScoreDialog openInstance() {
        return instance;
    }

    /** The information area of {@code tab} - package-visible for tests. */
    JTextArea infoArea(CowScoreTab tab) {
        return infoAreas.get(tab);
    }

    /** The text of the information area of the active tab, null on "Info" (it has none) - package-visible for tests. */
    String infoText() {
        return selectedTab().map(tab -> infoAreas.get(tab).getText()).orElse(null);
    }

    /**
     * The information area at the top of a tab: wrapped, read-only white text in a teal-tinted
     * frame with rounded corners, like the "Live" label of the context bar (see {@link HintStyle}).
     * Its preferred width is tiny, so the text never decides the dialog's width, and it is always
     * three lines high (plus padding), so switching tabs does not move the content.
     */
    private static final class InfoArea extends JTextArea {
        private static final int ROWS = 3;
        /** Corner arc of the frame - 14 px radius. */
        private static final int ARC = 28;

        InfoArea() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            // The look and feel sets text areas in bold - the information reads like a label.
            setFont(UIManager.getFont("Label.font"));
            // Padding between the frame and the text.
            setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            HintStyle.paintHintBackground(g, this, HintStyle.TEAL, ARC);
            super.paintComponent(g);
        }

        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();
            return new Dimension(1, ROWS * getRowHeight() + insets.top + insets.bottom);
        }
    }

    /** Index of {@code tab} in the tabbed pane - behind the "Info" tab, so not its {@link CowScoreTab#ordinal()}. */
    private static int tabIndex(CowScoreTab tab) {
        return tab.ordinal() + INFO_TAB_INDEX + 1;
    }

    /** Switches to {@code tab}. */
    public void selectTab(CowScoreTab tab) {
        tabs.setSelectedIndex(tabIndex(tab));
    }

    /** Switches to the "Info" tab. */
    public void selectInfoTab() {
        tabs.setSelectedIndex(INFO_TAB_INDEX);
    }

    /** True while the "Info" tab is shown. */
    public boolean isInfoTabSelected() {
        return tabs.getSelectedIndex() == INFO_TAB_INDEX;
    }

    /** The tab currently shown - empty on the "Info" tab, which is no {@link CowScoreTab}. */
    public Optional<CowScoreTab> selectedTab() {
        int index = tabs.getSelectedIndex();
        for (CowScoreTab tab : CowScoreTab.values()) {
            if (tabIndex(tab) == index) {
                return Optional.of(tab);
            }
        }
        return Optional.empty();
    }

    /** The displayed title of {@code tab} - with a leading {@code *} while it has unsaved changes. */
    public String tabTitle(CowScoreTab tab) {
        return tabs.getTitleAt(tabIndex(tab));
    }

    /** The displayed title of the "Info" tab - package-visible for tests. */
    String infoTabTitle() {
        return tabs.getTitleAt(INFO_TAB_INDEX);
    }

    /** Whether "Restore defaults" can be used - not on "Info" - package-visible for tests. */
    boolean isRestoreDefaultsEnabled() {
        return restoreDefaultsButton.isEnabled();
    }

    /** The panel of {@code tab}. */
    public CowScorePanel panel(CowScoreTab tab) {
        return panels.get(tab);
    }

    private JPanel buildToolbarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        saveButton.addActionListener(e -> saveAll());
        buttons.add(saveButton);
        restoreDefaultsButton.setToolTipText(LanguageService.displayName(KEY_RESTORE_DEFAULTS));
        restoreDefaultsButton.addActionListener(e -> onRestoreDefaults());
        buttons.add(restoreDefaultsButton);
        panel.add(buttons, BorderLayout.WEST);
        return panel;
    }

    /** Puts a {@code *} in front of every unsaved tab's title and colors the save button red while anything is unsaved. */
    private void updateUnsavedState() {
        boolean anyUnsaved = false;
        for (Map.Entry<CowScoreTab, CowScorePanel> entry : panels.entrySet()) {
            boolean unsaved = entry.getValue().hasUnsavedChanges();
            anyUnsaved |= unsaved;
            String title = LanguageService.displayName(entry.getKey().textKey());
            tabs.setTitleAt(tabIndex(entry.getKey()), unsaved ? "*" + title : title);
        }
        saveButton.setIcon(IconLoader.iconFor(ICON_SAVE_SCORES, TOOLBAR_ICON_SIZE, anyUnsaved ? IconLoader.RED : IconLoader.BLUE));
        String tooltip = LanguageService.displayName(KEY_SAVE_SCORES);
        saveButton.setToolTipText(anyUnsaved ? tooltip + " – " + LanguageService.displayName(KEY_UNSAVED_SUFFIX) : tooltip);
    }

    /**
     * Saves every tab with unsaved changes - a failing tab shows the error and the others
     * are saved anyway.
     *
     * @return true if nothing is left unsaved
     */
    boolean saveAll() {
        boolean allSaved = true;
        boolean savedAny = false;
        for (Map.Entry<CowScoreTab, CowScorePanel> entry : panels.entrySet()) {
            CowScorePanel panel = entry.getValue();
            if (!panel.hasUnsavedChanges()) {
                continue;
            }
            try {
                panel.save();
                savedAny = true;
            } catch (IOException ex) {
                allSaved = false;
                Logger.logException("Saving the CowScore tab " + entry.getKey() + " failed", ex);
                JOptionPane.showMessageDialog(this,
                        LanguageService.displayName(KEY_SAVE_ERROR_TAB, LanguageService.displayName(entry.getKey().textKey()))
                                + "\n" + LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + ex.getMessage(),
                        LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
        }
        if (savedAny && onSaved != null) {
            onSaved.run();
        }
        return allSaved;
    }

    /** Restores the defaults of the active tab only, after its own confirmation question - nothing on "Info". */
    private void onRestoreDefaults() {
        Optional<CowScoreTab> tab = selectedTab();
        if (tab.isEmpty()) {
            return;
        }
        CowScorePanel panel = panels.get(tab.get());
        int answer = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName(panel.restoreDefaultsConfirmKey()),
                LanguageService.displayName("common.confirmTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            panel.restoreDefaults();
        }
    }

    /** Window "X": with unsaved changes asks save / discard / cancel; a failed save keeps the dialog open. */
    private void onClose() {
        boolean anyUnsaved = panels.values().stream().anyMatch(CowScorePanel::hasUnsavedChanges);
        if (anyUnsaved) {
            int choice = JOptionPane.showConfirmDialog(this, LanguageService.displayName(KEY_UNSAVED_QUESTION),
                    LanguageService.displayName("common.unsavedChangesTitle"),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION && !saveAll()) {
                return;
            }
            if (choice == JOptionPane.NO_OPTION) {
                Logger.log("CowScore dialog closed - unsaved changes discarded");
            }
        }
        dispose();
    }
}
