package org.c2w.gui.common;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class FortComboBox extends JComboBox<Fortification> {

    private static final String KEY_NO_FORTIFICATION = "common.none";
    private java.util.List<Fortification> fortificationCatalog;
    private FortificationType type = FortificationType.HERO;
    DefaultComboBoxModel<Fortification> model = new DefaultComboBoxModel<>();

    public FortComboBox(List<Fortification> fortificationCatalog, FortificationType type){
        super();
        this.fortificationCatalog = fortificationCatalog;
        this.type = type;
        init();
    }

    private void init(){
        List<Fortification> sorted = fortificationCatalog.stream()
                .filter(f -> f.type() == type)
                .sorted(Comparator.comparing(f -> LanguageService.displayName(f.id())))
                .toList();

        setModel(model);
        model.addElement(null);
        sorted.forEach(model::addElement);

        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? LanguageService.displayName(KEY_NO_FORTIFICATION)
                        : LanguageService.displayName(((Fortification) value).id()));
                return this;
            }
        });
    }
}
