package org.c2w.util;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

import java.nio.file.Path;

public class AppContext {
    private Guild guild;
    private Path guildFilePath;
    private Lineup lineup;
    private Path lineupFilePath;


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
    }

    public Lineup copyLineup() {
        return new Lineup(lineup.guildId(), lineup.guildName(), lineup.algorithmName(), lineup.createdAt(), lineup.entries());
    }
}
