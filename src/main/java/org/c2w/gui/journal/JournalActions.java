package org.c2w.gui.journal;

import org.c2w.data.journal.db.JournalDatabase;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.gui.action.ActionId;
import org.c2w.gui.action.AppAction;
import org.c2w.gui.action.MainActions;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.JournalMaintenanceService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The "Weltenschlacht Journal" actions and their windows - kept out of
 * {@code Cow2Frame} and {@code MainMenuBar}: choose battle logs and run the
 * import assistant, the (single) battle list, battle details (several), "build
 * teams from logs", "update teams (sync)", and the
 * maintenance windows for players, seasons and name mappings. After every change
 * of the journal all open journal windows reload. All entries are disabled while
 * no guild is open.
 */
public final class JournalActions {

    private final JFrame frame;
    private final AppContext context;
    private final GuildService guildService;
    private final Predicate<String> guildSwitcher;
    private final JournalOperations operations;
    private final List<JournalBattleDetailDialog> details = new ArrayList<>();
    private JournalBattleListDialog battleList;
    private JournalPlayersDialog players;
    private JournalSeasonsDialog seasons;
    private JournalNameMappingsDialog nameMappings;

    /**
     * @param guildSwitcher asks about unsaved changes and switches to the guild in a folder;
     *                      true if it switched (the main window's usual way)
     */
    public JournalActions(JFrame frame, AppContext context, GuildService guildService, Predicate<String> guildSwitcher) {
        this.frame = frame;
        this.context = context;
        this.guildService = guildService;
        this.guildSwitcher = guildSwitcher;
        this.operations = new JournalOperations(new JournalMaintenanceService(context), this::journalChanged);
    }

