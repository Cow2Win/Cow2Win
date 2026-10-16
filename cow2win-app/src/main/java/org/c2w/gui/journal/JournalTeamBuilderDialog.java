package org.c2w.gui.journal;

import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.GuildMember;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.Glyphs;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.JournalTeamBuilderService;
import org.c2w.service.journal.TeamBuildPlan.*;
import org.c2w.service.journal.TeamBuildResult;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Build teams from logs" (modal): one sentence says what happens, the list shows
 * only what changes - per guild member with a selectable proposal a collapsible
 * header (checkbox, name, a summary of the selection as chips, problems) and per
 * proposal a row (checkbox, kind, power, heroes/titans with images, pet/totems,
 * target - a combo only if there is a choice); proposals of older battles under
 * their own collapsed section. Problems are shown where they arise. Reads in the
 * background; the logic is in {@link TeamBuilderModel} and the
 * {@link JournalTeamBuilderService}. "Apply and save" applies the selection and
 * saves the guild at once; afterwards a summary.
 */
public final class JournalTeamBuilderDialog extends JDialog {

    private static final int ICON_SIZE = 22;
    private static final int UNITS_WIDTH = 300;

    private final AppContext context;
    private final GuildService guildService;
    private final JournalTeamBuilderService service;
    private final Runnable onApplied;
    private final Runnable openPlayers;
    private final boolean guildDirtyAtOpen;
    private final JLabel header = new JLabel(" ");
    private final JPanel subline = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel dirtyHint = new JLabel(JournalTexts.text("journal.teams.dirtyHint"));
    private final JCheckBox selectAll = new JCheckBox();
    private final JPanel center = new JPanel(new BorderLayout());
    private final JPanel unassigned = new JPanel(new BorderLayout());
    private final JLabel problems = new JLabel(" ");
    private final JButton applyButton = new JButton();
    private final JButton cancelButton = new JButton(JournalTexts.text("journal.button.cancel"));
    private final JPanel extraButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final List<MemberView> memberViews = new ArrayList<>();
    private final Map<String, Boolean> expanded = new HashMap<>();
    private final Map<String, Boolean> olderExpanded = new HashMap<>();
    private TeamBuilderModel model;
    private boolean updating;
    private int badgeWidth;
    private int powerWidth;
    private int dateWidth;

