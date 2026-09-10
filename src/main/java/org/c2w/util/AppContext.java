package org.c2w.util;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class AppContext {
    private Guild guild;
    private Path guildFilePath;
    private Lineup lineup;
    private Path lineupFilePath;

    /**
     * Snapshot of totalPower/buffMemberCount PER FORTIFICATION (keyed by
     * {@link Fortification#id()}), for the lineup exactly as it was loaded
     * from {@link #lineupFilePath}, taken by {@link #set(Lineup, Path)} -
     * see {@link LineupBaseline} (added 2026-09-10; changed the same day
     * from one combined value for the whole lineup to one entry per
     * fortification). Deliberately NOT touched by {@link #setLineup} alone,
     * since that is used for in-memory edits to the SAME file (team
     * assignments, algorithm runs, "clear lineup", ...), so this always
     * keeps reflecting what is currently saved on disk. Only contains an
     * entry for fortifications that have at least one team assigned in the
     * loaded lineup. See {@link #loadedFortificationBaselines()}/
     * {@link #fortificationDiffFromLoaded(String)}.
     */
    private Map<String, LineupBaseline> loadedFortificationBaselines = Map.of();


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
    }

    /** File this guild was loaded from / should be saved to. */
    public Path guildFilePath() {
        return guildFilePath;
    }

    /**
     * Replaces both the currently open guild and the file it belongs to, in
     * one call - use this (rather than {@link #setGuild} alone) whenever the
     * guild being set was loaded from a DIFFERENT file than
     * {@link #guildFilePath()} currently holds (e.g.
     * {@code org.tdi.cow2.gui.ToolbarPanel}'s guild combo box switching to
     * another guild folder under workspace/), so the two fields always
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
    }

    /** File this lineup was loaded from / should be saved to. */
    public Path lineupFilePath() {
        return lineupFilePath;
    }

    /**
     * Replaces both the currently open lineup and the file it belongs to, in
     * one call - use this (rather than {@link #setLineup} alone) whenever
     * the lineup being set was loaded from a DIFFERENT file than
     * {@link #lineupFilePath()} currently holds (e.g. {@code org.tdi.cow2.gui.ToolbarPanel}'s
     * lineup combo box switching to another ".lineup" file in the guild
     * folder), so the two fields always describe the same file and never
     * drift apart.
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
        // guild is always set before the first lineup (see C2WApp#main), so
        // it is safe to use here for the buffMemberCount half of each baseline.
        this.loadedFortificationBaselines = computeFortificationBaselines(lineup, guild);
    }

    /**
     * totalPower/buffMemberCount, per fortification id, as they stood at the
     * moment the currently open lineup was (re)loaded from
     * {@link #lineupFilePath()} - see the field Javadoc. Empty (never null)
     * before any lineup has ever been loaded, or for a lineup with no
     * entries.
     */
    public Map<String, LineupBaseline> loadedFortificationBaselines() {
        return loadedFortificationBaselines;
    }

    /**
     * {@link #loadedFortificationBaselines()} for one fortification, or null
     * if that fortification had no team assigned when the lineup was loaded.
     */
    public LineupBaseline loadedFortificationBaseline(String fortificationId) {
        return loadedFortificationBaselines.get(fortificationId);
    }

    /**
     * How far the CURRENT in-memory lineup ({@link #lineup()}) has drifted,
     * for ONE fortification, from what was loaded (see
     * {@link #loadedFortificationBaseline(String)}) - positive values mean
     * the current lineup is now stronger / has more buff members at this
     * fortification than what is saved on disk. Not wired into the GUI yet
     * (planned for later); callers can use this once needed instead of
     * hand-rolling the comparison.
     *
     * @throws IllegalStateException if this fortification had no team
     *         assigned in the loaded lineup (nothing to diff against)
     * @throws IllegalArgumentException if fortificationId is not a known
     *         fortification (see {@link FortificationRepository#findById})
     */
    public LineupBaseline.Diff fortificationDiffFromLoaded(String fortificationId) {
        LineupBaseline loaded = loadedFortificationBaselines.get(fortificationId);
        if (loaded == null) {
            throw new IllegalStateException(
                    "No baseline was loaded for fortification '" + fortificationId + "'");
        }
        Fortification fortification = FortificationRepository.findById(fortificationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fortification id: " + fortificationId));
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