    /**
     * Registers the journal actions ("Import …", "Battle list …", the maintenance windows, ...)
     * in {@code actions} - the menu itself is built by {@code MainMenuBar}. They are only
     * enabled while a guild is open.
     */
    public void registerActions(MainActions actions) {
        List<AppAction> journalActions = List.of(
                actions.register(new AppAction(ActionId.JOURNAL_IMPORT, this::importWithChooser)),
                actions.register(new AppAction(ActionId.JOURNAL_BATTLES, this::openBattleList)),
                actions.register(new AppAction(ActionId.JOURNAL_BUILD_TEAMS, this::openTeamBuilder)),
                actions.register(new AppAction(ActionId.JOURNAL_SYNC, this::openSync)),
                actions.register(new AppAction(ActionId.JOURNAL_PLAYERS, this::openPlayers)),
                actions.register(new AppAction(ActionId.JOURNAL_SEASONS, this::openSeasons)),
                actions.register(new AppAction(ActionId.JOURNAL_NAME_MAPPINGS, this::openNameMappings)));
        Runnable update = () -> {
            boolean guildOpen = context.guild() != null && context.guildFilePath() != null;
            journalActions.forEach(action -> action.setEnabled(guildOpen));
        };
        update.run();
        context.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                update.run();
            }
        });
    }

    /** Lets the user choose battle logs and imports them. */
    public void importWithChooser() {
        JFileChooser chooser = new JFileChooser(startDirectory().toFile());
        chooser.setDialogTitle(JournalTexts.text("journal.fileChooser.title"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setAcceptAllFileFilterUsed(true);
        FileNameExtensionFilter csv = new FileNameExtensionFilter(JournalTexts.text("journal.fileChooser.filter"), "csv");
        chooser.addChoosableFileFilter(csv);
        chooser.setFileFilter(csv);
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<Path> files = Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList();
        if (files.isEmpty()) {
            return;
        }
        Path folder = files.get(0).toAbsolutePath().getParent();
        if (folder != null) {
            Config.setLastJournalImportDir(folder.toString());
            Config.save();
        }
        importFiles(files);
    }

    /** Runs the import assistant for {@code files} (empty: let the user choose them first). */
    public void importFiles(List<Path> files) {
        if (files.isEmpty()) {
            importWithChooser();
            return;
        }
        if (context.guild() == null) {
            return;
        }
        JournalImportDialog.open(frame, context, guildService, new JournalImportDialog.Host() {
            @Override
            public boolean switchToGuild(String folderName) {
                return guildSwitcher.test(folderName);
            }

            @Override
            public void openBattleList() {
                JournalActions.this.openBattleList();
            }

            @Override
            public void importFinished() {
                journalChanged();
            }

            @Override
            public void openTeamBuilder() {
                JournalActions.this.openTeamBuilder();
            }

            @Override
            public void openSync() {
                JournalActions.this.openSync();
            }
        }, files);
    }

    /** "Update teams (sync)" for the open guild (modal). */
    public void openSync() {
        if (context.guild() == null) {
            return;
        }
        JournalSyncDialog.open(frame, context, guildService, this::openTeamBuilder);
    }

    /** "Build teams from logs" for the open guild (modal). */
    public void openTeamBuilder() {
        if (context.guild() == null) {
            return;
        }
        JournalTeamBuilderDialog.open(frame, context, guildService, this::journalChanged);
    }

    /** Shows the battle list (one window, brought to front if already open). */
    public void openBattleList() {
        if (battleList == null || !battleList.isDisplayable()) {
            battleList = new JournalBattleListDialog(frame, context, new JournalBattleListDialog.Host() {
                @Override
                public void importFiles(List<Path> files) {
                    JournalActions.this.importFiles(files);
                }

                @Override
                public void openDetail(int battleId) {
                    JournalActions.this.openDetail(battleId);
                }

                @Override
                public void openPlayers() {
                    JournalActions.this.openPlayers();
                }

                @Override
                public void openSeasons() {
                    JournalActions.this.openSeasons();
                }

                @Override
                public void openNameMappings() {
                    JournalActions.this.openNameMappings();
                }

                @Override
                public JournalOperations operations() {
                    return operations;
                }
            });
        } else {
            battleList.reload();
        }
        bringToFront(battleList);
    }

    /** Opens the detail of a battle - one window per battle, an open one is brought to front. */
    public void openDetail(int battleId) {
        details.removeIf(d -> !d.isDisplayable());
        Optional<JournalBattleDetailDialog> open = details.stream().filter(d -> d.battleId() == battleId).findFirst();
        if (open.isPresent()) {
            bringToFront(open.get());
            return;
        }
        Window owner = battleList != null && battleList.isDisplayable() ? battleList : frame;
        JournalBattleDetailDialog detail = new JournalBattleDetailDialog(owner, context,
                new JournalBattleDetailDialog.Host() {
                    @Override
                    public JournalOperations operations() {
                        return operations;
                    }

                    @Override
                    public void importWithChooser() {
                        JournalActions.this.importWithChooser();
                    }
                }, battleId);
        details.add(detail);
        bringToFront(detail);
    }

    /** Shows the player assignments (one window). */
    public void openPlayers() {
        if (players == null || !players.isDisplayable()) {
            players = new JournalPlayersDialog(frame, context, operations.service(), this::journalChanged);
        } else {
            players.reload();
        }
        bringToFront(players);
    }

    /** Shows the seasons (one window). */
    public void openSeasons() {
        if (seasons == null || !seasons.isDisplayable()) {
            seasons = new JournalSeasonsDialog(frame, context, operations.service(), this::journalChanged);
        } else {
            seasons.reload();
        }
        bringToFront(seasons);
    }

    /** Shows the name mappings (one window). */
    public void openNameMappings() {
        if (nameMappings == null || !nameMappings.isDisplayable()) {
            nameMappings = new JournalNameMappingsDialog(frame, context, operations);
        } else {
            nameMappings.reload();
        }
        bringToFront(nameMappings);
    }

    /** The journal changed (import, delete, season, assignment, parse again): every open journal window reloads. */
    public void journalChanged() {
        if (battleList != null && battleList.isDisplayable()) {
            battleList.reload();
        }
        details.removeIf(d -> !d.isDisplayable());
        List.copyOf(details).forEach(JournalBattleDetailDialog::reload);
        if (players != null && players.isDisplayable()) {
            players.reload();
        }
        if (seasons != null && seasons.isDisplayable()) {
            seasons.reload();
        }
        if (nameMappings != null && nameMappings.isDisplayable()) {
            nameMappings.reload();
        }
    }

    private static void bringToFront(Window window) {
        window.setVisible(true);
        window.toFront();
    }

    /**
     * The addition to the "delete guild" confirmation for the guild in {@code folderName}:
     * mentions the Weltenschlacht journal (with its number of battles, if it can be read)
     * only if the guild has one. Never creates a journal file.
     */
    public String removeGuildJournalNote(String folderName) {
        return removeGuildJournalNote(context, guildService, folderName);
    }

    static String removeGuildJournalNote(AppContext context, GuildService guildService, String folderName) {
        Path guildDir = guildService.guildDir(folderName);
        if (!JournalDatabase.exists(guildDir)) {
            return "";
        }
        Integer battles = null;
        if (context.guildFilePath() != null
                && Objects.equals(context.guildFilePath().getParent().toAbsolutePath().normalize(),
                guildDir.toAbsolutePath().normalize())) {
            try {
                Optional<JournalRepository> repo = context.journal().repository(false);
                if (repo.isPresent()) {
                    battles = repo.get().countAll().battles();
                }
            } catch (JournalException e) {
                Logger.logException("Could not count the battles of the journal", e);
            }
        }
        return JournalTexts.removeGuildJournalNote(true, battles);
    }

    /** Last import folder, else the user's Downloads folder, else the home folder. */
    static Path startDirectory() {
        String last = Config.getLastJournalImportDir();
        if (!last.isBlank() && Files.isDirectory(Paths.get(last))) {
            return Paths.get(last);
        }
        Path home = Paths.get(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : home;
    }
}
