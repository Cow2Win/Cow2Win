package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.data.repository.*;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.FortComboBox;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;
import org.c2w.util.TeamScoreCalculator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Guild-wide counterpart of {@link org.c2w.gui.fort.FortificationEntryDialog}:
 * lets the player build/edit hero and titan teams for the whole guild in one
 * place, instead of one fortification at a time.
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
 *     <li>Rows aren't capped by a fortification's capacity - a "+" button per
 *     section (hero/titan) appends a fresh, empty row for a brand new team at
 *     any time (see {@link #addRow}).</li>
 * </ul>
 *
 * <p>On save, every row of a section (hero or titan) is resolved to a
 * (member, teamIndex) slot exactly like {@code FortificationEntryDialog}
 * does, and the resulting {@link Lineup.Entry} list for that
 * {@link Lineup.TeamType} is rebuilt from scratch from the current rows -
 * this is safe (unlike a per-fortification dialog) because every row already
 * covers either a pre-existing entry of that type or a brand new team, so the
 * full set of rows is always a complete picture of that type's assignments.
 */
public final class GuildEntryDialog extends JDialog {

    private static final int MAX_HERO_TEAMS = 3;
    private static final int MAX_TITAN_TEAMS = 2;

    /** Mirrors {@code GuildEditorDialog#MAX_MEMBERS} - enforced the same way here when {@link MemberComboEditor} creates a new member inline (see {@link #buildMemberCombo}). */
    private static final int MAX_MEMBERS = 30;

    private static final int ICON_SIZE = 32;

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for an unselected member/fortification combo slot. */
    private static final String KEY_NO_SELECTION = "common.none";

    private static final String KEY_TITLE = "guildEntry.title";
    private static final String KEY_SAVE_TEAMS = "guildEntry.saveTeams";
    private static final String KEY_ADD_ROW = "guildEntry.addRow";
    private static final String KEY_HERO_TEAMS = "guildEntry.heroTeams";
    private static final String KEY_TITAN_TEAMS = "guildEntry.titanTeams";

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

    private final AppContext appContext;
    private final Runnable onSaved;

    private final GuildDraft draft;

    /** Fetched once and reused for every row's {@link FortComboBox} and for looking up a row's preset fortification, so {@link FortComboBox#setSelectedItem} always matches an item that is actually in that combo's model. */
    private final List<Fortification> fortificationCatalog = FortificationRepository.findAll();

    private final JPanel heroRowsPanel = new JPanel();
    private final JPanel titanRowsPanel = new JPanel();

    private final List<RowState<Hero>> heroRowStates = new ArrayList<>();
    private final List<RowState<Titan>> titanRowStates = new ArrayList<>();

    /**
     * Every member combo built so far (see {@link #buildMemberCombo}), so a
     * member created inline through one row's combo (see
     * {@link MemberComboEditor}) can be added to every other row's dropdown
     * too via {@link #refreshAllMemberCombos} - not just the row it was
     * typed into.
     */
    private final List<JComboBox<MemberDraft>> memberCombos = new ArrayList<>();

    public GuildEntryDialog(Frame owner, AppContext appContext, Runnable onSaved) {
        super(owner, LanguageService.displayName(KEY_TITLE), false);
        if (appContext == null) {
            throw new IllegalArgumentException("GuildEntryDialog needs a appContext");
        }
        this.appContext = appContext;
        this.onSaved = onSaved;

        this.draft = GuildDraftConverter.fromGuild(appContext.guild());
        for (MemberDraft member : draft.members) {
            ensureTeamCount(member.heroTeams, MAX_HERO_TEAMS);
            ensureTeamCount(member.titanTeams, MAX_TITAN_TEAMS);
        }

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        heroRowsPanel.setLayout(new BoxLayout(heroRowsPanel, BoxLayout.Y_AXIS));
        titanRowsPanel.setLayout(new BoxLayout(titanRowsPanel, BoxLayout.Y_AXIS));

        JPanel sectionsPanel = new JPanel();
        sectionsPanel.setLayout(new BoxLayout(sectionsPanel, BoxLayout.Y_AXIS));
        sectionsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        SectionSpec<Hero> heroSpec = buildHeroSpec();
        SectionSpec<Titan> titanSpec = buildTitanSpec();

        sectionsPanel.add(buildSectionHeader(KEY_HERO_TEAMS));
        sectionsPanel.add(heroRowsPanel);
        sectionsPanel.add(alignLeft(buildAddRowButton(heroRowsPanel, heroRowStates, heroSpec)));
        sectionsPanel.add(Box.createVerticalStrut(16));
        sectionsPanel.add(buildSectionHeader(KEY_TITAN_TEAMS));
        sectionsPanel.add(titanRowsPanel);
        sectionsPanel.add(alignLeft(buildAddRowButton(titanRowsPanel, titanRowStates, titanSpec)));

        buildSection(heroRowsPanel, heroRowStates, heroSpec);
        buildSection(titanRowsPanel, titanRowStates, titanSpec);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JScrollPane(sectionsPanel), BorderLayout.CENTER);

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

    private static JLabel buildSectionHeader(String key) {
        JLabel header = new JLabel(LanguageService.displayName(key));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        return header;
    }

    private static JPanel alignLeft(JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(component);
        return panel;
    }

    private SectionSpec<Hero> buildHeroSpec() {
        return new SectionSpec<>(HeroRepository.findAll(), GuildEntryDialog::heroLabel,
                h -> IconLoader.iconFor(h.imagePath(), ICON_SIZE), Comparator.comparing(GuildEntryDialog::heroLabel),
                m -> m.heroTeams, FortificationType.HERO, Lineup.TeamType.HERO,
                GuildEntryDialog::heroMatchesBuff, GuildEntryDialog::heroScoreBreakdown);
    }

    private SectionSpec<Titan> buildTitanSpec() {
        return new SectionSpec<>(TitanRepository.findAll(), GuildEntryDialog::titanLabel,
                t -> IconLoader.iconFor(t.imagePath(), ICON_SIZE), Comparator.comparing(GuildEntryDialog::titanLabel),
                m -> m.titanTeams, FortificationType.TITAN, Lineup.TeamType.TITAN,
                GuildEntryDialog::titanMatchesBuff, GuildEntryDialog::titanScoreBreakdown);
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

    private static String titanLabel(Titan titan) {
        return LanguageService.displayName(titan.id());
    }

    private static boolean heroMatchesBuff(Fortification fortification, Hero hero) {
        return fortification.buff() instanceof RoleBuff roleBuff && hero.roles().contains(roleBuff.role());
    }

    private static boolean titanMatchesBuff(Fortification fortification, Titan titan) {
        return fortification.buff() instanceof ElementBuff elementBuff && titan.element() == elementBuff.element();
    }

    /** Mirrors {@code FortificationEntryDialog#heroScoreBreakdown} - see {@link TeamScoreCalculator#scoreFor(HeroTeam, Fortification)}. */
    private static TeamScoreCalculator.Breakdown heroScoreBreakdown(TeamDraft<Hero> teamDraft, Fortification fortification) {
        HeroTeam heroTeam = new HeroTeam(null, teamDraft.members, teamDraft.totalPower);
        return TeamScoreCalculator.scoreFor(heroTeam, fortification);
    }

    /** The TITAN-side counterpart of {@link #heroScoreBreakdown} - see {@link TeamScoreCalculator#scoreFor(TitanTeam, Fortification)}. */
    private static TeamScoreCalculator.Breakdown titanScoreBreakdown(TeamDraft<Titan> teamDraft, Fortification fortification) {
        TitanTeam titanTeam = new TitanTeam(null, teamDraft.members, teamDraft.totalPower);
        return TeamScoreCalculator.scoreFor(titanTeam, fortification);
    }

    /**
     * Builds one section's (hero or titan) initial rows, one per existing
     * {@link Lineup.Entry} of {@code spec.teamType()} across every
     * fortification (not just one) - each becomes an editable row via
     * {@link #addRow}. Further rows can be appended later through the
     * section's "+" button (see {@link #buildAddRowButton}).
     */
    private <T> void buildSection(JPanel sectionRowsPanel, List<RowState<T>> rowStates, SectionSpec<T> spec) {
        List<Lineup.Entry> existingEntries = appContext.lineup().entries().stream()
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
            addRow(sectionRowsPanel, rowStates, spec, teams.get(entry.teamIndex()), member, entry.teamIndex(), fortification);
        }
    }

    /**
     * Appends one new row to {@code sectionRowsPanel} - either restoring an
     * existing team ({@code existingDraft}/{@code originalMember}/
     * {@code originalTeamIndex}/{@code presetFortification} all non-null, from
     * {@link #buildSection}) or starting a brand new, empty one (all four
     * {@code null}/{@code -1}, from the section's "+" button - see
     * {@link #buildAddRowButton}).
     */
    private <T> void addRow(JPanel sectionRowsPanel, List<RowState<T>> rowStates, SectionSpec<T> spec,
                             TeamDraft<T> existingDraft, MemberDraft originalMember, int originalTeamIndex,
                             Fortification presetFortification) {
        TeamDraft<T> rowDraft = new TeamDraft<>();
        if (existingDraft != null) {
            rowDraft.members.addAll(existingDraft.members);
            rowDraft.totalPower = existingDraft.totalPower;
            rowDraft.lastModified = existingDraft.lastModified;
        }

        FortComboBox fortCombo = new FortComboBox(fortificationCatalog, spec.fortificationType());
        fortCombo.setPreferredSize(new Dimension(COMBO_WIDTH, MEMBER_COMBO_HEIGHT));
        if (presetFortification != null) {
            fortCombo.setSelectedItem(presetFortification);
        }

        JComboBox<MemberDraft> memberCombo = buildMemberCombo();
        memberCombo.setPreferredSize(new Dimension(COMBO_WIDTH, MEMBER_COMBO_HEIGHT));
        if (originalMember != null) {
            memberCombo.setSelectedItem(originalMember);
        }

        int rowNumber = rowStates.size() + 1;
        JLabel buffCountLabel = buildBuffCountLabel(spec.fortificationType());
        TeamEditorPanel<T> teamEditor = buildTeamEditorPanel(rowDraft, spec,
                () -> updateBuffCountLabel(buffCountLabel, rowDraft, (Fortification) fortCombo.getSelectedItem(), spec, rowNumber));
        updateBuffCountLabel(buffCountLabel, rowDraft, (Fortification) fortCombo.getSelectedItem(), spec, rowNumber);

        // Same convention as FortificationEntryDialog: picking "- none -" as
        // the member clears this row's team, so an unwanted row is simply
        // dropped on save (totalPower == 0) instead of ever being written out.
        memberCombo.addActionListener(e -> {
            if (memberCombo.getSelectedItem() == null) {
                teamEditor.clear();
            }
        });
        fortCombo.addActionListener(e -> updateBuffCountLabel(buffCountLabel, rowDraft,
                (Fortification) fortCombo.getSelectedItem(), spec, rowNumber));

        rowStates.add(new RowState<>(rowDraft, memberCombo, fortCombo, originalMember, originalTeamIndex, spec));
        sectionRowsPanel.add(buildRowPanel(fortCombo, memberCombo, teamEditor, buffCountLabel));
        sectionRowsPanel.revalidate();
        sectionRowsPanel.repaint();
    }

    /** The section's "+" button - appends one fresh, empty row for a new team (see {@link #addRow}). */
    private <T> FlatButton buildAddRowButton(JPanel sectionRowsPanel, List<RowState<T>> rowStates, SectionSpec<T> spec) {
        FlatButton addButton = new FlatButton(IconLoader.iconFor(ICON_ADD_ROW, TOOLBAR_ICON_SIZE, IconLoader.BLUE));
        addButton.setToolTipText(LanguageService.displayName(KEY_ADD_ROW));
        addButton.addActionListener(e -> addRow(sectionRowsPanel, rowStates, spec, null, null, -1, null));
        return addButton;
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
    private static <T> void updateBuffCountLabel(JLabel buffCountLabel, TeamDraft<T> teamDraft, Fortification selectedFortification,
                                                 SectionSpec<T> spec, int rowNumber) {
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
                .sorted(Comparator.comparing(GuildEntryDialog::memberDisplayName, String.CASE_INSENSITIVE_ORDER))
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
     * from every row, not just the one it was typed into.
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
                JOptionPane.showMessageDialog(GuildEntryDialog.this,
                        "A guild has at most " + MAX_MEMBERS + " members.", "Not possible",
                        JOptionPane.WARNING_MESSAGE);
                return combo.getSelectedItem();
            }
            MemberDraft created = new MemberDraft(typed, typed);
            ensureTeamCount(created.heroTeams, MAX_HERO_TEAMS);
            ensureTeamCount(created.titanTeams, MAX_TITAN_TEAMS);
            draft.members.add(created);
            Logger.log("Created guild member: " + typed);
            // Deferred: this combo's own model/selection is still mid-update
            // by JComboBox at this point (it called getItem() to find out
            // what to select next) - rebuilding models now, including this
            // one, would step on that. Runs right after, on the same EDT turn.
            SwingUtilities.invokeLater(GuildEntryDialog.this::refreshAllMemberCombos);
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

    private <T> TeamEditorPanel<T> buildTeamEditorPanel(TeamDraft<T> teamDraft, SectionSpec<T> spec, Runnable onChanged) {
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
     * Resolves every row of {@code rowStates} to a (member, teamIndex) slot,
     * exactly like {@code FortificationEntryDialog#performSave} does for its
     * own rows - empty rows (totalPower == 0) are simply skipped, a filled-in
     * row with no member selected or with no free slot left for its member
     * aborts the whole save with a warning dialog (returning {@code null}).
     */
    private <T> List<RowResolution<T>> resolveResolutions(List<RowState<T>> rowStates) {
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
            List<TeamDraft<T>> teams = row.spec.teamsOf().apply(selectedMember);
            int preferredIndex = selectedMember == row.originalMember ? row.originalTeamIndex : -1;
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
            resolutions.add(new RowResolution<>(selectedMember, idx, row.teamDraft, selectedFortification));
        }
        return resolutions;
    }

    private <T> void applyResolutions(List<RowResolution<T>> resolutions, Function<MemberDraft, List<TeamDraft<T>>> teamsOf) {
        for (RowResolution<T> resolution : resolutions) {
            TeamDraft<T> target = teamsOf.apply(resolution.member()).get(resolution.teamIndex());
            target.members.clear();
            target.members.addAll(resolution.sourceDraft().members);
            target.totalPower = resolution.sourceDraft().totalPower;
            target.lastModified = resolution.sourceDraft().lastModified;
        }
    }

    /** One resolution per fortification-assigned, non-empty row - a row left on "- none -" for its fortification saves its team without deploying it anywhere. */
    private <T> List<Lineup.Entry> buildEntries(List<RowResolution<T>> resolutions, Lineup.TeamType teamType) {
        List<Lineup.Entry> entries = new ArrayList<>();
        for (RowResolution<T> resolution : resolutions) {
            if (resolution.fortification() == null) {
                continue;
            }
            entries.add(new Lineup.Entry(resolution.fortification().id(), resolution.member().id, teamType,
                    resolution.teamIndex(), resolution.sourceDraft().totalPower, 0, 0));
        }
        return entries;
    }

    private void performSave() {
        List<RowResolution<Hero>> heroResolutions = resolveResolutions(heroRowStates);
        if (heroResolutions == null) {
            return;
        }
        List<RowResolution<Titan>> titanResolutions = resolveResolutions(titanRowStates);
        if (titanResolutions == null) {
            return;
        }

        applyResolutions(heroResolutions, m -> m.heroTeams);
        applyResolutions(titanResolutions, m -> m.titanTeams);

        // Every HERO/TITAN entry is regenerated wholesale from the current
        // rows (rather than diffed against the previous lineup) - safe here
        // because buildSection seeded one row per pre-existing entry of that
        // type across every fortification, so the rows are always a complete
        // picture of that type's assignments, unlike FortificationEntryDialog
        // whose rows only ever covered one fortification's slots.
        List<Lineup.Entry> updatedEntries = new ArrayList<>();
        updatedEntries.addAll(buildEntries(heroResolutions, Lineup.TeamType.HERO));
        updatedEntries.addAll(buildEntries(titanResolutions, Lineup.TeamType.TITAN));

        Lineup currentLineup = appContext.lineup();
        Lineup updatedLineup = new Lineup(currentLineup.guildId(), currentLineup.guildName(),
                currentLineup.algorithmName(), currentLineup.createdAt(), updatedEntries);
        Guild updatedGuild = GuildDraftConverter.toGuild(draft);

        try {
            GuildRepository.save(updatedGuild, appContext.guildFilePath());
            LineupRepository.save(updatedLineup, appContext.lineupFilePath());
            appContext.setGuild(updatedGuild);
            appContext.setLineup(updatedLineup);
            Logger.log("Saved guild teams");
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
     * Bundles everything specific to one type (Hero or Titan) that both
     * {@link #buildSection} and {@link #addRow} need - the generic
     * counterpart of the separate lambdas
     * {@code FortificationEntryDialog#buildRows} passes into
     * {@code buildRowsGeneric} for its two branches.
     */
    private record SectionSpec<T>(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                                  Comparator<T> catalogOrder, Function<MemberDraft, List<TeamDraft<T>>> teamsOf,
                                  FortificationType fortificationType, Lineup.TeamType teamType,
                                  BiFunction<Fortification, T, Boolean> matchesBuff,
                                  BiFunction<TeamDraft<T>, Fortification, TeamScoreCalculator.Breakdown> scoreBreakdownOf) {
    }

    private static final class RowState<T> {
        final TeamDraft<T> teamDraft;
        final JComboBox<MemberDraft> memberCombo;
        final FortComboBox fortCombo;
        final MemberDraft originalMember;
        final int originalTeamIndex;
        final SectionSpec<T> spec;

        RowState(TeamDraft<T> teamDraft, JComboBox<MemberDraft> memberCombo, FortComboBox fortCombo,
                 MemberDraft originalMember, int originalTeamIndex, SectionSpec<T> spec) {
            this.teamDraft = teamDraft;
            this.memberCombo = memberCombo;
            this.fortCombo = fortCombo;
            this.originalMember = originalMember;
            this.originalTeamIndex = originalTeamIndex;
            this.spec = spec;
        }
    }

    /** One row's save-time resolution to a real (member, teamIndex) slot, plus which fortification (if any) it should be deployed to. */
    private record RowResolution<T>(MemberDraft member, int teamIndex, TeamDraft<T> sourceDraft, Fortification fortification) {
    }
}
