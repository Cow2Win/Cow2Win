package org.c2w.gui;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.gui.action.Stage;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.DataStatus;
import org.c2w.service.DataStatus.Area;
import org.c2w.service.DataStatus.Level;
import org.c2w.service.DataStatus.StageResult;
import org.c2w.service.DataStatusService;
import org.c2w.service.DataStatusService.FileTimes;
import org.c2w.service.DataStatusService.StaleCheck;
import org.c2w.service.GuildService;
import org.c2w.service.JournalSyncService;
import org.c2w.service.journal.SyncPlan;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Evaluates the {@link DataStatus} of the open guild ({@link DataStatusService}) and hands it
 * out: traffic light and short text of every {@link ProcessBar} tile, the {@link StatusBar},
 * the "next step → {stage}" button, and the {@linkplain #addListener listeners} (e.g. the
 * member table of the input stage).
 *
 * <p>Every trigger ({@link #requestUpdate()}: guild, lineup, dirty state, fortification type,
 * stage switch, settings, journal, CowScores) restarts a 200 ms timer, so several events in a
 * row lead to one evaluation. The file accesses (journal, Original lineup, file times) run in a
 * {@link SwingWorker}; errors leave the value out and go to the log, never into a dialog.
 */
public final class DataStatusController {

    private static final String KEY_NEXT_STEP_TO = "processBar.nextStepTo";
    private static final String KEY_NEWEST_DEFENSE = "statusBar.newestDefense";
    private static final String KEY_STALE_JOURNAL = "statusBar.staleJournal";
    private static final String KEY_STALE_AGE = "statusBar.staleAge";
    private static final String KEY_LINEUP = "statusBar.lineup";

    /** Events within this time are evaluated together. */
    static final int DEBOUNCE_MILLIS = 200;

    private final AppContext context;
    private final GuildService guildService;
    private final ProcessBar processBar;
    private final StatusBar statusBar;
    private final Consumer<Stage> stageSwitcher;
    private final Timer timer;
    private final List<Consumer<DataStatus>> listeners = new ArrayList<>();
    private final NextStepAction nextStepAction = new NextStepAction();

    /** Counts the started evaluations; a worker whose number is no longer the latest is dropped. */
    private int generation;
    private DataStatus current;

    /**
     * @param stageSwitcher switches the main window to a stage (like a click on its tile)
     */
    public DataStatusController(AppContext context, GuildService guildService, ProcessBar processBar,
                                StatusBar statusBar, Consumer<Stage> stageSwitcher) {
        this.context = context;
        this.guildService = guildService;
        this.processBar = processBar;
        this.statusBar = statusBar;
        this.stageSwitcher = stageSwitcher;
        this.timer = new Timer(DEBOUNCE_MILLIS, e -> evaluate());
        timer.setRepeats(false);
        context.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                requestUpdate();
            }

            @Override
            public void lineupChanged() {
                requestUpdate();
            }

            @Override
            public void dirtyStateChanged() {
                requestUpdate();
            }

            @Override
            public void fortificationTypeChanged() {
                requestUpdate();
            }
        });
    }

    /** Evaluates again in {@link #DEBOUNCE_MILLIS} ms - restarts the timer if it is already waiting. */
    public void requestUpdate() {
        timer.restart();
    }

    /** {@code listener} gets every new data status (on the Swing thread). */
    public void addListener(Consumer<DataStatus> listener) {
        listeners.add(listener);
    }

    /** The "next step" action - for tests. */
    NextStepAction nextStepAction() {
        return nextStepAction;
    }

    /** The last evaluated data status, null before the first evaluation. */
    public DataStatus current() {
        return current;
    }

    /** Starts an evaluation: reads the files in the background, then computes and shows the status. */
    private void evaluate() {
        int myGeneration = ++generation;
        Snapshot snapshot = new Snapshot(context.guild(), context.guildFilePath(), context.lineup(),
                context.lineupFilePath(), context.isLineupDirty(), context.fortificationType(),
                new StaleCheck(Config.isStaleCheckEnabled(), Config.getStaleAfterDays()));
        if (snapshot.guild() == null) {
            apply(myGeneration, snapshot, FileData.EMPTY);
            return;
        }
        Catalog catalog = context.catalog();
        new SwingWorker<FileData, Void>() {
            @Override
            protected FileData doInBackground() {
                return readFiles(snapshot, catalog);
            }

            @Override
            protected void done() {
                FileData files;
                try {
                    files = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    Logger.logException("Data status: could not read the files", e.getCause());
                    files = FileData.EMPTY;
                }
                apply(myGeneration, snapshot, files);
            }
        }.execute();
    }

    /** Off the Swing thread: the journal's sync plan, the Original lineup and the file times. */
    private FileData readFiles(Snapshot snapshot, Catalog catalog) {
        SyncPlan plan = null;
        try {
            plan = new JournalSyncService(context, guildService).prepare();
        } catch (Exception e) {
            Logger.logException("Data status: could not read the journal", e);
        }
        Lineup original = null;
        Path guildDir = snapshot.guildFilePath() == null ? null : snapshot.guildFilePath().getParent();
        if (guildDir != null) {
            Path originalPath = LineupFiles.originalPathFor(guildDir);
            if (Files.exists(originalPath)) {
                try {
                    original = LineupRepository.load(originalPath);
                } catch (IOException e) {
                    Logger.logException("Data status: could not load the Original lineup " + originalPath, e);
                }
            }
        }
        List<Instant> cowScoreFiles = new ArrayList<>();
        if (catalog != null) {
            cowScoreFiles.add(lastModified(catalog.heroes().cowScoreFile()));
            cowScoreFiles.add(lastModified(catalog.titans().cowScoreFile()));
            cowScoreFiles.add(lastModified(catalog.pets().cowScoreFile()));
            cowScoreFiles.add(lastModified(catalog.warFlags().cowScoreFile()));
        }
        // Changed CowScore bonuses in the settings count like a changed CowScore file.
        cowScoreFiles.add(Config.getCowScoreBonusesChangedAt());
        FileTimes times = new FileTimes(lastModified(snapshot.guildFilePath()),
                lastModified(snapshot.lineupFilePath()), cowScoreFiles);
        return new FileData(plan, original, times);
    }

    private static Instant lastModified(Path file) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            Logger.logException("Data status: could not read the time of " + file, e);
            return null;
        }
    }

    /** On the Swing thread: computes the status (unless a newer evaluation was started) and shows it. */
    private void apply(int myGeneration, Snapshot snapshot, FileData files) {
        if (myGeneration != generation) {
            return;
        }
        DataStatus status = DataStatusService.evaluate(new DataStatusService.Input(snapshot.guild(),
                snapshot.lineup(), snapshot.lineupFilePath(), snapshot.lineupDirty(), snapshot.fortificationType(),
                snapshot.staleCheck(), LocalDate.now(), files.syncPlan(), files.original(), files.times()));
        show(status);
    }

    /** Shows {@code status} in the process bar, the status bar and at the listeners. */
    void show(DataStatus status) {
        current = status;
        for (Area area : Area.values()) {
            Stage stage = stageOf(area);
            StageResult result = status.result(area);
            processBar.setStatus(stage, statusOf(result.level()));
            processBar.setSubtitle(stage, shortText(result));
        }
        Optional<Area> next = status.nextStep();
        if (next.isPresent()) {
            nextStepAction.setTarget(stageOf(next.get()));
            processBar.setNextStepAction(nextStepAction);
        } else {
            processBar.setNextStepAction(null);
        }
        statusBar.setParts(statusBarParts(status));
        List.copyOf(listeners).forEach(listener -> listener.accept(status));
    }

    /** The process stage of {@code area}. */
    static Stage stageOf(Area area) {
        return switch (area) {
            case INPUT -> Stage.INPUT;
            case CONCEPT -> Stage.CONCEPT;
            case OUTPUT -> Stage.OUTPUT;
        };
    }

    /** The tile's traffic light for {@code level}. */
    static StageStatus statusOf(Level level) {
        return switch (level) {
            case NONE -> StageStatus.NONE;
            case OK -> StageStatus.OK;
            case ATTENTION -> StageStatus.ATTENTION;
            case ACTION_NEEDED -> StageStatus.ACTION_NEEDED;
        };
    }

    /** The short text of {@code result}: its parts in the configured language, joined by ", "; null for none. */
    static String shortText(StageResult result) {
        if (result.texts().isEmpty()) {
            return null;
        }
        List<String> parts = result.texts().stream()
                .map(part -> LanguageService.displayName(part.key(), part.args().toArray()))
                .toList();
        return String.join(", ", parts);
    }

    /** The status bar's parts: newest defense log, outdated teams, lineup status - missing values left out. */
    static List<String> statusBarParts(DataStatus status) {
        List<String> parts = new ArrayList<>();
        if (status.newestDefense() != null) {
            String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(JournalTexts.locale()).format(status.newestDefense());
            parts.add(LanguageService.displayName(KEY_NEWEST_DEFENSE, date));
        }
        if (status.staleJournal() > 0) {
            parts.add(LanguageService.displayName(KEY_STALE_JOURNAL, status.staleJournal(), status.memberCount()));
        } else if (status.staleAfterDays() != null && status.tooOld() > 0) {
            parts.add(LanguageService.displayName(KEY_STALE_AGE, status.tooOld(), status.memberCount(),
                    status.staleAfterDays()));
        }
        String concept = shortText(status.concept());
        if (concept != null) {
            parts.add(LanguageService.displayName(KEY_LINEUP, concept));
        }
        return parts;
    }

    /** The "next step → {stage}" action - only shown while there is a next step. */
    final class NextStepAction extends AbstractAction {

        private Stage target;

        void setTarget(Stage target) {
            this.target = target;
            putValue(NAME, LanguageService.displayName(KEY_NEXT_STEP_TO,
                    LanguageService.displayName(target.stageTextKey())));
        }

        Stage target() {
            return target;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (target != null) {
                stageSwitcher.accept(target);
            }
        }
    }

    /** The state of the open guild and lineup when an evaluation starts (read on the Swing thread). */
    private record Snapshot(Guild guild, Path guildFilePath, Lineup lineup, Path lineupFilePath,
                            boolean lineupDirty, FortificationType fortificationType,
                            StaleCheck staleCheck) {
    }

    /** What was read in the background; null where something does not exist or could not be read. */
    private record FileData(SyncPlan syncPlan, Lineup original, FileTimes times) {
        static final FileData EMPTY = new FileData(null, null, FileTimes.NONE);
    }
}
