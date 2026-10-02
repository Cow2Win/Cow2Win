package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.LineupBaseline;
import org.c2w.gui.common.GridPanel;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FortificationMapPanel extends GridPanel {

    private final AppContext appContext;

    private boolean showHeroFortifications = true;
    private boolean showTitanFortifications = true;

    /** True while the "Changes" checkbox in the toolbar (see ToolbarPanel) is selected - then every {@link FortificationPanel} shows its power change against the lineup as loaded or last saved instead of its current total power (see AppContext#fortificationDiffFromLoaded). */
    private boolean showChanges = false;

    /**
     * Always shows {@code appContext}'s current guild/lineup - rebuilt
     * automatically whenever either of them changes (see
     * {@link AppContext.Listener}), so nobody has to refresh this panel
     * by hand.
     */
    public FortificationMapPanel(AppContext appContext){
        super(8,5);

        this.appContext = appContext;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        init();
        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                init();
            }

            @Override
            public void lineupChanged() {
                init();
            }
        });
    }


    private void init(){
        Lineup lineup = appContext.lineup();
        Guild guild = appContext.guild();
        // Transparent: the background image is painted once,
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
            // Change since the lineup was loaded or last saved - a fortification that was empty
            // back then counts as a baseline of 0 (see AppContext#fortificationDiffFromLoaded).
            LineupBaseline.Diff diff = appContext.fortificationDiffFromLoaded(fort.id());
            setComponentAt(fort.row(), fort.column(), new FortificationPanel(fort, filledSlots, totalPower,
                    diff.totalPowerDiff(), showChanges, buffPercent, buffPercentDiff(fort, diff),
                    diff.buffMemberCountDiff(), appContext));
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
     * Buff percentage change of {@code fortification} for {@code diff}: the
     * buff-member change times {@code buff.bonusPercent()}, the same way
     * {@link BuffCalculationService#calculateBuffForFortification} derives the
     * percentage itself. 0 for a fortification without a buff.
     */
    static int buffPercentDiff(Fortification fortification, LineupBaseline.Diff diff) {
        if (fortification.buff() == null) {
            return 0;
        }
        return (int) (diff.buffMemberCountDiff() * fortification.buff().bonusPercent());
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

