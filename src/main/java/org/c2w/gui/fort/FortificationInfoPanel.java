package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.data.repository.HeroRepository;
import org.c2w.data.repository.TitanRepository;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.Config;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class FortificationInfoPanel extends JPanel {

    private static final int PROFIT_ICON_SIZE = 28;

    private final Fortification fortification;

    //private final JComboBox<Integer> strategicImportanceCombo = new JComboBox<>(STRATEGIC_IMPORTANCE_VALUES);
    private final DefaultListModel<String> buffProfitsListModel = new DefaultListModel<>();
    private final JList<String> buffProfitsList = new JList<>(buffProfitsListModel);
    private final JComboBox<String> addBuffProfitCombo = new JComboBox<>();

    public FortificationInfoPanel(Fortification fortification) {
        if (fortification == null) {
            throw new IllegalArgumentException("FortificationInfoPanel needs a fortification");
        }
        this.fortification = fortification;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(buildInfoPanel());
        if (fortification.buff() != null) {
            add(buildBuffProfitsPanel());
        }

        loadFromFortification();
    }

    private void loadFromFortification() {
        //strategicImportanceCombo.setSelectedItem(clampToStrategicImportanceRange(fortification.strategicImportance()));

        buffProfitsListModel.clear();
        if (fortification.buff() != null) {
            fortification.buff().buffProfits().forEach(buffProfitsListModel::addElement);
        }
        refreshAddBuffProfitCombo();
    }

    private static int clampToStrategicImportanceRange(int value) {
        return Math.max(1, Math.min(10, value));
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int y = 0;

        addInfoRow(panel, gbc, y++, "Buff:", buffText());
        addInfoRow(panel, gbc, y++, LanguageService.displayName("fortificationDetail.prerequisites"), prerequisitesText());

        gbc.gridx = 0;
        gbc.gridy = y;
        panel.add(new JLabel("Strategic Importance:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(fortification.strategicImportance()+""), gbc);
        return panel;
    }

    private static void addInfoRow(JPanel panel, GridBagConstraints gbc, int y, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(value), gbc);
    }

    private String buffText() {
        Buff buff = fortification.buff();
        if (buff == null) {
            return LanguageService.displayName("common.none");
        }
        String text = buff.display();
        if (text == null || text.isBlank()) {
            text = buff.effect().name() + " (" + Config.NUMBER_FORMAT.format(buff.bonusPercent()) + "%)";
        }
        return text;
    }


    private String prerequisitesText() {
        if (fortification.prerequisites().isEmpty()) {
            return LanguageService.displayName("common.none");
        }
        return fortification.prerequisites().stream()
                .map(LanguageService::displayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }


    private JPanel buildBuffProfitsPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Buff profits"));

        ListCellRenderer<Object> iconRenderer = buildProfitIconRenderer();
        buffProfitsList.setCellRenderer(iconRenderer);
        buffProfitsList.setVisibleRowCount(2);
        buffProfitsList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        panel.add(new JScrollPane(buffProfitsList), BorderLayout.CENTER);

        addBuffProfitCombo.setRenderer(iconRenderer);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(addBuffProfitCombo);
        JButton addButton = new JButton("+ Add");
        addButton.addActionListener(e -> onAddBuffProfit());
        JButton removeButton = new JButton("- Remove");
        removeButton.addActionListener(e -> onRemoveBuffProfit());
        controls.add(addButton);
        controls.add(removeButton);
        panel.add(controls, BorderLayout.SOUTH);

        return panel;
    }

    /** Icon-only renderer (name as tooltip, text fallback if no icon) shared by {@link #buffProfitsList} and {@link #addBuffProfitCombo}. */
    private ListCellRenderer<Object> buildProfitIconRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String id = (String) value;
                if (id == null) {
                    setText(null);
                    setIcon(null);
                    setToolTipText(null);
                    return this;
                }
                String name = LanguageService.displayName(id);
                Icon icon = iconForProfitId(id);
                if (icon != null) {
                    setText(null);
                    setIcon(icon);
                    setToolTipText(name);
                } else {
                    setText(name);
                    setIcon(null);
                    setToolTipText(null);
                }
                return this;
            }
        };
    }


    private Icon iconForProfitId(String id) {
        Buff buff = fortification.buff();
        if (buff instanceof RoleBuff) {
            return HeroRepository.findById(id).map(h -> IconLoader.iconFor(h.imagePath(), PROFIT_ICON_SIZE)).orElse(null);
        }
        if (buff instanceof ElementBuff) {
            return TitanRepository.findById(id).map(t -> IconLoader.iconFor(t.imagePath(), PROFIT_ICON_SIZE)).orElse(null);
        }
        return null;
    }

    /** Rebuilds {@link #addBuffProfitCombo}'s options: every hero/titan (matching the buff kind) not already in {@link #buffProfitsListModel}, sorted by display name. */
    private void refreshAddBuffProfitCombo() {
        Set<String> alreadyIncluded = new LinkedHashSet<>();
        for (int i = 0; i < buffProfitsListModel.size(); i++) {
            alreadyIncluded.add(buffProfitsListModel.get(i));
        }

        Buff buff = fortification.buff();
        java.util.List<String> available;
        if (buff instanceof RoleBuff) {
            available = HeroRepository.findAll().stream()
                    .map(Hero::id)
                    .filter(id -> !alreadyIncluded.contains(id))
                    .sorted(Comparator.comparing(LanguageService::displayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } else if (buff instanceof ElementBuff) {
            available = TitanRepository.findAll().stream()
                    .map(Titan::id)
                    .filter(id -> !alreadyIncluded.contains(id))
                    .sorted(Comparator.comparing(LanguageService::displayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } else {
            available = java.util.List.of();
        }
        addBuffProfitCombo.setModel(new DefaultComboBoxModel<>(available.toArray(new String[0])));
    }

    private void onAddBuffProfit() {
        String selected = (String) addBuffProfitCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        buffProfitsListModel.addElement(selected);
        refreshAddBuffProfitCombo();
    }

    private void onRemoveBuffProfit() {
        String selected = buffProfitsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        buffProfitsListModel.removeElement(selected);
        refreshAddBuffProfitCombo();
    }

    public Fortification applyEditsTo(Fortification base) {

        java.util.List<String> buffProfitIds = new ArrayList<>();
        for (int i = 0; i < buffProfitsListModel.size(); i++) {
            buffProfitIds.add(buffProfitsListModel.get(i));
        }
        Buff updatedBuff = withUpdatedBuffProfits(base.buff(), buffProfitIds);

        return new Fortification(base.id(), base.type(), base.capacity(), base.captureBonus(), base.row(),
                base.column(), updatedBuff, base.prerequisites(), base.strategicImportance());
    }

    /** Returns buff with buffProfits replaced by buffProfitIds (all its other fields unchanged) - null/an unrecognized Buff variant is passed through as-is. */
    private static Buff withUpdatedBuffProfits(Buff buff, List<String> buffProfitIds) {
        if (buff instanceof RoleBuff roleBuff) {
            return new RoleBuff(roleBuff.role(), roleBuff.effect(), roleBuff.bonusPercent(), buffProfitIds, roleBuff.display());
        }
        if (buff instanceof ElementBuff elementBuff) {
            return new ElementBuff(elementBuff.element(), elementBuff.effect(), elementBuff.bonusPercent(), buffProfitIds, elementBuff.display());
        }
        return buff;
    }
}

