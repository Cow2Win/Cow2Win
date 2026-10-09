package org.c2w.gui.journal;

import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.TeamKind;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.TitanTeam;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.JournalTeamBuilderService;
import org.c2w.service.journal.TeamBuildPlan.*;
import org.c2w.service.journal.TeamBuildResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Build teams from logs" (modal): per guild member (collapsible) its current
 * teams and the team proposals from the defense logs - checkbox, kind, power,
 * fortification/position, battle day, where the composition comes from, the
 * heroes/titans with images, pet/totems, the target (new team / fills empty team
 * n) and, for a proposal without composition, "take over a known composition".
 * Reads in the background; the logic is in {@link TeamBuilderModel} and the
 * {@link JournalTeamBuilderService}. Afterwards a summary and "Save guild now".
 */
public final class JournalTeamBuilderDialog extends JDialog {

    private static final int ICON_SIZE = 22;

    private final AppContext context;
    private final GuildService guildService;
    private final JournalTeamBuilderService service;
    private final Runnable onApplied;
    private final JLabel header = new JLabel(" ");
    private final JCheckBox onlyWithoutTeams = new JCheckBox(JournalTexts.text("journal.teams.onlyWithoutTeams"));
    private final JPanel center = new JPanel(new BorderLayout());
    private final JLabel problems = new JLabel(" ");
    private final JButton applyButton = new JButton();
    private final JButton clearButton = new JButton(JournalTexts.text("journal.teams.clear"));
    private final JButton cancelButton = new JButton(JournalTexts.text("journal.button.cancel"));
    private final JPanel extraButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private TeamBuilderModel model;
    private JPanel memberList;

