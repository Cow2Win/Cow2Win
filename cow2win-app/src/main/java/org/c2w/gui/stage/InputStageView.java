package org.c2w.gui.stage;

import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanTeam;
import org.c2w.gui.StageStatus;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.DataStatus;
import org.c2w.service.GuildMemberService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.c2w.gui.stage.InfoSections.*;

/**
 * The stage view "input": everything that goes into a guild in one place. The work area is
 * the member overview - one row per member with the teams of the selected fortification type;
 * a click selects a member, a double click (or Enter) opens the team assignment for this member
 * only; "+" / "-" next to the count, the context menu and Del add and delete members (saved right
 * away, see {@link GuildMemberService}). The info panel shows the
 * selected member's teams and the state of the guild's Weltenschlacht journal; the action list
 * is the input menu.
 */
public class InputStageView extends StageView {

    private static final String KEY_COUNT = "memberOverview.count";
    private static final String KEY_SHOWN = "memberOverview.shown";
    private static final String KEY_ONLY_STALE = "memberOverview.onlyStale";
    private static final String KEY_STALE_JOURNAL = "memberOverview.stale.journal";
    private static final String KEY_MEMBER = "stageInfo.member";
    private static final String KEY_MEMBER_NONE = "stageInfo.member.none";
    private static final String KEY_MEMBER_TEAM = "stageInfo.member.team";
    private static final String KEY_MEMBER_NO_TEAMS = "stageInfo.member.noTeams";
    private static final String KEY_JOURNAL = "stageInfo.journal";
    private static final String KEY_JOURNAL_NONE = "stageInfo.journal.none";
    private static final String KEY_JOURNAL_ERROR = "stageInfo.journal.error";
    private static final String KEY_JOURNAL_COUNTS = "stageInfo.journal.counts";
    private static final String KEY_JOURNAL_NEWEST_DEFENSE = "stageInfo.journal.newestDefense";
    private static final String KEY_JOURNAL_RECENT = "stageInfo.journal.recent";
    private static final String KEY_PET = "teamEditor.pet";
    private static final String KEY_WAR_FLAG = "teamEditor.warFlag";
    private static final String KEY_TOTEMS = "teamEditor.totems";
    private static final String KEY_HEROES = "fortificationMap.showHeroes";
    private static final String KEY_TITANS = "fortificationMap.showTitans";

    /** Selected table row: the teal accent with low opacity. */
    private static final Color SELECTION_FILL = new Color(IconLoader.BLUE.getRed(), IconLoader.BLUE.getGreen(),
            IconLoader.BLUE.getBlue(), 70);

    private static final int ROW_HEIGHT = 28;
    private static final int STATUS_COLUMN_WIDTH = 30;
    private static final int LIGHT = 10;

    private static final String KEY_ADD_MEMBER = "memberOverview.addMember";
    private static final String KEY_REMOVE_MEMBER = "memberOverview.removeMember";
    private static final String ICON_ADD_MEMBER = "/images/app/member-new.png";
    private static final String ICON_REMOVE_MEMBER = "/images/app/member-remove.png";
    private static final int MEMBER_ICON_SIZE = 20;

    private final AppContext appContext;
    /** Opens the team assignment for one member (id) - see {@code ActionBar#openTeamEntryFor}. */
    private final Consumer<String> openTeamEntryForMember;
    private final GuildMemberService memberService;
    private final FlatButton addMemberButton = new FlatButton(
            IconLoader.iconFor(ICON_ADD_MEMBER, MEMBER_ICON_SIZE, IconLoader.GREEN));
    private final FlatButton removeMemberButton = new FlatButton(
            IconLoader.iconFor(ICON_REMOVE_MEMBER, MEMBER_ICON_SIZE, IconLoader.RED));
    private final JMenuItem addMemberItem = new JMenuItem(LanguageService.displayName(KEY_ADD_MEMBER));
    private final JMenuItem removeMemberItem = new JMenuItem(LanguageService.displayName(KEY_REMOVE_MEMBER));

    private final MemberTableModel tableModel = new MemberTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<MemberTableModel> sorter = new TableRowSorter<>(tableModel);
    /** "Show outdated only" in the action list: hides the green members. */
    private JCheckBox onlyStaleToggle;
    private final JLabel countLabel = new JLabel();
    private final JPanel memberSection = sectionBody();
    private final JPanel journalSection = sectionBody();

