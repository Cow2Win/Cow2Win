package org.c2w.service;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.LineupBaseline;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The guild and lineup currently open in the application, the files they
 * belong to, and whether either of them has unsaved changes.
 *
 * <p>Observable: every change fires the matching {@link Listener} callback,
 * so views (fortification map, toolbar combo boxes, window title, ...)
 * update themselves instead of every caller having to remember to refresh
 * them. Changes should go through the services in {@code org.c2w.service},
 * which also persist to disk and keep the dirty state right - this class
 * itself only holds state and notifies.
 */
public class AppContext {

    /** Callbacks for changes to an {@link AppContext}; every method defaults to doing nothing. */
    public interface Listener {
        /** The guild and/or its file changed. */
        default void guildChanged() {
        }

        /** The lineup and/or its file changed. */
        default void lineupChanged() {
        }

        /** {@link #isGuildDirty()} and/or {@link #isLineupDirty()} changed. */
        default void dirtyStateChanged() {
        }
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** Runs the listener notifications - directly by default, see {@link #setEventDispatcher}. */
    private volatile Consumer<Runnable> eventDispatcher = Runnable::run;
    private final Catalog catalog;
    private final JournalService journal;

    private Guild guild;
    private Path guildFilePath;
    private Lineup lineup;
    private Path lineupFilePath;
    private boolean guildDirty;
    private boolean lineupDirty;

    /**
     * Snapshot of totalPower/buffMemberCount PER FORTIFICATION (keyed by
     * {@link Fortification#id()}), for the lineup exactly as it stands in
     * {@link #lineupFilePath} - i.e. as it was loaded or last saved - taken by
     * {@link #set(Lineup, Path)} / {@link #switchTo} on load and retaken by
     * {@link #markLineupSaved()} after every successful save - see
     * {@link LineupBaseline}. Deliberately NOT touched by {@link #setLineup}
     * alone, since that is used for in-memory edits to the SAME file (team
     * assignments, algorithm runs, "clear lineup", ...). Only contains an
     * entry for fortifications that have at least one team assigned in the
     * loaded/saved lineup. See
     * {@link #loadedFortificationBaselines()}/
     * {@link #fortificationDiffFromLoaded(String)}.
     */
    private Map<String, LineupBaseline> loadedFortificationBaselines = Map.of();

    public AppContext(Catalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        this.catalog = catalog;
        this.journal = new JournalService(this);
    }

    /** The hero/titan/pet/war flag catalogs of the workspace this app was started with. */
    public Catalog catalog() {
        return catalog;
    }

    /**
     * The Weltenschlacht journal of the open guild (opened lazily, closed when
     * another guild is opened - see {@link JournalService}).
     */
    public JournalService journal() {
        return journal;
    }

    /** The guild currently open in the application. */
    public Guild guild() {
        return guild;
    }

    /** Replaces the currently open guild (e.g. after the user edited and saved it). */
    public void setGuild(Guild guild) {
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        this.guild = guild;
        fireGuildChanged();
    }

    /** File this guild was loaded from / should be saved to. */
    public Path guildFilePath() {
        return guildFilePath;
    }

    /**
     * Replaces both the currently open guild and the file it belongs to, in
     * one call - use this (rather than {@link #setGuild} alone) whenever the
     * guild being set was loaded from a DIFFERENT file than
     * {@link #guildFilePath()} currently holds, so the two fields always
     * describe the same file and never drift apart.
     */
    public void set(Guild guild, Path guildFilePath) {
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        if (guildFilePath == null) {
            throw new IllegalArgumentException("guildFilePath must not be null");
        }
        this.guild = guild;
        this.guildFilePath = guildFilePath;
        fireGuildChanged();
    }

    /** The lineup currently open in the application. */
    public Lineup lineup() {
        return lineup;
    }

    /** Replaces the currently open lineup (e.g. after a new assignment run). */
    public void setLineup(Lineup lineup) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        this.lineup = lineup;
        fireLineupChanged();
    }

    /** File this lineup was loaded from / should be saved to. */
    public Path lineupFilePath() {
        return lineupFilePath;
    }

    /**
     * Replaces both the currently open lineup and the file it belongs to, in
     * one call - use this (rather than {@link #setLineup} alone) whenever
     * the lineup being set was loaded from a DIFFERENT file than
     * {@link #lineupFilePath()} currently holds, so the two fields always
     * describe the same file and never drift apart. Also retakes
     * {@link #loadedFortificationBaselines()}.
     */
    public void set(Lineup lineup, Path lineupFilePath) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        if (lineupFilePath == null) {
            throw new IllegalArgumentException("lineupFilePath must not be null");
        }
        this.lineup = lineup;
        this.lineupFilePath = lineupFilePath;
        // guild is always set before the first lineup (see WorkspaceBootstrap#start), so
        // it is safe to use here for the buffMemberCount half of each baseline.
        this.loadedFortificationBaselines = computeFortificationBaselines(lineup, guild);
        fireLineupChanged();
    }

    /**
     * Replaces guild AND lineup (and both files) in one step - for switching
     * to another guild, so listeners are only notified once everything is
     * consistent again, instead of seeing the new guild together with the
     * previous guild's lineup in between.
     */
    public void switchTo(Guild guild, Path guildFilePath, Lineup lineup, Path lineupFilePath) {
        if (guild == null || guildFilePath == null) {
            throw new IllegalArgumentException("guild and guildFilePath must not be null");
        }
        if (lineup == null || lineupFilePath == null) {
            throw new IllegalArgumentException("lineup and lineupFilePath must not be null");
        }
        this.guild = guild;
        this.guildFilePath = guildFilePath;
        this.lineup = lineup;
        this.lineupFilePath = lineupFilePath;
        this.loadedFortificationBaselines = computeFortificationBaselines(lineup, guild);
        fireGuildChanged();
        fireLineupChanged();
    }