    private JournalTeamBuilderDialog(Window owner, AppContext context, GuildService guildService, Runnable onApplied) {
        super(owner, JournalTexts.text("journal.teams.title"), ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.guildService = guildService;
        this.service = new JournalTeamBuilderService(context, guildService);
        this.onApplied = onApplied;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel top = JournalImportDialog.verticalPanel();
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        top.add(header);
        top.add(JournalImportDialog.wrapLabel(JournalTexts.text("journal.teams.hint")));
        onlyWithoutTeams.setAlignmentX(LEFT_ALIGNMENT);
        top.add(onlyWithoutTeams);
        onlyWithoutTeams.addActionListener(e -> {
            model.setOnlyMembersWithoutTeams(onlyWithoutTeams.isSelected());
            showMembers();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(applyButton);
        buttons.add(clearButton);
        buttons.add(cancelButton);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        problems.setForeground(Color.RED);
        south.add(problems, BorderLayout.NORTH);
        south.add(extraButtons, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        applyButton.addActionListener(e -> apply());
        clearButton.addActionListener(e -> {
            model.clearSelection();
            showMembers();
        });
        cancelButton.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Logger.log("Team builder closed");
            }
        });
        setSize(1150, 720);
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog and reads the journal in the background. */
    public static void open(Window owner, AppContext context, GuildService guildService, Runnable onApplied) {
        JournalTeamBuilderDialog dialog = new JournalTeamBuilderDialog(owner, context, guildService, onApplied);
        dialog.load();
        dialog.setVisible(true);
    }

    private void load() {
        header.setText(JournalTexts.text("journal.import.reading"));
        setButtonsEnabled(false);
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

    private void setButtonsEnabled(boolean enabled) {
        applyButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        onlyWithoutTeams.setEnabled(enabled);
    }

    // --- members ---

    private void showMembers() {
        header.setText(JournalTexts.text("journal.teams.header", String.valueOf(model.memberCount()),
                String.valueOf(model.proposalCount()), String.valueOf(model.proposalsWithComposition()),
                String.valueOf(model.plan().defenseLogs())));
        center.removeAll();
        if (model.plan().members().isEmpty()) {
            center.add(JournalImportDialog.wrapLabel(JournalTexts.text(model.plan().defenseLogs() == 0
                    ? "journal.teams.noDefenseLogs" : "journal.teams.noMembers")), BorderLayout.NORTH);
        } else {
            memberList = JournalImportDialog.verticalPanel();
            for (MemberPlan member : model.members()) {
                memberList.add(memberSection(member));
            }
            memberList.add(Box.createVerticalGlue());
            JScrollPane scroll = new JScrollPane(memberList);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            center.add(scroll, BorderLayout.CENTER);
        }
        center.add(skippedSection(), BorderLayout.SOUTH);
        setButtonsEnabled(true);
        updateButtons();
        center.revalidate();
        center.repaint();
    }

    private JComponent memberSection(MemberPlan member) {
        JPanel section = new JPanel(new BorderLayout());
        section.setAlignmentX(LEFT_ALIGNMENT);
        JPanel content = JournalImportDialog.verticalPanel();
        content.setBorder(BorderFactory.createEmptyBorder(0, 24, 6, 0));
        String names = member.logNames().stream().map(JournalTexts::visibleSpaces).collect(Collectors.joining(", "));
        String title = JournalTexts.text("journal.teams.member", member.memberName(), names,
                String.valueOf(member.proposals().size()), JournalTexts.date(member.sourceDate()));
        JToggleButton toggle = new JToggleButton("▾ " + title, true);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.addActionListener(e -> {
            content.setVisible(toggle.isSelected());
            toggle.setText((toggle.isSelected() ? "▾ " : "▸ ") + title);
            section.revalidate();
        });
        section.add(toggle, BorderLayout.NORTH);

        content.add(existingTeams(member));
        for (Proposal p : member.proposals()) {
            content.add(proposalRow(p));
        }
        section.add(content, BorderLayout.CENTER);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
        return section;
    }

    private JComponent existingTeams(MemberPlan member) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(new JLabel(JournalTexts.text("journal.teams.existing")));
        GuildMember m = model.guildMember(member.memberId()).orElse(null);
        if (m == null || m.heroTeams().isEmpty() && m.titanTeams().isEmpty()) {
            panel.add(new JLabel(JournalTexts.text("journal.teams.none")));
            return panel;
        }
        for (HeroTeam t : m.heroTeams()) {
            panel.add(new JLabel(existingText(TeamKind.HERO, t.index(), t.totalPower(),
                    t.heroes() == null || t.heroes().isEmpty())));
        }
        for (TitanTeam t : m.titanTeams()) {
            panel.add(new JLabel(existingText(TeamKind.TITAN, t.index(), t.totalPower(), t.titans().isEmpty())));
        }
        return panel;
    }

    private static String existingText(TeamKind kind, int index, int power, boolean empty) {
        return "[" + JournalTexts.team(kind, index) + ": " + JournalTexts.number(power)
                + (empty ? " – " + JournalTexts.text("journal.teams.empty") : "") + "]";
    }

    private JComponent proposalRow(Proposal p) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JCheckBox check = new JCheckBox();
        check.setSelected(model.isSelected(p.id()));
        check.setEnabled(p.selectable());
        row.add(check);
        row.add(JournalImportDialog.boldLabel(JournalTexts.of("teamKind", p.kind())));
        row.add(new JLabel(JournalTexts.number(p.power())));
        row.add(new JLabel(JournalTexts.text("journal.teams.where",
                JournalTexts.fortification(p.fortificationId(), p.fortificationName()), String.valueOf(p.position()),
                JournalTexts.date(p.battleDate()))));
        JLabel source = new JLabel(sourceText(p.source()));
        source.setForeground(sourceColor(p.source()));
        row.add(source);
        JPanel units = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.add(units);

        if (!p.selectable()) {
            row.add(new JLabel(p.status() == ProposalStatus.ALREADY_PRESENT
                    ? JournalTexts.text("journal.teams.present", JournalTexts.team(p.kind(), p.existingTeamIndex()))
                    : JournalTexts.text("journal.teams.overLimit")));
            showUnits(units, p.composition());
            return row;
        }

        JComboBox<Target> target = new JComboBox<>(model.targetChoices(p).toArray(new Target[0]));
        target.setRenderer(PlayersStepPanel.textRenderer(t -> targetText(p.kind(), (Target) t)));
        target.setSelectedItem(model.target(p.id()));
        target.setEnabled(check.isSelected());
        row.add(target);

        JComboBox<Object> known = null;
        if (p.composition() == null && !p.knownCompositions().isEmpty()) {
            List<Object> items = new ArrayList<>();
            items.add(JournalTexts.text("journal.teams.knownNone"));
            items.addAll(p.knownCompositions());
            known = new JComboBox<>(items.toArray());
            known.setRenderer(PlayersStepPanel.textRenderer(o -> o instanceof Composition c ? knownText(c)
                    : String.valueOf(o)));
            Composition chosen = model.composition(p.id());
            known.setSelectedItem(chosen == null ? items.get(0) : chosen);
            known.setToolTipText(JournalTexts.text("journal.teams.knownHint"));
            known.setPreferredSize(new Dimension(300, known.getPreferredSize().height));
            row.add(new JLabel(JournalTexts.text("journal.teams.known")));
            row.add(known);
            JComboBox<Object> knownBox = known;
            known.addActionListener(e -> {
                model.setKnownComposition(p.id(), knownBox.getSelectedItem() instanceof Composition c ? c : null);
                showUnits(units, model.composition(p.id()));
                updateButtons();
            });
        }
        showUnits(units, model.composition(p.id()));

        check.addActionListener(e -> {
            model.setSelected(p.id(), check.isSelected());
            target.setEnabled(check.isSelected());
            target.setSelectedItem(model.target(p.id()));
            updateButtons();
        });
        target.addActionListener(e -> {
            if (target.getSelectedItem() instanceof Target t && check.isSelected()) {
                model.setTarget(p.id(), t);
                updateButtons();
            }
        });
        return row;
    }

    private void showUnits(JPanel panel, Composition c) {
        panel.removeAll();
        if (c != null) {
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
        }
        panel.revalidate();
        panel.repaint();
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

    static String sourceText(CompositionSource source) {
        String symbol = switch (source) {
            case DEFENSE_UNITS -> "🛡 ";
            case ATTACK_EXACT_POWER -> "⚔ ";
            case POWER_ONLY -> "○ ";
        };
        return symbol + JournalTexts.of("compositionSource", source);
    }

    private static Color sourceColor(CompositionSource source) {
        return source == CompositionSource.POWER_ONLY ? Color.GRAY : new Color(46, 125, 50);
    }

    static String targetText(TeamKind kind, Target target) {
        return target.kind() == Target.Kind.NEW ? JournalTexts.text("journal.teams.target.NEW")
                : JournalTexts.text("journal.teams.target.FILL", JournalTexts.team(kind, target.teamIndex()));
    }

    private static String knownText(Composition c) {
        String units = c.unitIds().stream().map(LanguageService::displayName).collect(Collectors.joining(", "));
        return JournalTexts.text("journal.teams.knownItem", units, JournalTexts.date(c.battleDate()),
                JournalTexts.number(c.power()));
    }

    private JComponent skippedSection() {
        List<String> lines = new ArrayList<>();
        for (SkippedPlayer s : model.plan().skippedPlayers()) {
            lines.add(JournalTexts.text("journal.teams.skippedLine", JournalTexts.visibleSpaces(s.rawName()),
                    JournalTexts.of("skipReason", s.reason())));
        }
        List<String> withoutData = model.membersWithoutLogData();
        if (!withoutData.isEmpty()) {
            lines.add(JournalTexts.text("journal.teams.withoutData", String.join(", ", withoutData)));
        }
        JPanel panel = new JPanel(new BorderLayout());
        if (lines.isEmpty()) {
            return panel;
        }
        JTextArea text = new JTextArea(String.join("\n", lines), Math.min(6, lines.size()), 60);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setVisible(false);
        String title = JournalTexts.text("journal.teams.skipped", String.valueOf(model.plan().skippedPlayers().size()),
                String.valueOf(withoutData.size()));
        JToggleButton toggle = new JToggleButton("▸ " + title);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.addActionListener(e -> {
            scroll.setVisible(toggle.isSelected());
            toggle.setText((toggle.isSelected() ? "▾ " : "▸ ") + title);
            panel.revalidate();
            center.revalidate();
        });
        panel.add(toggle, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void updateButtons() {
        applyButton.setText(JournalTexts.text("journal.teams.apply", String.valueOf(model.selectedCount())));
        List<TeamBuildResult.Error> errors = model.selectedCount() == 0 ? List.of() : model.problems();
        applyButton.setEnabled(model.canApply());
        problems.setText(errors.isEmpty() ? " " : errors.stream().map(this::errorText)
                .distinct().collect(Collectors.joining("  ")));
    }

    private String errorText(TeamBuildResult.Error error) {
        String member = error.memberId() == null ? "" : model.guildMember(error.memberId())
                .map(GuildMember::name).orElse(error.memberId());
        return JournalTexts.text("journal.teams.error." + error.kind().name(), member);
    }

    // --- apply and result ---

    private void apply() {
        if (!model.canApply()) {
            return;
        }
        TeamBuildResult result = service.apply(model.plan(), model.selection());
        if (!result.isSuccess()) {
            problems.setText(result.errors().stream().map(this::errorText).distinct().collect(Collectors.joining("  ")));
            return;
        }
        onApplied.run();
        showResult(result);
    }

    private void showResult(TeamBuildResult result) {
        header.setText(JournalTexts.text("journal.teams.done"));
        onlyWithoutTeams.setVisible(false);
        center.removeAll();
        JPanel panel = JournalImportDialog.verticalPanel();
        panel.add(new JLabel(JournalTexts.text("journal.teams.result", String.valueOf(result.created()),
                String.valueOf(result.filled()), String.valueOf(result.withComposition()))));
        result.bySource().forEach((source, count) -> panel.add(new JLabel("   " + sourceText(source) + ": " + count)));
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
        if (context.isGuildDirty()) {
            panel.add(Box.createVerticalStrut(10));
            JLabel warning = JournalImportDialog.boldLabel(JournalTexts.text("journal.summary.guildChanged"));
            warning.setForeground(new Color(200, 120, 0));
            panel.add(warning);
            JButton save = new JButton(JournalTexts.text("journal.result.saveGuild"));
            save.addActionListener(e -> {
                try {
                    guildService.saveGuild(GuildService.SaveOrigin.JOURNAL_TEAM_BUILDER);
                    save.setEnabled(false);
                    warning.setText(JournalTexts.text("journal.result.guildSaved"));
                    warning.setForeground(UIManager.getColor("Label.foreground"));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, LanguageService.displayName("common.saveGuildError") + "\n"
                            + ex.getMessage(), LanguageService.displayName("common.saveErrorTitle"),
                            JOptionPane.ERROR_MESSAGE);
                }
            });
            extraButtons.add(save);
        }
        center.add(new JScrollPane(panel), BorderLayout.CENTER);
        applyButton.setVisible(false);
        clearButton.setVisible(false);
        problems.setText(" ");
        cancelButton.setText(JournalTexts.text("journal.button.close"));
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private String memberName(String memberId) {
        return context.guild() == null ? memberId : context.guild().members().stream()
                .filter(m -> m.id().equals(memberId)).map(GuildMember::name).findFirst().orElse(memberId);
    }

    /** For tests: the model once loaded. */
    TeamBuilderModel model() {
        return model;
    }

    /** Package-visible factory for the smoke test (not shown). */
    static JournalTeamBuilderDialog create(Window owner, AppContext context, GuildService guildService) {
        JournalTeamBuilderDialog dialog = new JournalTeamBuilderDialog(owner, context, guildService, () -> { });
        dialog.load();
        return dialog;
    }
}
