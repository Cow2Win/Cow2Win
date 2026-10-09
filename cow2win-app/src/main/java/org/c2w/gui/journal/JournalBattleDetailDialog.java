package org.c2w.gui.journal;

import org.c2w.data.journal.FightUnit;
import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.UnitKind;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.db.LogInfo;
import org.c2w.data.repository.Catalog;
import org.c2w.service.AppContext;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The detail of one battle (not modal, several may be open): head data, then the
 * tabs Defense (our defenders per fortification - held/fallen - with a short
 * evaluation; hint + import if the defense log is missing), Attack (display
 * only), Fortifications (overview per fortification and direction) and Problems
 * (only if there are parse problems). Teams of a fight are shown below the
 * fights when the log contains them. All content comes from a
 * {@link BattleDetailModel}, read in the background.
 */
public final class JournalBattleDetailDialog extends JDialog {

    /** What the detail needs from the journal menu. */
    public interface Host {
        JournalOperations operations();

        /** Starts an import with the file chooser. */
        void importWithChooser();
    }

    private final AppContext context;
    private final Host host;
    private final int battleId;
    private final JPanel content = new JPanel(new BorderLayout(0, 8));
    private final JButton reparseButton = new JButton(JournalTexts.text("journal.action.reparse"));
    private final JButton saveCsvButton = new JButton(JournalTexts.text("journal.action.saveCsv") + " …");
    private final JButton deleteButton = new JButton(JournalTexts.text("journal.action.delete"));
    /** The guild file the battle belongs to - the detail closes when another guild is opened. */
    private final Path guildFile;
    private final AppContext.Listener listener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            if (!Objects.equals(guildFile, context.guildFilePath())) {
                dispose();
            }
        }
    };
    private BattleDetailModel model;
    private int selectedTab;

    public JournalBattleDetailDialog(Window owner, AppContext context, Host host, int battleId) {
        super(owner, ModalityType.MODELESS);
        this.context = context;
        this.host = host;
        this.battleId = battleId;
        this.guildFile = context.guildFilePath();
        context.addListener(listener);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setTitle(JournalTexts.text("journal.import.reading"));

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        root.add(content, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(reparseButton);
        buttons.add(saveCsvButton);
        buttons.add(deleteButton);
        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.WEST);
        JButton close = new JButton(JournalTexts.text("journal.button.close"));
        close.addActionListener(e -> dispose());
        south.add(close, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        reparseButton.addActionListener(e -> host.operations().reparse(this, List.of(battleId)));
        saveCsvButton.addActionListener(e -> {
            if (model != null) {
                JPopupMenu menu = new JPopupMenu();
                for (Component item : host.operations().saveCsvMenu(this, model.battle()).getMenuComponents()) {
                    menu.add(item);
                }
                menu.show(saveCsvButton, 0, saveCsvButton.getHeight());
            }
        });
        deleteButton.addActionListener(e -> {
            if (model != null) {
                host.operations().deleteBattles(this, List.of(model.battle()), this::dispose);
            }
        });

        setSize(1300, 820);
        setLocationRelativeTo(owner);
        reload();
    }

    public int battleId() {
        return battleId;
    }

    @Override
    public void dispose() {
        context.removeListener(listener);
        super.dispose();
    }

    /** Reads the battle again (after a change in the journal); closes if it no longer exists. */
    public void reload() {
        if (!Objects.equals(guildFile, context.guildFilePath())) {
            dispose();
            return;
        }
        enableActions(false);
        JournalSwing.background(this, () -> {
            Optional<JournalRepository> repo = context.journal().repository(false);
            if (repo.isEmpty()) {
                return Optional.<BattleDetailModel>empty();
            }
            return BattleDetailModel.load(repo.get(), battleId, context.guild());
        }, (Optional<BattleDetailModel> loaded) -> {
            if (loaded.isEmpty()) {
                dispose();
                return;
            }
            show(loaded.get());
        });
    }

    private void enableActions(boolean enabled) {
        reparseButton.setEnabled(enabled);
        saveCsvButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void show(BattleDetailModel newModel) {
        this.model = newModel;
        BattleSummary b = model.battle();
        setTitle(JournalTexts.text("journal.detail.title", JournalTexts.date(b.date()), b.opponent().name()));
        content.removeAll();
        content.add(header(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(JournalTexts.of("direction", LogDirection.DEFENSE), directionTab(LogDirection.DEFENSE));
        tabs.addTab(JournalTexts.of("direction", LogDirection.ATTACK), directionTab(LogDirection.ATTACK));
        tabs.addTab(JournalTexts.text("journal.detail.tab.forts"), fortsTab());
        if (model.hasProblems()) {
            tabs.addTab(JournalTexts.text("journal.detail.tab.problems", String.valueOf(model.problems().size())),
                    problemsTab());
        }
        tabs.setSelectedIndex(Math.min(selectedTab, tabs.getTabCount() - 1));
        tabs.addChangeListener(e -> selectedTab = tabs.getSelectedIndex());
        content.add(tabs, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
        enableActions(true);
    }

    // --- head ---

    private JComponent header() {
        BattleSummary b = model.battle();
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 0, 1, 12);
        int[] row = {0};
        java.util.function.BiConsumer<String, String> add = (label, value) -> {
            c.gridy = row[0]++;
            c.gridx = 0;
            c.weightx = 0;
            panel.add(JournalImportDialog.boldLabel(label), c);
            c.gridx = 1;
            c.weightx = 1;
            JLabel valueLabel = new JLabel(value);
            valueLabel.setToolTipText(value);
            panel.add(valueLabel, c);
        };
        add.accept(JournalTexts.text("journal.detail.date"), JournalTexts.date(b.date()));
        String own = model.ownGuild() == null ? "" : JournalTexts.text("journal.detail.guild",
                model.ownGuild().name(), String.valueOf(model.ownGuild().server()),
                String.valueOf(model.ownGuild().gameGuildId()));
        String opponent = JournalTexts.text("journal.detail.guild", b.opponent().name(),
                String.valueOf(b.opponent().server()), String.valueOf(b.opponent().gameGuildId()));
        add.accept(JournalTexts.text("journal.detail.guilds"), JournalTexts.text("journal.detail.versus", own, opponent));
        add.accept(JournalTexts.text("journal.detail.result"), b.status() == BattleStatus.RUNNING
                ? JournalTexts.text("journal.overview.running") : JournalBattleTableModel.resultText(b));
        add.accept(JournalTexts.text("journal.detail.rankingPoints"), b.status() == BattleStatus.RUNNING
                ? "–" : JournalTexts.rankingPoints(b.rankingPoints()));
        add.accept(JournalTexts.text("journal.detail.points"), JournalTexts.text("journal.detail.pointsValue",
                b.ownPoints() == null ? "–" : JournalTexts.number(b.ownPoints()),
                b.opponentPoints() == null ? "–" : JournalTexts.number(b.opponentPoints())));
        add.accept(JournalTexts.text("journal.detail.check"), JournalTexts.of("verdict", model.check().verdict()));
        add.accept(JournalTexts.text("journal.detail.season"), b.seasonNumber() == null
                ? JournalTexts.text("journal.detail.noSeason")
                : JournalTexts.text("journal.season.number", String.valueOf(b.seasonNumber())));
        for (LogDirection direction : List.of(LogDirection.DEFENSE, LogDirection.ATTACK)) {
            Optional<LogInfo> info = model.logs().stream().filter(l -> l.direction() == direction).findFirst();
            add.accept(JournalTexts.text("journal.detail.log", JournalTexts.of("direction", direction)),
                    info.map(l -> JournalTexts.text("journal.detail.logValue", l.language(), l.fileName(),
                            JournalTexts.dateTime(l.importedAt()), String.valueOf(l.parserVersion()),
                            String.valueOf(l.problemCount()))).orElse(JournalTexts.text("journal.detail.noLog")));
        }
        return panel;
    }

    // --- defense / attack ---

    private JComponent directionTab(LogDirection direction) {
        Optional<BattleDetailModel.DirectionView> found = model.view(direction);
        if (found.isEmpty()) {
            JPanel panel = new JPanel(new GridBagLayout());
            JPanel inner = JournalImportDialog.verticalPanel();
            JLabel label = new JLabel(JournalTexts.text(direction == LogDirection.DEFENSE
                    ? "journal.detail.noDefense" : "journal.detail.noAttack"));
            label.setAlignmentX(CENTER_ALIGNMENT);
            inner.add(label);
            JButton importButton = new JButton(JournalTexts.text("journal.battles.import"));
            importButton.setAlignmentX(CENTER_ALIGNMENT);
            importButton.addActionListener(e -> host.importWithChooser());
            inner.add(Box.createVerticalStrut(8));
            inner.add(importButton);
            panel.add(inner);
            return panel;
        }
        BattleDetailModel.DirectionView view = found.get();
        UnitsPanel units = view.hasUnits() ? new UnitsPanel(context.catalog()) : null;
        List<JTable> tables = new ArrayList<>();
        JPanel forts = JournalImportDialog.verticalPanel();
        for (BattleDetailModel.FortGroup fort : view.forts()) {
            FightTableModel tableModel = new FightTableModel(direction, fort.fights());
            JTable table = JournalSwing.table(tableModel);
            table.setDefaultRenderer(Object.class, new FightRenderer(tableModel));
            table.setDefaultRenderer(Integer.class, new FightRenderer(tableModel));
            table.getColumnModel().getColumn(0).setPreferredWidth(40);
            table.getColumnModel().getColumn(1).setPreferredWidth(260);
            table.getColumnModel().getColumn(3).setPreferredWidth(200);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tables.add(table);
            if (units != null) {
                table.getSelectionModel().addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting() || table.getSelectedRow() < 0) {
                        return;
                    }
                    tables.stream().filter(t -> t != table).forEach(JTable::clearSelection);
                    units.show(tableModel.row(table.getSelectedRow()));
                });
            }
            JPanel box = new JPanel(new BorderLayout());
            box.setAlignmentX(LEFT_ALIGNMENT);
            box.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), fortTitle(direction, fort),
                    TitledBorder.LEFT, TitledBorder.TOP));
            if (!fort.fights().isEmpty()) {
                box.add(table.getTableHeader(), BorderLayout.NORTH);
                box.add(table, BorderLayout.CENTER);
            }
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
            forts.add(box);
        }
        JScrollPane scroll = new JScrollPane(forts);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(JournalImportDialog.wrapLabel(summary(view)), BorderLayout.NORTH);
        if (units == null) {
            panel.add(scroll, BorderLayout.CENTER);
        } else {
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, units);
            split.setResizeWeight(0.6);
            panel.add(split, BorderLayout.CENTER);
        }
        return panel;
    }

    private static String summary(BattleDetailModel.DirectionView view) {
        String captured = view.capturedForts().isEmpty() ? JournalTexts.text("journal.detail.none")
                : view.capturedForts().stream().map(BattleDetailModel.FortGroup::displayName)
                .collect(Collectors.joining(", "));
        String perFort = view.forts().stream()
                .map(f -> f.displayName() + " " + JournalTexts.number(f.points()))
                .collect(Collectors.joining(" · "));
        return JournalTexts.text("journal.detail.summary." + view.direction().name(),
                String.valueOf(view.goodFights()), String.valueOf(view.badFights()), JournalTexts.number(view.points()),
                captured, perFort);
    }

    private static String fortTitle(LogDirection direction, BattleDetailModel.FortGroup fort) {
        List<String> parts = new ArrayList<>();
        parts.add(fort.displayName());
        if (fort.buff() != null) {
            parts.add(JournalTexts.text("journal.detail.fort.buff", fort.buff().rawText()));
        }
        if (fort.undefended() > 0) {
            parts.add(JournalTexts.text("journal.detail.fort.undefended." + direction.name(),
                    String.valueOf(fort.undefended()), JournalTexts.number(fort.undefendedPoints())));
        }
        if (fort.captured()) {
            parts.add(JournalTexts.text("journal.detail.fort.captured." + direction.name(),
                    JournalTexts.number(fort.capturePoints())));
        }
        parts.add(JournalTexts.text("journal.detail.fort.score." + direction.name(), String.valueOf(fort.goodFights()),
                String.valueOf(fort.badFights())));
        parts.add(JournalTexts.text("journal.detail.fort.points", JournalTexts.number(fort.points())));
        return String.join("  ·  ", parts);
    }

    /** The single fights of one fortification. */
    private static final class FightTableModel extends AbstractTableModel {
        private final LogDirection direction;
        private final List<BattleDetailModel.FightRow> rows;

        FightTableModel(LogDirection direction, List<BattleDetailModel.FightRow> rows) {
            this.direction = direction;
            this.rows = rows;
        }

        BattleDetailModel.FightRow row(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return direction == LogDirection.ATTACK ? 8 : 7;
        }

        @Override
        public String getColumnName(int column) {
            boolean defense = direction == LogDirection.DEFENSE;
            return JournalTexts.text(switch (column) {
                case 0 -> "journal.detail.col.position";
                case 1 -> defense ? "journal.detail.col.ourDefender" : "journal.detail.col.ourAttacker";
                case 2, 4 -> "journal.detail.col.power";
                case 3 -> defense ? "journal.detail.col.opponentAttacker" : "journal.detail.col.opponentDefender";
                case 5 -> "journal.detail.col.outcome";
                case 6 -> defense ? "journal.detail.col.opponentPoints" : "journal.detail.col.points";
                default -> "journal.detail.col.buff";
            });
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0, 2, 4, 6 -> Integer.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            BattleDetailModel.FightRow f = rows.get(row);
            return switch (column) {
                case 0 -> f.position();
                case 1 -> f.ourPlayer().label();
                case 2 -> f.ourPower();
                case 3 -> JournalTexts.visibleSpaces(f.opponentName());
                case 4 -> f.opponentPower();
                case 5 -> JournalTexts.of("outcome", f.outcome());
                case 6 -> f.points();
                default -> f.buff() == null ? "" : f.buff().rawText();
            };
        }
    }

    /** Colors the outcome, marks players whose member no longer exists. */
    private static final class FightRenderer extends JournalSwing.TextRenderer {
        private final FightTableModel model;

        FightRenderer(FightTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            if (column == 0) {
                setText(String.valueOf(value));
            }
            BattleDetailModel.FightRow f = model.row(row);
            if (!selected && column == 5) {
                setBackground(JournalSwing.blend(table.getBackground(),
                        f.outcome().good() ? JournalSwing.GOOD : JournalSwing.BAD));
            }
            setForeground(!selected && column == 1 && f.ourPlayer().memberMissing() ? new Color(200, 120, 0)
                    : selected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }

    /** Both teams of the selected fight, with images and values. */
    private static final class UnitsPanel extends JPanel {
        private final Catalog catalog;
        private final UnitTableModel ours = new UnitTableModel();
        private final UnitTableModel theirs = new UnitTableModel();
        private final JLabel hint = new JLabel(JournalTexts.text("journal.detail.units.select"));

        UnitsPanel(Catalog catalog) {
            super(new BorderLayout(0, 4));
            this.catalog = catalog;
            add(hint, BorderLayout.NORTH);
            JPanel tables = new JPanel(new GridLayout(1, 2, 8, 0));
            tables.add(teamBox(JournalTexts.text("journal.detail.units.our"), ours));
            tables.add(teamBox(JournalTexts.text("journal.detail.units.opponent"), theirs));
            add(tables, BorderLayout.CENTER);
        }

        private JComponent teamBox(String title, UnitTableModel model) {
            JTable table = JournalSwing.table(model);
            table.setRowHeight(JournalCatalog.ICON_SIZE + 4);
            table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                               int row, int column) {
                    super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                    setIcon(JournalCatalog.icon(catalog, model.unit(row)));
                    return this;
                }
            });
            int[] widths = {130, 60, 40, 40, 70, 85, 75, 75, 95};
            for (int i = 0; i < widths.length; i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }
            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(BorderFactory.createTitledBorder(title));
            return scroll;
        }

        void show(BattleDetailModel.FightRow fight) {
            ours.setUnits(fight.ourUnits());
            theirs.setUnits(fight.opponentUnits());
            hint.setText(fight.hasUnits()
                    ? JournalTexts.visibleSpaces(fight.ourPlayer().rawName()) + "  ⚔  "
                    + JournalTexts.visibleSpaces(fight.opponentName())
                    : JournalTexts.text("journal.detail.units.none"));
        }
    }

    /** The units of one team: name, color, stars, level, power, damage dealt/taken, healing, pet/patronage. */
    private static final class UnitTableModel extends AbstractTableModel {
        private static final String[] KEYS = {"unit", "color", "stars", "level", "power", "damage", "taken", "healing",
                "patronage"};
        private List<FightUnit> units = List.of();

        void setUnits(List<FightUnit> newUnits) {
            units = newUnits;
            fireTableDataChanged();
        }

        FightUnit unit(int row) {
            return units.get(row);
        }

        @Override
        public int getRowCount() {
            return units.size();
        }

        @Override
        public int getColumnCount() {
            return KEYS.length;
        }

        @Override
        public String getColumnName(int column) {
            return JournalTexts.text("journal.detail.units.col." + KEYS[column]);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column >= 2 && column <= 7 ? Integer.class : Object.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            FightUnit u = units.get(row);
            return switch (column) {
                case 0 -> JournalCatalog.unitName(u);
                case 1 -> u.colorText();
                case 2 -> u.stars();
                case 3 -> u.level();
                case 4 -> u.power();
                case 5 -> u.kind() == UnitKind.TOTEM ? null : u.damageDealt();
                case 6 -> u.kind() == UnitKind.TOTEM ? null : u.damageTaken();
                case 7 -> u.kind() == UnitKind.TOTEM ? null : u.healing();
                default -> u.patronage() == null ? "" : u.patronage().petName() + " (" + JournalTexts.number(
                        u.patronage().power()) + ")";
            };
        }
    }

    // --- fortifications, problems ---

    private JComponent fortsTab() {
        List<BattleDetailModel.FortOverviewRow> rows = model.fortOverview();
        String[] keys = {"direction", "fort", "positions", "undefended", "fights", "good", "bad", "points", "captured"};
        AbstractTableModel tableModel = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return keys.length;
            }

            @Override
            public String getColumnName(int column) {
                return JournalTexts.text("journal.detail.forts.col." + keys[column]);
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column >= 2 && column <= 7 ? Integer.class : Object.class;
            }

            @Override
            public Object getValueAt(int row, int column) {
                BattleDetailModel.FortOverviewRow r = rows.get(row);
                BattleDetailModel.FortGroup f = r.fort();
                return switch (column) {
                    case 0 -> JournalTexts.of("direction", r.direction());
                    case 1 -> f.displayName();
                    case 2 -> f.positions();
                    case 3 -> f.undefended();
                    case 4 -> f.fights().size();
                    case 5 -> f.goodFights();
                    case 6 -> f.badFights();
                    case 7 -> f.points();
                    default -> JournalTexts.text(f.captured() ? "journal.common.yes" : "journal.common.no");
                };
            }
        };
        JTable table = JournalSwing.table(tableModel);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(JournalImportDialog.wrapLabel(JournalTexts.text("journal.detail.forts.hint")), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JComponent problemsTab() {
        List<BattleDetailModel.ProblemRow> rows = model.problems();
        String[] keys = {"log", "line", "reason", "text"};
        AbstractTableModel tableModel = new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return keys.length;
            }

            @Override
            public String getColumnName(int column) {
                return JournalTexts.text("journal.detail.problems.col." + keys[column]);
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 ? Integer.class : Object.class;
            }

            @Override
            public Object getValueAt(int row, int column) {
                BattleDetailModel.ProblemRow p = rows.get(row);
                return switch (column) {
                    case 0 -> JournalTexts.of("direction", p.direction());
                    case 1 -> p.lineNumber();
                    case 2 -> p.reason();
                    default -> p.line();
                };
            }
        };
        JTable table = JournalSwing.table(tableModel);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(500);
        return new JScrollPane(table);
    }
}
