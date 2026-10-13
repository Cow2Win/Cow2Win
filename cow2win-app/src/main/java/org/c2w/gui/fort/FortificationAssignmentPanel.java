package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.FortificationAssignment;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.FortificationTypeStyle;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.stage.ConceptInfoModel;
import org.c2w.i18n.LanguageService;
import org.c2w.service.AppContext;
import org.c2w.service.LineupService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The content of {@link FortificationAssignmentDialog}: the name and the facts
 * ("filled/capacity · power · buff %") of one fortification and one row per place - the
 * assigned teams (strongest first, see {@link ConceptInfoModel#teamsOf}) with "move/swap" and
 * "remove", then the free places with "assign team". Every change goes straight into the open
 * lineup via {@link LineupService}; the panel follows the lineup through its
 * {@link AppContext} listener (detach it with {@link #detach()}).
 *
 * <p>There are no assignment rules besides the capacity: a member may have several teams in
 * the same fortification, fortification marks are not checked. While the live lineup is open
 * the panel is read-only - no buttons, a hint instead.
 *
 * <p>The choices (team to assign, target fortification, team to swap with) are made by a
 * {@link Chooser}, so the logic can be tested without showing any window.
 */
class FortificationAssignmentPanel extends JPanel {

    /** Makes the choices of the panel - a list dialog in the application, a fixed answer in tests. */
    interface Chooser {

        /**
         * The team to assign to {@code fortification}, from {@code candidates} (see
         * {@link FortificationAssignment#candidatesFor}) - empty for none. {@code candidates}
         * may be empty.
         */
        Optional<FortificationAssignment.Candidate> chooseCandidate(Fortification fortification,
                                                                    List<FortificationAssignment.Candidate> candidates);

        /** The fortification to move a team to, from {@code targets} - empty for none. */
        Optional<Fortification> chooseTarget(List<Target> targets);

        /** The team of the full fortification {@code target} to swap with, from {@code teams} - empty for none. */
        Optional<ConceptInfoModel.AssignedTeam> chooseSwapPartner(Fortification target,
                                                                  List<ConceptInfoModel.AssignedTeam> teams);
    }

    /** A fortification a team can be moved to, with its number of assigned teams. */
    record Target(Fortification fortification, int filledSlots) {

        boolean isFull() {
            return filledSlots >= fortification.capacity();
        }
    }

    static final String KEY_SLOT = "fortificationAssignment.slot";
    static final String KEY_FREE = "fortificationAssignment.free";
    static final String KEY_ASSIGN = "fortificationAssignment.assign";
    static final String KEY_MOVE = "fortificationAssignment.move";
    static final String KEY_REMOVE = "fortificationAssignment.remove";
    private static final String KEY_READ_ONLY = "common.originalReadOnly";
    private static final String KEY_COW_SCORE = "teamsOverview.valueMode.cowScore";

    private static final String ICON_ASSIGN = "/images/app/add.png";
    private static final String ICON_MOVE = "/images/app/move.png";
    private static final String ICON_REMOVE = "/images/app/delete.png";
    private static final int BUTTON_ICON_SIZE = 18;
    static final int AVATAR_SIZE = 24;

    /** Widths of the columns of a place row, so the rows line up. */
    static final int SLOT_WIDTH = 64;
    static final int NAME_WIDTH = 140;
    /** Room for five heroes plus pet and war flag. */
    static final int AVATARS_WIDTH = 7 * (AVATAR_SIZE + 2) + 4;
    static final int POWER_WIDTH = 80;
    static final int COW_SCORE_WIDTH = 100;

    private final AppContext appContext;
    private final Fortification fortification;
    private final LineupService lineupService;
    private final Chooser chooser;
    private final boolean readOnly;

    private final JLabel factsLabel = new JLabel();
    private final JPanel rowsPanel = new JPanel();
    private final AppContext.Listener listener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            refresh();
        }

        @Override
        public void lineupChanged() {
            refresh();
        }
    };

    /** The rows currently shown, one per place - for tests. */
    private final List<Row> rows = new ArrayList<>();

    /** A place row: its assigned team (null for a free place) and its buttons (none while read-only). */
    record Row(ConceptInfoModel.AssignedTeam team, List<JButton> buttons) {
    }

    FortificationAssignmentPanel(AppContext appContext, Fortification fortification, Chooser chooser) {
        super(new BorderLayout(0, 10));
        this.appContext = appContext;
        this.fortification = fortification;
        this.lineupService = new LineupService(appContext);
        this.chooser = chooser;
        this.readOnly = lineupService.isOriginalOpen();
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(LanguageService.displayName(fortification.id()));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, nameLabel.getFont().getSize2D() + 2f));
        nameLabel.setForeground(FortificationTypeStyle.color(fortification.type()));
        header.add(nameLabel);
        header.add(Box.createVerticalStrut(2));
        factsLabel.setForeground(mutedColor());
        header.add(factsLabel);
        if (readOnly) {
            header.add(Box.createVerticalStrut(8));
            header.add(new JLabel(LanguageService.displayName(KEY_READ_ONLY)));
        }
        for (Component component : header.getComponents()) {
            ((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
        }
        add(header, BorderLayout.NORTH);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        add(rowsPanel, BorderLayout.CENTER);

        refresh();
        appContext.addListener(listener);
    }

    /** Stops following the lineup - call when the dialog closes. */
    void detach() {
        appContext.removeListener(listener);
    }

    boolean isReadOnly() {
        return readOnly;
    }

    /** The place rows currently shown - for tests. */
    List<Row> rows() {
        return List.copyOf(rows);
    }

    /** The text of the facts line - for tests. */
    String factsText() {
        return factsLabel.getText();
    }

    /** Rebuilds the facts line and the place rows from the open lineup. */
    private void refresh() {
        Lineup lineup = appContext.lineup();
        Guild guild = appContext.guild();
        ConceptInfoModel.FortificationFacts facts = ConceptInfoModel.factsOf(fortification, lineup, guild);
        factsLabel.setText(factsText(facts));

        rows.clear();
        rowsPanel.removeAll();
        List<ConceptInfoModel.AssignedTeam> teams = ConceptInfoModel.teamsOf(fortification, lineup, guild);
        // An old lineup may hold more teams than places - none of them is hidden.
        int rowCount = Math.max(fortification.capacity(), teams.size());
        for (int i = 0; i < rowCount; i++) {
            ConceptInfoModel.AssignedTeam team = i < teams.size() ? teams.get(i) : null;
            rowsPanel.add(team != null ? teamRow(i + 1, team, guild) : freeRow(i + 1));
            rowsPanel.add(Box.createVerticalStrut(4));
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    /** "filled/capacity · power · buff %", like the "selected fortification" section of the concept stage view. */
    static String factsText(ConceptInfoModel.FortificationFacts facts) {
        return facts.filledSlots() + "/" + facts.capacity() + "  ·  " + FortificationPanel.compactPower(facts.totalPower())
                + (facts.buffPercent() == null ? "" : "  ·  " + facts.buffPercent() + " %");
    }

    private JPanel teamRow(int slot, ConceptInfoModel.AssignedTeam team, Guild guild) {
        List<JButton> buttons = new ArrayList<>();
        if (!readOnly) {
            buttons.add(iconButton(ICON_MOVE, KEY_MOVE, () -> moveTeam(team)));
            buttons.add(iconButton(ICON_REMOVE, KEY_REMOVE, () -> removeTeam(team)));
        }
        rows.add(new Row(team, buttons));
        JPanel row = row();
        row.add(cell(new JLabel(LanguageService.displayName(KEY_SLOT, slot)), SLOT_WIDTH));
        row.add(cell(new JLabel(team.memberName()), NAME_WIDTH));
        row.add(cell(TeamAvatars.of(team.entry(), guild, AVATAR_SIZE), AVATARS_WIDTH));
        row.add(cell(rightAligned(FortificationPanel.compactPower(team.power())), POWER_WIDTH));
        row.add(cell(rightAligned(cowScoreText(team.cowScore())), COW_SCORE_WIDTH));
        row.add(Box.createHorizontalStrut(12));
        row.add(Box.createHorizontalGlue());
        buttons.forEach(row::add);
        return row;
    }

    private JPanel freeRow(int slot) {
        List<JButton> buttons = new ArrayList<>();
        if (!readOnly) {
            JButton assign = new JButton(LanguageService.displayName(KEY_ASSIGN),
                    IconLoader.iconFor(ICON_ASSIGN, BUTTON_ICON_SIZE, IconLoader.BLUE));
            assign.addActionListener(e -> assignTeam());
            buttons.add(assign);
        }
        rows.add(new Row(null, buttons));
        JPanel row = row();
        row.add(cell(new JLabel(LanguageService.displayName(KEY_SLOT, slot)), SLOT_WIDTH));
        JLabel free = new JLabel(LanguageService.displayName(KEY_FREE));
        free.setForeground(mutedColor());
        row.add(free);
        row.add(Box.createHorizontalGlue());
        buttons.forEach(row::add);
        return row;
    }

    // --- actions ---

    /** "+ Assign team": assigns the chosen candidate - moving it here if it is in another fortification. */
    void assignTeam() {
        List<FortificationAssignment.Candidate> candidates =
                FortificationAssignment.candidatesFor(fortification, appContext.lineup(), appContext.guild());
        chooser.chooseCandidate(fortification, candidates).ifPresent(candidate ->
                lineupService.assignTeam(candidate.memberId(), candidate.teamType(), candidate.teamIndex(), fortification));
    }

    /** "Remove": the team loses its assignment - no question asked. */
    void removeTeam(ConceptInfoModel.AssignedTeam team) {
        Lineup.Entry entry = team.entry();
        lineupService.assignTeam(entry.teamMemberId(), entry.teamType(), entry.teamIndex(), null);
    }

    /**
     * "Move/swap": moves the team to the chosen fortification of the same type, or - if that
     * one is full - swaps it with a chosen team of it. Nothing happens if a choice is cancelled.
     */
    void moveTeam(ConceptInfoModel.AssignedTeam team) {
        Optional<Fortification> chosen = chooser.chooseTarget(targets());
        if (chosen.isEmpty()) {
            return;
        }
        Fortification target = chosen.get();
        Lineup.Entry entry = team.entry();
        LineupService.AssignResult result = lineupService.assignTeam(entry.teamMemberId(), entry.teamType(),
                entry.teamIndex(), target);
        if (result.assigned()) {
            return;
        }
        List<ConceptInfoModel.AssignedTeam> targetTeams = ConceptInfoModel.teamsOf(target, appContext.lineup(), appContext.guild());
        chooser.chooseSwapPartner(target, targetTeams)
                .ifPresent(partner -> lineupService.swapTeams(entry, partner.entry()));
    }

    /** The other fortifications of this one's type, sorted by display name, with their fill level. */
    List<Target> targets() {
        Lineup lineup = appContext.lineup();
        List<Target> targets = new ArrayList<>();
        for (Fortification other : FortificationRepository.findAll()) {
            if (other.type() == fortification.type() && !other.id().equals(fortification.id())) {
                int filled = (int) lineup.entries().stream()
                        .filter(entry -> entry.fortificationId().equals(other.id())).count();
                targets.add(new Target(other, filled));
            }
        }
        targets.sort(Comparator.comparing(target -> LanguageService.displayName(target.fortification().id()),
                String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    // --- building blocks, shared with the choice lists of FortificationAssignmentDialog ---

    /** "CowScore 12.3" - one decimal like the team overviews. */
    static String cowScoreText(double cowScore) {
        return LanguageService.displayName(KEY_COW_SCORE) + " " + String.format(Locale.ROOT, "%.1f", cowScore);
    }

    /** A horizontal row of cells. */
    static JPanel row() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    /** {@code component} with a fixed width, so the cells of all rows line up. */
    static JComponent cell(JComponent component, int width) {
        Dimension size = new Dimension(width, Math.max(component.getPreferredSize().height, AVATAR_SIZE + 2));
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        component.setMaximumSize(size);
        return component;
    }

    static JLabel rightAligned(String text) {
        return new JLabel(text, SwingConstants.RIGHT);
    }

    private static JButton iconButton(String iconPath, String tooltipKey, Runnable action) {
        FlatButton button = new FlatButton(IconLoader.iconFor(iconPath, BUTTON_ICON_SIZE, IconLoader.BLUE));
        button.setToolTipText(LanguageService.displayName(tooltipKey));
        button.addActionListener(e -> action.run());
        return button;
    }

    static Color mutedColor() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }
}
