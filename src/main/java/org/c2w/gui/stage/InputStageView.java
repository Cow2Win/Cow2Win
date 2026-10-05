package org.c2w.gui.stage;

import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanTeam;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.MainActions;
import org.c2w.gui.action.Stage;
import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.service.AppContext;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.c2w.gui.stage.InfoSections.*;

/**
 * The stage view "input": everything that goes into a guild in one place. The work area is
 * the member overview - one row per member with the teams of the selected fortification type;
 * a click selects a member, a double click opens the team assignment. The info panel shows the
 * selected member's teams and the state of the guild's Weltenschlacht journal; the action list
 * is the input menu.
 */
public class InputStageView extends StageView {

    private static final String KEY_COUNT = "memberOverview.count";
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

    private final AppContext appContext;
    private final MainActions actions;

    private final MemberTableModel tableModel = new MemberTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel countLabel = new JLabel();
    private final JPanel memberSection = sectionBody();
    private final JPanel journalSection = sectionBody();

    /** Id of the selected member, null if none - kept across rebuilds while the member exists. */
    private String selectedMemberId;

    /** True while {@link #refreshMembers()} rebuilds the table - selection events are then ignored. */
    private boolean refreshing;

    public InputStageView(AppContext appContext, MainActions actions) {
        super(actions);
        if (appContext == null) {
            throw new IllegalArgumentException("InputStageView needs the AppContext");
        }
        this.appContext = appContext;
        this.actions = actions;
        setUpTable();
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
        countLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        area.add(countLabel, BorderLayout.NORTH);

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

        DefaultTableCellRenderer textRenderer = new TransparentRenderer(SwingConstants.LEFT);
        DefaultTableCellRenderer numberRenderer = new TransparentRenderer(SwingConstants.RIGHT) {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof Integer number ? GuiUtils.NUMBER_FORMAT.format(number) : "");
            }
        };
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_MEMBER).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_TEAMS).setCellRenderer(new TransparentRenderer(SwingConstants.CENTER));
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_TOTAL_POWER).setCellRenderer(numberRenderer);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_STRONGEST_TEAM).setCellRenderer(numberRenderer);
        table.getColumnModel().getColumn(MemberTableModel.COLUMN_LAST_MODIFIED).setCellRenderer(new TransparentRenderer(SwingConstants.RIGHT));

        TableRowSorter<MemberTableModel> sorter = new TableRowSorter<>(tableModel);
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

    /** A double click on a member: the team assignment of the selected fortification type - the same as the menu entry. */
    private void openTeamEntry() {
        Action teamEntry = actions.get(ActionId.OPEN_GUILD_TEAM_ENTRY);
        if (teamEntry.isEnabled()) {
            teamEntry.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, null));
        }
    }

    /** Rebuilds the table for the open guild and the selected fortification type, keeping the selected member. */
    private void refreshMembers() {
        Guild guild = appContext.guild();
        refreshing = true;
        try {
            tableModel.setRows(MemberOverviewModel.rows(guild, appContext.fortificationType()));
            int modelRow = selectedMemberId == null ? -1 : tableModel.indexOf(selectedMemberId);
            if (modelRow < 0) {
                selectedMemberId = null;
                table.clearSelection();
            } else {
                int viewRow = table.convertRowIndexToView(modelRow);
                table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            }
        } finally {
            refreshing = false;
        }
        int count = guild == null || guild.members() == null ? 0 : guild.members().size();
        countLabel.setText(LanguageService.displayName(KEY_COUNT, count, Guild.MAX_MEMBERS));
        showMember();
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
            addLine(memberSection, titleLabel(member.get().name()));
            boolean heroes = appContext.fortificationType() == FortificationType.HERO;
            List<JComponent> teamBlocks = heroes ? heroTeamBlocks(member.get()) : titanTeamBlocks(member.get());
            if (teamBlocks.isEmpty()) {
                addLine(memberSection, mutedLabel(LanguageService.displayName(KEY_MEMBER_NO_TEAMS,
                        LanguageService.displayName(heroes ? KEY_HEROES : KEY_TITANS))));
            }
            teamBlocks.forEach(block -> addLine(memberSection, block));
        }
        relayout(memberSection);
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

    /** A cell renderer that lets the table's translucent background show through, except on the selected row. */
    private static class TransparentRenderer extends DefaultTableCellRenderer {

        /** Whether the cell being rendered is selected - then {@link #paintComponent} fills it. */
        private boolean selected;

        TransparentRenderer(int alignment) {
            setHorizontalAlignment(alignment);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            // Never opaque: a translucent fill on an opaque component leaves paint artifacts.
            selected = isSelected;
            setOpaque(false);
            setForeground(table.getForeground());
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
}
