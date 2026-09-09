package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.data.repository.*;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.guild.*;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FortificationEntryDialog extends JDialog {

    private static final int MAX_HERO_TEAMS = 3;
    private static final int MAX_TITAN_TEAMS = 2;

    private static final int ICON_SIZE = 32;

    /** Language file key (see resources/language/*.txt) for an unselected member combo/team slot. */
    private static final String KEY_NO_SELECTION = "common.none";

    /** Language file key (see resources/language/*.txt) for the tooltip of the "save" toolbar button. */
    private static final String KEY_SAVE_TEAMS = "fortificationEntry.saveTeams";

    /** Classpath path of the "save" button's icon - same icon every other save {@link FlatButton} in the app uses. */
    private static final String ICON_SAVE_TEAMS = "/images/app/save.png";

    /** Target size of the toolbar icon. */
    private static final int TOOLBAR_ICON_SIZE = 20;

    private static final long TYPEAHEAD_TIMEOUT_MS = 1000;

    private static final int MEMBER_COMBO_TOP_OFFSET = 6;

    private final Fortification fortification;
    private final AppContext appContext;
    private final Runnable onSaved;

    private final GuildDraft draft;
    private final List<MemberDraft> memberOptions;

    /**
     * Catalog fields (strategicImportance/buffProfits) editable directly
     * above {@link #rowsPanel} (see constructor) - folded into
     * {@link #performSave}'s own save via
     * {@link FortificationInfoPanel#applyEditsTo(Fortification)} rather than
     * needing a separate save action of its own.
     */
    private final FortificationInfoPanel infoPanel;

    private final JPanel rowsPanel = new JPanel();

    /**
     * Set by {@link #buildRowsGeneric} (generic there, capturing the concrete
     * T - Hero or Titan - matching {@link #fortification}'s type) and simply
     * invoked by the toolbar's save button (see {@link #buildToolbarPanel()}).
     * A no-op until {@link #buildRowsGeneric} has run once from the constructor.
     */
    private Runnable saveAction = () -> { };

    public FortificationEntryDialog(Frame owner, Fortification fortification, AppContext appContext,
                                     Runnable onSaved) {
        super(owner, LanguageService.displayName(fortification.id()), false);
        if (fortification == null) {
            throw new IllegalArgumentException("FortificationEntryDialog needs a fortification");
        }
        if (appContext == null) {
            throw new IllegalArgumentException("FortificationEntryDialog needs a appContext");
        }
        this.fortification = fortification;
        this.appContext = appContext;
        this.onSaved = onSaved;
        this.infoPanel = new FortificationInfoPanel(fortification);

        this.draft = GuildDraftConverter.fromGuild(appContext.guild());
        for (MemberDraft member : draft.members) {
            ensureTeamCount(member.heroTeams, MAX_HERO_TEAMS);
            ensureTeamCount(member.titanTeams, MAX_TITAN_TEAMS);
        }
        this.memberOptions = draft.members.stream()
                .sorted(Comparator.comparing((MemberDraft m) -> m.name.isBlank() ? m.id : m.name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        buildRows();

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(infoPanel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(rowsPanel), BorderLayout.CENTER);

        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        setSize(950, Math.min(820, 140 + infoPanel.getPreferredSize().height + fortification.capacity() * 60));
        setLocationRelativeTo(owner);
    }

     private static <T> void ensureTeamCount(List<TeamDraft<T>> teams, int maxCount) {
        while (teams.size() < maxCount) {
            teams.add(new TeamDraft<>());
        }
    }

    private void buildRows() {
        if (fortification.type() == FortificationType.HERO) {
            buildRowsGeneric(HeroRepository.findAll(), FortificationEntryDialog::heroLabel,
                    h -> IconLoader.iconFor(h.imagePath(), ICON_SIZE),
                    Comparator.comparing(FortificationEntryDialog::heroLabel),
                    m -> m.heroTeams, Lineup.TeamType.HERO);
        } else {
            buildRowsGeneric(TitanRepository.findAll(), FortificationEntryDialog::titanLabel,
                    t -> IconLoader.iconFor(t.imagePath(), ICON_SIZE),
                    Comparator.comparing(FortificationEntryDialog::titanLabel),
                    m -> m.titanTeams, Lineup.TeamType.TITAN);
        }
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

    private static String titanLabel(Titan titan) {
        return LanguageService.displayName(titan.id());
    }

    private <T> void buildRowsGeneric(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                                      Comparator<T> catalogOrder, Function<MemberDraft, List<TeamDraft<T>>> teamsOf,
                                      Lineup.TeamType teamType) {
        List<RowState<T>> rowStates = new ArrayList<>();

        List<Lineup.Entry> existingEntries = appContext.lineup().entries().stream()
                .filter(e -> e.fortificationId().equals(fortification.id()) && e.teamType() == teamType)
                .toList();

        for (int i = 0; i < fortification.capacity(); i++) {
            JComboBox<MemberDraft> combo = buildMemberCombo();
            combo.setPreferredSize(new Dimension(150, 41));

            TeamDraft<T> rowDraft = new TeamDraft<>();
            MemberDraft originalMember = null;
            int originalTeamIndex = -1;

            if (i < existingEntries.size()) {
                Lineup.Entry entry = existingEntries.get(i);
                MemberDraft member = findMemberDraft(entry.teamMemberId());
                List<TeamDraft<T>> teams = member == null ? null : teamsOf.apply(member);
                if (member != null && teams != null && entry.teamIndex() >= 0 && entry.teamIndex() < teams.size()) {
                    originalMember = member;
                    originalTeamIndex = entry.teamIndex();
                    TeamDraft<T> existingDraft = teams.get(originalTeamIndex);
                    rowDraft.members.addAll(existingDraft.members);
                    rowDraft.totalPower = existingDraft.totalPower;
                    rowDraft.lastModified = existingDraft.lastModified;
                    combo.setSelectedItem(member);
                } else {
                    Logger.log("Could not restore an assigned team for " + LanguageService.displayName(fortification.id())
                            + " - the member or team no longer exists");
                }
            }

            JPanel teamEditor = buildTeamEditorPanel(rowDraft, catalog, label, icon, catalogOrder);
            rowStates.add(new RowState<>(rowDraft, combo, originalMember, originalTeamIndex));
            rowsPanel.add(buildRowPanel(i, combo, teamEditor));
        }

        saveAction = () -> performSave(rowStates, teamsOf, teamType);
    }

    private JPanel buildRowPanel(int index, JComboBox<MemberDraft> combo, JPanel teamEditor) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setBorder(BorderFactory.createEmptyBorder(MEMBER_COMBO_TOP_OFFSET, 0, 0, 0));
        left.add(combo);

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

        return row;
    }

    private JComboBox<MemberDraft> buildMemberCombo() {
        DefaultComboBoxModel<MemberDraft> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        memberOptions.forEach(model::addElement);
        JComboBox<MemberDraft> combo = new JComboBox<>(model);
        combo.setKeySelectionManager(buildMemberKeySelectionManager());
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText(LanguageService.displayName(KEY_NO_SELECTION));
                } else {
                    MemberDraft m = (MemberDraft) value;
                    setText(m.name.isBlank() ? m.id : m.name);
                }
                return this;
            }
        });
        return combo;
    }

    private static JComboBox.KeySelectionManager buildMemberKeySelectionManager() {
        return new JComboBox.KeySelectionManager() {
            private String typed = "";
            private long lastKeystrokeAt = 0;

            @Override
            public int selectionForKey(char keyChar, ComboBoxModel<?> model) {
                long now = System.currentTimeMillis();
                String continued = now - lastKeystrokeAt <= TYPEAHEAD_TIMEOUT_MS ? typed : "";
                String candidate = continued + Character.toLowerCase(keyChar);
                lastKeystrokeAt = now;

                int match = firstMatch(model, candidate);
                if (match < 0 && !continued.isEmpty()) {
                    candidate = String.valueOf(Character.toLowerCase(keyChar));
                    match = firstMatch(model, candidate);
                }
                typed = candidate;
                return match;
            }

            private int firstMatch(ComboBoxModel<?> model, String prefix) {
                for (int i = 0; i < model.getSize(); i++) {
                    Object element = model.getElementAt(i);
                    if (element == null) {
                        continue;
                    }
                    MemberDraft member = (MemberDraft) element;
                    String display = member.name.isBlank() ? member.id : member.name;
                    if (display.toLowerCase().startsWith(prefix)) {
                        return i;
                    }
                }
                return -1;
            }
        };
    }

    private <T> JPanel buildTeamEditorPanel(TeamDraft<T> teamDraft, List<T> catalog, Function<T, String> label,
                                            Function<T, Icon> icon, Comparator<T> catalogOrder) {
        return new TeamEditorPanel<>(catalog, label, icon, null, teamDraft,
                LanguageService.displayName(KEY_NO_SELECTION), catalogOrder);
    }

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


    private static String entryKey(Lineup.TeamType type, String memberId, int teamIndex) {
        return type + "#" + memberId + "#" + teamIndex;
    }

    private MemberDraft findMemberDraft(String memberId) {
        return draft.members.stream().filter(m -> m.id.equals(memberId)).findFirst().orElse(null);
    }


    private JPanel buildToolbarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_TEAMS, TOOLBAR_ICON_SIZE));
        saveButton.setToolTipText(LanguageService.displayName(KEY_SAVE_TEAMS));
        saveButton.addActionListener(e -> saveAction.run());
        buttons.add(saveButton);
        panel.add(buttons, BorderLayout.WEST);
        return panel;
    }

    private <T> void performSave(List<RowState<T>> rowStates, Function<MemberDraft, List<TeamDraft<T>>> teamsOf,
                                 Lineup.TeamType teamType) {
        Set<String> boundKeys = new HashSet<>();
        List<RowResolution<T>> resolutions = new ArrayList<>();

        for (RowState<T> row : rowStates) {
            if (row.teamDraft.totalPower == 0) {
                continue; // empty row - simply dropped, same convention as GuildDraftConverter#toGuild
            }
            MemberDraft selectedMember = (MemberDraft) row.combo.getSelectedItem();
            if (selectedMember == null) {
                JOptionPane.showMessageDialog(this,
                        "A row has a team filled in but no member selected - pick a member for it, or clear its power, before saving.",
                        "Could not save", JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<TeamDraft<T>> teams = teamsOf.apply(selectedMember);
            int preferredIndex = selectedMember == row.originalMember ? row.originalTeamIndex : -1;
            int idx = resolveTeamIndex(teams, boundKeys, selectedMember.id, preferredIndex);
            if (idx < 0) {
                JOptionPane.showMessageDialog(this,
                        "'" + (selectedMember.name.isBlank() ? selectedMember.id : selectedMember.name)
                                + "' has no free team slot left.",
                        "Could not save", JOptionPane.WARNING_MESSAGE);
                return;
            }
            boundKeys.add(rowKey(selectedMember.id, idx));
            resolutions.add(new RowResolution<>(selectedMember, idx, row.teamDraft));
        }

        for (RowResolution<T> resolution : resolutions) {
            TeamDraft<T> target = teamsOf.apply(resolution.member()).get(resolution.teamIndex());
            target.members.clear();
            target.members.addAll(resolution.sourceDraft().members);
            target.totalPower = resolution.sourceDraft().totalPower;
            target.lastModified = resolution.sourceDraft().lastModified;
        }

        Set<String> reassigned = new HashSet<>();
        for (RowResolution<T> resolution : resolutions) {
            reassigned.add(entryKey(teamType, resolution.member().id, resolution.teamIndex()));
        }

        Lineup currentLineup = appContext.lineup();
        List<Lineup.Entry> updatedEntries = new ArrayList<>();
        for (Lineup.Entry entry : currentLineup.entries()) {
            boolean forThisFortification = entry.fortificationId().equals(fortification.id());
            boolean reassignedElsewhere = reassigned.contains(entryKey(entry.teamType(), entry.teamMemberId(), entry.teamIndex()));
            if (!forThisFortification && !reassignedElsewhere) {
                updatedEntries.add(entry);
            }
        }
        for (RowResolution<T> resolution : resolutions) {
            updatedEntries.add(new Lineup.Entry(fortification.id(), resolution.member().id, teamType, resolution.teamIndex(),
                    resolution.sourceDraft().totalPower, 0, 0));
        }

        Lineup updatedLineup = new Lineup(currentLineup.guildId(), currentLineup.guildName(),
                currentLineup.algorithmName(), currentLineup.createdAt(), updatedEntries);
        Guild updatedGuild = GuildDraftConverter.toGuild(draft);

        Fortification updatedFortification = infoPanel.applyEditsTo(fortification);
        List<Fortification> updatedCatalog = FortificationRepository.findAll().stream()
                .map(f -> f.id().equals(updatedFortification.id()) ? updatedFortification : f)
                .collect(Collectors.toList());

        try {
            FortificationRepository.save(updatedCatalog);
            GuildRepository.save(updatedGuild, appContext.guildFilePath());
            LineupRepository.save(updatedLineup, appContext.lineupFilePath());
            appContext.setGuild(updatedGuild);
            appContext.setLineup(updatedLineup);
            Logger.log("Saved: " + LanguageService.displayName(fortification.id()));
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

    private static final class RowState<T> {
        final TeamDraft<T> teamDraft;
        final JComboBox<MemberDraft> combo;
        final MemberDraft originalMember;
        final int originalTeamIndex;

        RowState(TeamDraft<T> teamDraft, JComboBox<MemberDraft> combo, MemberDraft originalMember, int originalTeamIndex) {
            this.teamDraft = teamDraft;
            this.combo = combo;
            this.originalMember = originalMember;
            this.originalTeamIndex = originalTeamIndex;
        }
    }

    /** One row's save-time resolution to a real (member, teamIndex) slot - see {@link #performSave}. */
    private record RowResolution<T>(MemberDraft member, int teamIndex, TeamDraft<T> sourceDraft) {
    }
}
