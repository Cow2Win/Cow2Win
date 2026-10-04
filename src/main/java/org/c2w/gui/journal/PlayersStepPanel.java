package org.c2w.gui.journal;

import org.c2w.data.model.Guild;
import org.c2w.gui.journal.ImportWizardModel.MemberChoice;
import org.c2w.service.journal.PlayerAnswer;
import org.c2w.service.journal.PlayerAutoAssignment;
import org.c2w.service.journal.PlayerQuestion;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Players step: the defenders of the defense log that need an answer, each with
 * the allowed answers and - for "assign to" / "renamed" - a member chooser with
 * the service's suggestions on top. Automatic assignments are listed collapsed.
 * Every change goes straight into the {@link ImportWizardModel}.
 */
final class PlayersStepPanel extends JPanel {

    private final ImportWizardModel model;
    private final Runnable onChange;
    private final JLabel memberCount = new JLabel();
    private final List<Row> rows = new ArrayList<>();
    private boolean updating;

    PlayersStepPanel(ImportWizardModel model, Runnable onChange) {
        super(new BorderLayout(0, 8));
        this.model = model;
        this.onChange = onChange;

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(JournalImportDialog.wrapLabel(JournalTexts.text("journal.players.hint")));
        memberCount.setAlignmentX(LEFT_ALIGNMENT);
        top.add(memberCount);
        if (!model.plan().autoAssignments().isEmpty()) {
            top.add(autoAssignments(model.plan().autoAssignments()));
        }
        add(top, BorderLayout.NORTH);

        JPanel list = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        int y = 0;
        for (PlayerQuestion q : model.plan().playerQuestions()) {
            Row row = new Row(q);
            rows.add(row);
            c.gridy = y++;
            c.gridx = 0;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            list.add(row.name, c);
            c.gridx = 1;
            list.add(row.kind, c);
            c.gridx = 2;
            list.add(row.member, c);
            c.gridx = 3;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            list.add(row.info, c);
        }
        c.gridy = y;
        c.weighty = 1;
        list.add(Box.createGlue(), c);
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        refresh();
    }

