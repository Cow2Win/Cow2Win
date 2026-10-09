package org.c2w.gui;

import org.c2w.gui.common.GuiUtils;
import org.c2w.gui.common.IconLoader;
import org.c2w.infra.Logger;

import javax.swing.*;
import java.awt.*;

import static org.c2w.C2WApp.BASE_TITLE;

/**
 * Startup splash screen shown while {@code C2WApp} loads the workspace and
 * builds the main window: black background framed by white lines, the
 * application name centered on top, {@code splash.png} in the middle and a
 * status line at the bottom that tells what is being done right now (see
 * {@link #setStatus}).
 *
 * <p>All public methods may be called from any thread - they hop to the
 * Swing event thread themselves, so the (non-EDT) startup code in
 * {@code C2WApp#main} can drive the splash directly.
 */
public final class SplashWindow extends JWindow {

    /** Classpath-absolute path to the splash image. */
    private static final String SPLASH_IMAGE_PATH = "/images/app/splash.png";
    private static final int IMAGE_SIZE = 312;

    /** Minimum time the splash stays visible, so it does not just flash up on a fast start. */
    public static final long MIN_VISIBLE_MILLIS = 2000;

    private static final int WIDTH = 480;
    private static final int HEIGHT = 520;
    private static final int LINE_INSET = 10;

    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private long shownAt;

    private SplashWindow() {
        JPanel content = new LinedPanel();
        content.setBorder(BorderFactory.createEmptyBorder(30, 30, 24, 30));

        JLabel titleLabel = new JLabel(BASE_TITLE, SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(40).deriveFont(Font.BOLD));
        content.add(titleLabel, BorderLayout.NORTH);

        JLabel imageLabel = new JLabel(loadImage(), SwingConstants.CENTER);
        content.add(imageLabel, BorderLayout.CENTER);

        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        content.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
    }

    /** Creates and shows the splash screen; blocks until it is on screen. */
    public static SplashWindow showSplash() {
        SplashWindow[] holder = new SplashWindow[1];
        GuiUtils.runOnEdtAndWait(() -> {
            holder[0] = new SplashWindow();
            holder[0].setVisible(true);
        });
        holder[0].shownAt = System.currentTimeMillis();
        return holder[0];
    }

    /**
     * Shows {@code status} (e.g. "Starting database ...") in the status line.
     * Deliberately not logged: the first steps run before config.properties
     * is loaded, i.e. before {@link Logger}'s log file location is known.
     */
    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    /**
     * Blocks the calling thread until the splash has been visible for at
     * least {@link #MIN_VISIBLE_MILLIS}. Must not be called on the Swing
     * event thread.
     */
    public void awaitMinimumDisplayTime() {
        long remaining = MIN_VISIBLE_MILLIS - (System.currentTimeMillis() - shownAt);
        if (remaining <= 0) {
            return;
        }
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Hides and disposes the splash screen. */
    public void close() {
        SwingUtilities.invokeLater(this::dispose);
    }

    /**
     * The splash image with its black lines recolored white, so they show on
     * the black background (see {@link IconLoader#iconFor(String, int, Color)}).
     */
    private static Icon loadImage() {
        ImageIcon icon = IconLoader.iconForWithDayColor(SPLASH_IMAGE_PATH, IMAGE_SIZE);
        if (icon == null) {
            Logger.log("Splash image not found: " + SPLASH_IMAGE_PATH);
        }
        return icon;
    }

    /** Black panel with a white double-line frame and white separator lines below the title / above the status. */
    private static final class LinedPanel extends JPanel {

        LinedPanel() {
            super(new BorderLayout(0, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                int w = getWidth();
                int h = getHeight();

                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(LINE_INSET, LINE_INSET, w - 2 * LINE_INSET - 1, h - 2 * LINE_INSET - 1);
                g2.setStroke(new BasicStroke(1f));
                int inner = LINE_INSET + 5;
                g2.drawRect(inner, inner, w - 2 * inner - 1, h - 2 * inner - 1);

                Component north = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.NORTH);
                Component south = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.SOUTH);
                int lineFrom = inner + 30;
                int lineTo = w - inner - 30;
                if (north != null) {
                    int y = north.getY() + north.getHeight() + 8;
                    g2.drawLine(lineFrom, y, lineTo, y);
                }
                if (south != null) {
                    int y = south.getY() - 8;
                    g2.drawLine(lineFrom, y, lineTo, y);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
