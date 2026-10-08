package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.BuffCalculationService;
import org.c2w.domain.LineupBaseline;
import org.c2w.gui.common.GridPanel;
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
 * the info panel of {@code org.c2w.gui.stage.ConceptStageView}).
 *
 * <p>A click on a fortification selects it, a second click clears the selection - see
 * {@link #selectedFortification()} and {@link #addSelectionListener}.
 */
public class FortificationMapPanel extends GridPanel {

    /** Minimum size of a grid cell - keeps empty rows and columns from collapsing. */
    private static final Dimension MIN_CELL_SIZE = new Dimension(FortificationPanel.PREFERRED_WIDTH, 56);

    private final AppContext appContext;

    /**
     * What every {@link FortificationPanel} shows (see {@link #setValueMode}): the current power,
     * the change since loading/saving or the change against the live lineup.
     */
    private FortificationValueMode valueMode = FortificationValueMode.POWER;

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
                    diff.totalPowerDiff(), valueMode, buffPercent, buffPercentDiff(fort, diff),
                    diff.buffMemberCountDiff(), selected, this::toggleSelection));
        }
        if (!selectionVisible) {
            setSelectedId(null);
        }
        // The lineup summary of the selected type is not part of the map: it is shown in the
        // info panel of the concept stage view (see org.c2w.gui.stage.ConceptStageView).
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
