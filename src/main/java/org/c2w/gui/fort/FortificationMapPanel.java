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
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The map of the fortifications of the {@linkplain AppContext#fortificationType() selected
 * fortification type}, one {@link FortificationPanel} each; the cells of the other type stay
 * empty, and the grid keeps every row and column (see {@link GridPanel#setUniformCells}) so a
 * fortification is at the same place for both types. Only the selected type's lineup summary
 * is shown (heroes top left, titans top right).
 *
 * <p>A click on a fortification selects it, a second click clears the selection - see
 * {@link #selectedFortification()} and {@link #addSelectionListener}.
 */
public class FortificationMapPanel extends GridPanel {

    /** Minimum size of a grid cell - keeps empty rows and columns from collapsing. */
    private static final Dimension MIN_CELL_SIZE = new Dimension(FortificationPanel.PREFERRED_WIDTH, 56);

    private final AppContext appContext;

    /** True while the change view is on (see {@link #setShowChanges}; currently not offered in the UI) - then every {@link FortificationPanel} shows its power change against the lineup as loaded or last saved instead of its current total power (see AppContext#fortificationDiffFromLoaded). */
    private boolean showChanges = false;

    /** Id of the selected fortification, null if none is selected. */
    private String selectedId;

    private final List<Consumer<Optional<Fortification>>> selectionListeners = new ArrayList<>();

    /**
     * Always shows {@code appContext}'s current guild/lineup and fortification type - rebuilt
     * automatically whenever one of them changes (see {@link AppContext.Listener}), so nobody
     * has to refresh this panel by hand.
     */
    public FortificationMapPanel(AppContext appContext){
        super(8,5);

        this.appContext = appContext;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setUniformCells(MIN_CELL_SIZE);
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

            @Override
            public void fortificationTypeChanged() {
                setSelectedId(null);
                init();
            }
        });
    }


    private void init(){
        Lineup lineup = appContext.lineup();
        Guild guild = appContext.guild();
        FortificationType selectedType = appContext.fortificationType();
        // Transparent: the background image is painted once,
        // higher up in the component hierarchy, by Cow2Frame's content pane -
        // see Cow2Frame.BackgroundPanel. Staying non-opaque here (and in every
        // panel/scroll pane between this one and that content pane) is what lets
        // it show through instead of being painted over by this panel's own
        // background.
        setOpaque(false);
        clearAllCells();
        List<Fortification> fortificationCatalog = FortificationRepository.findAll();
        Map<String, Integer> filledSlotsMap = new HashMap<>();
        Map<String, Integer> totalPowerMap = new HashMap<>();


        for (Lineup.Entry entry : lineup.entries()) {
            String fortId = entry.fortificationId();
            filledSlotsMap.put(fortId, filledSlotsMap.getOrDefault(fortId, 0) + 1);
            totalPowerMap.put(fortId, totalPowerMap.getOrDefault(fortId, 0) + BuffCalculationService.totalPowerOf(entry, guild));
        }

        boolean selectionVisible = false;
        for (Fortification fort : fortificationCatalog) {
            if (fort.type() != selectedType) {
                continue;
            }
            int buffPercent = BuffCalculationService.calculateBuffForFortification(
                    fort.id(), lineup, guild, fort);
            int filledSlots = filledSlotsMap.getOrDefault(fort.id(), 0);
            int totalPower = totalPowerMap.getOrDefault(fort.id(), 0);
            // Change since the lineup was loaded or last saved - a fortification that was empty
            // back then counts as a baseline of 0 (see AppContext#fortificationDiffFromLoaded).
            LineupBaseline.Diff diff = appContext.fortificationDiffFromLoaded(fort.id());
            boolean selected = fort.id().equals(selectedId);
            selectionVisible |= selected;
            setComponentAt(fort.row(), fort.column(), new FortificationPanel(fort, filledSlots, totalPower,
                    diff.totalPowerDiff(), showChanges, buffPercent, buffPercentDiff(fort, diff),
                    diff.buffMemberCountDiff(), selected, this::toggleSelection));
        }
        if (!selectionVisible) {
            setSelectedId(null);
        }

        // Only the selected fortification type's summary: heroes top left, titans top right (same row).
        if (selectedType == FortificationType.HERO) {
            setComponentAt(0, 0, withUniformWidth(new HeroLineupSummaryPanel(lineup, guild)));
        } else {
            setComponentAt(0, columns() - 1, withUniformWidth(new TitanLineupSummaryPanel(lineup, guild)));
        }
    }

    /**
     * Gives {@code component} the same preferred width as a {@link FortificationPanel}, so a
     * wider summary does not widen its column - every column then has the same width for
     * both fortification types.
     */
    private static JComponent withUniformWidth(JComponent component) {
        component.setPreferredSize(new Dimension(FortificationPanel.PREFERRED_WIDTH,
                component.getPreferredSize().height));
        return component;
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

    /** The selected fortification, empty if none is selected. */
    public Optional<Fortification> selectedFortification() {
        if (selectedId == null) {
            return Optional.empty();
        }
        return FortificationRepository.findAll().stream()
                .filter(fort -> fort.id().equals(selectedId))
                .findFirst();
    }

    /** {@code listener} is told the new selection whenever it changes (empty when it is cleared). */
    public void addSelectionListener(Consumer<Optional<Fortification>> listener) {
        selectionListeners.add(listener);
    }

    /**
     * What a click on a fortification does: selects {@code fortification}, or clears the
     * selection if it is already the selected one.
     */
    void toggleSelection(Fortification fortification) {
        setSelectedId(fortification.id().equals(selectedId) ? null : fortification.id());
    }

    private void setSelectedId(String id) {
        if (Objects.equals(selectedId, id)) {
            return;
        }
        selectedId = id;
        for (Component component : getComponents()) {
            if (component instanceof FortificationPanel panel) {
                panel.setSelected(panel.fortification().id().equals(id));
            }
        }
        Optional<Fortification> selection = selectedFortification();
        for (Consumer<Optional<Fortification>> listener : List.copyOf(selectionListeners)) {
            listener.accept(selection);
        }
    }

    public boolean isShowChanges() {
        return showChanges;
    }

    /** Switches every {@link FortificationPanel} between total power and power change - currently not called: the "changes" checkbox was removed from the action bar, the change view has no switch in the UI yet. */
    public void setShowChanges(boolean showChanges) {
        this.showChanges = showChanges;
        init();
    }

}
