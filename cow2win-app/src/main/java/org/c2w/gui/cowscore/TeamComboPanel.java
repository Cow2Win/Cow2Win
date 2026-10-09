package org.c2w.gui.cowscore;

import org.c2w.data.model.ComboSource;
import org.c2w.data.model.Hero;
import org.c2w.data.model.TeamCombo;
import org.c2w.data.model.TeamCombos;
import org.c2w.data.model.Titan;
import org.c2w.data.repository.HeroRepository;
import org.c2w.data.repository.TeamComboRepository;
import org.c2w.data.repository.TitanRepository;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.ComboTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Hero combos" / "Titan combos" tab of the CowScore dialog: maintains the {@link TeamCombo}s of
 * {@code heroCombos.json} / {@code titanCombos.json} (see {@link TeamComboRepository}) in a working
 * copy until {@link #save()}. Use {@link #forHeroes}/{@link #forTitans}; both kinds work exactly
 * alike and only differ in the member catalog, the texts (keys {@code heroCombos.*} /
 * {@code titanCombos.*}) and where the saved combos are registered.
 *
 * <p>Same layout as the other tabs - the list of combos on the left (deactivated ones gray,
 * invalid ones red) with "new" and "delete" buttons below it, on the right the selected combo:
 * header with the members' avatars and its name, optional name, up to
 * {@value TeamCombo#MAX_MEMBERS} members, "active" and where it comes from.
 *
 * <p>Changing a shipped combo makes it the user's own ({@link ComboSource#USER}) - otherwise
 * the merge on the next start would undo the change. Shipped combos cannot be deleted (they
 * would come back on the next start), only deactivated. A new combo gets its id on the first
 * save: the members' ids joined with "-", with "-2", "-3", ... if that is taken. After saving,
 * the combos are handed to {@link TeamScoreCalculator} right away.
 */
public final class TeamComboPanel extends JPanel implements CowScorePanel {

    private static final String ICON_NEW = "/images/app/add.png";
    private static final String ICON_DELETE = "/images/app/delete.png";
    private static final int BUTTON_ICON_SIZE = 16;
    private static final int MEMBER_ICON_SIZE = 20;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Text color of an invalid combo in the list - readable on the dark background. */
    private static final Color INVALID_COLOR = new Color(229, 115, 115);

    /** A combo being edited - unlike {@link TeamCombo} it may be invalid for a while. */
    static final class Draft {
        /** Null until the combo was saved for the first time. */
        String id;
        String name = "";
        final String[] memberIds = new String[TeamCombo.MAX_MEMBERS];
        ComboSource source = ComboSource.USER;
        LocalDate deactivated;

        static Draft of(TeamCombo combo) {
            Draft draft = new Draft();
            draft.id = combo.id();
            draft.name = combo.hasCustomName() ? combo.name() : "";
            for (int i = 0; i < combo.memberIds().size(); i++) {
                draft.memberIds[i] = combo.memberIds().get(i);
            }
            draft.source = combo.source();
            draft.deactivated = combo.deactivated();
            return draft;
        }

        /** The chosen members, in field order. */
        List<String> members() {
            return Arrays.stream(memberIds).filter(Objects::nonNull).toList();
        }

        TeamCombo toCombo(String comboId) {
            return new TeamCombo(comboId, name, members(), source, deactivated);
        }
    }

    private final TeamComboRepository repository;
    /** All ids of the member catalog, sorted by localized name - the choices of a member combo box. */
    private final List<String> memberIds;
    /** Avatar path of a member id, empty if unknown. */
    private final Function<String, Optional<String>> imagePathOf;
    /** Prefix of every language file key of this tab, e.g. {@code "heroCombos"}. */
    private final String textPrefix;
    /** Member word of the member-specific keys, e.g. {@code "Hero"} for {@code heroCombos.noHero}. */
    private final String memberWord;
    /** Where the saved combos are registered, e.g. {@link TeamScoreCalculator#setHeroCombos}. */
    private final Consumer<TeamCombos> register;
    /** Ids of the shipped combos - these cannot be deleted. */
    private final Set<String> shippedIds;

    private final DefaultListModel<Draft> listModel = new DefaultListModel<>();
    private final JList<Draft> list = new JList<>(listModel);
    private final JPanel leftPanel = new JPanel(new BorderLayout());
    private final JPanel detailContainer = new JPanel(new BorderLayout());
    private final JScrollPane detailScrollPane = new JScrollPane(detailContainer);
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final FlatButton newButton = new FlatButton(IconLoader.iconFor(ICON_NEW, BUTTON_ICON_SIZE, IconLoader.GREEN));
    private final FlatButton deleteButton = new FlatButton(IconLoader.iconFor(ICON_DELETE, BUTTON_ICON_SIZE, IconLoader.RED));
    private final List<ChangeListener> changeListeners = new ArrayList<>();
    private boolean unsaved;

    // Detail fields of the selected combo - filled by showSelected(), edits go to the draft.
    private final JPanel headerHolder = new JPanel(new BorderLayout());
    private final JTextField nameField = new JTextField(20);
    private final List<JComboBox<String>> memberCombos = new ArrayList<>();
    private final JCheckBox activeCheckBox = new JCheckBox();
    private final JLabel deactivatedLabel = new JLabel();
    private final JLabel sourceLabel = new JLabel();
    private final JLabel shippedHintLabel = new JLabel();
    private final JLabel problemLabel = new JLabel();
    private final JPanel detailPanel;
    /** True while the fields are filled from a draft - their listeners must not count that as an edit. */
    private boolean filling;

    private TeamComboPanel(TeamComboRepository repository, List<String> memberIds,
                           Function<String, Optional<String>> imagePathOf, String textPrefix, String memberWord,
                           Consumer<TeamCombos> register) {
        super(new BorderLayout());
        this.repository = Objects.requireNonNull(repository);
        this.memberIds = memberIds.stream()
                .sorted(Comparator.comparing(LanguageService::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
        this.imagePathOf = Objects.requireNonNull(imagePathOf);
        this.textPrefix = textPrefix;
        this.memberWord = memberWord;
        this.register = Objects.requireNonNull(register);
        this.shippedIds = repository.defaultCombos().combos().stream().map(TeamCombo::id).collect(Collectors.toSet());

        list.setCellRenderer(new ComboRenderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });
        newButton.setToolTipText(LanguageService.displayName(text("new")));
        newButton.addActionListener(e -> newCombo());
        deleteButton.addActionListener(e -> onDelete());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttons.add(newButton);
        buttons.add(deleteButton);
        leftPanel.add(new JScrollPane(list), BorderLayout.CENTER);
        leftPanel.add(buttons, BorderLayout.SOUTH);

        detailPanel = buildDetailPanel();
        split.setLeftComponent(leftPanel);
        split.setRightComponent(detailScrollPane);
        split.setResizeWeight(0);
        setListWidth(CowScoreLayout.MIN_LIST_WIDTH);
        add(split, BorderLayout.CENTER);

        load(repository.combos().combos().stream().map(Draft::of).toList());
    }

    /** The "Hero combos" tab: {@code heroCombos.json}, members from {@code heroes}. */
    public static TeamComboPanel forHeroes(TeamComboRepository repository, HeroRepository heroes) {
        return new TeamComboPanel(repository, heroes.findAll().stream().map(Hero::id).toList(),
                id -> heroes.findById(id).map(Hero::imagePath), "heroCombos", "Hero",
                TeamScoreCalculator::setHeroCombos);
    }

    /** The "Titan combos" tab: {@code titanCombos.json}, members from {@code titans}. */
    public static TeamComboPanel forTitans(TeamComboRepository repository, TitanRepository titans) {
        return new TeamComboPanel(repository, titans.findAll().stream().map(Titan::id).toList(),
                id -> titans.findById(id).map(Titan::imagePath), "titanCombos", "Titan",
                TeamScoreCalculator::setTitanCombos);
    }

    // --- CowScorePanel ---

    @Override
    public boolean hasUnsavedChanges() {
        return unsaved;
    }

    /**
     * Checks every combo, gives new ones their id, writes the combo file and hands the
     * combos to {@link TeamScoreCalculator}.
     *
     * @throws IOException if a combo is invalid (nothing is written) or writing failed
     */
    @Override
    public void save() throws IOException {
        List<Draft> drafts = drafts();
        List<String> invalid = drafts.stream().filter(d -> problemKey(d) != null).map(this::displayName).toList();
        if (!invalid.isEmpty()) {
            throw new IOException(LanguageService.displayName(text("invalid"), String.join(", ", invalid)));
        }
        Set<String> takenIds = new HashSet<>(shippedIds);
        drafts.stream().map(d -> d.id).filter(Objects::nonNull).forEach(takenIds::add);
        List<String> ids = new ArrayList<>();
        List<TeamCombo> combos = new ArrayList<>();
        for (Draft draft : drafts) {
            String comboId = draft.id != null ? draft.id : newId(draft.members(), takenIds);
            takenIds.add(comboId);
            ids.add(comboId);
            combos.add(draft.toCombo(comboId));
        }
        repository.save(combos);
        for (int i = 0; i < drafts.size(); i++) {
            drafts.get(i).id = ids.get(i);
        }
        register.accept(repository.combos());
        Logger.log("Saved: " + repository.fileName());
        setUnsaved(false);
        showSelected();
    }

    /**
     * Every shipped combo back to its shipped version (and missing ones back), the user's own
     * combos stay. Not saved yet.
     */
    @Override
    public void restoreDefaults() {
        List<Draft> result = new ArrayList<>(drafts());
        for (TeamCombo shipped : repository.defaultCombos().combos()) {
            Draft draft = Draft.of(shipped);
            int index = indexOfId(result, shipped.id());
            if (index >= 0) {
                result.set(index, draft);
            } else {
                result.add(draft);
            }
        }
        load(result);
        Logger.log("CowScore " + repository.fileName() + ": restored the shipped combos (not saved yet)");
        setUnsaved(true);
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    @Override
    public String restoreDefaultsConfirmKey() {
        return text("restoreDefaultsConfirm");
    }

    @Override
    public JComponent component() {
        return this;
    }

    @Override
    public List<String> listLabels() {
        return drafts().stream().map(this::displayName).toList();
    }

    @Override
    public void setListWidth(int width) {
        leftPanel.setPreferredSize(new Dimension(width, CowScoreLayout.LIST_PREFERRED_HEIGHT));
        leftPanel.setMinimumSize(new Dimension(width, 0));
        split.setDividerLocation(width + split.getInsets().left);
    }

    @Override
    public JScrollPane detailScrollPane() {
        return detailScrollPane;
    }

    // --- actions, package-visible for tests ---

    /** Adds an empty combo of the user and selects it. */
    void newCombo() {
        Draft draft = new Draft();
        listModel.addElement(draft);
        list.setSelectedValue(draft, true);
        setUnsaved(true);
    }

    /** Deletes the selected combo without asking - not for a shipped one. */
    void deleteSelected() {
        Draft draft = list.getSelectedValue();
        if (draft == null || !canDelete(draft)) {
            return;
        }
        int index = list.getSelectedIndex();
        listModel.remove(index);
        if (!listModel.isEmpty()) {
            list.setSelectedIndex(Math.min(index, listModel.size() - 1));
        }
        setUnsaved(true);
    }

    /** False for a combo whose id is shipped - it would come back on the next start. */
    boolean canDelete(Draft draft) {
        return draft.id == null || !shippedIds.contains(draft.id);
    }

    /** Selects the combo with {@code id}. */
    void select(String id) {
        for (int i = 0; i < listModel.size(); i++) {
            if (id.equals(listModel.get(i).id)) {
                list.setSelectedIndex(i);
                return;
            }
        }
    }

    Draft selectedDraft() {
        return list.getSelectedValue();
    }

    List<Draft> drafts() {
        List<Draft> drafts = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            drafts.add(listModel.get(i));
        }
        return drafts;
    }

    JTextField nameField() {
        return nameField;
    }

    List<JComboBox<String>> memberCombos() {
        return memberCombos;
    }

    JCheckBox activeCheckBox() {
        return activeCheckBox;
    }

    JButton deleteButton() {
        return deleteButton;
    }

    /** Language file key of what is wrong with {@code draft}, or null if it is valid. */
    String problemKey(Draft draft) {
        List<String> members = draft.members();
        if (members.size() < TeamCombo.MIN_MEMBERS) {
            return text("problem.tooFew");
        }
        if (new HashSet<>(members).size() != members.size()) {
            return text("problem.duplicate" + memberWord);
        }
        Set<String> memberSet = Set.copyOf(members);
        for (int i = 0; i < listModel.size(); i++) {
            Draft other = listModel.get(i);
            if (other != draft && Set.copyOf(other.members()).equals(memberSet)) {
                return text("problem.sameMembers");
            }
        }
        return null;
    }

    /** The id for a new combo with {@code members}: their ids joined with "-", "-2", "-3", ... if taken. */
    static String newId(List<String> members, Set<String> takenIds) {
        String base = String.join("-", members);
        String id = base;
        for (int suffix = 2; takenIds.contains(id); suffix++) {
            id = base + "-" + suffix;
        }
        return id;
    }

    // --- private ---

    private void onDelete() {
        Draft draft = list.getSelectedValue();
        if (draft == null || !canDelete(draft)) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                LanguageService.displayName(text("deleteConfirm"), displayName(draft)),
                LanguageService.displayName("common.confirmTitle"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            deleteSelected();
        }
    }

    /** Shows {@code drafts} sorted by display name and selects the first one. */
    private void load(List<Draft> drafts) {
        listModel.clear();
        drafts.stream().sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
                .forEach(listModel::addElement);
        if (!listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }
        showSelected();
    }

    /** The language file key {@code <prefix>.<suffix>} of this tab, e.g. {@code heroCombos.new}. */
    private String text(String suffix) {
        return textPrefix + "." + suffix;
    }

    private static int indexOfId(List<Draft> drafts, String id) {
        for (int i = 0; i < drafts.size(); i++) {
            if (id.equals(drafts.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private String displayName(Draft draft) {
        if (!draft.name.isBlank()) {
            return draft.name.trim();
        }
        List<String> members = draft.members();
        return members.isEmpty() ? LanguageService.displayName(text("unnamed")) : ComboTexts.memberNames(members);
    }

    private void setUnsaved(boolean unsaved) {
        this.unsaved = unsaved;
        ChangeEvent event = new ChangeEvent(this);
        List.copyOf(changeListeners).forEach(l -> l.stateChanged(event));
    }

    /** An edit of the selected combo: a shipped one becomes the user's own, the tab unsaved. */
    private void edited(Draft draft) {
        draft.source = ComboSource.USER;
        setUnsaved(true);
        updateDerived(draft);
        list.repaint();
    }

    /** Fills the detail fields from the selected combo. */
    private void showSelected() {
        Draft draft = list.getSelectedValue();
        detailContainer.removeAll();
        deleteButton.setEnabled(draft != null && canDelete(draft));
        deleteButton.setToolTipText(LanguageService.displayName(draft != null && !canDelete(draft)
                ? text("deleteShipped") : text("delete")));
        if (draft != null) {
            filling = true;
            try {
                nameField.setText(draft.name);
                for (int i = 0; i < memberCombos.size(); i++) {
                    memberCombos.get(i).setSelectedItem(draft.memberIds[i]);
                }
                activeCheckBox.setSelected(draft.deactivated == null);
            } finally {
                filling = false;
            }
            updateDerived(draft);
            detailContainer.add(detailPanel, BorderLayout.CENTER);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    /** Header, "deactivated since", source, hint and problem of {@code draft}. */
    private void updateDerived(Draft draft) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (String memberId : draft.members()) {
            imagePathOf.apply(memberId)
                    .map(path -> IconLoader.iconFor(path, AbstractCowScorePanel.AVATAR_SIZE))
                    .ifPresent(icon -> header.add(new JLabel(icon)));
        }
        header.add(CowScoreLayout.nameLabel(displayName(draft), null));
        headerHolder.removeAll();
        headerHolder.add(header, BorderLayout.WEST);
        headerHolder.revalidate();
        headerHolder.repaint();

        deactivatedLabel.setText(draft.deactivated == null ? ""
                : LanguageService.displayName(text("deactivatedSince"), DATE_FORMAT.format(draft.deactivated)));
        sourceLabel.setText(LanguageService.displayName(text("source.") + draft.source.name()));
        shippedHintLabel.setVisible(draft.id != null && shippedIds.contains(draft.id));
        String problem = problemKey(draft);
        problemLabel.setText(problem == null ? "" : LanguageService.displayName(problem));
    }

    private JPanel buildDetailPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(headerHolder, gbc);
        gbc.gridwidth = 1;

        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                nameChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                nameChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                nameChanged();
            }
        });
        addFormRow(panel, gbc, 1, text("name"), nameField);

        JLabel memberColumn = new JLabel(LanguageService.displayName(text("column." + memberWord.toLowerCase(Locale.ROOT))));
        memberColumn.setForeground(Color.WHITE);
        memberColumn.setFont(memberColumn.getFont().deriveFont(Font.BOLD));
        memberColumn.setBorder(new MatteBorder(0, 0, 2, 0, Color.WHITE));
        addFormRow(panel, gbc, 2, text("members"), memberColumn);
        for (int i = 0; i < TeamCombo.MAX_MEMBERS; i++) {
            JComboBox<String> combo = new JComboBox<>();
            combo.addItem(null);
            memberIds.forEach(combo::addItem);
            combo.setRenderer(new MemberRenderer());
            combo.setMaximumRowCount(15);
            int slot = i;
            combo.addActionListener(e -> {
                Draft draft = list.getSelectedValue();
                if (!filling && draft != null && !Objects.equals(draft.memberIds[slot], combo.getSelectedItem())) {
                    draft.memberIds[slot] = (String) combo.getSelectedItem();
                    edited(draft);
                }
            });
            memberCombos.add(combo);
            addFormRow(panel, gbc, 3 + i, null, combo);
        }

        activeCheckBox.addActionListener(e -> {
            Draft draft = list.getSelectedValue();
            if (!filling && draft != null) {
                draft.deactivated = activeCheckBox.isSelected() ? null : LocalDate.now();
                edited(draft);
            }
        });
        JPanel activePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        activePanel.add(activeCheckBox);
        activePanel.add(Box.createHorizontalStrut(8));
        activePanel.add(deactivatedLabel);
        int row = 3 + TeamCombo.MAX_MEMBERS;
        addFormRow(panel, gbc, row++, text("active"), activePanel);
        addFormRow(panel, gbc, row++, text("source"), sourceLabel);
        shippedHintLabel.setText(LanguageService.displayName(text("shippedHint")));
        addFormRow(panel, gbc, row++, null, shippedHintLabel);
        problemLabel.setForeground(INVALID_COLOR);
        addFormRow(panel, gbc, row++, null, problemLabel);

        // Takes the remaining height, so the form stays at the top.
        gbc.gridy = row;
        gbc.weighty = 1;
        panel.add(Box.createGlue(), gbc);
        return panel;
    }

    private void nameChanged() {
        Draft draft = list.getSelectedValue();
        if (!filling && draft != null && !draft.name.equals(nameField.getText())) {
            draft.name = nameField.getText();
            edited(draft);
        }
    }

    private static void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelKey, JComponent component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        if (labelKey != null) {
            JLabel label = new JLabel(LanguageService.displayName(labelKey));
            label.setForeground(Color.WHITE);
            panel.add(label, gbc);
        }
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = component instanceof JTextField || component instanceof JComboBox ? GridBagConstraints.NONE
                : GridBagConstraints.HORIZONTAL;
        panel.add(component, gbc);
    }

    /** List entry: display name, gray if deactivated, red if invalid. */
    private final class ComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Draft draft) {
                setText(displayName(draft));
                if (problemKey(draft) != null) {
                    setForeground(INVALID_COLOR);
                } else if (draft.deactivated != null) {
                    setForeground(Color.GRAY);
                }
            }
            return this;
        }
    }

    /** A member in a member combo box: avatar and localized name, "- no hero -" (or titan) for null. */
    private final class MemberRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String memberId) {
                setText(LanguageService.displayName(memberId));
                setIcon(imagePathOf.apply(memberId)
                        .map(path -> IconLoader.iconFor(path, MEMBER_ICON_SIZE)).orElse(null));
            } else {
                setText(LanguageService.displayName(text("no" + memberWord)));
                setIcon(null);
            }
            return this;
        }
    }
}
