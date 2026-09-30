package org.c2w.service;

import org.c2w.infra.Config;

import java.nio.file.Path;

/**
 * Remembers which guild/lineup file was opened last, so the next start
 * reopens it. {@link #CONFIG} (the default of every service) persists to
 * config.properties; tests pass {@link #NONE} instead, so they never touch
 * the real config file.
 */
public interface RecentFiles {

    void guildOpened(Path guildFilePath);

    void lineupOpened(Path lineupFilePath);

    /** Stores both paths in config.properties (see {@link Config}). */
    RecentFiles CONFIG = new RecentFiles() {
        @Override
        public void guildOpened(Path guildFilePath) {
            Config.setLastGuildPath(guildFilePath.toString());
            Config.save();
        }

        @Override
        public void lineupOpened(Path lineupFilePath) {
            Config.setLastLineUpPath(lineupFilePath.toString());
            Config.save();
        }
    };

    /** Remembers nothing. */
    RecentFiles NONE = new RecentFiles() {
        @Override
        public void guildOpened(Path guildFilePath) {
        }

        @Override
        public void lineupOpened(Path lineupFilePath) {
        }
    };
}
