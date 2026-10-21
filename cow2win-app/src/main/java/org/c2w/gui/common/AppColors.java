package org.c2w.gui.common;

import java.awt.*;

/**
 * The central color palette of Cow2Win (app and DataTool). Every color has a role - what it means -
 * not a color name: the same meaning uses the same constant. This is the only class of the main code
 * that creates colors ({@code new Color(...)}); everything else takes a constant, a {@link #tint} or a
 * {@link #mix} from here.
 * <p>
 * Each status comes as a pair: a base color ({@code ACCENT}, {@code SUCCESS}, ...) for areas, icons,
 * fill bars and tinted backgrounds, and a light text color ({@code ..._TEXT}) for text, numbers and
 * links, readable (contrast at least 4.5 : 1) on the Material Oceanic background {@code #263238} and on
 * the fortification tile. Text in a status color always uses the {@code ..._TEXT} variant.
 */
public final class AppColors {

    // --- Accent and status -------------------------------------------------------------------

    /** Accent: default icons, the active process stage, selection and focus (areas and icons). */
    public static final Color ACCENT = new Color(0x00, 0x96, 0x88);
    /** Accent as text: the "Live" status, information areas, accent-colored text. */
    public static final Color ACCENT_TEXT = new Color(0x50, 0xC2, 0xAA);

    /** Success/gain: "new" icons, full fill bar, won battles, highlighted values (areas and icons). */
    public static final Color SUCCESS = new Color(0x4E, 0x85, 0x42);
    /** Success/gain as text: plus values, matches, won fights. */
    public static final Color SUCCESS_TEXT = new Color(0x81, 0xC7, 0x84);

    /** Warning/attention: partial fill bar, the "attention" traffic light, warning rows (areas and icons). */
    public static final Color WARNING = new Color(0xD9, 0x82, 0x2B);
    /** Warning as text: "unsaved" hint, warning texts in the journal dialogs. */
    public static final Color WARNING_TEXT = new Color(0xFF, 0xB7, 0x4D);

    /** Error/loss: delete/remove icons, "unsaved" save icon, empty fill bar, lost battles (areas and icons). */
    public static final Color ERROR = new Color(0x9F, 0x29, 0x36);
    /** Error/loss as text: minus values, error and limit texts, invalid entries. */
    public static final Color ERROR_TEXT = new Color(0xEF, 0x9A, 0x9A);

    /** Information/marker: the current replay step, the comparison side of the tornado chart (areas). */
    public static final Color INFO = new Color(0x42, 0xA5, 0xF5);
    /** Information as text: links. */
    public static final Color INFO_TEXT = new Color(0x64, 0xB5, 0xF6);

    // --- Heatmap -----------------------------------------------------------------------------

    // Palette "Glut": the levels get lighter from strong to weak, so they differ in brightness, not only in hue.

    /** Very strong fortification (above 110 %) in the heatmap of the fortification map (dark red, areas only). */
    public static final Color HEATMAP_VERY_HIGH = new Color(0x8E, 0x1B, 0x1B);
    /** Strong fortification (100 % to 110 %) in the heatmap of the fortification map (red, areas only). */
    public static final Color HEATMAP_HIGH = new Color(0xD2, 0x46, 0x2A);
    /** Slightly weak fortification (90 % to below 100 %) in the heatmap of the fortification map (orange, areas only). */
    public static final Color HEATMAP_MEDIUM = new Color(0xF0, 0x8C, 0x2E);
    /** Weak fortification (below 90 %) in the heatmap of the fortification map (light yellow, areas only). */
    public static final Color HEATMAP_LOW = new Color(0xF6, 0xD3, 0x65);

    // --- Fortification type ------------------------------------------------------------------

    /** Everything belonging to the hero side: map labels, tables, replay markers, chips. */
    public static final Color HERO = Color.WHITE;
    /** Everything belonging to the titan side: map labels, tables, replay markers, chips. */
    public static final Color TITAN = new Color(0xD7, 0xBA, 0x59);

    // --- Neutral tones -----------------------------------------------------------------------

    /** Muted text: headings and secondary text of panels, empty hints, disabled text. */
    public static final Color TEXT_MUTED = new Color(255, 255, 255, 140);
    /** Muted areas, dots and icons: disabled icons, "outdated per journal", fights without battle. */
    public static final Color MUTED = new Color(0x90, 0x9A, 0xA4);
    /** Thin separator lines and outlines, table grids. */
    public static final Color LINE = new Color(255, 255, 255, 40);
    /** Stronger lines: switch borders, arrows of the process bar. */
    public static final Color LINE_STRONG = new Color(255, 255, 255, 70);
    /** Lightly raised surface: tiles of the process bar, fortification tiles. */
    public static final Color SURFACE = new Color(255, 255, 255, 20);
    /** Raised surface under the mouse. */
    public static final Color SURFACE_HOVER = new Color(255, 255, 255, 32);
    /** Selected segment of a switch (heroes/titans in the context bar). */
    public static final Color SURFACE_SELECTED = new Color(0x5A, 0x6A, 0x72);
    /** Darkening overlay: context bar, side column of the stages. */
    public static final Color SHADE = new Color(0, 0, 0, 70);

    // --- Tints -------------------------------------------------------------------------------

    /** Alpha (0-255) of normally tinted areas: hints, battle list rows, chips, highlighted cells. */
    public static final int TINT = 60;
    /** Alpha (0-255) of strongly tinted areas: selected, active or hovered. */
    public static final int TINT_STRONG = 80;

    private AppColors() {
        // Constants and helpers only
    }

    /** {@code base} with the same RGB values and the given {@code alpha} (0-255), e.g. {@link #TINT}. */
    public static Color tint(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /**
     * Linear mix of {@code a} and {@code b} per channel (RGB and alpha):
     * {@code ratio} 0 gives {@code a}, 1 gives {@code b}.
     */
    public static Color mix(Color a, Color b, double ratio) {
        double r = Math.max(0, Math.min(1, ratio));
        return new Color(
                channel(a.getRed(), b.getRed(), r),
                channel(a.getGreen(), b.getGreen(), r),
                channel(a.getBlue(), b.getBlue(), r),
                channel(a.getAlpha(), b.getAlpha(), r));
    }

    /** {@code c} as {@code #rrggbb} (alpha ignored) for HTML stylesheets. */
    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static int channel(int a, int b, double ratio) {
        return (int) Math.round(a + (b - a) * ratio);
    }
}
