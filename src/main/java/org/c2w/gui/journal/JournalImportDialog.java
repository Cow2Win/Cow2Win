package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalLockedException;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.JournalImportService;
import org.c2w.service.JournalSyncService;
import org.c2w.service.journal.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * The import assistant of the Weltenschlacht journal (modal): reads the chosen
 * battle logs in the background ({@link JournalImportService#prepare}), shows
 * the steps of an {@link ImportWizardModel} - overview, guild, season, players,
 * unknown names, summary; only those with content - and imports in the
 * background ({@link JournalImportService#execute}), then shows the result.
 * "Cancel" changes nothing. No import logic here: questions, suggestions and
 * checks all come from the service, the state from the model.
 */
public final class JournalImportDialog extends JDialog {

    /** What the dialog needs from the main window. */
    public interface Host {
        /** Asks about unsaved changes and switches to the guild in that folder; true if switched. */
        boolean switchToGuild(String folderName);

        /** Opens (or brings to front) the battle list. */
        void openBattleList();

        /** Called after a successful import (e.g. to refresh the battle list). */
        void importFinished();

        /** Opens "build teams from logs". */
        void openTeamBuilder();

        /** Opens "update teams (sync)". */
        void openSync();
    }

    private final AppContext context;
    private final GuildService guildService;
    private final JournalImportService service;
    private final Host host;
    private final List<Path> files;

    private final JLabel stepLabel = new JLabel();
    private final JPanel center = new JPanel(new BorderLayout());
    private final JLabel blockLabel = new JLabel();
    private final JButton backButton = new JButton(JournalTexts.text("journal.button.back"));
    private final JButton nextButton = new JButton(JournalTexts.text("journal.button.next"));
    private final JButton importButton = new JButton(JournalTexts.text("journal.button.import"));
    private final JButton cancelButton = new JButton(JournalTexts.text("journal.button.cancel"));
    private final JPanel extraButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

    private ImportWizardModel model;
    private boolean busy;

    private JournalImportDialog(Window owner, AppContext context, GuildService guildService, Host host, List<Path> files) {
        super(owner, JournalTexts.text("journal.import.title"), ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.guildService = guildService;
        this.service = new JournalImportService(context, guildService);
        this.host = host;
        this.files = List.copyOf(files);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!busy) {
                    dispose();
                }
            }
        });
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        stepLabel.setFont(stepLabel.getFont().deriveFont(Font.BOLD, stepLabel.getFont().getSize2D() + 2f));
        root.add(stepLabel, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        blockLabel.setForeground(Color.RED);
        south.add(blockLabel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(backButton);
        buttons.add(nextButton);
        buttons.add(importButton);
        buttons.add(cancelButton);
        south.add(extraButtons, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        backButton.addActionListener(e -> {
            model.back();
            showStep();
        });
        nextButton.addActionListener(e -> {
            model.next();
            showStep();
        });
        importButton.addActionListener(e -> runImport());
        cancelButton.addActionListener(e -> dispose());

        setSize(980, 640);
        setLocationRelativeTo(owner);
    }

    /** Opens the assistant for {@code files} and starts reading them in the background. */
    public static void open(Window owner, AppContext context, GuildService guildService, Host host, List<Path> files) {
        JournalImportDialog dialog = new JournalImportDialog(owner, context, guildService, host, files);
        dialog.runPrepare();
        dialog.setVisible(true);
    }

    // --- background work ---

    private void runPrepare() {
        showBusy(JournalTexts.text("journal.import.reading"));
        Logger.log("Journal import: reading " + files.size() + " file(s)");
        new SwingWorker<ImportPlan, Void>() {
            @Override
            protected ImportPlan doInBackground() throws JournalException {
                return service.prepare(files);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    model = new ImportWizardModel(get(), context.guild());
                    showStep();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showFailure(e.getCause());
                }
            }
        }.execute();
    }

    private void runImport() {
        if (busy || !model.canImport()) {
            return;
        }
        ImportPlan plan = model.plan();
        ImportAnswers answers = model.toAnswers();
        showBusy(JournalTexts.text("journal.import.writing"));
        new SwingWorker<ImportResult, Void>() {
            @Override
            protected ImportResult doInBackground() {
                return service.execute(plan, answers);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    ImportResult result = get();
                    showResult(result);
                    if (result.isSuccess()) {
                        host.importFinished();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showFailure(e.getCause());
                }
            }
        }.execute();
    }

    private void showBusy(String text) {
        busy = true;
        stepLabel.setText(text);
        center.removeAll();
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(progress);
        center.add(panel, BorderLayout.CENTER);
        blockLabel.setText(" ");
        extraButtons.removeAll();
        for (JButton b : List.of(backButton, nextButton, importButton, cancelButton)) {
            b.setEnabled(false);
        }
        refreshLayout();
    }

    // --- steps ---

    private void showStep() {
        ImportWizardModel.Step step = model.currentStep();
        stepLabel.setText(JournalTexts.text("journal.import.stepOf", String.valueOf(model.currentIndex() + 1),
                String.valueOf(model.steps().size())) + " – " + JournalTexts.of("step", step));
        center.removeAll();
        extraButtons.removeAll();
        JComponent page = switch (step) {
            case OVERVIEW -> new OverviewStepPanel(model.plan(), this::switchGuild);
            case GUILD -> guildPage();
            case SEASON -> seasonPage();
            case PLAYERS -> new PlayersStepPanel(model, this::updateButtons);
            case NAMES -> new NamesStepPanel(model, this::catalog, this::updateButtons);
            case SUMMARY -> summaryPage();
        };
        center.add(page, BorderLayout.CENTER);
        cancelButton.setText(JournalTexts.text("journal.button.cancel"));
        updateButtons();
        refreshLayout();
    }

    private void updateButtons() {
        backButton.setVisible(true);
        backButton.setEnabled(model.canGoBack());
        nextButton.setVisible(!model.isLastStep());
        nextButton.setEnabled(model.canGoNext());
        importButton.setVisible(model.isLastStep() && model.plan().isImportable());
        importButton.setEnabled(model.canImport());
        cancelButton.setEnabled(true);
        Set<ImportWizardModel.Block> blocks = model.isLastStep() ? model.blockingProblems()
                : model.blockingProblems(model.currentStep());
        blocks.remove(ImportWizardModel.Block.PLAN_NOT_IMPORTABLE); // the overview shows the plan errors itself
        blockLabel.setText(blocks.isEmpty() ? " "
                : blocks.stream().map(JournalTexts::block).collect(Collectors.joining("  ")));
    }

    private JComponent guildPage() {
        GuildLinkQuestion q = model.plan().guildLink();
        JPanel panel = verticalPanel();
        panel.add(new JLabel(JournalTexts.text("journal.guild.question", escape(q.gameGuild().name()),
                String.valueOf(q.gameGuild().server()), String.valueOf(q.gameGuild().gameGuildId()),
                escape(context.guild().name()))));
        JRadioButton yes = new JRadioButton(JournalTexts.text("journal.guild.yes"), model.linkGuild());
        JRadioButton no = new JRadioButton(JournalTexts.text("journal.guild.no"), !model.linkGuild());
        ButtonGroup group = new ButtonGroup();
        group.add(yes);
        group.add(no);
        yes.addActionListener(e -> {
            model.setLinkGuild(true);
            updateButtons();
        });
        no.addActionListener(e -> {
            model.setLinkGuild(false);
            updateButtons();
        });
        panel.add(yes);
        panel.add(no);
        return panel;
    }

    private JComponent seasonPage() {
        JPanel panel = verticalPanel();
        for (SeasonQuestion q : model.plan().seasonQuestions()) {
            JPanel box = verticalPanel();
            box.setBorder(BorderFactory.createTitledBorder(JournalTexts.of("seasonKind", q.kind()) + " – "
                    + JournalTexts.text("journal.season.number", String.valueOf(q.suggestedNumber()))));
            box.add(new JLabel(JournalTexts.text("journal.season.battles",
                    q.battleDates().stream().map(JournalTexts::date).collect(Collectors.joining(", ")))));

            LocalDate start = model.seasonStart(q.id()).orElse(q.suggestedStart());
            JSpinner date = dateSpinner(start);
            JLabel end = new JLabel();
            JRadioButton assign = new JRadioButton(JournalTexts.text("journal.season.assign"), model.seasonStart(q.id()).isPresent());
            JRadioButton none = new JRadioButton(JournalTexts.text("journal.season.none"), model.seasonStart(q.id()).isEmpty());
            ButtonGroup group = new ButtonGroup();
            group.add(assign);
            group.add(none);
            Runnable apply = () -> {
                if (assign.isSelected()) {
                    model.setSeasonStart(q.id(), toLocalDate((Date) date.getValue()));
                } else {
                    model.setNoSeason(q.id());
                }
                date.setEnabled(assign.isSelected());
                end.setText(model.seasonEnd(q.id())
                        .map(e -> JournalTexts.text("journal.season.end", JournalTexts.date(e.minusDays(1)))).orElse(" "));
                updateButtons();
            };
            assign.addActionListener(e -> apply.run());
            none.addActionListener(e -> apply.run());
            date.addChangeListener(e -> apply.run());

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.add(assign);
            row.add(new JLabel(JournalTexts.text("journal.season.start")));
            row.add(date);
            row.add(end);
            box.add(row);
            box.add(none);
            apply.run();
            panel.add(box);
        }
        return new JScrollPane(panel);
    }

    private JComponent summaryPage() {
        ImportWizardModel.Summary s = model.summary();
        JPanel panel = verticalPanel();
        panel.add(new JLabel(JournalTexts.text("journal.summary.battles", String.valueOf(s.battlesNew()),
                String.valueOf(s.battlesUpdated()), String.valueOf(s.battlesUnchanged()))));
        panel.add(new JLabel(JournalTexts.text("journal.summary.seasons", String.valueOf(s.seasonsNew()))));
        if (s.linkGuild()) {
            panel.add(new JLabel(JournalTexts.text("journal.summary.linkGuild",
                    JournalTexts.opponent(model.plan().guildLink().gameGuild()))));
        }
        panel.add(new JLabel(JournalTexts.text("journal.summary.members", String.valueOf(s.membersNew()),
                String.valueOf(s.membersRenamed()))));
        panel.add(new JLabel(JournalTexts.text("journal.summary.assignments", String.valueOf(s.assignments()))));
        panel.add(new JLabel(JournalTexts.text("journal.summary.nameMappings", String.valueOf(s.nameMappings()))));
        if (s.guildChanges()) {
            panel.add(Box.createVerticalStrut(10));
            JLabel warning = boldLabel(JournalTexts.text("journal.summary.guildChanged"));
            warning.setForeground(new Color(200, 120, 0));
            panel.add(warning);
        }
        return panel;
    }

    // --- result ---

    private void showResult(ImportResult result) {
        stepLabel.setText(JournalTexts.text(result.isSuccess() ? "journal.result.success" : "journal.result.failed"));
        center.removeAll();
        extraButtons.removeAll();
        JPanel panel = verticalPanel();
        if (result.isSuccess()) {
            panel.add(new JLabel(JournalTexts.text("journal.result.battles", String.valueOf(result.battles().size()),
                    JournalTexts.number(result.fights()))));
            panel.add(new JLabel(JournalTexts.text("journal.result.members", String.valueOf(result.createdMembers().size()),
                    String.valueOf(result.renamedMembers().size()))));
            panel.add(new JLabel(JournalTexts.text("journal.result.assignments", String.valueOf(result.assignments()))));
            panel.add(new JLabel(JournalTexts.text("journal.result.nameMappings", String.valueOf(result.nameMappings()))));
            if (!result.warnings().isEmpty()) {
                panel.add(Box.createVerticalStrut(8));
                panel.add(boldLabel(JournalTexts.text("journal.result.warnings")));
                result.warnings().forEach(w -> panel.add(wrapLabel(w)));
            }
            boolean guildChanged = result.guildLinked() || !result.createdMembers().isEmpty()
                    || !result.renamedMembers().isEmpty();
            if (guildChanged && context.isGuildDirty()) {
                panel.add(Box.createVerticalStrut(10));
                JLabel warning = boldLabel(JournalTexts.text("journal.summary.guildChanged"));
                warning.setForeground(new Color(200, 120, 0));
                panel.add(warning);
                JButton save = new JButton(JournalTexts.text("journal.result.saveGuild"));
                save.addActionListener(e -> saveGuild(save, warning));
                extraButtons.add(save);
            }
            if (!result.createdMembers().isEmpty()
                    || org.c2w.service.JournalTeamBuilderService.hasMembersWithoutTeams(context.guild())) {
                JButton teams = new JButton(JournalTexts.text("menu.journal.buildTeams"));
                teams.addActionListener(e -> {
                    dispose();
                    host.openTeamBuilder();
                });
                extraButtons.add(teams);
            }
            Optional<LocalDate> importedDefense = model.plan().battles().stream()
                    .filter(b -> b.actions().containsKey(LogDirection.DEFENSE)).map(PlannedBattle::date)
                    .max(Comparator.naturalOrder());
            if (importedDefense.isPresent()) {
                JButton sync = new JButton(JournalTexts.text("journal.result.sync"));
                sync.addActionListener(e -> {
                    dispose();
                    host.openSync();
                });
                extraButtons.add(sync);
                JLabel newest = new JLabel(" ");
                panel.add(newest);
                JournalSwing.background(this, () -> new JournalSyncService(context, guildService)
                        .newestDefenseBattleDate(), date -> {
                    if (date.isPresent() && date.get().isAfter(importedDefense.get())) {
                        newest.setText(JournalTexts.text("journal.result.syncNewest", JournalTexts.date(date.get())));
                    }
                });
            }
            JButton battles = new JButton(JournalTexts.text("journal.result.openBattles"));
            battles.addActionListener(e -> {
                dispose();
                host.openBattleList();
            });
            extraButtons.add(battles);
        } else {
            for (ImportResult.ImportError error : result.errors()) {
                panel.add(wrapLabel(JournalTexts.of("importError", error.kind())));
            }
        }
        center.add(new JScrollPane(panel), BorderLayout.CENTER);
        finishButtons();
    }

    private void showFailure(Throwable error) {
        Logger.logException("Journal import failed", error);
        stepLabel.setText(JournalTexts.text("journal.result.failed"));
        center.removeAll();
        extraButtons.removeAll();
        JPanel panel = verticalPanel();
        panel.add(wrapLabel(errorText(error)));
        center.add(panel, BorderLayout.CENTER);
        finishButtons();
    }

    /** User text for an exception from the journal (locked journal gets its own message). */
    static String errorText(Throwable error) {
        if (error instanceof JournalLockedException) {
            return JournalTexts.text("journal.error.locked");
        }
        if (error instanceof JournalException) {
            return JournalTexts.text("journal.error.journal", error.getMessage());
        }
        return JournalTexts.text("journal.error.unexpected", String.valueOf(error.getMessage()));
    }

    private void finishButtons() {
        backButton.setVisible(false);
        nextButton.setVisible(false);
        importButton.setVisible(false);
        cancelButton.setText(JournalTexts.text("journal.button.close"));
        cancelButton.setEnabled(true);
        blockLabel.setText(" ");
        refreshLayout();
    }

    private void saveGuild(JButton save, JLabel warning) {
        try {
            guildService.saveGuild();
            save.setEnabled(false);
            warning.setText(JournalTexts.text("journal.result.guildSaved"));
            warning.setForeground(UIManager.getColor("Label.foreground"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName("common.saveGuildError") + "\n" + ex.getMessage(),
                    LanguageService.displayName("common.saveErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- guild switch (logs of another Cow2Win guild) ---

    private void switchGuild(PlanError error) {
        if (error.gameGuildId() == null) {
            return;
        }
        guildService.findOtherGuildFolderByGameGuildId(error.gameGuildId(), guildService.currentGuildFolderName())
                .filter(host::switchToGuild)
                .ifPresent(folder -> runPrepare());
    }

    // --- catalog for unknown names ---

    private List<NamesStepPanel.CatalogChoice> catalog(NameMappingKind kind) {
        return JournalCatalog.choices(context.catalog(), kind);
    }

    // --- small Swing helpers ---

    private void refreshLayout() {
        center.revalidate();
        center.repaint();
        getContentPane().revalidate();
    }

    static JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    static JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    /** A label that wraps long text. */
    static JLabel wrapLabel(String text) {
        JLabel label = new JLabel("<html><div style='width:700px'>" + escape(text) + "</div></html>");
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static JSpinner dateSpinner(LocalDate value) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(toDate(value), null, null, java.util.Calendar.DAY_OF_MONTH));
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, JournalTexts.locale());
        String pattern = format instanceof SimpleDateFormat sdf ? sdf.toPattern() : "dd.MM.yyyy";
        spinner.setEditor(new JSpinner.DateEditor(spinner, pattern));
        return spinner;
    }

    static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
