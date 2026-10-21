package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.FortificationHeatmap;
import org.c2w.domain.LineupBaseline;
import org.c2w.gui.common.GridPanel;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.nio.file.Path;
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
 * fortification is at the same place for both types. The lineup summary is not on the map (see
 * the info panel of {@code org.c2w.gui.stage.ConceptStageView}). In the top row, titans top left and
 * heroes top right, a {@link FortificationHeatmapPanel} shows where the power of the selected type
 * sits (see {@link FortificationHeatmap}).
 *
 * <p>A click on a fortification selects it, a second click clears the selection - see
 * {@link #selectedFortification()} and {@link #addSelectionListener}. A double-click selects
 * it (always - its first click may just have cleared the selection) and asks the
 * {@linkplain #addOpenListener open listeners} to open it for editing.
 */
public class FortificationMapPanel extends GridPanel {

    /** Minimum size of a grid cell - keeps empty rows and columns from collapsing. */
    private static final Dimension MIN_CELL_SIZE = new Dimension(FortificationPanel.PREFERRED_WIDTH, 56);

    /** Row of the heatmap cell of each fortification type: titans top left, heroes top right. */
    static final int HEATMAP_ROW = 0;

    /** True once a heatmap cell occupied by a fortification was logged - logged only once. */
    private static boolean heatmapCellConflictLogged;

    private final AppContext appContext;

    /**
     * What every {@link FortificationPanel} shows (see {@link #setValueMode}): the current power,
     * the change since loading/saving or the change against the live lineup.
     */
    private FortificationValueMode valueMode = FortificationValueMode.POWER;

    /** Id of the selected fortification, null if none is selected. */
    private String selectedId;

    private final List<Consumer<Optional<Fortification>>> selectionListeners = new ArrayList<>();

    private final List<Consumer<Fortification>> openListeners = new ArrayList<>();

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
            public void dirtyStateChanged() {
                // Saving the live lineup may change the live comparison - like the comparison
                // section of the concept stage view, which refreshes here as well.
                if (valueMode == FortificationValueMode.LIVE_COMPARISON) {
                    init();
                }
            }

            @Override
            public void fortificationTypeChanged() {
                setSelectedId(null);
                init();
            }
        });
        // "Apply to live" in the output stage writes the live file without an AppContext event -
        // the live comparison is read fresh whenever the map is shown again.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()
                    && valueMode == FortificationValueMode.LIVE_COMPARISON) {
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

        // Read fresh on every rebuild, so the map is right after the live lineup was maintained.
        Lineup live = valueMode == FortificationValueMode.LIVE_COMPARISON ? liveLineup() : null;
        // One source for the heatmap and the tiles' average power per slot (FortificationValueMode#AVERAGE_POWER).
        FortificationHeatmap heatmap = FortificationHeatmap.of(selectedType, fortificationCatalog, lineup, guild);
        boolean selectionVisible = false;
        for (Fortification fort : fortificationCatalog) {
            if (fort.type() != selectedType) {
                continue;
            }
            int buffPercent = BuffCalculationService.calculateBuffForFortification(
                    fort.id(), lineup, guild, fort);
            int filledSlots = filledSlotsMap.getOrDefault(fort.id(), 0);
            int totalPower = totalPowerMap.getOrDefault(fort.id(), 0);
            // Change against the live lineup, or since the lineup was loaded or last saved - a
            // fortification that was empty back then counts as a baseline of 0 (see
            // AppContext#fortificationDiffFromLoaded).
            LineupBaseline.Diff diff = live != null
                    ? LineupBaseline.forFortification(fort, live, guild).diffFrom(LineupBaseline.forFortification(fort, lineup, guild))
                    : appContext.fortificationDiffFromLoaded(fort.id());
            boolean selected = fort.id().equals(selectedId);
            selectionVisible |= selected;
            setComponentAt(fort.row(), fort.column(), new FortificationPanel(fort, filledSlots, totalPower,
                    averagePower(heatmap, fort), diff.totalPowerDiff(), valueMode, buffPercent,
                    buffPercentDiff(fort, diff), diff.buffMemberCountDiff(), selected, this::toggleSelection,
                    this::open));
        }
        if (!selectionVisible) {
            setSelectedId(null);
        }
        setHeatmap(heatmap, fortificationCatalog);
        // The lineup summary of the selected type is not part of the map: it is shown in the
        // info panel of the concept stage view (see org.c2w.gui.stage.ConceptStageView).
    }

    /**
     * The power per slot of {@code fort}, rounded - exactly the value {@code heatmap} colors by
     * (see {@link FortificationHeatmap.Rating#value()}); 0 if it is not of the heatmap's type.
     */
    static int averagePower(FortificationHeatmap heatmap, Fortification fort) {
        FortificationHeatmap.Rating rating = heatmap.rating(fort.id());
        return rating == null ? 0 : (int) Math.round(rating.value());
    }

    /**
     * Puts {@code heatmap} into the cell of its type (see {@link #heatmapColumn}) - always power per
     * slot, whatever the {@link #valueMode}. Skipped (logged once) if a fortification occupies that cell.
     */
    private void setHeatmap(FortificationHeatmap heatmap, List<Fortification> catalog) {
        FortificationType type = heatmap.type();
        int column = heatmapColumn(type);
        if (componentAt(HEATMAP_ROW, column) != null) {
            if (!heatmapCellConflictLogged) {
                heatmapCellConflictLogged = true;
                Logger.log("Fortification map: cell (" + HEATMAP_ROW + ", " + column
                        + ") is occupied by a fortification - the " + type + " heatmap is not shown");
            }
            return;
        }
        setComponentAt(HEATMAP_ROW, column, new FortificationHeatmapPanel(heatmap, catalog, rows(), columns()));
    }

    /** Column of the heatmap of {@code type} in row {@link #HEATMAP_ROW}: titans left, heroes right. */
    int heatmapColumn(FortificationType type) {
        return type == FortificationType.TITAN ? 0 : columns() - 1;
    }

    /**
     * The lineup the live comparison compares with: the open lineup itself if it is the live
     * lineup (every change is then 0), otherwise the guild's live file - an empty lineup if there
     * is none or it cannot be read (the whole power then shows as a gain).
     */
    private Lineup liveLineup() {
        if (LineupFiles.isOriginal(appContext.lineupFilePath())) {
            return appContext.lineup();
        }
        Path guildFile = appContext.guildFilePath();
        return LineupFiles.loadOriginal(guildFile == null ? null : guildFile.getParent())
                .orElseGet(LineupRepository::createEmptyLineup);
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

    /** {@code listener} is told every fortification that is double-clicked, after it was selected. */
    public void addOpenListener(Consumer<Fortification> listener) {
        openListeners.add(listener);
    }

    /**
     * What a double-click on a fortification does: selects {@code fortification} (no toggling)
     * and then tells the open listeners.
     */
    void open(Fortification fortification) {
        setSelectedId(fortification.id());
        for (Consumer<Fortification> listener : List.copyOf(openListeners)) {
            listener.accept(fortification);
        }
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

    public FortificationValueMode valueMode() {
        return valueMode;
    }

    /**
     * Switches what every {@link FortificationPanel} shows and rebuilds the map - driven by the
     * "fortification values" combo box in the action list of the concept stage view.
     */
    public void setValueMode(FortificationValueMode valueMode) {
        if (valueMode == null) {
            throw new IllegalArgumentException("valueMode must not be null");
        }
        this.valueMode = valueMode;
        init();
    }

}
