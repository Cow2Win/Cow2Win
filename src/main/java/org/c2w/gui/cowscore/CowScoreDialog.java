package org.c2w.gui.cowscore;

import org.c2w.data.repository.Catalog;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.flag.WarFlagCowScorePanel;
import org.c2w.gui.hero.HeroCowScorePanel;
import org.c2w.gui.pet.PetCowScorePanel;
import org.c2w.gui.titan.TitanCowScorePanel;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Non-modal dialog for maintaining the CowScore marks of heroes, titans, pets and war
 * flags - one {@link CowScorePanel} per {@link CowScoreTab}. Opened from the "File" menu,
 * independent of the currently open guild/lineup, since the catalogs are shared across
 * every guild. Only one instance exists at a time, see {@link #open}.
 *
 * <p>The shared toolbar saves every tab with unsaved changes and restores the defaults of
 * the active tab only. A tab with unsaved changes shows a {@code *} in front of its title,
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

    private static final String ICON_SAVE_SCORES = "/images/app/save.png";
    private static final String ICON_RESTORE_DEFAULTS = "/images/app/restore.png";
    private static final int TOOLBAR_ICON_SIZE = 20;

    /** The open dialog, or null - see {@link #open}. */
    private static CowScoreDialog instance;

    private final Map<CowScoreTab, CowScorePanel> panels = new EnumMap<>(CowScoreTab.class);
    private final JTabbedPane tabs = new JTabbedPane();
    private final FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_SCORES, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
    /** Runs after a save that wrote at least one tab, may be null - see {@link #open(Frame, Catalog, CowScoreTab, Runnable)}. */
    private Runnable onSaved;

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
        if (instance == null) {
            instance = new CowScoreDialog(owner, catalog);
        }
        instance.onSaved = onSaved;
        instance.selectTab(initialTab);
        instance.setVisible(true);
        instance.toFront();
        return instance;
    }

    private CowScoreDialog(Frame owner, Catalog catalog) {
        super(owner, LanguageService.displayTitle(KEY_TITLE), false);
        panels.put(CowScoreTab.HEROES, new HeroCowScorePanel(catalog.heroes()));
        panels.put(CowScoreTab.TITANS, new TitanCowScorePanel(catalog.titans()));
        panels.put(CowScoreTab.PETS, new PetCowScorePanel(catalog.pets()));
        panels.put(CowScoreTab.WAR_FLAGS, new WarFlagCowScorePanel(catalog.warFlags()));
        panels.forEach((tab, panel) -> {
            tabs.addTab(LanguageService.displayName(tab.textKey()), panel.component());
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
        updateUnsavedState();
        setSize(740, 560);
        setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        if (instance == this) {
            instance = null;
        }
        super.dispose();
    }

    /** Switches to {@code tab}. */
    public void selectTab(CowScoreTab tab) {
        tabs.setSelectedIndex(tab.ordinal());
    }

    /** The tab currently shown. */
    public CowScoreTab selectedTab() {
        return CowScoreTab.values()[tabs.getSelectedIndex()];
    }

    /** The displayed title of {@code tab} - with a leading {@code *} while it has unsaved changes. */
    public String tabTitle(CowScoreTab tab) {
        return tabs.getTitleAt(tab.ordinal());
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
        FlatButton restoreDefaultsButton = new FlatButton(IconLoader.iconFor(ICON_RESTORE_DEFAULTS, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
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
            tabs.setTitleAt(entry.getKey().ordinal(), unsaved ? "*" + title : title);
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

    /** Restores the defaults of the active tab only, after its own confirmation question. */
    private void onRestoreDefaults() {
        CowScorePanel panel = panels.get(selectedTab());
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
