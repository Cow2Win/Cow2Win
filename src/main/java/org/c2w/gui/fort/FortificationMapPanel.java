package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.common.GridPanel;
import org.c2w.util.AppContext;
import org.c2w.util.BuffCalculationService;
import org.c2w.util.LineupBaseline;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FortificationMapPanel extends GridPanel {

    private final AppContext appContext;
    private Lineup lineup;
    private Guild guild;

    private Runnable onGuildChangedElsewhere;
    private boolean showHeroFortifications = true;
    private boolean showTitanFortifications = true;

    /** True while the "Changes" checkbox in the toolbar (see ToolbarPanel) is selected - then every {@link FortificationPanel} shows its power change against the baseline loaded from disk instead of its current total power (see AppContext#loadedFortificationBaseline). */
    private boolean showChanges = false;

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
        // Transparent since 2026-09-17: the background image is now painted once,
        // higher up in the component hierarchy, by Cow2Frame's content pane -
        // see Cow2Frame.BackgroundPanel. Staying non-opaque here (and in every
        // panel/scroll pane between this one and that content pane) is what lets
        // it show through instead of being painted over by this panel's own
        // background.
        setOpaque(false);
        List<Fortification> fortificationCatalog = FortificationRepository.findAll();
        Map<String, Integer> filledSlotsMap = new HashMap<>();
        Map<String, Integer> totalPowerMap = new HashMap<>();


        for (Lineup.Entry entry : lineup.entries()) {
            String fortId = entry.fortificationId();
            filledSlotsMap.put(fortId, filledSlotsMap.getOrDefault(fortId, 0) + 1);
            totalPowerMap.put(fortId, totalPowerMap.getOrDefault(fortId, 0) + BuffCalculationService.totalPowerOf(entry, guild));
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
            // No baseline entry means this fortification had no team assigned when the lineup was loaded (see AppContext#set(Lineup, Path)) - treat that as a loaded totalPower/buffMemberCount of 0, so anything now assigned here shows up as a full gain.
            LineupBaseline loadedBaseline = appContext.loadedFortificationBaseline(fort.id());
            int totalPowerDiff = totalPower - (loadedBaseline == null ? 0 : loadedBaseline.totalPower());
            // buffPercent = matching members x buff.bonusPercent() (see BuffCalculationService#calculateBuffForFortification) - the loaded side of the diff is derived from the baseline's buffMemberCount the same way, rather than caching buffPercent itself.
            int loadedBuffPercent = (loadedBaseline == null || fort.buff() == null)
                    ? 0 : (int) (loadedBaseline.buffMemberCount() * fort.buff().bonusPercent());
            int buffPercentDiff = buffPercent - loadedBuffPercent;
            setComponentAt(fort.row(), fort.column(), new FortificationPanel(fort, filledSlots, totalPower, totalPowerDiff,
                    showChanges, buffPercent, buffPercentDiff, appContext, this));
        }

        // Hero summary top left, titan summary top right (same row) - each one
        // only while its fortification type is shown on the map.
        clearCellAt(0, 0);
        if (showHeroFortifications) {
            setComponentAt(0, 0, new HeroLineupSummaryPanel(lineup, guild));
        }
        clearCellAt(0, columns() - 1);
        if (showTitanFortifications) {
            setComponentAt(0, columns() - 1, new TitanLineupSummaryPanel(lineup, guild));
        }
    }

    /**
     * Whether a fortification of the given type currently belongs on the
     * map - {@link #showHeroFortifications}/{@link #showTitanFortifications}
     * respectively, see class Javadoc.
     */
    private boolean isVisible(FortificationType type) {
        return type == FortificationType.HERO ? showHeroFortifications : showTitanFortifications;
    }


    public boolean isShowHeroFortifications() {
        return showHeroFortifications;
    }

    /** Shows/hides every HERO fortification on the map - driven by the toolbar's "show heroes" checkbox (see ToolbarPanel). */
    public void setShowHeroFortifications(boolean showHeroFortifications) {
        this.showHeroFortifications = showHeroFortifications;
        init();
    }

    public boolean isShowTitanFortifications() {
        return showTitanFortifications;
    }

    /** Shows/hides every TITAN fortification on the map - driven by the toolbar's "show titans" checkbox (see ToolbarPanel). */
    public void setShowTitanFortifications(boolean showTitanFortifications) {
        this.showTitanFortifications = showTitanFortifications;
        init();
    }

    public boolean isShowChanges() {
        return showChanges;
    }

    /** Switches every {@link FortificationPanel} between total power and power change - driven by the toolbar's "changes" checkbox (see ToolbarPanel). */
    public void setShowChanges(boolean showChanges) {
        this.showChanges = showChanges;
        init();
    }

}