    /** Id of the selected member, null if none - kept across rebuilds while the member exists. */
    private String selectedMemberId;

    /** The last data status, null before the first one. */
    private DataStatus dataStatus;

    /** True while {@link #refreshMembers()} rebuilds the table - selection events are then ignored. */
    private boolean refreshing;

    /**
     * @param openTeamEntryForMember opens the team assignment of the selected fortification type
     *                               for one member (its id) - on a double click, Enter and after
     *                               adding a member
     */
    public InputStageView(AppContext appContext, MainActions actions, Consumer<String> openTeamEntryForMember) {
        super(actions);
        if (appContext == null) {
            throw new IllegalArgumentException("InputStageView needs the AppContext");
        }
        this.appContext = appContext;
        this.openTeamEntryForMember = Objects.requireNonNull(openTeamEntryForMember);
        this.memberService = new GuildMemberService(appContext);
        setUpTable();
        setUpMemberActions();
        build();

        refreshMembers();
        reloadJournal();
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                refreshMembers();
                reloadJournal();
            }

            @Override
            public void fortificationTypeChanged() {
                refreshMembers();
                // The cell texts take the color of the selected type, see textColor().
                table.repaint();
            }

            @Override
            public void dirtyStateChanged() {
                // Teams may have been changed in a dialog; an import or sync also changes the guild.
                refreshMembers();
                reloadJournal();
            }
        });
    }

    @Override
    public Stage stage() {
        return Stage.INPUT;
    }

    /** The journal can change without an AppContext event (e.g. maintenance windows) - reload it whenever the view is shown. */
    @Override
    public void onShown() {
        reloadJournal();
    }

    // --- work area: member overview ---

    @Override
    protected JComponent createWorkArea() {
        JPanel area = translucentPanel(new BorderLayout());
        countLabel.setForeground(MUTED_COLOR);
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 12));
        header.add(countLabel);
        header.add(addMemberButton);
        header.add(removeMemberButton);
        area.add(header, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        area.add(scrollPane, BorderLayout.CENTER);
        return area;
    }

    private void setUpTable() {
        table.setOpaque(false);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(ROW_HEIGHT);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        Supplier<Color> textColor = this::textColor;
        DefaultTableCellRenderer textRenderer = new TransparentRenderer(SwingConstants.LEFT, textColor);
        DefaultTableCellRenderer numberRenderer = new TransparentRenderer(SwingConstants.RIGHT, textColor) {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof Integer number ? GuiUtils.NUMBER_FORMAT.format(number) : "");
            }
        };
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_STATUS).setCellRenderer(new StatusRenderer(textColor));
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_STATUS).setMinWidth(STATUS_COLUMN_WIDTH);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_STATUS).setMaxWidth(STATUS_COLUMN_WIDTH);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_MEMBER).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_TEAMS).setCellRenderer(new TransparentRenderer(SwingConstants.CENTER, textColor));
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_TOTAL_POWER).setCellRenderer(numberRenderer);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_STRONGEST_TEAM).setCellRenderer(numberRenderer);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_LAST_MODIFIED).setCellRenderer(new TransparentRenderer(SwingConstants.RIGHT, textColor));

        sorter.setSortKeys(List.of(new RowSorter.SortKey(MemberTableModel.COLUMN_TOTAL_POWER, SortOrder.DESCENDING)));
        sorter.setSortsOnUpdates(true);
        table.setRowSorter(sorter);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing) {
                int viewRow = table.getSelectedRow();
                selectedMemberId = viewRow < 0 ? null
                        : tableModel.row(table.convertRowIndexToModel(viewRow)).memberId();
                showMember();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    openTeamEntry();
                }
            }
        });
    }

    /** A double click (or Enter) on a member: the team assignment of the selected fortification type for this member only. */
    private void openTeamEntry() {
        if (selectedMemberId != null) {
            openTeamEntryForMember.accept(selectedMemberId);
        }
    }

    // --- add / delete members ---

    /** "+" / "-" next to the count, the table's context menu, Del and Enter in the table. */
    private void setUpMemberActions() {
        addMemberButton.setToolTipText(LanguageService.displayName(KEY_ADD_MEMBER));
        addMemberButton.addActionListener(e -> onAddMember());
        removeMemberButton.setToolTipText(LanguageService.displayName(KEY_REMOVE_MEMBER));
        removeMemberButton.addActionListener(e -> onRemoveMember());
        addMemberItem.addActionListener(e -> onAddMember());
        removeMemberItem.addActionListener(e -> onRemoveMember());

        JPopupMenu popup = new JPopupMenu();
        popup.add(addMemberItem);
        popup.add(removeMemberItem);
        table.setComponentPopupMenu(popup);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectRowForPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                selectRowForPopup(e);
            }
        });

        InputMap keys = table.getInputMap(JComponent.WHEN_FOCUSED);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "c2w.removeMember");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "c2w.openTeamEntry");
        table.getActionMap().put("c2w.removeMember", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onRemoveMember();
            }
        });
        table.getActionMap().put("c2w.openTeamEntry", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openTeamEntry();
            }
        });
    }

    /** A right click selects the member under the mouse first - on empty space, nothing is selected. */
    private void selectRowForPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
            table.clearSelection();
        } else {
            table.setRowSelectionInterval(viewRow, viewRow);
        }
    }

    /** "Add member" only below {@link Guild#MAX_MEMBERS}, "delete member" only with a selected member. */
    private void updateMemberActions() {
        boolean canAdd = memberService.canAddMember();
        String addTooltip = canAdd ? LanguageService.displayName(KEY_ADD_MEMBER)
                : LanguageService.displayName("common.maxMembers", Guild.MAX_MEMBERS);
        addMemberButton.setEnabled(canAdd);
        addMemberButton.setToolTipText(addTooltip);
        addMemberItem.setEnabled(canAdd);
        addMemberItem.setToolTipText(canAdd ? null : addTooltip);
        boolean canRemove = selectedMemberId != null;
        removeMemberButton.setEnabled(canRemove);
        removeMemberItem.setEnabled(canRemove);
    }

    /**
     * Asks for the name (its id is the name), adds the member and saves the guild right away,
     * selects it and opens its team assignment.
     */
    private void onAddMember() {
        if (!memberService.canAddMember()) {
            return;
        }
        String name = JOptionPane.showInputDialog(dialogParent(), LanguageService.displayName("memberOverview.namePrompt"),
                LanguageService.displayName(KEY_ADD_MEMBER), JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        if (memberService.isTaken(name)) {
            JOptionPane.showMessageDialog(dialogParent(), LanguageService.displayName("memberOverview.memberExists"),
                    LanguageService.displayName("common.notPossibleTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        GuildMember member;
        try {
            member = memberService.addMember(name);
        } catch (IOException e) {
            Logger.logException("Could not add the member " + name.trim(), e);
            showSaveError(e);
            return;
        }
        selectedMemberId = member.id();
        refreshMembers();
        openTeamEntryForMember.accept(member.id());
    }

    /**
     * After a confirmation naming what goes with it: deletes the selected member, saves the
     * guild and cleans up lineups and journal right away (see {@link GuildMemberService#removeMember}).
     */
    private void onRemoveMember() {
        String memberId = selectedMemberId;
        if (memberId == null) {
            return;
        }
        GuildMemberService.RemovalImpact impact = memberService.impactOf(memberId);
        String message = LanguageService.displayName("memberOverview.removeConfirm", impact.displayName(),
                impact.heroTeams(), impact.titanTeams(), impact.entries(), impact.lineups());
        Object[] options = {UIManager.getString("OptionPane.yesButtonText"), UIManager.getString("OptionPane.noButtonText")};
        int choice = JOptionPane.showOptionDialog(dialogParent(), message, LanguageService.displayName(KEY_REMOVE_MEMBER),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        if (choice != 0) {
            return;
        }
        GuildMemberService.RemovalResult result;
        try {
            result = memberService.removeMember(memberId);
        } catch (IOException e) {
            Logger.logException("Could not remove the member " + memberId, e);
            showSaveError(e);
            return;
        }
        selectedMemberId = null;
        refreshMembers();
        if (!result.failures().isEmpty()) {
            JOptionPane.showMessageDialog(dialogParent(), LanguageService.displayName("memberOverview.removeErrors")
                            + "\n" + String.join("\n", result.failures()),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showSaveError(IOException e) {
        JOptionPane.showMessageDialog(dialogParent(), LanguageService.displayName("common.saveError") + "\n" + e.getMessage(),
                LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
    }

    /** The main window (centered dialogs), or this view while it is not in a window. */
    private Component dialogParent() {
        Window window = SwingUtilities.getWindowAncestor(this);
        return window != null ? window : this;
    }

    /** Text color of the member table: the color of the selected fortification type. */
    private Color textColor() {
        return FortificationTypeStyle.color(appContext.fortificationType());
    }

    /** Rebuilds the table for the open guild and the selected fortification type, keeping the selected member. */
    private void refreshMembers() {
        Guild guild = appContext.guild();
        updateTable(() -> tableModel.setRows(MemberOverviewModel.rows(guild, appContext.fortificationType())));
    }

    /**
     * Runs {@code change} (rebuilding or filtering the table) and selects the selected member
     * again - if it is still shown; then updates the count and the info panel.
     */
    private void updateTable(Runnable change) {
        refreshing = true;
        try {
            change.run();
            int modelRow = selectedMemberId == null ? -1 : tableModel.indexOf(selectedMemberId);
            int viewRow = modelRow < 0 ? -1 : table.convertRowIndexToView(modelRow);
            if (viewRow < 0) {
                selectedMemberId = null;
                table.clearSelection();
            } else {
                table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            }
        } finally {
            refreshing = false;
        }
        Guild guild = appContext.guild();
        int count = guild == null || guild.members() == null ? 0 : guild.members().size();
        String countText = LanguageService.displayName(KEY_COUNT, count, Guild.MAX_MEMBERS);
        if (isOnlyStale()) {
            countText += " " + LanguageService.displayName(KEY_SHOWN, table.getRowCount());
        }
        countLabel.setText(countText);
        showMember();
    }

    /**
     * Shows a new data status (from the {@code DataStatusController}): the traffic lights of the
     * members, the "show outdated only" filter and the deviations of the selected member.
     */
    public void setDataStatus(DataStatus status) {
        dataStatus = status;
        tableModel.setFindings(status == null ? Map.of() : status.members());
        refreshMembers();
    }

    // --- action list: "show outdated only" ---

    @Override
    protected void addActionListExtras(StageActionList actionList) {
        onlyStaleToggle = new JCheckBox(LanguageService.displayName(KEY_ONLY_STALE));
        onlyStaleToggle.setOpaque(false);
        onlyStaleToggle.addActionListener(e -> applyOnlyStale());
        actionList.addExtra(onlyStaleToggle);
    }

    private void applyOnlyStale() {
        updateTable(() -> sorter.setRowFilter(isOnlyStale() ? new StaleFilter() : null));
    }

    private boolean isOnlyStale() {
        return onlyStaleToggle != null && onlyStaleToggle.isSelected();
    }

    /** Shows only red and orange members. */
    private static final class StaleFilter extends RowFilter<TableModel, Integer> {
        @Override
        public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
            return ((MemberTableModel) entry.getModel()).status(entry.getIdentifier()).isStale();
        }
    }

    // --- info panel ---

    @Override
    protected JComponent createInfoPanel() {
        JPanel panel = InfoSections.infoPanel();
        addSection(panel, KEY_MEMBER, memberSection);
        addSection(panel, KEY_JOURNAL, journalSection);
        return panel;
    }

    /** Shows the selected member's teams of the selected fortification type, or the hint to select one. */
    private void showMember() {
        memberSection.removeAll();
        Optional<GuildMember> member = selectedMember();
        if (member.isEmpty()) {
            addLine(memberSection, mutedLabel(LanguageService.displayName(KEY_MEMBER_NONE)));
        } else {
            addLine(memberSection, titleLabel(member.get().displayName()));
            staleJournalLines(member.get().id()).forEach(line -> addLine(memberSection, line));
            boolean heroes = appContext.fortificationType() == FortificationType.HERO;
            List<JComponent> teamBlocks = heroes ? heroTeamBlocks(member.get()) : titanTeamBlocks(member.get());
            if (teamBlocks.isEmpty()) {
                addLine(memberSection, mutedLabel(LanguageService.displayName(KEY_MEMBER_NO_TEAMS,
                        LanguageService.displayName(heroes ? KEY_HEROES : KEY_TITANS))));
            }
            teamBlocks.forEach(block -> addLine(memberSection, block));
        }
        relayout(memberSection);
        updateMemberActions();
    }

    /**
     * For a member outdated per journal (red): the date of the defense log and every team the
     * log shows differently - team number, stored vs. log power. Empty otherwise.
     */
    private List<JComponent> staleJournalLines(String memberId) {
        Optional<DataStatus.MemberFinding> finding = dataStatus == null ? Optional.empty() : dataStatus.member(memberId);
        if (finding.isEmpty() || finding.get().state() != DataStatus.MemberState.STALE_JOURNAL) {
            return List.of();
        }
        Color red = StageStatus.ACTION_NEEDED.color();
        List<JComponent> lines = new ArrayList<>();
        lines.add(coloredLabel(LanguageService.displayName(KEY_STALE_JOURNAL, shortDate(finding.get().logDate())), red));
        for (DataStatus.TeamDeviation deviation : finding.get().deviations()) {
            String text = deviation.index() < 0
                    ? JournalTexts.of("teamKind", deviation.kind()) + ": " + JournalTexts.number(deviation.logPower())
                    : JournalTexts.team(deviation.kind(), deviation.index()) + ": "
                    + JournalTexts.powerChange(deviation.storedPower(), deviation.logPower()).replace("→", arrow());
            lines.add(mutedLabel(text));
        }
        return lines;
    }

    private Optional<GuildMember> selectedMember() {
        Guild guild = appContext.guild();
        if (selectedMemberId == null || guild == null || guild.members() == null) {
            return Optional.empty();
        }
        return guild.members().stream().filter(m -> m.id().equals(selectedMemberId)).findFirst();
    }

    private List<JComponent> heroTeamBlocks(GuildMember member) {
        List<JComponent> blocks = new ArrayList<>();
        if (member.heroTeams() == null) {
            return blocks;
        }
        member.heroTeams().stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(HeroTeam::index))
                .forEach(team -> {
                    List<String> lines = new ArrayList<>();
                    lines.add(names(team.heroes() == null ? List.of() : team.heroes().stream().map(Hero::id).toList()));
                    if (team.pet() != null) {
                        lines.add(LanguageService.displayName(KEY_PET) + ": " + LanguageService.displayName(team.pet().id()));
                    }
                    if (team.warFlag() != null) {
                        lines.add(LanguageService.displayName(KEY_WAR_FLAG) + ": " + LanguageService.displayName(team.warFlag().id()));
                    }
                    blocks.add(teamBlock(team.index(), team.totalPower(), team.lastModified(), lines));
                });
        return blocks;
    }

    private List<JComponent> titanTeamBlocks(GuildMember member) {
        List<JComponent> blocks = new ArrayList<>();
        if (member.titanTeams() == null) {
            return blocks;
        }
        member.titanTeams().stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(TitanTeam::index))
                .forEach(team -> {
                    List<String> lines = new ArrayList<>();
                    lines.add(names(team.titans() == null ? List.of() : team.titans().stream().map(Titan::id).toList()));
                    if (team.totems() != null && !team.totems().isEmpty()) {
                        lines.add(LanguageService.displayName(KEY_TOTEMS) + ": " + TotemTexts.names(team.totems()));
                    }
                    blocks.add(teamBlock(team.index(), team.totalPower(), team.lastModified(), lines));
                });
        return blocks;
    }

    /** "Team n", power and last change, then the given lines (members of the team, pet, war flag, totems). */
    private static JComponent teamBlock(int index, int power, LocalDate lastModified, List<String> lines) {
        JPanel block = sectionBody();
        block.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel heading = new JLabel(LanguageService.displayName(KEY_MEMBER_TEAM, index + 1)
                + "   " + GuiUtils.NUMBER_FORMAT.format(power)
                + (lastModified == null ? "" : "   · " + shortDate(lastModified)));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        addLine(block, heading);
        for (String line : lines) {
            addLine(block, mutedLabel(line));
        }
        return block;
    }

    private static String names(List<String> ids) {
        return ids.stream().map(LanguageService::displayName).collect(Collectors.joining(", "));
    }

    // --- journal ---

    /** Reads the journal in the background and shows it when done. */
    private void reloadJournal() {
        new SwingWorker<JournalInfoModel.JournalInfo, Void>() {
            @Override
            protected JournalInfoModel.JournalInfo doInBackground() {
                return JournalInfoModel.load(appContext.journal());
            }

            @Override
            protected void done() {
                try {
                    showJournal(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showJournal(JournalInfoModel.JournalInfo.of(JournalInfoModel.State.ERROR));
                }
            }
        }.execute();
    }

    /** Shows {@code info} in the journal section. Package-visible for tests. */
    void showJournal(JournalInfoModel.JournalInfo info) {
        journalSection.removeAll();
        switch (info.state()) {
            case NO_JOURNAL -> addLine(journalSection, mutedLabel(LanguageService.displayName(KEY_JOURNAL_NONE)));
            case ERROR -> addLine(journalSection, mutedLabel(LanguageService.displayName(KEY_JOURNAL_ERROR)));
            case LOADED -> {
                addLine(journalSection, new JLabel(LanguageService.displayName(KEY_JOURNAL_COUNTS,
                        number(info.battles()), number(info.logs()))));
                if (info.newestDefense() != null) {
                    addLine(journalSection, mutedLabel(LanguageService.displayName(KEY_JOURNAL_NEWEST_DEFENSE,
                            shortDate(info.newestDefense()))));
                }
                if (!info.recentBattles().isEmpty()) {
                    JLabel recent = new JLabel(LanguageService.displayName(KEY_JOURNAL_RECENT));
                    recent.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
                    addLine(journalSection, recent);
                    for (BattleSummary battle : info.recentBattles()) {
                        String result = battle.result() == null ? "" : "  ·  " + JournalTexts.of("battleResult", battle.result());
                        addLine(journalSection, mutedLabel(shortDate(battle.date()) + "  "
                                + JournalTexts.opponent(battle.opponent()) + result));
                    }
                }
            }
        }
        relayout(journalSection);
    }

    // --- for tests ---

    /** Selects the row of the member with {@code memberId} (as a click would). */
    void selectMember(String memberId) {
        int modelRow = tableModel.indexOf(memberId);
        if (modelRow < 0) {
            table.clearSelection();
        } else {
            int viewRow = table.convertRowIndexToView(modelRow);
            table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        }
    }

    JTable table() {
        return table;
    }

    JButton addMemberButton() {
        return addMemberButton;
    }

    JButton removeMemberButton() {
        return removeMemberButton;
    }

    JCheckBox onlyStaleToggle() {
        return onlyStaleToggle;
    }

    String countText() {
        return countLabel.getText();
    }

    JLabel countLabel() {
        return countLabel;
    }

    List<String> memberSectionTexts() {
        List<String> texts = new ArrayList<>();
        collectTexts(memberSection, texts);
        return texts;
    }

    List<String> journalSectionTexts() {
        List<String> texts = new ArrayList<>();
        collectTexts(journalSection, texts);
        return texts;
    }

    /**
     * A cell renderer that lets the table's translucent background show through, except on the
     * selected row; the text color comes from a supplier (selected rows included).
     */
    private static class TransparentRenderer extends DefaultTableCellRenderer {

        private final Supplier<Color> textColor;

        /** Whether the cell being rendered is selected - then {@link #paintComponent} fills it. */
        private boolean selected;

        TransparentRenderer(int alignment, Supplier<Color> textColor) {
            setHorizontalAlignment(alignment);
            this.textColor = textColor;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            // Never opaque: a translucent fill on an opaque component leaves paint artifacts.
            selected = isSelected;
            setOpaque(false);
            setForeground(textColor.get());
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                g.setColor(SELECTION_FILL);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(g);
        }
    }

    /** The traffic light of a member's data status: a colored dot with its tooltip, nothing while unknown. */
    private static final class StatusRenderer extends TransparentRenderer {

        private Color light;

        StatusRenderer(Supplier<Color> textColor) {
            super(SwingConstants.CENTER, textColor);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            MemberTableModel.StatusValue status = (MemberTableModel.StatusValue) value;
            light = status == null ? null : status.light().color();
            setText("");
            setToolTipText(status == null ? null : status.tooltip());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (light == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(light);
                g2.fillOval((getWidth() - LIGHT) / 2, (getHeight() - LIGHT) / 2, LIGHT, LIGHT);
            } finally {
                g2.dispose();
            }
        }
    }
}
