package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.common.GridPanel;
import org.c2w.util.AppContext;
import org.c2w.util.BuffCalculationService;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FortificationMapPanel extends GridPanel {

    /** Language file key (see resources/language/*.txt) for the "show heroes" checkbox label. */
    private static final String KEY_SHOW_HEROES = "fortificationMap.showHeroes";

    /** Language file key (see resources/language/*.txt) for the "show titans" checkbox label. */
    private static final String KEY_SHOW_TITANS = "fortificationMap.showTitans";

    private final AppContext appContext;
    private Lineup lineup;
    private Guild guild;

    private Runnable onGuildChangedElsewhere;
    private boolean showHeroFortifications = true;
    private boolean showTitanFortifications = true;

    public FortificationMapPanel(AppContext appContext){
        super(8,5);

        this.appContext = appContext;
        this.lineup = appContext.lineup();
        this.guild = appContext.guild();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        init();
    }


    public void refresh(Lineup lineup) {
        refresh(lineup, this.guild);
    }


    public void setOnGuildChangedElsewhere(Runnable onGuildChangedElsewhere) {
        this.onGuildChangedElsewhere = onGuildChangedElsewhere;
    }

    public void refreshAfterExternalSave() {
        refresh(appContext.lineup(), appContext.guild());
        if (onGuildChangedElsewhere != null) {
            onGuildChangedElsewhere.run();
        }
    }

    public void refresh(Lineup lineup, Guild guild) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        this.lineup = lineup;
        this.guild = guild;
        init();
    }

    private void init(){
        List<Fortification> fortificationCatalog = FortificationRepository.findAll();
        Map<String, Integer> filledSlotsMap = new HashMap<>();
        Map<String, Integer> totalPowerMap = new HashMap<>();


        for (Lineup.Entry entry : lineup.entries()) {
            String fortId = entry.fortificationId();
            filledSlotsMap.put(fortId, filledSlotsMap.getOrDefault(fortId, 0) + 1);
            totalPowerMap.put(fortId, totalPowerMap.getOrDefault(fortId, 0) + entry.totalPower());
        }

        for (Fortification fort : fortificationCatalog) {
            if (!isVisible(fort.type())) {
                clearCellAt(fort.row(), fort.column());
                continue;
            }
            int buffPercent = BuffCalculationService.calculateBuffForFortification(
                    fort.id(), lineup, guild, fort);
            int filledSlots = filledSlotsMap.getOrDefault(fort.id(), 0);
            int totalPower = totalPowerMap.getOrDefault(fort.id(), 0);
            setComponentAt(fort.row(), fort.column(), new FortificationPanel(fort, filledSlots, totalPower, buffPercent,
                    appContext, this));
        }

        setComponentAt(0, 0, new LineupSummaryPanel(lineup, guild));
        setComponentAt(0, 4, buildTypeFilterPanel());
    }

    /**
     * Whether a fortification of the given type currently belongs on the
     * map - {@link #showHeroFortifications}/{@link #showTitanFortifications}
     * respectively, see class Javadoc.
     */
    private boolean isVisible(FortificationType type) {
        return type == FortificationType.HERO ? showHeroFortifications : showTitanFortifications;
    }


    private JPanel buildTypeFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JCheckBox showHeroesCheckbox = new JCheckBox(LanguageService.displayName(KEY_SHOW_HEROES), showHeroFortifications);
        showHeroesCheckbox.addActionListener(e -> {
            showHeroFortifications = showHeroesCheckbox.isSelected();
            init();
        });

        JCheckBox showTitansCheckbox = new JCheckBox(LanguageService.displayName(KEY_SHOW_TITANS), showTitanFortifications);
        showTitansCheckbox.addActionListener(e -> {
            showTitanFortifications = showTitansCheckbox.isSelected();
            init();
        });

        panel.add(showHeroesCheckbox);
        panel.add(showTitansCheckbox);
        return panel;
    }

}

