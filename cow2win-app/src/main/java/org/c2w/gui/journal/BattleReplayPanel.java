package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.common.AppColors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * The content of the tabs "Attack (visual)" and "Defense (visual)": the battle
 * step by step on a simplified map of the fortifications ({@link BattleReplayMapPanel}),
 * next to it the current step, the running score, the last steps and a legend,
 * below a timeline with one bar per step and the buttons start / back / next / end
 * (also on the keys Home, Left, Right, End). Starts at step 0 unless a step is given.
 *
 * <p>Colors follow the log's result column in both directions - the attacker's view: a single fight
 * the attacker won in the success text color, one the attacker lost in the error text color, a
 * captured fortification in the success base color (see {@link BattleReplayMapPanel#fightColor},
 * {@link BattleReplayMapPanel#capturedColor}).
 */
final class BattleReplayPanel extends JPanel {

    private static final int SIDE_WIDTH = 380;
    private static final int HISTORY_SIZE = 7;

    private final LogDirection direction;
    private final BattleReplay replay;
    private final IntConsumer stepListener;
    private final BattleReplayMapPanel map;
    private final Timeline timeline = new Timeline();
    private final JLabel stepLabel = new JLabel();
    private final Badge stepBadge = new Badge();
    private final JLabel stepTitle = new JLabel();
    private final JLabel stepSubtitle = new JLabel();
    private final JPanel stepBody = new JPanel(new GridBagLayout());
    private final JLabel stepPoints = new JLabel();
    private final JLabel[] scoreValues = new JLabel[4];
    private final JPanel history = new JPanel(new GridBagLayout());
    private final JButton first = new JButton();
    private final JButton prev = new JButton(JournalTexts.text("journal.replay.prev"));
    private final JButton next = new JButton(JournalTexts.text("journal.replay.next"));
    private final JButton last = new JButton();
    private int step;

    /**
     * @param direction    attack (opponent's fortifications) or defense (ours)
     * @param steps        the log's rows as steps, in file order
     * @param model        the battle (guild names for the title)
     * @param initialStep  the step to show (kept across reloads); 0 if it no longer exists
     * @param stepListener told about every new step
     */
    BattleReplayPanel(LogDirection direction, List<BattleDetailModel.ReplayStep> steps, BattleDetailModel model,
                      int initialStep, IntConsumer stepListener) {
        super(new BorderLayout(10, 8));
        this.direction = direction;
        this.replay = new BattleReplay(direction, steps, FortificationRepository.findAll());
        this.stepListener = stepListener;
        this.map = new BattleReplayMapPanel(direction);
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setFocusable(true);

        add(header(model), BorderLayout.NORTH);
        add(map, BorderLayout.CENTER);
        add(side(), BorderLayout.EAST);
        add(bottom(), BorderLayout.SOUTH);
        installKeys();
        MouseAdapter focus = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        };
        map.addMouseListener(focus);
        addMouseListener(focus);

        step = initialStep >= 0 && initialStep <= replay.size() ? initialStep : 0;
        update();
    }

    /** The step shown (0 = start). */
    int step() {
        return step;
    }

    /** Number of steps. */
    int stepCount() {
        return replay.size();
    }

    /** Shows step {@code newStep} (clamped). */
    void goTo(int newStep) {
        int clamped = Math.max(0, Math.min(newStep, replay.size()));
        if (clamped == step) {
            return;
        }
        step = clamped;
        update();
        stepListener.accept(step);
    }

    // --- layout ---

    private JComponent header(BattleDetailModel model) {
        String name = direction == LogDirection.ATTACK ? model.battle().opponent().name()
                : model.ownGuild() == null ? "" : model.ownGuild().name();
        JLabel title = new JLabel(JournalTexts.text("journal.replay.title." + direction.name(),
                JournalTexts.visibleSpaces(name)));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(title);
        JLabel order = grey(new JLabel(JournalTexts.text("journal.replay.orderHint"), new InfoIcon(), SwingConstants.LEFT));
        JPanel header = new JPanel(new BorderLayout());
        header.add(left, BorderLayout.WEST);
        header.add(order, BorderLayout.EAST);
        return header;
    }

    private JComponent side() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(SIDE_WIDTH, 10));

        // the current step
        JPanel card = box();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        grey(stepLabel);
        top.add(stepLabel, BorderLayout.WEST);
        top.add(stepBadge, BorderLayout.EAST);
        card.add(top);
        stepTitle.setFont(stepTitle.getFont().deriveFont(Font.BOLD, stepTitle.getFont().getSize2D() + 5));
        stepTitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        card.add(stepTitle);
        grey(stepSubtitle);
        card.add(stepSubtitle);
        stepBody.setOpaque(false);
        stepBody.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
        card.add(stepBody);
        stepPoints.setFont(stepPoints.getFont().deriveFont(Font.BOLD));
        card.add(stepPoints);
        for (Component c : card.getComponents()) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }
        fixHeight(card);
        side.add(card);
        side.add(Box.createVerticalStrut(8));

        // running score
        JPanel score = new JPanel(new GridLayout(2, 4, 6, 0));
        String[] keys = {"points", "good", "bad", "forts"};
        // colored by the attacker's result, like everything else: attacker wins = success (text colors)
        boolean attack = direction == LogDirection.ATTACK;
        Color[] colors = {null, BattleReplayMapPanel.fightColor(attack), BattleReplayMapPanel.fightColor(!attack),
                AppColors.SUCCESS_TEXT};
        for (int i = 0; i < 4; i++) {
            scoreValues[i] = new JLabel("0", SwingConstants.CENTER);
            scoreValues[i].setFont(scoreValues[i].getFont().deriveFont(Font.BOLD, scoreValues[i].getFont().getSize2D() + 3));
            if (colors[i] != null) {
                scoreValues[i].setForeground(colors[i]);
            }
            score.add(scoreValues[i]);
        }
        for (String key : keys) {
            score.add(grey(new JLabel(JournalTexts.text("journal.replay.score." + direction.name() + "." + key),
                    SwingConstants.CENTER)));
        }
        JPanel scoreBox = box();
        scoreBox.add(score);
        fixHeight(scoreBox);
        side.add(scoreBox);
        side.add(Box.createVerticalStrut(8));

        // history
        JPanel historyBox = box();
        JLabel historyTitle = new JLabel(JournalTexts.text("journal.replay.history"));
        historyTitle.setFont(historyTitle.getFont().deriveFont(Font.BOLD));
        historyTitle.setAlignmentX(LEFT_ALIGNMENT);
        historyBox.add(historyTitle);
        historyTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        history.setOpaque(false);
        JPanel historyTop = new JPanel(new BorderLayout());
        historyTop.setOpaque(false);
        historyTop.setAlignmentX(LEFT_ALIGNMENT);
        historyTop.add(history, BorderLayout.NORTH);
        historyBox.add(historyTop);
        side.add(historyBox);
        side.add(Box.createVerticalStrut(8));

        // legend
        JPanel legend = new JPanel(new GridLayout(3, 2, 6, 2));
        legend.add(legendItem(BattleReplayMapPanel.Dot.HELD, "journal.replay.legend.held"));
        legend.add(legendItem(BattleReplayMapPanel.Dot.TAKEN, "journal.replay.legend.taken." + direction.name()));
        legend.add(legendItem(BattleReplayMapPanel.Dot.REPELLED, "journal.replay.legend.repelled." + direction.name()));
        legend.add(legendItem(BattleReplayMapPanel.Dot.FREE, "journal.replay.legend.free"));
        legend.add(legendItem(BattleReplayMapPanel.Dot.LOCKED, "journal.replay.legend.locked"));
        legend.setAlignmentX(LEFT_ALIGNMENT);
        fixHeight(legend);
        side.add(legend);
        return side;
    }

    private JComponent bottom() {
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(timeline, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        first.setIcon(new ArrowIcon(false, true));
        first.setToolTipText(JournalTexts.text("journal.replay.first"));
        prev.setIcon(new ArrowIcon(false, false));
        next.setIcon(new ArrowIcon(true, false));
        next.setHorizontalTextPosition(SwingConstants.LEFT);
        next.setFont(next.getFont().deriveFont(Font.BOLD));
        last.setIcon(new ArrowIcon(true, true));
        last.setToolTipText(JournalTexts.text("journal.replay.last"));
        first.addActionListener(e -> goTo(0));
        prev.addActionListener(e -> goTo(step - 1));
        next.addActionListener(e -> goTo(step + 1));
        last.addActionListener(e -> goTo(replay.size()));
        buttons.add(first);
        buttons.add(prev);
        buttons.add(next);
        buttons.add(last);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legend.add(barLegend(BattleReplayMapPanel.fightColor(true), "journal.replay.label." + (direction == LogDirection.ATTACK
                ? "win" : "fell")));
        legend.add(barLegend(BattleReplayMapPanel.fightColor(false), "journal.replay.label." + (direction == LogDirection.ATTACK
                ? "loss" : "held")));
        legend.add(barLegend(AppColors.MUTED, "journal.replay.label.undefended"));
        legend.add(barLegend(BattleReplayMapPanel.capturedColor(), "journal.replay.label." + (direction == LogDirection.ATTACK
                ? "captured" : "lost")));
        JPanel row = new JPanel(new BorderLayout());
        row.add(buttons, BorderLayout.WEST);
        row.add(legend, BorderLayout.EAST);
        bottom.add(row, BorderLayout.SOUTH);
        return bottom;
    }

    private void installKeys() {
        InputMap input = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = getActionMap();
        bind(input, actions, KeyEvent.VK_RIGHT, "replayNext", () -> goTo(step + 1));
        bind(input, actions, KeyEvent.VK_LEFT, "replayPrev", () -> goTo(step - 1));
        bind(input, actions, KeyEvent.VK_HOME, "replayFirst", () -> goTo(0));
        bind(input, actions, KeyEvent.VK_END, "replayLast", () -> goTo(replay.size()));
    }

    private static void bind(InputMap input, ActionMap actions, int key, String name, Runnable action) {
        input.put(KeyStroke.getKeyStroke(key, 0), name);
        actions.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static JPanel box() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(LEFT_ALIGNMENT);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.LINE_STRONG),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        return box;
    }

    private static void fixHeight(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    private static JLabel grey(JLabel label) {
        label.setForeground(AppColors.TEXT_MUTED);
        return label;
    }

    private JLabel legendItem(BattleReplayMapPanel.Dot dot, String key) {
        return new JLabel(JournalTexts.text(key), new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                BattleReplayMapPanel.paintDot(g2, dot, direction, x + 1, y + 1, 10);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 12;
            }

            @Override
            public int getIconHeight() {
                return 12;
            }
        }, SwingConstants.LEFT);
    }

    private static JLabel barLegend(Color color, String key) {
        return grey(new JLabel(JournalTexts.text(key), new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRect(x + 2, y, 5, 12);
            }

            @Override
            public int getIconWidth() {
                return 9;
            }

            @Override
            public int getIconHeight() {
                return 12;
            }
        }, SwingConstants.LEFT));
    }

    // --- content ---

    private void update() {
        BattleReplay.State state = replay.stateAt(step);
        map.setState(state);
        timeline.repaint();
        updateCard(state);
        int[] values = {state.points(), state.good(), state.bad(), state.captured()};
        for (int i = 0; i < 4; i++) {
            scoreValues[i].setText(JournalTexts.number(values[i]));
        }
        updateHistory();
        first.setEnabled(step > 0);
        prev.setEnabled(step > 0);
        next.setEnabled(step < replay.size());
        last.setEnabled(step < replay.size());
        revalidate();
        repaint();
    }

    private void updateCard(BattleReplay.State state) {
        stepBody.removeAll();
        BattleDetailModel.ReplayStep s = state.current();
        if (s == null) {
            stepLabel.setText(JournalTexts.text("journal.replay.start"));
            stepBadge.set(JournalTexts.text("journal.replay.start"), AppColors.INFO_TEXT);
            stepTitle.setText(JournalTexts.text("journal.replay.ready"));
            stepSubtitle.setText(" ");
            stepBody.add(wrap(JournalTexts.text("journal.replay.readyText")), bodyConstraints(0, 0, 2));
            stepPoints.setText(" ");
            return;
        }
        stepLabel.setText(JournalTexts.text("journal.replay.step", String.valueOf(step), String.valueOf(replay.size())));
        stepBadge.set(label(s), labelColor(s));
        stepTitle.setText(s.displayName());
        Optional<Fortification> fort = replay.fortification(s);
        String type = fort.map(f -> JournalTexts.text(f.type() == FortificationType.HERO
                ? "journal.replay.heroFort" : "journal.replay.titanFort")).orElse("");
        switch (s.kind()) {
            case FIGHT -> {
                // unknown fortification: no type behind the position
                stepSubtitle.setText(JournalTexts.text("journal.replay.position", String.valueOf(s.position()), type)
                        .replaceAll("\\s*·\\s*$", ""));
                addSide(0, "journal.replay.attacker", s.attackerName(), s.attackerPower());
                addSide(1, "journal.replay.defender", s.defenderName(), s.defenderPower());
            }
            case UNDEFENDED -> {
                stepSubtitle.setText(type.isEmpty() ? " " : type);
                int total = fort.map(Fortification::capacity).orElse(s.freePositions());
                stepBody.add(wrap(JournalTexts.text("journal.replay.undefendedText", String.valueOf(s.freePositions()),
                        String.valueOf(total))), bodyConstraints(0, 0, 2));
            }
            case CAPTURED -> {
                stepSubtitle.setText(type.isEmpty() ? " " : type);
                stepBody.add(wrap(JournalTexts.text("journal.replay.capturedText." + direction.name())),
                        bodyConstraints(0, 0, 2));
            }
        }
        stepPoints.setText(JournalTexts.text("journal.replay.points." + direction.name()) + "  +"
                + JournalTexts.number(s.points()));
    }

    private void addSide(int row, String key, String name, int power) {
        JLabel role = grey(new JLabel(JournalTexts.text(key)));
        stepBody.add(role, bodyConstraints(0, row, 1));
        JLabel player = new JLabel(JournalTexts.visibleSpaces(name) + "  (" + JournalTexts.number(power) + ")");
        player.setToolTipText(player.getText());
        GridBagConstraints c = bodyConstraints(1, row, 1);
        c.weightx = 1;
        stepBody.add(player, c);
    }

    private static GridBagConstraints bodyConstraints(int x, int y, int width) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = width > 1 ? 1 : 0;
        c.insets = new Insets(1, 0, 1, 10);
        return c;
    }

    private static JComponent wrap(String text) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setColumns(28);
        area.setRows(2);
        return area;
    }

    private void updateHistory() {
        history.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        if (step == 0) {
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 1;
            history.add(grey(new JLabel(JournalTexts.text("journal.replay.noHistory"))), c);
            return;
        }
        int row = 0;
        for (int n = step; n >= 1 && n > step - HISTORY_SIZE; n--, row++) {
            BattleDetailModel.ReplayStep s = replay.steps().get(n - 1);
            int target = n;
            JLabel number = grey(new JLabel(String.valueOf(n)));
            JLabel what = new JLabel(s.kind() == BattleDetailModel.ReplayStep.Kind.FIGHT
                    ? JournalTexts.text("journal.replay.historyPos", s.displayName(), String.valueOf(s.position()))
                    : s.displayName());
            if (n == step) {
                what.setFont(what.getFont().deriveFont(Font.BOLD));
            }
            Badge badge = new Badge();
            badge.set(label(s), labelColor(s));
            JLabel points = new JLabel("+" + JournalTexts.number(s.points()), SwingConstants.RIGHT);
            JComponent[] cells = {number, what, badge, points};
            for (int i = 0; i < cells.length; i++) {
                c.gridx = i;
                c.gridy = row;
                c.weightx = i == 1 ? 1 : 0;
                c.fill = i == 1 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
                c.anchor = i == 3 ? GridBagConstraints.EAST : GridBagConstraints.WEST;
                cells[i].setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cells[i].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        requestFocusInWindow();
                        goTo(target);
                    }
                });
                history.add(cells[i], c);
            }
        }
    }

    /** The result label of a step, from our point of view. */
    private String label(BattleDetailModel.ReplayStep s) {
        boolean attack = direction == LogDirection.ATTACK;
        return JournalTexts.text("journal.replay.label." + switch (s.kind()) {
            case FIGHT -> attack ? (s.attackerWins() ? "win" : "loss") : (s.attackerWins() ? "fell" : "held");
            case UNDEFENDED -> "undefended";
            case CAPTURED -> attack ? "captured" : "lost";
        });
    }

    /** The text color of a step's result label - a captured fortification too, so it stays readable. */
    private Color labelColor(BattleDetailModel.ReplayStep s) {
        return switch (s.kind()) {
            case FIGHT -> BattleReplayMapPanel.fightColor(s.attackerWins());
            case UNDEFENDED -> AppColors.MUTED;
            case CAPTURED -> AppColors.SUCCESS_TEXT;
        };
    }

    /** The color of a step's bar on the timeline. */
    private Color barColor(BattleDetailModel.ReplayStep s) {
        return switch (s.kind()) {
            case FIGHT -> BattleReplayMapPanel.fightColor(s.attackerWins());
            case UNDEFENDED -> AppColors.MUTED;
            case CAPTURED -> BattleReplayMapPanel.capturedColor();
        };
    }

    // --- small components ---

    /** A colored result label with a rounded, tinted background. */
    private static final class Badge extends JLabel {
        private Color color = AppColors.MUTED;

        Badge() {
            setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 1));
        }

        void set(String text, Color newColor) {
            setText(text);
            color = newColor;
            setForeground(newColor);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AppColors.tint(color, AppColors.TINT));
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 10, 10));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** One bar per step: past steps in their color, later ones faint, the current one higher and outlined. */
    private final class Timeline extends JComponent {
        private static final int HEIGHT = 34;
        private static final int GAP = 2;
        private static final int MAX_WIDTH = 12;

        Timeline() {
            setPreferredSize(new Dimension(400, HEIGHT));
            setToolTipText("");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    BattleReplayPanel.this.requestFocusInWindow();
                    int index = indexAt(e.getX());
                    if (index >= 0) {
                        goTo(index + 1);
                    }
                }
            });
        }

        /** Width of a bar and the gap after it. */
        private double[] geometry() {
            int n = Math.max(1, replay.size());
            double pitch = Math.min(MAX_WIDTH + GAP, (double) getWidth() / n);
            double gap = pitch >= 2 + GAP + 1 ? GAP : Math.max(0, pitch - 2);
            return new double[]{pitch, Math.max(2, pitch - gap)};
        }

        private int indexAt(int x) {
            double pitch = geometry()[0];
            int index = (int) (x / pitch);
            return index >= 0 && index < replay.size() ? index : -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double[] geo = geometry();
            double pitch = geo[0];
            double width = geo[1];
            int h = getHeight();
            for (int i = 0; i < replay.size(); i++) {
                BattleDetailModel.ReplayStep s = replay.steps().get(i);
                boolean current = i + 1 == step;
                boolean tall = s.kind() == BattleDetailModel.ReplayStep.Kind.CAPTURED;
                double barH = current ? h - 4 : tall ? h * 0.8 : h * 0.6;
                double x = i * pitch;
                double y = h - barH - (current ? 2 : 0);
                Color color = barColor(s);
                g2.setColor(i + 1 > step ? AppColors.tint(color, AppColors.TINT) : color);
                g2.fill(new RoundRectangle2D.Double(x, y, width, barH, 2, 2));
                if (current) {
                    g2.setColor(AppColors.INFO);
                    g2.setStroke(new BasicStroke(2f));
                    g2.draw(new RoundRectangle2D.Double(x - 1, y - 1, width + 2, barH + 2, 3, 3));
                }
            }
            g2.dispose();
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = indexAt(event.getX());
            if (index < 0) {
                return null;
            }
            BattleDetailModel.ReplayStep s = replay.steps().get(index);
            return JournalTexts.text("journal.replay.barTooltip", String.valueOf(index + 1), s.displayName(), label(s));
        }
    }

    /** A small triangle (with a bar for start / end), drawn - the UI font has no such glyphs. */
    private static final class ArrowIcon implements Icon {
        private final boolean right;
        private final boolean bar;

        ArrowIcon(boolean right, boolean bar) {
            this.right = right;
            this.bar = bar;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.isEnabled() ? c.getForeground() : AppColors.MUTED);
            double tx = bar ? (right ? x : x + 3) : x;
            Path2D triangle = new Path2D.Double();
            if (right) {
                triangle.moveTo(tx, y);
                triangle.lineTo(tx + 8, y + 5);
                triangle.lineTo(tx, y + 10);
            } else {
                triangle.moveTo(tx + 8, y);
                triangle.lineTo(tx, y + 5);
                triangle.lineTo(tx + 8, y + 10);
            }
            triangle.closePath();
            g2.fill(triangle);
            if (bar) {
                g2.fillRect(right ? x + 9 : x, y, 2, 10);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return bar ? 11 : 8;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }

    /** A small "i" in a circle. */
    private static final class InfoIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AppColors.MUTED);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(x, y, 12, 12);
            g2.fillRect(x + 5, y + 5, 2, 5);
            g2.fillRect(x + 5, y + 2, 2, 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 13;
        }

        @Override
        public int getIconHeight() {
            return 13;
        }
    }
}