    private JournalTeamBuilderDialog(Window owner, AppContext context, GuildService guildService, Runnable onApplied,
                                     Runnable openPlayers) {
        super(owner, JournalTexts.text("journal.teams.title"), ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.guildService = guildService;
        this.service = new JournalTeamBuilderService(context, guildService);
        this.onApplied = onApplied;
        this.openPlayers = openPlayers;
        this.guildDirtyAtOpen = context.isGuildDirty();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel top = JournalImportDialog.verticalPanel();
        Font font = header.getFont();
        header.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.3f));
        header.setAlignmentX(LEFT_ALIGNMENT);
        top.add(header);
        top.add(Box.createVerticalStrut(4));
        subline.setAlignmentX(LEFT_ALIGNMENT);
        subline.add(new JLabel(JournalTexts.text("journal.teams.subline") + "  "));
        subline.add(link(JournalTexts.text("journal.teams.rulesLink"), this::showRules));
        top.add(subline);
        dirtyHint.setForeground(AppColors.WARNING_TEXT);
        dirtyHint.setAlignmentX(LEFT_ALIGNMENT);
        dirtyHint.setVisible(guildDirtyAtOpen);
        top.add(dirtyHint);
        top.add(Box.createVerticalStrut(6));
        selectAll.setAlignmentX(LEFT_ALIGNMENT);
        selectAll.setVisible(false);
        selectAll.addActionListener(e -> {
            model.selectAll(!model.allSelected());
            updateState();
        });
        top.add(selectAll);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancelButton);
        buttons.add(applyButton);
        JPanel south = JournalImportDialog.verticalPanel();
        unassigned.setAlignmentX(LEFT_ALIGNMENT);
        unassigned.setVisible(false);
        south.add(unassigned);
        south.add(Box.createVerticalStrut(4));
        problems.setForeground(AppColors.ERROR_TEXT);
        problems.setAlignmentX(LEFT_ALIGNMENT);
        south.add(problems);
        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setAlignmentX(LEFT_ALIGNMENT);
        buttonRow.add(extraButtons, BorderLayout.WEST);
        buttonRow.add(buttons, BorderLayout.EAST);
        south.add(buttonRow);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(applyButton);

        applyButton.addActionListener(e -> apply());
        cancelButton.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Logger.log("Team builder closed");
            }
        });
        setSize(1000, 720);
        setLocationRelativeTo(owner);
    }

    /**
     * Opens the dialog and reads the journal in the background.
     *
     * @param onApplied   after teams were applied
     * @param openPlayers opens the players window - "assign players" closes this dialog first and runs it
     */
    public static void open(Window owner, AppContext context, GuildService guildService, Runnable onApplied,
                            Runnable openPlayers) {
        JournalTeamBuilderDialog dialog = new JournalTeamBuilderDialog(owner, context, guildService, onApplied,
                openPlayers);
        dialog.load();
        dialog.setVisible(true);
    }

    private void load() {
        header.setText(JournalTexts.text("journal.import.reading"));
        applyButton.setText(JournalTexts.text("journal.teams.apply", "0"));
        applyButton.setEnabled(false);
        JPanel busy = new JPanel(new GridBagLayout());
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        busy.add(progress);
        center.add(busy, BorderLayout.CENTER);
        JournalSwing.background(this, service::prepare, plan -> {
            model = new TeamBuilderModel(plan, context.guild(), context.catalog());
            showMembers();
        });
    }

    private void showRules() {
        String text = JournalImportDialog.escape(JournalTexts.text("journal.teams.rules")).replace("\n", "<br><br>");
        JOptionPane.showMessageDialog(this, "<html><div style='width:450px'>" + text + "</div></html>", getTitle(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    // --- members ---

    private void showMembers() {
        header.setText(JournalTexts.text("journal.teams.header", String.valueOf(model.sourceCount())));
        center.removeAll();
        memberViews.clear();
        String empty = model.plan().defenseLogs() == 0 ? "journal.teams.noDefenseLogs"
                : model.noMemberAssigned() ? "journal.teams.noMembers"
                : model.members().isEmpty() ? "journal.teams.noProposals" : null;
        if (empty != null) {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.add(JournalImportDialog.wrapLabel(JournalTexts.text(empty)));
            center.add(panel, BorderLayout.CENTER);
        } else {
            measureColumns();
            JPanel list = JournalImportDialog.verticalPanel();
            for (MemberPlan member : model.members()) {
                MemberView view = new MemberView(member);
                memberViews.add(view);
                list.add(view.section);
            }
            int nothingToDo = model.membersWithoutNewTeams();
            if (nothingToDo > 0) {
                JLabel label = new JLabel(JournalTexts.text("journal.teams.nothingToDo", String.valueOf(nothingToDo)),
                        new SymbolIcon(Symbol.CHECK, AppColors.TEXT_MUTED), SwingConstants.LEFT);
                label.setForeground(AppColors.TEXT_MUTED);
                label.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
                label.setAlignmentX(LEFT_ALIGNMENT);
                list.add(label);
            }
            list.add(Box.createVerticalGlue());
            JPanel holder = new JPanel(new BorderLayout());
            holder.add(list, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(holder);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            center.add(scroll, BorderLayout.CENTER);
            selectAll.setVisible(true);
        }
        showUnassigned();
        updateState();
        center.revalidate();
        center.repaint();
    }

    /** The hint about defenders not assigned to a member, with "assign players …". */
    private void showUnassigned() {
        List<String> names = model.unassignedPlayers();
        unassigned.removeAll();
        unassigned.setVisible(!names.isEmpty());
        if (names.isEmpty()) {
            return;
        }
        HighlightPanel bar = new HighlightPanel(new BorderLayout(8, 0));
        bar.setHighlight(AppColors.tint(AppColors.WARNING, AppColors.TINT), AppColors.WARNING);
        JLabel text = new JLabel(JournalTexts.text("journal.teams.unassigned", String.valueOf(names.size())),
                new SymbolIcon(Symbol.WARNING, AppColors.WARNING), SwingConstants.LEFT);
        bar.setToolTipText(names.stream().map(JournalTexts::visibleSpaces).collect(Collectors.joining(", ")));
        text.setToolTipText(bar.getToolTipText());
        bar.add(text, BorderLayout.CENTER);
        bar.add(link(JournalTexts.text("journal.teams.assignPlayers"), () -> {
            dispose();
            openPlayers.run();
        }), BorderLayout.EAST);
        unassigned.add(bar, BorderLayout.CENTER);
    }

    /** Fixed column widths, so the rows of all members are aligned. */
    private void measureColumns() {
        FontMetrics metrics = getFontMetrics(new JLabel().getFont());
        badgeWidth = 0;
        for (TeamKind kind : TeamKind.values()) {
            badgeWidth = Math.max(badgeWidth, metrics.stringWidth(badgeText(kind)) + 16);
        }
        powerWidth = metrics.stringWidth(JournalTexts.number(99_999_999)) + 12;
        dateWidth = metrics.stringWidth(JournalTexts.date(LocalDate.of(2026, 12, 31))) + 12;
    }

    /** One member: header and (collapsible) proposal rows. */
    private final class MemberView {
        final MemberPlan member;
        final JPanel section = new JPanel(new BorderLayout());
        final HighlightPanel head = new HighlightPanel(new BorderLayout(8, 0));
        final JLabel expander = new JLabel();
        final JCheckBox check = new JCheckBox();
        final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        final JLabel state = new JLabel();
        final JPanel content = JournalImportDialog.verticalPanel();
        final List<RowView> rows = new ArrayList<>();
        JLabel olderToggle;
        JPanel olderRows;

        MemberView(MemberPlan member) {
            this.member = member;
            expanded.computeIfAbsent(member.memberId(), id -> model.expandedAtStart(member));
            section.setAlignmentX(LEFT_ALIGNMENT);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            left.setOpaque(false);
            left.add(expander);
            check.setOpaque(false);
            check.addActionListener(e -> {
                model.selectAll(member.memberId(), !model.allSelected(member.memberId()));
                updateState();
            });
            left.add(check);
            JLabel name = JournalImportDialog.boldLabel(member.memberName());
            String names = member.logNames().stream().map(JournalTexts::visibleSpaces).collect(Collectors.joining(", "));
            name.setToolTipText(JournalTexts.text("journal.teams.memberTooltip", names,
                    JournalTexts.date(member.sourceDate())));
            left.add(name);
            chips.setOpaque(false);
            left.add(chips);
            head.add(left, BorderLayout.CENTER);
            state.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
            head.add(state, BorderLayout.EAST);
            MouseAdapter toggle = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    expanded.put(member.memberId(), !expanded.get(member.memberId()));
                    updateExpanded();
                }
            };
            for (Component c : List.of(head, left, expander, name, chips, state)) {
                c.addMouseListener(toggle);
            }
            head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            section.add(head, BorderLayout.NORTH);

            content.setBorder(BorderFactory.createEmptyBorder(0, 28, 6, 0));
            for (Proposal p : model.sourceProposals(member)) {
                RowView row = new RowView(p, false);
                rows.add(row);
                content.add(row.panel);
            }
            List<Proposal> older = model.olderProposals(member);
            if (!older.isEmpty()) {
                olderExpanded.putIfAbsent(member.memberId(), false);
                olderRows = JournalImportDialog.verticalPanel();
                for (Proposal p : older) {
                    RowView row = new RowView(p, true);
                    rows.add(row);
                    olderRows.add(row.panel);
                }
                olderToggle = new JLabel();
                olderToggle.setForeground(AppColors.TEXT_MUTED);
                olderToggle.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 0));
                olderToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                olderToggle.setAlignmentX(LEFT_ALIGNMENT);
                olderToggle.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        olderExpanded.put(member.memberId(), !olderExpanded.get(member.memberId()));
                        updateExpanded();
                    }
                });
                content.add(olderToggle);
                content.add(olderRows);
            }
            section.add(content, BorderLayout.CENTER);
            updateExpanded();
        }

        void updateExpanded() {
            boolean open = expanded.get(member.memberId());
            expander.setText(Glyphs.expander(open).strip());
            content.setVisible(open);
            if (olderToggle != null) {
                boolean olderOpen = olderExpanded.get(member.memberId());
                olderToggle.setText(Glyphs.expander(olderOpen) + JournalTexts.text("journal.teams.older",
                        String.valueOf(olderRows.getComponentCount())));
                olderRows.setVisible(olderOpen);
            }
            section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
            section.revalidate();
            section.repaint();
        }

        void update() {
            check.setSelected(model.allSelected(member.memberId()));
            showChips();
            List<TeamBuildResult.Error> errors = model.problemsOf(member.memberId());
            if (errors.isEmpty()) {
                int present = model.presentCount(member);
                state.setIcon(null);
                state.setText(present > 0 ? JournalTexts.text("journal.teams.presentCount", String.valueOf(present)) : "");
                state.setForeground(AppColors.TEXT_MUTED);
                head.setHighlight(null, null);
            } else {
                state.setIcon(new SymbolIcon(Symbol.ERROR, AppColors.ERROR));
                state.setText(errors.stream().map(e -> JournalTexts.of("teams.errorShort", e.kind())).distinct()
                        .collect(Collectors.joining("  ")));
                state.setForeground(AppColors.ERROR_TEXT);
                head.setHighlight(AppColors.tint(AppColors.ERROR, AppColors.TINT), null);
                boolean olderError = rows.stream().anyMatch(r -> r.older && !model.problemsOfProposal(r.proposal.id()).isEmpty());
                if (!expanded.get(member.memberId()) || olderError && !olderExpanded.get(member.memberId())) {
                    expanded.put(member.memberId(), true);
                    if (olderError) {
                        olderExpanded.put(member.memberId(), true);
                    }
                }
            }
            rows.forEach(RowView::update);
            updateExpanded();
        }

        private void showChips() {
            chips.removeAll();
            TeamBuilderModel.Summary summary = model.summary(member.memberId());
            if (summary.isEmpty()) {
                JLabel none = new JLabel(JournalTexts.text("journal.teams.chip.none"));
                none.setForeground(AppColors.TEXT_MUTED);
                chips.add(none);
            }
            if (summary.newHeroTeams() > 0) {
                chips.add(chip(countText(summary.newHeroTeams(), "journal.teams.chip.newHero.one",
                        "journal.teams.chip.newHero.other"), null));
            }
            if (summary.newTitanTeams() > 0) {
                chips.add(chip(countText(summary.newTitanTeams(), "journal.teams.chip.newTitan.one",
                        "journal.teams.chip.newTitan.other"), null));
            }
            for (TeamBuilderModel.Fill fill : summary.fills()) {
                chips.add(chip(JournalTexts.text("journal.teams.chip.fill",
                        JournalTexts.team(fill.kind(), fill.teamIndex())), null));
            }
            chips.revalidate();
            chips.repaint();
        }
    }

    /** One proposal: checkbox, kind, power, (older: battle day), units, target. */
    private final class RowView {
        final Proposal proposal;
        final boolean older;
        final HighlightPanel panel = new HighlightPanel(new GridBagLayout());
        final JCheckBox check = new JCheckBox();
        final JComboBox<Target> target;
        final String where;

        RowView(Proposal p, boolean older) {
            this.proposal = p;
            this.older = older;
            this.where = JournalTexts.text("journal.teams.where",
                    JournalTexts.fortification(p.fortificationId(), p.fortificationName()), String.valueOf(p.position()),
                    JournalTexts.date(p.battleDate()));
            panel.setAlignmentX(LEFT_ALIGNMENT);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(1, 2, 1, 6);

            check.setOpaque(false);
            panel.add(check, c);
            Color typeColor = p.kind() == TeamKind.HERO ? AppColors.HERO : AppColors.TITAN;
            JLabel badge = chip(badgeText(p.kind()), AppColors.tint(typeColor, AppColors.TINT));
            badge.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(fixed(badge, badgeWidth), c);
            JLabel power = new JLabel(JournalTexts.number(p.power()), SwingConstants.RIGHT);
            panel.add(fixed(power, powerWidth), c);
            JLabel date = new JLabel(older ? JournalTexts.date(p.battleDate()) : "");
            date.setForeground(AppColors.TEXT_MUTED);
            panel.add(fixed(date, dateWidth), c);
            panel.add(fixed(units(p.composition()), UNITS_WIDTH), c);

            List<Target> choices = model.targetChoices(p);
            c.weightx = 1;
            if (choices.size() > 1) {
                JPanel box = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                box.setOpaque(false);
                box.add(new JLabel(Glyphs.displayable("→")));
                target = new JComboBox<>(choices.toArray(new Target[0]));
                target.setRenderer(PlayersStepPanel.textRenderer(t -> targetText(p.kind(), (Target) t)));
                target.addActionListener(e -> {
                    if (!updating && target.getSelectedItem() instanceof Target t && check.isSelected()) {
                        model.setTarget(p.id(), t);
                        updateState();
                    }
                });
                box.add(target);
                panel.add(box, c);
            } else {
                target = null;
                Target only = choices.isEmpty() ? model.target(p.id()) : choices.get(0);
                panel.add(new JLabel(Glyphs.displayable("→ ") + targetText(p.kind(), only)), c);
            }
            check.addActionListener(e -> {
                model.setSelected(p.id(), check.isSelected());
                updateState();
            });
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        }

        void update() {
            boolean selected = model.isSelected(proposal.id());
            check.setSelected(selected);
            if (target != null) {
                target.setEnabled(selected);
                target.setSelectedItem(model.target(proposal.id()));
            }
            List<TeamBuildResult.Error> errors = model.problemsOfProposal(proposal.id());
            if (errors.isEmpty()) {
                panel.setHighlight(null, null);
                setTooltip(where);
            } else {
                panel.setHighlight(AppColors.tint(AppColors.ERROR, AppColors.TINT), AppColors.ERROR);
                setTooltip(errors.stream().map(e -> JournalTexts.of("teams.errorShort", e.kind())).distinct()
                        .collect(Collectors.joining("  ")));
            }
        }

        private void setTooltip(String text) {
            panel.setToolTipText(text);
            for (Component c : panel.getComponents()) {
                if (c instanceof JComponent j && !(c instanceof JComboBox<?>) && !(c instanceof JPanel)) {
                    j.setToolTipText(text);
                }
            }
        }
    }

    private JPanel units(Composition c) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        panel.setOpaque(false);
        NameMappingKind kind = c.kind() == TeamKind.HERO ? NameMappingKind.HERO : NameMappingKind.TITAN;
        for (String id : c.unitIds()) {
            panel.add(unitLabel(kind, id));
        }
        if (c.petId() != null) {
            panel.add(new JLabel("+"));
            panel.add(unitLabel(NameMappingKind.PET, c.petId()));
        }
        if (!c.totems().isEmpty()) {
            panel.add(new JLabel("+ " + TotemTexts.list(c.totems())));
        }
        return panel;
    }

    private JLabel unitLabel(NameMappingKind kind, String id) {
        Icon icon = JournalCatalog.icon(context.catalog(), kind, id);
        JLabel label = new JLabel(icon == null ? LanguageService.displayName(id) : null, scaled(icon), SwingConstants.LEFT);
        label.setToolTipText(LanguageService.displayName(id));
        return label;
    }

    private static Icon scaled(Icon icon) {
        if (icon instanceof ImageIcon image && image.getIconWidth() != ICON_SIZE) {
            return new ImageIcon(image.getImage().getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH));
        }
        return icon;
    }

    static String targetText(TeamKind kind, Target target) {
        return target.kind() == Target.Kind.NEW ? JournalTexts.text("journal.teams.target.NEW")
                : JournalTexts.text("journal.teams.target.FILL", JournalTexts.team(kind, target.teamIndex()));
    }

    private static String badgeText(TeamKind kind) {
        return JournalTexts.of("teams.badge", kind);
    }

    /** "+1 hero team" / "+2 hero teams". */
    private static String countText(int count, String one, String other) {
        return JournalTexts.text(count == 1 ? one : other, String.valueOf(count));
    }

    // --- state ---

    /** Brings checkboxes, targets, chips, problems and the buttons in line with the model. */
    private void updateState() {
        if (model == null) {
            return;
        }
        updating = true;
        try {
            selectAll.setText(JournalTexts.text("journal.teams.selected", String.valueOf(model.selectedCount()),
                    String.valueOf(model.selectableCount())));
            selectAll.setSelected(model.allSelected());
            memberViews.forEach(MemberView::update);
            showGeneralProblems(model.generalProblems());
            applyButton.setText(JournalTexts.text("journal.teams.apply", String.valueOf(model.selectedCount())));
            applyButton.setEnabled(model.canApply());
        } finally {
            updating = false;
        }
    }

    private void showGeneralProblems(List<TeamBuildResult.Error> errors) {
        problems.setText(errors.isEmpty() ? " " : errors.stream().map(this::errorText).distinct()
                .collect(Collectors.joining("  ")));
    }

    private String errorText(TeamBuildResult.Error error) {
        String member = error.memberId() == null ? "" : model.guildMember(error.memberId())
                .map(GuildMember::name).orElse(error.memberId());
        return JournalTexts.text("journal.teams.error." + error.kind().name(), member);
    }

    // --- apply and result ---

    private void apply() {
        if (model == null || !model.canApply()) {
            return;
        }
        TeamBuildResult result = service.apply(model.plan(), model.selection());
        if (!result.isSuccess()) {
            showGeneralProblems(result.errors());
            return;
        }
        boolean saved;
        try {
            guildService.saveGuild(GuildService.SaveOrigin.JOURNAL_TEAM_BUILDER);
            saved = true;
        } catch (Exception ex) {
            Logger.log("Team builder: saving the guild failed: " + ex.getMessage());
            showSaveError(ex);
            saved = false;
        }
        onApplied.run();
        showResult(result, saved);
    }

    private void showSaveError(Exception ex) {
        JOptionPane.showMessageDialog(this, LanguageService.displayName("common.saveGuildError") + "\n"
                + ex.getMessage(), LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
    }

    private void showResult(TeamBuildResult result, boolean saved) {
        header.setText(JournalTexts.text(saved ? "journal.teams.done" : "journal.teams.doneUnsaved"));
        subline.setVisible(false);
        dirtyHint.setVisible(false);
        selectAll.setVisible(false);
        unassigned.setVisible(false);
        center.removeAll();
        JPanel panel = JournalImportDialog.verticalPanel();
        panel.add(new JLabel(JournalTexts.text("journal.teams.result", String.valueOf(result.created()),
                String.valueOf(result.filled()))));
        for (TeamBuildResult.DroppedPet pet : result.droppedPets()) {
            panel.add(new JLabel(JournalTexts.text("journal.teams.droppedPet", memberName(pet.memberId()),
                    LanguageService.displayName(pet.petId()))));
        }
        for (TeamBuildResult.DroppedTotem totem : result.droppedTotems()) {
            panel.add(new JLabel(JournalTexts.text("journal.teams.droppedTotem", memberName(totem.memberId()),
                    TotemTexts.name(totem.totem()))));
        }
        panel.add(Box.createVerticalStrut(8));
        panel.add(JournalImportDialog.wrapLabel(JournalTexts.text("journal.teams.handWork")));
        if (!saved && context.isGuildDirty()) {
            panel.add(Box.createVerticalStrut(10));
            JLabel warning = JournalImportDialog.boldLabel(JournalTexts.text("journal.summary.guildChanged"));
            warning.setForeground(AppColors.WARNING_TEXT);
            panel.add(warning);
            JButton save = new JButton(JournalTexts.text("journal.result.saveGuild"));
            save.addActionListener(e -> {
                try {
                    guildService.saveGuild(GuildService.SaveOrigin.JOURNAL_TEAM_BUILDER);
                    save.setEnabled(false);
                    warning.setText(JournalTexts.text("journal.result.guildSaved"));
                    warning.setForeground(UIManager.getColor("Label.foreground"));
                } catch (Exception ex) {
                    showSaveError(ex);
                }
            });
            extraButtons.add(save);
        }
        center.add(new JScrollPane(panel), BorderLayout.CENTER);
        applyButton.setVisible(false);
        getRootPane().setDefaultButton(cancelButton);
        problems.setText(" ");
        cancelButton.setText(JournalTexts.text("journal.button.close"));
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private String memberName(String memberId) {
        return context.guild() == null ? memberId : context.guild().members().stream()
                .filter(m -> m.id().equals(memberId)).map(GuildMember::name).findFirst().orElse(memberId);
    }

    // --- small components ---

    private static <T extends JComponent> T fixed(T component, int width) {
        Dimension size = new Dimension(width, component.getPreferredSize().height);
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        return component;
    }

    /** A button that looks like a link. */
    private static JButton link(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        button.setForeground(AppColors.INFO_TEXT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    /** A small label with a rounded background ({@code null} = {@link AppColors#SURFACE_HOVER}). */
    private static JLabel chip(String text, Color fill) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill != null ? fill : AppColors.SURFACE_HOVER);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() * 0.92f));
        return label;
    }

    /** A panel that can be tinted and framed (for problems and hints), without changing its size. */
    private static final class HighlightPanel extends JPanel {
        private static final Border NONE = BorderFactory.createEmptyBorder(1, 1, 1, 1);
        private Color fill;

        HighlightPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(NONE);
        }

        void setHighlight(Color fill, Color frame) {
            this.fill = fill;
            setBorder(frame == null ? NONE : BorderFactory.createLineBorder(frame));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (fill != null) {
                g.setColor(fill);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(g);
        }
    }

    private enum Symbol {CHECK, WARNING, ERROR}

    /** A small drawn symbol - independent of the glyphs of the UI font. */
    private record SymbolIcon(Symbol symbol, Color color) implements Icon {
        private static final int SIZE = 14;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (symbol) {
                case CHECK -> {
                    Path2D path = new Path2D.Float();
                    path.moveTo(2, 7.5);
                    path.lineTo(5.5, 11);
                    path.lineTo(12, 3);
                    g2.draw(path);
                }
                case WARNING -> {
                    Path2D triangle = new Path2D.Float();
                    triangle.moveTo(7, 1);
                    triangle.lineTo(13, 13);
                    triangle.lineTo(1, 13);
                    triangle.closePath();
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(triangle);
                    g2.drawLine(7, 5, 7, 8);
                    g2.fillOval(6, 10, 2, 2);
                }
                case ERROR -> {
                    g2.fillOval(0, 0, SIZE - 1, SIZE - 1);
                    g2.setColor(Color.WHITE);
                    g2.drawLine(4, 4, SIZE - 5, SIZE - 5);
                    g2.drawLine(SIZE - 5, 4, 4, SIZE - 5);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    // --- tests ---

    /** For tests: the model once loaded. */
    TeamBuilderModel model() {
        return model;
    }

    /** For tests: the apply button. */
    JButton applyButton() {
        return applyButton;
    }

    /** Package-visible factory for the smoke test (not shown). */
    static JournalTeamBuilderDialog create(Window owner, AppContext context, GuildService guildService) {
        JournalTeamBuilderDialog dialog = new JournalTeamBuilderDialog(owner, context, guildService, () -> { },
                () -> { });
        dialog.load();
        return dialog;
    }
}
