package org.c2w.gui.common;

import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IconLoader {

    public static final Color GREEN = new Color(78,133,66);
    public static final Color BLUE = new Color(27,88,124);
    public static final Color RED = new Color(159,41,54);

    static final int TOOLBAR_ICON_SIZE = 20;

    private static final String BACKGROUND_IMAGE = "/images/app/background.png";

    private static final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();

    /** RGB mask (alpha channel excluded) of a fully black pixel - see {@link #iconFor(String, int, Color)}. */
    private static final int BLACK_RGB_MASK = 0x00FFFFFF;

    private IconLoader() {
    }

    /** Returns the icon for the given classpath-absolute image path, scaled to size x size, or null if not found. */
    public static ImageIcon iconFor(String imagePath, int size) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        String cacheKey = imagePath + "@" + size;
        ImageIcon cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ImageIcon loaded = loadScaledIcon(imagePath, size);
        if (loaded != null) {
            cache.put(cacheKey, loaded);
        }
        return loaded;
    }

    /**
     * Same as {@link #iconFor(String, int)}, but every fully black pixel
     * (RGB 0,0,0 - transparency/alpha of each pixel is kept as-is) of the
     * scaled image is overwritten with the given color, so e.g. a black
     * icon glyph can be recolored without needing a separate image file per
     * color. Every other pixel is left untouched, so non-black icon
     * content (anti-aliased edges, other colors) is unaffected.
     *
     * @return the recolored icon, or null if the image was not found
     */
    public static ImageIcon iconFor(String imagePath, int size, Color color) {
        if (imagePath == null || imagePath.isBlank() || color == null) {
            return null;
        }
        String cacheKey = imagePath + "@" + size + "@" + color.getRGB();
        ImageIcon cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ImageIcon loaded = loadScaledIcon(imagePath, size);
        if (loaded == null) {
            return null;
        }
        ImageIcon recolored = recolorBlackPixels(loaded, color);
        cache.put(cacheKey, recolored);
        return recolored;
    }

    public static ImageIcon iconForButton(String imagePath){
        Color c = UIManager.getColor("Label.foreground");
        return iconFor(imagePath,TOOLBAR_ICON_SIZE,c);
    }
    private static ImageIcon loadScaledIcon(String imagePath, int size) {
        URL resource = IconLoader.class.getResource(imagePath);
        if (resource == null) {
            Logger.log("Image not found on classpath: " + imagePath);
            return null;
        }
        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /** Draws {@code icon} into an ARGB {@link BufferedImage} and replaces every fully black pixel's RGB with {@code color}'s, keeping each pixel's original alpha. */
    private static ImageIcon recolorBlackPixels(ImageIcon icon, Color color) {
        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        g.drawImage(icon.getImage(), 0, 0, null);
        g.dispose();

        int replacementRgb = color.getRGB() & BLACK_RGB_MASK;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = buffered.getRGB(x, y);
                if ((argb & BLACK_RGB_MASK) == 0) {
                    int alpha = argb & 0xFF000000;
                    buffered.setRGB(x, y, alpha | replacementRgb);
                }
            }
        }
        return new ImageIcon(buffered);
    }

    public static Image getBackgroundImage(){
        URL resource = IconLoader.class.getResource(BACKGROUND_IMAGE);
        if (resource == null) {
            Logger.log("Image not found on classpath: " + BACKGROUND_IMAGE);
            return null;
        }
        return  new ImageIcon(resource).getImage();
    }
}
