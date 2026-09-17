package org.c2w.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the on-disk locations of Cow2Win's persistent data - guilds,
 * lineups, {@link Config}'s config.properties, {@link Logger}'s log file,
 * and (by default) backups - all gathered under one hidden umbrella folder,
 * {@link #ROOT} ({@code <user.home>/.cow2Win}), with {@link #DIR} (the
 * "workspace" proper: guilds/lineups/config.properties/log file) and
 * {@link #DEFAULT_BACKUP_DIR} as two sibling subfolders underneath it.
 *
 * <p>Anchored to the user's home directory (rather than the app's working
 * directory) so every install/update on this machine always finds the same
 * data automatically, with nothing to recreate or copy by hand.
 *
 * <p>This class only decides WHERE things live; it does not create {@link
 * #DIR} or {@link #DEFAULT_BACKUP_DIR} itself - the various {@code
 * Files.createDirectories} calls in {@link Config}, {@link Logger} and the
 * guild/lineup repositories do that on demand.
 */
public final class Workspace {

    /** {@code <user.home>/.cow2Win} - umbrella folder holding both {@link #DIR} and {@link #DEFAULT_BACKUP_DIR}. */
    public static final Path ROOT = Paths.get(System.getProperty("user.home"), ".cow2Win");

    /** {@code <user.home>/.cow2Win/workspace} - guilds, lineups, config.properties, log file. */
    public static final Path DIR = ROOT.resolve("workspace");

    /** {@code <user.home>/.cow2Win/backup} - default backup directory, "next to" {@link #DIR} under {@link #ROOT} - see {@link Config#getBackupDir()}. */
    public static final Path DEFAULT_BACKUP_DIR = ROOT.resolve("backup");

    private Workspace() {
    }
}