    private JComponent autoAssignments(List<PlayerAutoAssignment> assignments) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(LEFT_ALIGNMENT);
        DefaultListModel<String> items = new DefaultListModel<>();
        Map<String, String> memberNames = new HashMap<>();
        model.members().forEach(m -> memberNames.put(m.memberId(), m.memberName()));
        for (PlayerAutoAssignment a : assignments) {
            String member = memberNames.getOrDefault(a.memberId(), a.memberId());
            String text = JournalTexts.visibleSpaces(a.rawName()) + " → " + member;
            items.addElement(a.normalized()
                    ? "<html><b>" + escape(text) + "</b> (" + escape(JournalTexts.text("journal.players.autoNormalized")) + ")</html>"
                    : text);
        }
        JList<String> list = new JList<>(items);
        list.setVisibleRowCount(Math.min(6, items.size()));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setVisible(false);
        JToggleButton toggle = new JToggleButton("▸ " + JournalTexts.text("journal.players.auto", String.valueOf(assignments.size())));
        toggle.addActionListener(e -> {
            scroll.setVisible(toggle.isSelected());
            toggle.setText((toggle.isSelected() ? "▾ " : "▸ ")
                    + JournalTexts.text("journal.players.auto", String.valueOf(assignments.size())));
            revalidate();
        });
        panel.add(toggle, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /** Updates every row (duplicate renames affect other rows) and the member count. */
    private void refresh() {
        updating = true;
        try {
            Set<String> duplicates = model.duplicateRenameQuestionIds();
            rows.forEach(r -> r.update(duplicates));
            memberCount.setText(JournalTexts.text("journal.players.members",
                    String.valueOf(model.memberCountAfterImport()), String.valueOf(Guild.MAX_MEMBERS)));
            memberCount.setForeground(model.isMemberLimitExceeded() ? Color.RED : UIManager.getColor("Label.foreground"));
        } finally {
            updating = false;
        }
        onChange.run();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** One player question: name, answer kind, member chooser, info. */
    private final class Row {
        final PlayerQuestion question;
        final JLabel name;
        final JComboBox<PlayerAnswer.Kind> kind;
        final JComboBox<MemberChoice> member = new JComboBox<>();
        final JLabel info = new JLabel();
        private PlayerAnswer.Kind shownMemberKind;

        Row(PlayerQuestion question) {
            this.question = question;
            this.name = JournalImportDialog.boldLabel(JournalTexts.visibleSpaces(question.rawName()));
            List<PlayerAnswer.Kind> allowed = Arrays.stream(PlayerAnswer.Kind.values())
                    .filter(question.allowedAnswers()::contains).toList();
            kind = new JComboBox<>(allowed.toArray(new PlayerAnswer.Kind[0]));
            kind.setRenderer(textRenderer(k -> JournalTexts.of("answer", (PlayerAnswer.Kind) k)));
            member.setRenderer(textRenderer(m -> memberText((MemberChoice) m)));
            member.setPrototypeDisplayValue(new MemberChoice("", "MMMMMMMMMMMMMMMMMM"));
            kind.setSelectedItem(model.playerAnswer(question.id()).kind());
            kind.addActionListener(e -> onKindChanged());
            member.addActionListener(e -> onMemberChanged());
        }

        private void onKindChanged() {
            if (updating) {
                return;
            }
            PlayerAnswer.Kind k = (PlayerAnswer.Kind) kind.getSelectedItem();
            if (k == PlayerAnswer.Kind.ASSIGN || k == PlayerAnswer.Kind.RENAME) {
                List<MemberChoice> choices = choices(k);
                if (choices.isEmpty()) {
                    model.setPlayerAnswer(question.id(), PlayerAnswer.of(PlayerAnswer.Kind.OPEN));
                } else {
                    String id = choices.get(0).memberId();
                    model.setPlayerAnswer(question.id(), k == PlayerAnswer.Kind.ASSIGN ? PlayerAnswer.assign(id) : PlayerAnswer.rename(id));
                }
            } else {
                model.setPlayerAnswer(question.id(), PlayerAnswer.of(k));
            }
            refresh();
        }

        private void onMemberChanged() {
            if (updating || member.getSelectedItem() == null) {
                return;
            }
            String id = ((MemberChoice) member.getSelectedItem()).memberId();
            PlayerAnswer.Kind k = model.playerAnswer(question.id()).kind();
            if (k == PlayerAnswer.Kind.ASSIGN) {
                model.setPlayerAnswer(question.id(), PlayerAnswer.assign(id));
            } else if (k == PlayerAnswer.Kind.RENAME) {
                model.setPlayerAnswer(question.id(), PlayerAnswer.rename(id));
            }
            refresh();
        }

        private List<MemberChoice> choices(PlayerAnswer.Kind k) {
            return k == PlayerAnswer.Kind.RENAME ? model.renameChoices(question) : model.assignChoices(question);
        }

        void update(Set<String> duplicates) {
            PlayerAnswer answer = model.playerAnswer(question.id());
            kind.setSelectedItem(answer.kind());
            boolean needsMember = answer.kind() == PlayerAnswer.Kind.ASSIGN || answer.kind() == PlayerAnswer.Kind.RENAME;
            if (needsMember && shownMemberKind != answer.kind()) {
                member.setModel(new DefaultComboBoxModel<>(choices(answer.kind()).toArray(new MemberChoice[0])));
                shownMemberKind = answer.kind();
            }
            member.setEnabled(needsMember);
            member.setVisible(needsMember);
            if (needsMember) {
                for (int i = 0; i < member.getItemCount(); i++) {
                    if (member.getItemAt(i).memberId().equals(answer.memberId())) {
                        member.setSelectedIndex(i);
                    }
                }
            }
            info.setText(infoText(answer, duplicates.contains(question.id())));
        }

        private String infoText(PlayerAnswer answer, boolean duplicate) {
            if (duplicate) {
                return "<html><font color='red'>" + escape(JournalTexts.text("journal.players.duplicateRename")) + "</font></html>";
            }
            return switch (answer.kind()) {
                case ASSIGN -> question.nameSuggestions().stream()
                        .filter(s -> s.memberId().equals(answer.memberId())).findFirst()
                        .map(s -> JournalTexts.text("journal.players.distance", String.valueOf(s.distance()))).orElse("");
                case RENAME -> question.renameSuggestions().stream()
                        .filter(s -> s.memberId().equals(answer.memberId())).findFirst()
                        .map(this::evidenceText).orElse("");
                case CREATE -> JournalTexts.text("journal.players.createHint");
                default -> "";
            };
        }

        private String evidenceText(PlayerQuestion.RenameSuggestion s) {
            String evidence = s.evidence().stream().map(JournalTexts::evidence).map(PlayersStepPanel::escape)
                    .collect(Collectors.joining("; "));
            return s.hasUnitMatch()
                    ? "<html><b>" + escape(JournalTexts.text("journal.players.strongEvidence")) + ":</b> " + evidence + "</html>"
                    : "<html>" + evidence + "</html>";
        }

        private String memberText(MemberChoice m) {
            if (m == null) {
                return "";
            }
            boolean suggested = question.nameSuggestions().stream().anyMatch(s -> s.memberId().equals(m.memberId()))
                    || (shownMemberKind == PlayerAnswer.Kind.RENAME
                    && question.renameSuggestions().stream().anyMatch(s -> s.memberId().equals(m.memberId())));
            return (suggested ? "★ " : "") + m.memberName();
        }
    }

    static ListCellRenderer<Object> textRenderer(java.util.function.Function<Object, String> text) {
        DefaultListCellRenderer delegate = new DefaultListCellRenderer();
        return (list, value, index, selected, focus) ->
                delegate.getListCellRendererComponent(list, value == null ? "" : text.apply(value), index, selected, focus);
    }
}