    // --- dirty state ---

    /**
     * True if the guild has changes that are not saved yet - either in
     * {@link #guild()} itself or still pending in an open editor (e.g. a
     * power edit in a value overview dialog).
     */
    public boolean isGuildDirty() {
        return guildDirty;
    }

    /** True if {@link #lineup()} has changes that are not saved to {@link #lineupFilePath()} yet. */
    public boolean isLineupDirty() {
        return lineupDirty;
    }

    /** True if the guild or the lineup has unsaved changes. */
    public boolean hasUnsavedChanges() {
        return guildDirty || lineupDirty;
    }

    public void setGuildDirty(boolean guildDirty) {
        if (this.guildDirty != guildDirty) {
            this.guildDirty = guildDirty;
            fireDirtyStateChanged();
        }
    }

    public void setLineupDirty(boolean lineupDirty) {
        if (this.lineupDirty != lineupDirty) {
            this.lineupDirty = lineupDirty;
            fireDirtyStateChanged();
        }
    }

    /**
     * Marks the open lineup as saved to {@link #lineupFilePath()}: retakes
     * {@link #loadedFortificationBaselines()} from the current
     * {@link #lineup()}/{@link #guild()} (so every fortification diffs to 0
     * again), clears {@link #isLineupDirty()} and notifies listeners - the
     * fortification map via {@link Listener#lineupChanged()}, the dirty state
     * via {@link Listener#dirtyStateChanged()} (only if it was dirty). Call
     * this only after the lineup was written successfully.
     */
    public void markLineupSaved() {
        this.loadedFortificationBaselines = computeFortificationBaselines(lineup, guild);
        fireLineupChanged();
        setLineupDirty(false);
    }

    // --- listeners ---

    public void addListener(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * How listener notifications are run. The GUI sets one that runs them on the
     * Swing event thread (and waits), so a change made in a background task (e.g.
     * the journal import in a {@code SwingWorker}) never updates Swing components
     * from another thread. Default: directly, in the calling thread (tests, services).
     */
    public void setEventDispatcher(Consumer<Runnable> dispatcher) {
        this.eventDispatcher = dispatcher == null ? Runnable::run : dispatcher;
    }

    private void fireGuildChanged() {
        eventDispatcher.accept(() -> listeners.forEach(Listener::guildChanged));
    }

    private void fireLineupChanged() {
        eventDispatcher.accept(() -> listeners.forEach(Listener::lineupChanged));
    }

    private void fireDirtyStateChanged() {
        eventDispatcher.accept(() -> listeners.forEach(Listener::dirtyStateChanged));
    }

    // --- baselines ---

    /**
     * totalPower/buffMemberCount, per fortification id, as they stood at the
     * moment the currently open lineup was (re)loaded from or last saved to
     * {@link #lineupFilePath()} - see the field Javadoc. Empty (never null)
     * before any lineup has ever been loaded, or for a lineup with no
     * entries.
     */
    public Map<String, LineupBaseline> loadedFortificationBaselines() {
        return loadedFortificationBaselines;
    }

    /**
     * {@link #loadedFortificationBaselines()} for one fortification, or null
     * if that fortification had no team assigned when the lineup was loaded
     * or last saved.
     */
    public LineupBaseline loadedFortificationBaseline(String fortificationId) {
        return loadedFortificationBaselines.get(fortificationId);
    }

    /**
     * How far the CURRENT in-memory lineup ({@link #lineup()}) has drifted,
     * for ONE fortification, from what was loaded or last saved (see
     * {@link #loadedFortificationBaseline(String)}) - positive values mean
     * the current lineup is now stronger / has more buff members at this
     * fortification than what is saved on disk. Used by the fortification
     * map's "Changes" view and tooltips. A fortification without a baseline
     * (no team assigned when loaded/saved) counts as a baseline of (0, 0), so
     * anything assigned there now shows up as a full gain.
     *
     * @throws IllegalArgumentException if fortificationId is not a known
     *         fortification (see {@link FortificationRepository#findById})
     */
    public LineupBaseline.Diff fortificationDiffFromLoaded(String fortificationId) {
        Fortification fortification = FortificationRepository.findById(fortificationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fortification id: " + fortificationId));
        LineupBaseline loaded = loadedFortificationBaselines.getOrDefault(fortificationId, new LineupBaseline(0, 0));
        LineupBaseline current = LineupBaseline.forFortification(fortification, lineup, guild);
        return loaded.diffFrom(current);
    }

    /** Builds {@link #loadedFortificationBaselines}: one entry per distinct fortificationId occurring in the lineup's entries, skipping any id no longer in the catalog. */
    private static Map<String, LineupBaseline> computeFortificationBaselines(Lineup lineup, Guild guild) {
        Map<String, LineupBaseline> result = new LinkedHashMap<>();
        for (Lineup.Entry entry : lineup.entries()) {
            String fortificationId = entry.fortificationId();
            if (result.containsKey(fortificationId)) {
                continue;
            }
            FortificationRepository.findById(fortificationId).ifPresent(fortification ->
                    result.put(fortificationId, LineupBaseline.forFortification(fortification, lineup, guild)));
        }
        return Map.copyOf(result);
    }

    public Lineup copyLineup() {
        return new Lineup(lineup.guildId(), lineup.guildName(), lineup.algorithmName(), lineup.createdAt(), lineup.entries());
    }
}
