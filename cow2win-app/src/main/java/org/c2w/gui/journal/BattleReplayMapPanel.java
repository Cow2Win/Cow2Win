package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The simplified map of the visual replay: all fortifications of the catalog
 * (heroes and titans together) on their grid ({@code row}/{@code column}, 8 x 5),
 * lines to their prerequisites, and per fortification a tile with one dot per
 * position - defended, beaten, captured without a fight. Captured and locked
 * fortifications and the current one are marked. Only for the replay - the
 * map of the strategic concept is a different thing.
 */
final class BattleReplayMapPanel extends JComponent {

    // status colors with good contrast on the dark theme
    static final Color GREEN = new Color(102, 187, 106);
    static final Color RED = new Color(239, 83, 80);
    static final Color GOLD = new Color(255, 193, 7);
    static final Color BLUE = new Color(66, 165, 245);
    static final Color GREY = new Color(144, 154, 164);
    private static final Color HERO = new Color(179, 157, 219);
    private static final Color TITAN = new Color(77, 208, 225);

    static final int ROWS = 8;
    static final int COLUMNS = 5;
    private static final double PITCH_X = 132;
    private static final double PITCH_Y = 72;
    private static final double TILE_W = 120;
    private static final double TILE_H = 58;
    private static final double MARGIN = 8;

    /** Grid cells that differ from the catalog, where two fortifications share a cell: id -> {row, column}. */
    private static final Map<String, int[]> CELL_OVERRIDES = Map.of("shooting-range", new int[]{3, 1});

    /** How a position dot looks. */
    enum Dot {
        HELD, TAKEN, FREE, LOCKED
    }

    private final LogDirection direction;
    private BattleReplay.State state;

    BattleReplayMapPanel(LogDirection direction) {
        this.direction = direction;
        setOpaque(true);
        setToolTipText("");
        setPreferredSize(new Dimension((int) (COLUMNS * PITCH_X + 2 * MARGIN), (int) (ROWS * PITCH_Y + 2 * MARGIN)));
        setMinimumSize(new Dimension(300, 260));
    }

    void setState(BattleReplay.State newState) {
        this.state = newState;
        repaint();
    }

    /** The grid cell of a fortification ({row, column}), with the collision overrides. */
    static int[] cell(Fortification f) {
        int[] override = CELL_OVERRIDES.get(f.id());
        return override != null ? override : new int[]{f.row(), f.column()};
    }

    private double scale() {
        double sx = (getWidth() - 2 * MARGIN) / (COLUMNS * PITCH_X);
        double sy = (getHeight() - 2 * MARGIN) / (ROWS * PITCH_Y);
        return Math.max(0.3, Math.min(sx, sy));
    }

    private Point2D center(Fortification f) {
        double s = scale();
        double x0 = (getWidth() - COLUMNS * PITCH_X * s) / 2;
        double y0 = (getHeight() - ROWS * PITCH_Y * s) / 2;
        int[] cell = cell(f);
        return new Point2D.Double(x0 + (cell[1] + 0.5) * PITCH_X * s, y0 + (cell[0] + 0.5) * PITCH_Y * s);
    }

    private RoundRectangle2D tile(Fortification f, boolean current) {
        double s = scale() * (current ? 1.08 : 1);
        Point2D c = center(f);
        double w = TILE_W * s;
        double h = TILE_H * s;
        return new RoundRectangle2D.Double(c.getX() - w / 2, c.getY() - h / 2, w, h, 12 * s, 12 * s);
    }

    static Color background() {
        Color c = UIManager.getColor("Panel.background");
        return c != null ? c : new Color(38, 50, 56);
    }

