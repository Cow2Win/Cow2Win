package org.c2w.gui;

import org.c2w.gui.action.Stage;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The process bar below the {@link ContextBar}: one tile per process stage - input, strategic
 * concept, output (see {@link #STAGES}) - with number, title and subtitle, connected by small
 * arrows, plus a "next step" button on the right (hidden for now).
 *
 * <p>The {@linkplain #setActiveStage active stage} is highlighted in teal; as long as there are
 * no separate stage views yet, that is the strategic concept (the main window shows the
 * fortification map). The tiles are display only until the stage views of M3 exist - a click
 * does nothing yet; in M3 it will switch to the stage's view. Each tile already has a traffic
 * light ({@link StageStatus}), not shown until it is set from real data.
 */
public class ProcessBar extends JPanel {

    /** The process stages, in display order (numbered 1 to 3). */
    static final List<Stage> STAGES = List.of(Stage.INPUT, Stage.CONCEPT, Stage.OUTPUT);

    private static final String KEY_NEXT_STEP = "processBar.nextStep";

    /** Teal accent of the active tile and the "next step" button. */
    private static final Color ACCENT = IconLoader.BLUE;

    /** Line color of the tile borders, the arrows and the line under the bar - the same as under the context bar. */
    private static final Color LINE_COLOR = new Color(255, 255, 255, 40);

    private final List<StageTile> tiles = new ArrayList<>();
    private final NextStepButton nextStepButton = new NextStepButton();

    public ProcessBar() {
        super(new GridBagLayout());
        // No fill of its own, so the background image shows through (see Cow2Frame).
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, LINE_COLOR),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        for (int i = 0; i < STAGES.size(); i++) {
            Stage stage = STAGES.get(i);
            if (i > 0) {
                c.weightx = 0;
                c.insets = new Insets(0, 4, 0, 4);
                add(new Arrow(), c);
            }
            StageTile tile = new StageTile(stage, i + 1);
            tiles.add(tile);
            c.weightx = 1;
            c.insets = new Insets(0, 0, 0, 0);
            add(tile, c);
        }
        c.weightx = 0;
        c.insets = new Insets(0, 12, 0, 0);
        add(nextStepButton, c);

        setActiveStage(Stage.CONCEPT);
    }

    /** Highlights the tile of {@code stage} (and only that one). */
    public void setActiveStage(Stage stage) {
        tiles.forEach(tile -> tile.setActive(tile.stage() == stage));
    }

    /** The highlighted stage. */
    public Stage activeStage() {
        return tiles.stream().filter(StageTile::isActive).map(StageTile::stage).findFirst().orElse(null);
    }

    /** Sets the traffic light of {@code stage}'s tile. */
    public void setStatus(Stage stage, StageStatus status) {
        tile(stage).setStatus(status);
    }

    /**
     * Binds the "next step" button to {@code action} and shows it - or hides it for
     * {@code null}; without the button the tiles use the full width.
     */
    public void setNextStepAction(Action action) {
        nextStepButton.setAction(action);
        nextStepButton.setText(LanguageService.displayName(KEY_NEXT_STEP));
        setNextStepVisible(action != null);
    }

    public void setNextStepVisible(boolean visible) {
        nextStepButton.setVisible(visible);
        revalidate();
    }

    public boolean isNextStepVisible() {
        return nextStepButton.isVisible();
    }

    /** The tiles, in display order. */
    List<StageTile> tiles() {
        return List.copyOf(tiles);
    }

    StageTile tile(Stage stage) {
        return tiles.stream().filter(tile -> tile.stage() == stage).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No tile for stage " + stage));
    }

    /** {@code text} cut with "…" so that it fits into {@code width} pixels of {@code metrics}. */
    static String ellipsize(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return end == 0 ? "" : text.substring(0, end).stripTrailing() + ellipsis;
    }

    /**
     * One stage of the process bar: rounded tile with the number in a circle, the stage name
     * as title, a short description as subtitle and - once set - a traffic light on the right.
     * Display only until the stage views of M3 exist: not clickable, no hover effect, only the
     * subtitle as tooltip.
     */
    static final class StageTile extends JComponent {

        private static final int HEIGHT = 50;
        private static final int PADDING = 12;
        private static final int CIRCLE = 26;
        private static final int ARC = 10;
        private static final int LIGHT = 10;
        private static final int MIN_WIDTH = 120;

        private static final Color FILL = new Color(255, 255, 255, 18);
        private static final Color ACTIVE_FILL = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 48);
        private static final Color CIRCLE_FILL = new Color(255, 255, 255, 30);
        private static final Color SUBTITLE_COLOR = new Color(255, 255, 255, 140);

        private final Stage stage;
        private final int number;
        private final String title;
        private final String subtitle;
        private boolean active;
        private StageStatus status = StageStatus.NONE;

        StageTile(Stage stage, int number) {
            this.stage = stage;
            this.number = number;
            this.title = LanguageService.displayName(stage.stageTextKey());
            this.subtitle = LanguageService.displayName(stage.subtitleKey());
            setOpaque(false);
            setToolTipText(subtitle);
            Font base = UIManager.getFont("Label.font");
            setFont(base != null ? base : new JLabel().getFont());
        }

        Stage stage() {
            return stage;
        }

        int number() {
            return number;
        }

        String title() {
            return title;
        }

        String subtitle() {
            return subtitle;
        }

        boolean isActive() {
            return active;
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        StageStatus status() {
            return status;
        }

        /** Shows the traffic light in the status' color - none for {@link StageStatus#NONE}. */
        void setStatus(StageStatus status) {
            this.status = status == null ? StageStatus.NONE : status;
            repaint();
        }

        private Font titleFont() {
            return getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 1.5f);
        }

        private Font subtitleFont() {
            return getFont().deriveFont(Font.PLAIN, getFont().getSize2D() - 1.5f);
        }

        /**
         * The same small width for every tile, so the bar's layout (equal weights) gives all
         * three tiles the same width; texts that do not fit are cut with "…".
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(MIN_WIDTH, HEIGHT);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;

                g2.setColor(active ? ACTIVE_FILL : FILL);
                g2.fillRoundRect(0, 0, w, h, ARC, ARC);
                g2.setColor(active ? ACCENT : LINE_COLOR);
                g2.drawRoundRect(0, 0, w, h, ARC, ARC);

                // Number in a circle.
                int circleY = (getHeight() - CIRCLE) / 2;
                g2.setColor(active ? ACCENT : CIRCLE_FILL);
                g2.fillOval(PADDING, circleY, CIRCLE, CIRCLE);
                g2.setFont(titleFont().deriveFont(Font.BOLD, getFont().getSize2D()));
                FontMetrics numberMetrics = g2.getFontMetrics();
                String numberText = String.valueOf(number);
                g2.setColor(active ? Color.WHITE : foreground());
                g2.drawString(numberText, PADDING + (CIRCLE - numberMetrics.stringWidth(numberText)) / 2,
                        circleY + (CIRCLE - numberMetrics.getHeight()) / 2 + numberMetrics.getAscent());

                // Traffic light, only for a status other than NONE.
                Color light = status.color();
                int textRight = getWidth() - PADDING;
                if (light != null) {
                    g2.setColor(light);
                    g2.fillOval(getWidth() - PADDING - LIGHT, (getHeight() - LIGHT) / 2, LIGHT, LIGHT);
                    textRight -= LIGHT + 8;
                }

                // Title and subtitle, cut with "…" if the tile is too narrow.
                int textX = PADDING + CIRCLE + 10;
                int textWidth = Math.max(0, textRight - textX);
                FontMetrics titleMetrics = g2.getFontMetrics(titleFont());
                FontMetrics subtitleMetrics = g2.getFontMetrics(subtitleFont());
                int textHeight = titleMetrics.getHeight() + subtitleMetrics.getHeight();
                int top = (getHeight() - textHeight) / 2;
                g2.setFont(titleFont());
                g2.setColor(foreground());
                g2.drawString(ellipsize(title, titleMetrics, textWidth), textX, top + titleMetrics.getAscent());
                g2.setFont(subtitleFont());
                g2.setColor(SUBTITLE_COLOR);
                g2.drawString(ellipsize(subtitle, subtitleMetrics, textWidth), textX,
                        top + titleMetrics.getHeight() + subtitleMetrics.getAscent());
            } finally {
                g2.dispose();
            }
        }

        private static Color foreground() {
            Color color = UIManager.getColor("Label.foreground");
            return color != null ? color : Color.WHITE;
        }
    }

    /** The small arrow (►) between two tiles, in line color. */
    private static final class Arrow extends JComponent {

        private static final Color COLOR = new Color(255, 255, 255, 70);

        Arrow() {
            setOpaque(false);
            setPreferredSize(new Dimension(8, StageTile.HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR);
                int midY = getHeight() / 2;
                g2.fillPolygon(new int[]{0, getWidth(), 0}, new int[]{midY - 6, midY, midY + 6}, 3);
            } finally {
                g2.dispose();
            }
        }
    }

    /** The "next step →" button: teal, white bold text, rounded corners. Hidden until it gets an action. */
    private static final class NextStepButton extends JButton {

        NextStepButton() {
            super(LanguageService.displayName(KEY_NEXT_STEP));
            setUI(new BasicButtonUI());
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 1f));
            setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setVisible(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? ACCENT.brighter() : ACCENT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
