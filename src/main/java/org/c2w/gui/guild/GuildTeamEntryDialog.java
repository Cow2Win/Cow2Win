package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.FortComboBox;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.common.GuiUtils;
import org.c2w.util.AppContext;
import org.c2w.util.Config;
import org.c2w.util.LanguageService;
import org.c2w.util.LineupFiles;
import org.c2w.util.Logger;
import org.c2w.util.TeamScoreCalculator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Guild-wide counterpart of {@link org.c2w.gui.fort.FortificationEntryDialog},
 * for exactly ONE team type (hero or titan - see {@link GuildHeroEntryDialog}/
 * {@link GuildTitanEntryDialog}, the only two subclasses): lets the player
 * build/edit that type's teams for the whole guild in one place, instead of
 * one fortification at a time. Used to be a single dialog covering both
 * types side by side (see git history); split into one focused dialog per
 * type per the player's request, sharing everything type-agnostic here.
 *
 * <p>Differences from {@link org.c2w.gui.fort.FortificationEntryDialog}:
 * <ul>
 *     <li>No {@link org.c2w.gui.fort.FortificationInfoPanel} - this dialog
 *     never edits a single fortification's catalog fields.</li>
 *     <li>Not bound to one {@link Fortification} - each row carries its own
 *     {@link FortComboBox} (placed left of the member combo) so a team can be
 *     assigned to any fortification of the matching type, or to none at all
 *     (the team is then just saved into the member's draft without a
 *     {@link Lineup.Entry}, same as editing a team via
 *     {@link GuildEditorDialog}/{@link MemberEditorPanel}).</li>
 *     <li>Rows aren't capped by a fortification's capacity - the "+" button
 *     appends a fresh, empty row for a brand new team at any time (see
 *     {@link #addRow}).</li>
 * </ul>
 *
 * <p>On save, every row is resolved to a (member, teamIndex) slot exactly
 * like {@code FortificationEntryDialog} does, and the resulting
 * {@link Lineup.Entry} list for {@link #spec}'s {@link Lineup.TeamType} is
 * rebuilt from scratch from the current rows - this is safe (unlike a
 * per-fortification dialog) because every row already covers either a
 * pre-existing entry of that type or a brand new team, so the full set of
 * rows is always a complete picture of that type's assignments. Entries of
 * the OTHER team type (which this dialog has no rows for at all) are simply
 * carried over unchanged from the current {@link Lineup} - see
 * {@link #performSave}.
 */
abstract class GuildTeamEntryDialog<T> extends JDialog {

    /** Mirrors {@code GuildEditorDialog#MAX_MEMBERS} - enforced the same way here when {@link MemberComboEditor} creates a new member inline (see {@link #buildMemberCombo}). */
    private static final int MAX_MEMBERS = 30;

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for an unselected member/fortification combo slot. */
    private static final String KEY_NO_SELECTION = "common.none";

    private static final String KEY_SAVE_TEAMS = "guildEntry.saveTeams";
    private static final String KEY_ADD_ROW = "guildEntry.addRow";

    /** Classpath path of the "save" button's icon - same icon every other save {@link FlatButton} in the app uses. */
    private static final String ICON_SAVE_TEAMS = "/images/app/save.png";

    /** Classpath path of the "add row" button's icon. */
    private static final String ICON_ADD_ROW = "/images/app/add.png";

    /** Target size of the toolbar icons. */
    private static final int TOOLBAR_ICON_SIZE = 20;

    private static final int MEMBER_COMBO_TOP_OFFSET = 6;

    /** Height shared by the fortification/member combo boxes and the buff-member count label, so they all line up. */
    private static final int MEMBER_COMBO_HEIGHT = 41;

    /** Width of the buff-member count label - enough for a one/two-digit count plus " (" + score + ")". */
    private static final int BUFF_COUNT_LABEL_WIDTH = 64;

    /** Width shared by the fortification combo and the member combo (see {@link #addRow}). */
    private static final int COMBO_WIDTH = 150;

    /**
     * Stand-in used to score a row that currently has no fortification
     * selected (see {@link #updateBuffCountLabel}) - only its {@code buff() == null}
     * matters to {@link TeamScoreCalculator#scoreFor}, so an unassigned row
     * simply scores like a buff-less fortification (generalScore per member).
     */
    private static final Fortification UNASSIGNED_FORTIFICATION =
            new Fortification("__unassigned__", FortificationType.HERO, 1, 0, 0, 0, null, List.of(), 0);

    protected final AppContext appContext;
    private final Runnable onSaved;

    /** File the {@link #originalLineup} is loaded from / saved to - the reserved per-guild "Original" lineup (see {@link LineupFiles}). */
    private final Path originalLineupPath;

    /**
     * The lineup this dialog edits: the guild's fixed "Original" baseline
     * (the actual in-game deployment), NOT whatever lineup is currently
     * selected in the toolbar. Seeded on first use from the currently open
     * lineup (see {@link #loadOrSeedOriginalLineup()}) and rewritten on every
     * save (see {@link #performSave()}).
     */
    private Lineup originalLineup;

    protected final GuildDraft draft;

    /** Fetched once and reused for every row's {@link FortComboBox} and for looking up a row's preset fortification, so {@link FortComboBox#setSelectedItem} always matches an item that is actually in that combo's model. */
    private final List<Fortification> fortificationCatalog = FortificationRepository.findAll();

    /** This dialog's one and only type - which catalog/repository, team list, {@link FortificationType} and {@link Lineup.TeamType} it edits (see the two subclasses' {@code buildSpec}). */
    private final SectionSpec<T> spec;

    /** Cap on how many teams of {@link #spec}'s type a member can have - {@code GuildHeroEntryDialog.MAX_HERO_TEAMS}/{@code GuildTitanEntryDialog.MAX_TITAN_TEAMS}, passed in by the subclass. */
    private final int maxTeams;

    private final JPanel rowsPanel = new JPanel();

    private final List<RowState<T>> rowStates = new ArrayList<>();

    /**
     * Every member combo built so far (see {@link #buildMemberCombo}), so a
     * member created inline through one row's combo (see
     * {@link MemberComboEditor}) can be added to every other row's dropdown
     * too via {@link #refreshAllMemberCombos} - not just the row it was
     * typed into.
     */
    private final List<JComboBox<MemberDraft>> memberCombos = new ArrayList<>();

    /**
     * Last fortification picked (non-null) in any row's {@link FortComboBox} -
     * used by {@link #addRow} to prefill a brand new row's fortification
     * combo (see {@link #preselectFortificationFor}), so the player doesn't
     * have to re-pick the same fortification for every new row.
     */
    private Fortification lastSelectedFortification;

    protected GuildTeamEntryDialog(Frame owner, AppContext appContext, Runnable onSaved,
                                   String titleKey, SectionSpec<T> spec, int maxTeams) {
        super(owner, LanguageService.displayName(titleKey), false);
        if (appContext == null) {
            throw new IllegalArgumentException("GuildTeamEntryDialog needs a appContext");
        }
        this.appContext = appContext;
        this.onSaved = onSaved;
        this.spec = spec;
        this.maxTeams = maxTeams;

        this.draft = GuildDraftConverter.fromGuild(appContext.guild());
        for (MemberDraft member : draft.members) {
            ensureTeamCount(spec.teamsOf().apply(member), maxTeams);
        }

        Path guildDir = appContext.guildFilePath().getParent();
        this.originalLineupPath = LineupFiles.originalPathFor(guildDir);
        this.originalLineup = loadOrSeedOriginalLineup();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setOpaque(false);
        JPanel sectionPanel = new JPanel();
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));
        sectionPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        sectionPanel.add(rowsPanel);
        sectionPanel.add(alignLeft(buildAddRowButton()));
        // Without this, the BoxLayout above stretches its children to fill
        // whatever extra height the JScrollPane's viewport has to offer
        // (rowsPanel's rows use GridBagLayout, which reports an unbounded
        // maximum size) - the rows end up spread out across the whole
        // dialog instead of stacking tightly from the top. Glue soaks up
        // that leftover space instead, so the rows/button stay anchored at
        // the top like a vertical FlowLayout (see also buildRowPanel/alignLeft,
        // which cap their own component's maximum height for the same reason).
        sectionPanel.add(Box.createVerticalGlue());

        buildSection();
        bindDeleteRowShortcut();

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JScrollPane(sectionPanel), BorderLayout.CENTER);

        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        setSize(1050, 750);
        setLocationRelativeTo(owner);
    }

    private static <T> void ensureTeamCount(List<TeamDraft<T>> teams, int maxCount) {
        while (teams.size() < maxCount) {
            teams.add(new TeamDraft<>());
        }
    }

    /**
     * Loads the guild's fixed "Original" lineup (see {@link LineupFiles}) if
     * it already exists on disk, or seeds a fresh one from the currently open
     * lineup otherwise. Seeding from the current lineup (rather than starting
     * empty) means the player's existing in-game deployment isn't lost and
     * only needs adjusting to match reality on the first pass, instead of
     * being re-entered from scratch; it is written out as the Original
     * baseline on the first {@link #performSave()}. An unreadable Original
     * file is treated the same way (logged, then re-seeded).
     */
    private Lineup loadOrSeedOriginalLineup() {
        if (Files.exists(originalLineupPath)) {
            try {
                return LineupRepository.load(originalLineupPath);
            } catch (IOException e) {
                Logger.logException("Could not load the Original lineup " + originalLineupPath
                        + " - seeding it from the currently open lineup instead", e);
            }
        }
        Lineup current = appContext.lineup();
        return new Lineup(current.guildId(), current.guildName(), "", LocalDateTime.now(), current.entries());
    }

    private static JPanel alignLeft(JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(component);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    /**
     * Builds this dialog's initial rows, one per existing {@link Lineup.Entry}
     * of {@link #spec}'s type across every fortification (not just one) -
     * each becomes an editable row via {@link #addRow}. Further rows can be
     * appended later through the "+" button (see {@link #buildAddRowButton}).
     */
    private void buildSection() {
        List<Lineup.Entry> existingEntries = originalLineup.entries().stream()
                .filter(e -> e.teamType() == spec.teamType())
                .toList();

        for (Lineup.Entry entry : existingEntries) {
            MemberDraft member = findMemberDraft(entry.teamMemberId());
            List<TeamDraft<T>> teams = member == null ? null : spec.teamsOf().apply(member);
            if (member == null || teams == null || entry.teamIndex() < 0 || entry.teamIndex() >= teams.size()) {
                Logger.log("Could not restore an assigned team in the guild entry dialog - the member or team no longer exists");
                continue;
            }
            Fortification fortification = fortificationCatalog.stream()
                    .filter(f -> f.id().equals(entry.fortificationId()))
                    .findFirst().orElse(null);
            addRow(teams.get(entry.teamIndex()), member, entry.teamIndex(), fortification);
        }
    }

    /**
     * Appends one new row to {@link #rowsPanel} - either restoring an
     * existing team ({@code existingDraft}/{@code originalMember}/
     * {@code originalTeamIndex}/{@code presetFortification} all non-null, from
     * {@link #buildSection}) or starting a brand new, empty one (all four
     * {@code null}/{@code -1}, from the "+" button - see
     * {@link #buildAddRowButton}).
     */
    private void addRow(TeamDraft<T> existingDraft, MemberDraft originalMember, int originalTeamIndex,
                        Fortification presetFortification) {
        TeamDraft<T> rowDraft = new TeamDraft<>();
        if (existingDraft != null) {
            rowDraft.members.addAll(existingDraft.members);
            rowDraft.totalPower = existingDraft.totalPower;
            rowDraft.lastModified = existingDraft.lastModified;
        }

        FortComboBox fortCombo = new FortComboBox(fortificationCatalog, spec.fortificationType());
        fortCombo.setPreferredSize(new Dimension(COMBO_WIDTH, MEMBER_COMBO_HEIGHT));
        Fortification fortificationToSelect = presetFortification != null
                ? presetFortification : preselectFortificationFor(existingDraft);
        if (fortificationToSelect != null) {
            fortCombo.setSelectedItem(fortificationToSelect);
        }

        JComboBox<MemberDraft> memberCombo = buildMemberCombo();
        memberCombo.setPreferredSize(new Dimension(COMBO_WIDTH, MEMBER_COMBO_HEIGHT));
        if (originalMember != null) {
            memberCombo.setSelectedItem(originalMember);
        }

        int rowNumber = rowStates.size() + 1;
        JLabel buffCountLabel = buildBuffCountLabel(spec.fortificationType());
        TeamEditorPanel<T> teamEditor = buildTeamEditorPanel(rowDraft,
                () -> updateBuffCountLabel(buffCountLabel, rowDraft, (Fortification) fortCombo.getSelectedItem(), rowNumber));
        updateBuffCountLabel(buffCountLabel, rowDraft, (Fortification) fortCombo.getSelectedItem(), rowNumber);

        // Same convention as FortificationEntryDialog: picking "- none -" as
        // the member clears this row's team, so an unwanted row is simply
        // dropped on save (totalPower == 0) instead of ever being written out.
        memberCombo.addActionListener(e -> {
            if (memberCombo.getSelectedItem() == null) {
                teamEditor.clear();
            }
        });
        fortCombo.addActionListener(e -> {
            Fortification selected = (Fortification) fortCombo.getSelectedItem();
            if (selected != null) {
                lastSelectedFortification = selected;
            }
            updateBuffCountLabel(buffCountLabel, rowDraft, selected, rowNumber);
        });

        JPanel rowPanel = buildRowPanel(fortCombo, memberCombo, teamEditor, buffCountLabel);
        rowStates.add(new RowState<>(rowDraft, memberCombo, fortCombo, originalMember, originalTeamIndex, rowPanel));
        rowsPanel.add(rowPanel);
        rowsPanel.revalidate();
        rowsPanel.repaint();

        // A brand new row (from the "+" button - existingDraft == null) is
        // appended at the very bottom, so scroll the enclosing JScrollPane all
        // the way down to reveal it in full. Restored rows (existingDraft !=
        // null, from buildSection) are skipped so the view stays at the top
        // when the dialog first opens.
        //
        // Two-stage invokeLater on purpose: a single one fires before the
        // scroll pane has recomputed its scroll range for the now-taller
        // content, so the vertical scrollbar's maximum is still the OLD value
        // and scrolling to it stops short of the new row. The first stage runs
        // after the revalidate above lays the row out; the second, after the
        // scroll pane has updated its range - only then is getMaximum() final.
        if (existingDraft == null) {
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
                JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, rowPanel);
                if (scrollPane != null) {
                    JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                    verticalBar.setValue(verticalBar.getMaximum());
                }
            }));
        }
    }

    /**
     * The fortification to preselect for a brand new (non-restored) row -
     * {@link #lastSelectedFortification}, unless that fortification is
     * already full (see {@link #isFortificationFull}), or there simply isn't
     * one yet. Restored rows ({@code existingDraft != null}, from
     * {@link #buildSection}) are left untouched - they already carry their
     * own {@code presetFortification} - so this only ever runs for the "+"
     * button / add-row shortcut.
     */
    private Fortification preselectFortificationFor(TeamDraft<T> existingDraft) {
        if (existingDraft != null) {
            return null;
        }
        return lastSelectedFortification != null && !isFortificationFull(lastSelectedFortification)
                ? lastSelectedFortification : null;
    }

    private boolean isFortificationFull(Fortification fortification) {
        long assignedRows = rowStates.stream()
                .filter(row -> row.fortCombo.getSelectedItem() == fortification)
                .count();
        return assignedRows >= fortification.capacity();
    }

    /** The "+" button - appends one fresh, empty row for a new team (see {@link #addRow}). */
    private FlatButton buildAddRowButton() {
        FlatButton addButton = new FlatButton(IconLoader.iconFor(ICON_ADD_ROW, TOOLBAR_ICON_SIZE, Color.WHITE));
        addButton.setToolTipText(LanguageService.displayName(KEY_ADD_ROW));
        addButton.addActionListener(e -> {
            // Requirement: auto-save whatever is already filled in before a
            // fresh row is appended, so it's never lost even if the player
            // forgets to hit "save" themselves before adding more rows.
            performSave();
            addRow(null, null, -1, null);
        });
        bindAddRowShortcut(addButton);
        return addButton;
    }

    /**
     * Lets the "+" key (main keyboard, e.g. the dedicated "+" key on a German
     * layout) or Numpad-Plus trigger {@code addButton} - i.e. add a new,
     * empty row - without reaching for the mouse. Bound with
     * {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} on {@link #rowsPanel}
     * itself, so the shortcut fires whenever the focus is anywhere among
     * this dialog's rows.
     */
    private void bindAddRowShortcut(FlatButton addButton) {
        KeyStroke plus = KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0);
        KeyStroke numpadPlus = KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0);

        InputMap inputMap = rowsPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = rowsPanel.getActionMap();
        Object actionKey = "addRow";
        inputMap.put(plus, actionKey);
        inputMap.put(numpadPlus, actionKey);
        actionMap.put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addButton.doClick();
            }
        });
    }

    /**
     * Lets the "-" key or Numpad-Minus delete the row the focus is currently
     * in (see {@link #deleteFocusedRow}) - the destructive counterpart of the
     * "+" add-row shortcut (see {@link #bindAddRowShortcut}). Bound the same
     * way, with {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} on {@link #rowsPanel}.
     */
    private void bindDeleteRowShortcut() {
        KeyStroke minus = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0);
        KeyStroke numpadMinus = KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0);

        InputMap inputMap = rowsPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = rowsPanel.getActionMap();
        Object actionKey = "deleteRow";
        inputMap.put(minus, actionKey);
        inputMap.put(numpadMinus, actionKey);
        actionMap.put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteFocusedRow();
            }
        });
    }

    /**
     * Deletes the row the keyboard focus is currently in - and, unlike merely
     * clearing it, removes its team from the guild too (the {@code (memberId,
     * teamIndex)} slot it is bound to, see {@link RowState#boundMember}), then
     * re-saves. Confirmed first, since this is persistent and cannot be undone.
     * A brand new row that was never saved (no bound slot yet) simply
     * disappears. Does nothing when the focus is outside every row.
     */
    private void deleteFocusedRow() {
        RowState<T> row = focusedRow();
        if (row == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete this team and remove it from the guild? This cannot be undone.",
                "Delete team", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        // Drop the team from the guild draft if this row was ever bound to a
        // real slot (a never-saved row has boundMember == null and just
        // vanishes), compacting the member's remaining teams so their list
        // positions stay dense - i.e. keep matching their HeroTeam/TitanTeam
        // index, which GuildMember enforces and toGuild relies on.
        if (row.boundMember != null) {
            deleteBoundTeam(row.boundMember, row.boundTeamIndex);
        }

        rowStates.remove(row);
        rowsPanel.remove(row.panel);
        rowsPanel.revalidate();
        rowsPanel.repaint();

        // performSave rebuilds this type's teams and lineup entries from the
        // remaining rows, so the deleted team is gone from both the guild file
        // and the Original lineup.
        performSave();
    }

    /**
     * The {@link RowState} whose row panel currently contains the keyboard
     * focus, or {@code null} if the focus is outside every row (see
     * {@link #deleteFocusedRow}).
     */
    private RowState<T> focusedRow() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            return null;
        }
        for (RowState<T> row : rowStates) {
            if (row.panel == focusOwner || row.panel.isAncestorOf(focusOwner)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Removes the team at {@code teamIndex} from {@code member}'s team list of
     * this dialog's type and re-pads the list back to {@link #maxTeams} empty
     * trailing slots, so it keeps its fixed length while its non-empty entries
     * stay contiguous from index 0. Every other row bound to a LATER slot of
     * the same member is shifted down by one to follow its team (see
     * {@link RowState#boundTeamIndex}).
     */
    private void deleteBoundTeam(MemberDraft member, int teamIndex) {
        List<TeamDraft<T>> teams = spec.teamsOf().apply(member);
        if (teamIndex < 0 || teamIndex >= teams.size()) {
            return;
        }
        teams.remove(teamIndex);
        ensureTeamCount(teams, maxTeams);
        for (RowState<T> other : rowStates) {
            if (other.boundMember == member && other.boundTeamIndex > teamIndex) {
                other.boundTeamIndex--;
            }
        }
    }

    private JPanel buildRowPanel(FortComboBox fortCombo, JComboBox<MemberDraft> memberCombo, JPanel teamEditor, JLabel buffCountLabel) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setBorder(BorderFactory.createEmptyBorder(MEMBER_COMBO_TOP_OFFSET, 0, 0, 0));
        left.add(fortCombo);
        left.add(memberCombo);

        GridBagConstraints comboConstraints = new GridBagConstraints();
        comboConstraints.gridx = 0;
        comboConstraints.gridy = 0;
        comboConstraints.anchor = GridBagConstraints.NORTHWEST;
        comboConstraints.insets = new Insets(0, 0, 0, 8);
        row.add(left, comboConstraints);

        GridBagConstraints teamEditorConstraints = new GridBagConstraints();
        teamEditorConstraints.gridx = 1;
        teamEditorConstraints.gridy = 0;
        teamEditorConstraints.weightx = 1.0;
        teamEditorConstraints.fill = GridBagConstraints.HORIZONTAL;
        teamEditorConstraints.anchor = GridBagConstraints.NORTHWEST;
        row.add(teamEditor, teamEditorConstraints);

        // Behind (right of) the TeamEditorPanel - shows how many of its currently selected members increase the selected fortification's buff, plus the team's score total (see buildBuffCountLabel/updateBuffCountLabel).
        GridBagConstraints buffCountConstraints = new GridBagConstraints();
        buffCountConstraints.gridx = 2;
        buffCountConstraints.gridy = 0;
        buffCountConstraints.anchor = GridBagConstraints.NORTHWEST;
        buffCountConstraints.insets = new Insets(MEMBER_COMBO_TOP_OFFSET, 8, 0, 0);
        row.add(buffCountLabel, buffCountConstraints);

        // GridBagLayout reports an unbounded maximum size by default, which
        // would let rowsPanel's BoxLayout stretch every row to fill leftover
        // vertical space instead of stacking them tightly from the top (see
        // the vertical glue added where rowsPanel is built).
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

        return row;
    }

    private static JLabel buildBuffCountLabel(FortificationType type) {
        JLabel buffCountLabel = new JLabel("", JLabel.CENTER);
        buffCountLabel.setPreferredSize(new Dimension(BUFF_COUNT_LABEL_WIDTH, MEMBER_COMBO_HEIGHT));
        buffCountLabel.setForeground(type.getColor());
        return buffCountLabel;
    }

    /**
     * Sets buffCountLabel's text to how many of teamDraft's currently
     * selected members satisfy the selected fortification's buff (0 if no
     * fortification is selected), followed by the team's score total in
     * parentheses, e.g. "2 (4.5)" - see
     * {@code FortificationEntryDialog#updateBuffCountLabel} for the original,
     * per-fortification version of this. Also logs the breakdown to
     * {@link Logger} for debugging.
     */
    private void updateBuffCountLabel(JLabel buffCountLabel, TeamDraft<T> teamDraft, Fortification selectedFortification,
                                      int rowNumber) {
        long count = selectedFortification == null ? 0
                : teamDraft.members.stream().filter(member -> spec.matchesBuff().apply(selectedFortification, member)).count();

        Fortification scoringFortification = selectedFortification != null ? selectedFortification : UNASSIGNED_FORTIFICATION;
        TeamScoreCalculator.Breakdown breakdown = spec.scoreBreakdownOf().apply(teamDraft, scoringFortification);
        String rowLabel = selectedFortification != null ? LanguageService.displayName(selectedFortification.id())
                : LanguageService.displayName(KEY_NO_SELECTION);
        logSortScoreBreakdown(rowLabel, rowNumber, breakdown);

        buffCountLabel.setText(count + " (" + String.format(Locale.ROOT, "%.1f", breakdown.total()) + ")");
        buffCountLabel.setToolTipText(selectedFortification != null && selectedFortification.buff() != null
                ? selectedFortification.buff().display() : "");
    }

    /** Mirrors {@code FortificationEntryDialog#logSortScoreBreakdown} exactly - see there. */
    private static void logSortScoreBreakdown(String rowLabel, int rowNumber, TeamScoreCalculator.Breakdown breakdown) {
        List<Double> memberScores = breakdown.memberScores();
        if (memberScores.isEmpty() && breakdown.powerTerm() == 0) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(rowLabel).append(": Team ").append(rowNumber).append(" : ")
                .append(String.format(Locale.ROOT, "%.2f", breakdown.powerTerm()));
        for (double memberScore : memberScores) {
            message.append(" + ").append(String.format(Locale.ROOT, "%.2f", memberScore));
        }
        message.append(" = ").append(String.format(Locale.ROOT, "%.2f", breakdown.total()));
        Logger.logToFile(message.toString());
    }

    /**
     * Builds one row's member combo - unlike
     * {@code FortificationEntryDialog#buildMemberCombo}, this one is
     * editable (see {@link MemberComboEditor}): typing an existing member's
     * name/id and confirming (Enter/losing focus) selects that member,
     * exactly like picking it from the dropdown; typing a name that matches
     * no existing member instead creates a new one on the spot (same
     * {@code id == name} convention as {@code GuildEditorDialog#onAddMember}),
     * so a new member no longer needs a trip through {@link GuildEditorDialog}.
     * Every combo built here is tracked in {@link #memberCombos} so a member
     * created through one row shows up in every other row's dropdown too
     * (see {@link #refreshAllMemberCombos}).
     */
    private JComboBox<MemberDraft> buildMemberCombo() {
        JComboBox<MemberDraft> combo = new JComboBox<>();
        DefaultComboBoxModel<MemberDraft> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        sortedMembers().forEach(model::addElement);
        combo.setModel(model);
        combo.setEditable(true);
        combo.setEditor(new MemberComboEditor(combo));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText(LanguageService.displayName(KEY_NO_SELECTION));
                } else {
                    setText(memberDisplayName((MemberDraft) value));
                }
                return this;
            }
        });
        memberCombos.add(combo);
        return combo;
    }

    /** Display text for a member - its name, falling back to its id if the name is blank (same convention used throughout this class and {@code FortificationEntryDialog}/{@code GuildEditorDialog}). */
    private static String memberDisplayName(MemberDraft member) {
        return member.name.isBlank() ? member.id : member.name;
    }

    /** {@link #draft}'s members, sorted the same way every member-picking UI in this app sorts them (display name, case-insensitive). */
    private List<MemberDraft> sortedMembers() {
        return draft.members.stream()
                .sorted(Comparator.comparing(GuildTeamEntryDialog::memberDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** First member whose display name or id matches {@code text}, case-insensitively - used by {@link MemberComboEditor} to tell "pick an existing member" apart from "create a new one". */
    private MemberDraft findMemberByDisplayOrId(String text) {
        return draft.members.stream()
                .filter(m -> memberDisplayName(m).equalsIgnoreCase(text) || m.id.equalsIgnoreCase(text))
                .findFirst().orElse(null);
    }

    /**
     * Rebuilds every tracked {@link #memberCombos}' model from
     * {@link #sortedMembers()} (preserving each combo's current selection) -
     * called (deferred via {@link SwingUtilities#invokeLater}) after
     * {@link MemberComboEditor} creates a new member, so it becomes pickable
     * from every row, not just the one it was typed into; also called after
     * every successful {@link #performSave}, so every row stays in sync with
     * the freshly saved draft.
     */
    private void refreshAllMemberCombos() {
        List<MemberDraft> sorted = sortedMembers();
        for (JComboBox<MemberDraft> combo : memberCombos) {
            Object selected = combo.getSelectedItem();
            DefaultComboBoxModel<MemberDraft> model = new DefaultComboBoxModel<>();
            model.addElement(null);
            sorted.forEach(model::addElement);
            combo.setModel(model);
            combo.setSelectedItem(selected);
        }
    }

    /**
     * {@link ComboBoxEditor} for a member combo (see {@link #buildMemberCombo}):
     * shows/edits a {@link MemberDraft}'s display name as plain text instead
     * of falling back to {@link Object#toString()} (which {@link MemberDraft}
     * does not override), and resolves the typed text back to a
     * {@link MemberDraft} on commit - an existing member if the text matches
     * one (see {@link #findMemberByDisplayOrId}), a freshly created one
     * otherwise (added to {@link #draft}, capped at {@link #MAX_MEMBERS} like
     * {@code GuildEditorDialog#onAddMember}), or {@code null} for blank text
     * (same as picking "{@value #KEY_NO_SELECTION}" from the dropdown).
     */
    private final class MemberComboEditor implements ComboBoxEditor {
        private final JComboBox<MemberDraft> combo;
        private final JTextField textField = new JTextField();

        MemberComboEditor(JComboBox<MemberDraft> combo) {
            this.combo = combo;
            textField.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        }

        @Override
        public Component getEditorComponent() {
            return textField;
        }

        @Override
        public void setItem(Object item) {
            textField.setText(item == null ? "" : memberDisplayName((MemberDraft) item));
        }

        @Override
        public Object getItem() {
            String typed = textField.getText().trim();
            if (typed.isEmpty()) {
                return null;
            }
            MemberDraft existing = findMemberByDisplayOrId(typed);
            if (existing != null) {
                return existing;
            }
            if (draft.members.size() >= MAX_MEMBERS) {
                JOptionPane.showMessageDialog(GuildTeamEntryDialog.this,
                        "A guild has at most " + MAX_MEMBERS + " members.", "Not possible",
                        JOptionPane.WARNING_MESSAGE);
                return combo.getSelectedItem();
            }
            MemberDraft created = new MemberDraft(typed, typed);
            ensureTeamCount(spec.teamsOf().apply(created), maxTeams);
            draft.members.add(created);
            Logger.log("Created guild member: " + typed);
            // Deferred: this combo's own model/selection is still mid-update
            // by JComboBox at this point (it called getItem() to find out
            // what to select next) - rebuilding models now, including this
            // one, would step on that. Runs right after, on the same EDT turn.
            SwingUtilities.invokeLater(GuildTeamEntryDialog.this::refreshAllMemberCombos);
            return created;
        }

        @Override
        public void selectAll() {
            textField.selectAll();
        }

        @Override
        public void addActionListener(ActionListener listener) {
            textField.addActionListener(listener);
        }

        @Override
        public void removeActionListener(ActionListener listener) {
            textField.removeActionListener(listener);
        }
    }

    private TeamEditorPanel<T> buildTeamEditorPanel(TeamDraft<T> teamDraft, Runnable onChanged) {
        return new TeamEditorPanel<>(spec.catalog(), spec.label(), spec.icon(), null, teamDraft,
                LanguageService.displayName(KEY_NO_SELECTION), spec.catalogOrder(), onChanged);
    }

    /** Mirrors {@code FortificationEntryDialog#resolveTeamIndex} exactly - see there. */
    private static <T> int resolveTeamIndex(List<TeamDraft<T>> teams, Set<String> boundKeys, String memberId,
                                            int preferredIndex) {
        if (preferredIndex >= 0 && preferredIndex < teams.size() && !boundKeys.contains(rowKey(memberId, preferredIndex))) {
            return preferredIndex;
        }
        for (int i = 0; i < teams.size(); i++) {
            if (!boundKeys.contains(rowKey(memberId, i)) && teams.get(i).totalPower == 0) {
                return i;
            }
        }
        for (int i = 0; i < teams.size(); i++) {
            if (!boundKeys.contains(rowKey(memberId, i))) {
                return i;
            }
        }
        return -1;
    }

    private static String rowKey(String memberId, int teamIndex) {
        return memberId + "#" + teamIndex;
    }

    private MemberDraft findMemberDraft(String memberId) {
        return draft.members.stream().filter(m -> m.id.equals(memberId)).findFirst().orElse(null);
    }

    private JPanel buildToolbarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_TEAMS, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        saveButton.setToolTipText(LanguageService.displayName(KEY_SAVE_TEAMS));
        saveButton.addActionListener(e -> performSave());
        buttons.add(saveButton);
        panel.add(buttons, BorderLayout.WEST);
        return panel;
    }

    /**
     * Resolves every row of {@link #rowStates} to a (member, teamIndex) slot,
     * exactly like {@code FortificationEntryDialog#performSave} does for its
     * own rows - empty rows (totalPower == 0) are simply skipped, a filled-in
     * row with no member selected or with no free slot left for its member
     * aborts the whole save with a warning dialog (returning {@code null}).
     */
    private List<RowResolution<T>> resolveResolutions() {
        Set<String> boundKeys = new HashSet<>();
        List<RowResolution<T>> resolutions = new ArrayList<>();

        for (RowState<T> row : rowStates) {
            if (row.teamDraft.totalPower == 0) {
                continue; // empty row - simply dropped, same convention as GuildDraftConverter#toGuild
            }
            MemberDraft selectedMember = (MemberDraft) row.memberCombo.getSelectedItem();
            if (selectedMember == null) {
                JOptionPane.showMessageDialog(this,
                        "A row has a team filled in but no member selected - pick a member for it, or clear its power, before saving.",
                        "Could not save", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            List<TeamDraft<T>> teams = spec.teamsOf().apply(selectedMember);
            int preferredIndex = selectedMember == row.boundMember ? row.boundTeamIndex : -1;
            int idx = resolveTeamIndex(teams, boundKeys, selectedMember.id, preferredIndex);
            if (idx < 0) {
                JOptionPane.showMessageDialog(this,
                        "'" + memberDisplayName(selectedMember)
                                + "' has no free team slot left.",
                        "Could not save", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            boundKeys.add(rowKey(selectedMember.id, idx));
            Fortification selectedFortification = (Fortification) row.fortCombo.getSelectedItem();
            resolutions.add(new RowResolution<>(row, selectedMember, idx, row.teamDraft, selectedFortification));
        }
        return resolutions;
    }

    private void applyResolutions(List<RowResolution<T>> resolutions) {
        for (RowResolution<T> resolution : resolutions) {
            TeamDraft<T> target = spec.teamsOf().apply(resolution.member()).get(resolution.teamIndex());
            target.members.clear();
            target.members.addAll(resolution.sourceDraft().members);
            target.totalPower = resolution.sourceDraft().totalPower;
            target.lastModified = resolution.sourceDraft().lastModified;
            // Re-point this row at the (member, teamIndex) slot it was just
            // written into - see RowState#boundMember. Without this, a row not
            // restored from a lineup entry keeps boundMember == null, so the
            // NEXT save (e.g. the auto-save behind every "+") resolves it to a
            // fresh free slot and duplicates the team instead of updating it.
            resolution.row().boundMember = resolution.member();
            resolution.row().boundTeamIndex = resolution.teamIndex();
        }
    }

    /** One resolution per fortification-assigned, non-empty row - a row left on "- none -" for its fortification saves its team without deploying it anywhere. */
    private List<Lineup.Entry> buildEntries(List<RowResolution<T>> resolutions) {
        List<Lineup.Entry> entries = new ArrayList<>();
        for (RowResolution<T> resolution : resolutions) {
            if (resolution.fortification() == null) {
                continue;
            }
            entries.add(new Lineup.Entry(resolution.fortification().id(), resolution.member().id, spec.teamType(),
                    resolution.teamIndex()));
        }
        return entries;
    }

    private void performSave() {
        List<RowResolution<T>> resolutions = resolveResolutions();
        if (resolutions == null) {
            return;
        }

        applyResolutions(resolutions);

        // This dialog's own type's entries are regenerated wholesale from the
        // current rows (rather than diffed against the previous lineup) -
        // safe here because buildSection seeded one row per pre-existing
        // entry of that type across every fortification, so the rows are
        // always a complete picture of that type's assignments, unlike
        // FortificationEntryDialog whose rows only ever covered one
        // fortification's slots. The OTHER type's entries have no row
        // representation in this dialog at all, so they are simply carried
        // over unchanged instead of being dropped.
        //
        // Base is the guild's fixed "Original" lineup (see loadOrSeedOriginalLineup),
        // NOT whatever lineup happens to be selected in the toolbar - so an
        // optimized lineup that is currently open is never touched here.
        List<Lineup.Entry> updatedEntries = new ArrayList<>();
        updatedEntries.addAll(originalLineup.entries().stream()
                .filter(e -> e.teamType() != spec.teamType())
                .toList());
        updatedEntries.addAll(buildEntries(resolutions));

        // algorithmName stays empty: the Original lineup is hand-maintained
        // here, never produced by a LineupAlgorithm.
        Lineup updatedOriginal = new Lineup(originalLineup.guildId(), originalLineup.guildName(),
                "", originalLineup.createdAt(), updatedEntries);
        Guild updatedGuild = GuildDraftConverter.toGuild(draft);

        try {
            GuildRepository.save(updatedGuild, appContext.guildFilePath());
            LineupRepository.save(updatedOriginal, originalLineupPath);
            this.originalLineup = updatedOriginal;
            appContext.setGuild(updatedGuild);
            // Switch the app over to the freshly saved Original lineup, so the
            // toolbar lineup combo box and the fortification map immediately
            // show the in-game deployment that was just entered (the onSaved
            // callback repopulates the combo box and refreshes the map).
            appContext.set(updatedOriginal, originalLineupPath);
            Config.setLastLineUpPath(originalLineupPath.toString());
            Config.save();
            GuiUtils.editedLineup = false;
            Logger.log("Saved guild " + spec.teamType() + " teams into the Original lineup");
            // Keep every row's member combo (not just the one that triggered
            // this save) in sync with the freshly saved draft - e.g. an
            // inline-created member (see MemberComboEditor) that a
            // WHEN_ANCESTOR-scoped refresh may have missed while this save
            // was still pending.
            refreshAllMemberCombos();
            if (onSaved != null) {
                onSaved.run();
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "The data is invalid:\n" + ex.getMessage(),
                    "Error while saving", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save:\n" + ex.getMessage(),
                    "Error while saving", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Bundles everything specific to this dialog's one type (Hero or Titan)
     * that both {@link #buildSection} and {@link #addRow} need - the generic
     * counterpart of the separate lambdas
     * {@code FortificationEntryDialog#buildRows} passes into
     * {@code buildRowsGeneric} for its two branches. Built once by the
     * subclass (see {@code GuildHeroEntryDialog}/{@code GuildTitanEntryDialog})
     * and passed into the superclass constructor.
     */
    protected record SectionSpec<T>(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                                    Comparator<T> catalogOrder, Function<MemberDraft, List<TeamDraft<T>>> teamsOf,
                                    FortificationType fortificationType, Lineup.TeamType teamType,
                                    BiFunction<Fortification, T, Boolean> matchesBuff,
                                    BiFunction<TeamDraft<T>, Fortification, TeamScoreCalculator.Breakdown> scoreBreakdownOf) {
    }

    private static final class RowState<T> {
        final TeamDraft<T> teamDraft;
        final JComboBox<MemberDraft> memberCombo;
        final FortComboBox fortCombo;

        /**
         * The team slot this row is currently bound to - the {@code (memberId,
         * teamIndex)} primary key of the {@link HeroTeam}/{@link TitanTeam} it
         * edits IN PLACE (see {@link HeroTeam#index()}). Seeded from the lineup
         * entry's slot for a row restored by {@link #buildSection} and left
         * {@code null}/{@code -1} for a brand new row (from the "+" button)
         * that isn't tied to an existing team yet. Crucially, it is re-pointed
         * to whatever slot the row was written into after every
         * {@link #performSave} (see {@link #applyResolutions}), so a subsequent
         * save UPDATES that same team instead of resolving to the next free
         * slot and INSERTING a duplicate - the latter is what made repeated
         * saves (and every "+" click, which auto-saves first) pile up new
         * teams for rows that weren't restored from a lineup entry.
         */
        MemberDraft boundMember;
        int boundTeamIndex;

        /** This row's panel in {@link #rowsPanel} - kept so the delete shortcut can map the focused component back to its row (see {@link #focusedRow}). */
        final JPanel panel;

        RowState(TeamDraft<T> teamDraft, JComboBox<MemberDraft> memberCombo, FortComboBox fortCombo,
                 MemberDraft boundMember, int boundTeamIndex, JPanel panel) {
            this.teamDraft = teamDraft;
            this.memberCombo = memberCombo;
            this.fortCombo = fortCombo;
            this.boundMember = boundMember;
            this.boundTeamIndex = boundTeamIndex;
            this.panel = panel;
        }
    }

    /** One row's save-time resolution to a real (member, teamIndex) slot, plus which fortification (if any) it should be deployed to. */
    private record RowResolution<T>(RowState<T> row, MemberDraft member, int teamIndex, TeamDraft<T> sourceDraft,
                                    Fortification fortification) {
    }
}