    static Color foreground() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.WHITE;
    }

    static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(background());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (state == null) {
                return;
            }
            paintLines(g);
            BattleReplay.FortState current = null;
            for (BattleReplay.FortState f : state.forts().values()) {
                if (f.current()) {
                    current = f;
                } else {
                    paintTile(g, f);
                }
            }
            if (current != null) {
                paintTile(g, current);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintLines(Graphics2D g) {
        double s = scale();
        Color fg = foreground();
        for (BattleReplay.FortState f : state.forts().values()) {
            Point2D from = center(f.fort());
            for (String id : f.fort().prerequisites()) {
                BattleReplay.FortState pre = state.forts().get(id);
                if (pre == null) {
                    continue;
                }
                Point2D to = center(pre.fort());
                g.setColor(pre.captured() ? alpha(fg, 170) : alpha(fg, 45));
                g.setStroke(new BasicStroke((float) ((pre.captured() ? 2.4 : 1.2) * s)));
                g.drawLine((int) from.getX(), (int) from.getY(), (int) to.getX(), (int) to.getY());
            }
        }
    }

    private void paintTile(Graphics2D g, BattleReplay.FortState f) {
        double s = scale() * (f.current() ? 1.08 : 1);
        RoundRectangle2D tile = tile(f.fort(), f.current());
        Color bg = background();
        Color fg = foreground();
        // captured = the attacker won the fortification - the same color in both directions
        Color own = GREEN;
        Composite composite = g.getComposite();
        if (f.locked()) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        }
        // fill: opaque base so lines stay below, then the tint
        g.setColor(JournalSwing.blend(bg, alpha(fg, 22)));
        g.fill(tile);
        if (f.captured()) {
            g.setColor(alpha(own, 70));
            g.fill(tile);
        }
        // border
        if (f.current()) {
            g.setColor(BLUE);
            g.setStroke(new BasicStroke((float) (3 * s)));
        } else if (f.locked()) {
            g.setColor(alpha(fg, 140));
            g.setStroke(new BasicStroke((float) (1.2 * s), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10,
                    new float[]{(float) (4 * s), (float) (3 * s)}, 0));
        } else if (f.captured()) {
            g.setColor(own);
            g.setStroke(new BasicStroke((float) (1.6 * s)));
        } else {
            g.setColor(alpha(fg, 90));
            g.setStroke(new BasicStroke((float) (1.2 * s)));
        }
        g.draw(tile);

        double x = tile.getX();
        double y = tile.getY();
        double w = tile.getWidth();
        double pad = 7 * s;
        // H / T mark
        boolean hero = f.fort().type() == FortificationType.HERO;
        Font markFont = getFont().deriveFont(Font.BOLD, (float) Math.max(8, 10 * s));
        g.setFont(markFont);
        FontMetrics mm = g.getFontMetrics();
        String mark = hero ? "H" : "T";
        double markW = mm.stringWidth(mark) + 6 * s;
        double markH = mm.getAscent() + 2 * s;
        double markX = x + w - pad - markW;
        double markY = y + pad - 1 * s;
        Color markColor = hero ? HERO : TITAN;
        g.setColor(alpha(markColor, 60));
        g.fill(new RoundRectangle2D.Double(markX, markY, markW, markH, 5 * s, 5 * s));
        g.setColor(markColor);
        g.drawString(mark, (float) (markX + 3 * s), (float) (markY + mm.getAscent() - 0.5 * s));
        // flag / lock left of the mark
        double iconSize = markH;
        double iconX = markX - iconSize - 3 * s;
        if (f.captured()) {
            paintFlag(g, iconX, markY, iconSize, own);
        } else if (f.locked()) {
            paintLock(g, iconX, markY, iconSize, fg);
        } else {
            iconX = markX;
        }
        // name
        Font nameFont = getFont().deriveFont(Font.BOLD, (float) Math.max(9, Math.min(14, 11.5 * s)));
        g.setFont(nameFont);
        g.setColor(fg);
        String name = clip(JournalTexts.fortification(f.fort().id(), f.fort().id()), g.getFontMetrics(),
                (int) (iconX - x - pad - 3 * s));
        g.drawString(name, (float) (x + pad), (float) (markY + g.getFontMetrics().getAscent()));
        // dots
        int capacity = f.capacity();
        double d = 10 * s;
        double gap = 14 * s;
        double dotsW = (capacity - 1) * gap + d;
        double dx = x + (w - dotsW) / 2;
        double dy = y + tile.getHeight() - pad - d;
        BattleDetailModel.ReplayStep step = state.current();
        for (int p = 1; p <= capacity; p++) {
            double px = dx + (p - 1) * gap;
            Dot dot = f.beaten().contains(p) ? Dot.TAKEN : f.free().contains(p) ? Dot.FREE : Dot.HELD;
            paintDot(g, dot, direction, px, dy, d);
            if (f.current() && step != null && step.kind() == BattleDetailModel.ReplayStep.Kind.FIGHT
                    && step.position() == p) {
                g.setColor(step.attackerWins() ? GREEN : RED);
                g.setStroke(new BasicStroke((float) (2.2 * s)));
                double r = 4 * s;
                g.draw(new Ellipse2D.Double(px - r, dy - r, d + 2 * r, d + 2 * r));
            }
        }
        g.setComposite(composite);
    }

    /** One position dot (also used by the legend). */
    static void paintDot(Graphics2D g, Dot dot, LogDirection direction, double x, double y, double d) {
        Ellipse2D circle = new Ellipse2D.Double(x, y, d, d);
        Color fg = foreground();
        switch (dot) {
            case HELD -> {
                g.setColor(GREY);
                g.fill(circle);
            }
            case TAKEN -> {
                g.setColor(GREEN);
                g.fill(circle);
            }
            case FREE -> {
                g.setColor(alpha(fg, 200));
                g.setStroke(new BasicStroke((float) Math.max(1, d / 8), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                        10, new float[]{(float) (d / 5), (float) (d / 6)}, 0));
                g.draw(circle);
            }
            case LOCKED -> {
                Composite c = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                g.setColor(GREY);
                g.fill(circle);
                g.setComposite(c);
            }
        }
    }

    /** A small flag: pole and pennant. */
    static void paintFlag(Graphics2D g, double x, double y, double size, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) Math.max(1, size / 9)));
        g.drawLine((int) (x + size * 0.2), (int) (y + size * 0.05), (int) (x + size * 0.2), (int) (y + size));
        Path2D pennant = new Path2D.Double();
        pennant.moveTo(x + size * 0.2, y + size * 0.05);
        pennant.lineTo(x + size * 0.95, y + size * 0.3);
        pennant.lineTo(x + size * 0.2, y + size * 0.58);
        pennant.closePath();
        g.fill(pennant);
    }

    /** A small padlock: shackle and body. */
    static void paintLock(Graphics2D g, double x, double y, double size, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) Math.max(1, size / 9)));
        double bodyY = y + size * 0.45;
        g.draw(new java.awt.geom.Arc2D.Double(x + size * 0.25, y + size * 0.05, size * 0.5, size * 0.7, 0, 180,
                java.awt.geom.Arc2D.OPEN));
        g.drawLine((int) (x + size * 0.25), (int) (y + size * 0.4), (int) (x + size * 0.25), (int) bodyY);
        g.drawLine((int) (x + size * 0.75), (int) (y + size * 0.4), (int) (x + size * 0.75), (int) bodyY);
        g.fill(new RoundRectangle2D.Double(x + size * 0.1, bodyY, size * 0.8, size * 0.55, size * 0.15, size * 0.15));
    }

    /** {@code text} shortened with "…" to fit {@code width}. */
    static String clip(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = fm.getFont().canDisplay('…') ? "…" : "...";
        for (int end = text.length() - 1; end > 0; end--) {
            String s = text.substring(0, end).stripTrailing() + ellipsis;
            if (fm.stringWidth(s) <= width) {
                return s;
            }
        }
        return ellipsis;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (state == null) {
            return null;
        }
        List<BattleReplay.FortState> forts = new ArrayList<>(state.forts().values());
        // the current tile is painted last (on top) - test it first
        forts.sort((a, b) -> Boolean.compare(b.current(), a.current()));
        for (BattleReplay.FortState f : forts) {
            if (tile(f.fort(), f.current()).contains(event.getPoint())) {
                return JournalTexts.text("journal.replay.fortTooltip",
                        JournalTexts.fortification(f.fort().id(), f.fort().id()),
                        JournalTexts.text(f.fort().type() == FortificationType.HERO
                                ? "journal.replay.heroes" : "journal.replay.titans"),
                        String.valueOf(f.capacity()));
            }
        }
        return null;
    }
}
