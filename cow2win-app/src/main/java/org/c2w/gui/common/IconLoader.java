package org.c2w.gui.common;

import org.c2w.data.model.FortificationType;
import org.c2w.infra.CatalogStore;
import org.c2w.infra.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IconLoader {

    static final int TOOLBAR_ICON_SIZE = 20;

    private static final String BACKGROUND_IMAGE = "/images/app/background.png";

    /** Image folders whose files belong to the game catalog (each has its own placeholder on the classpath). */
    private static final List<String> CATALOG_IMAGE_FOLDERS =
            List.of("/images/heroes/", "/images/titans/", "/images/pets/", "/images/flags/");

    /** File name of the placeholder in every catalog image folder, e.g. {@code /images/heroes/placeholder.png}. */
    private static final String PLACEHOLDER_FILE_NAME = "placeholder.png";

    private static final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();

    /**
     * RGB mask (alpha channel excluded) of a fully black pixel - see {@link #iconFor(String, int, Color)}.
     */
    private static final int BLACK_RGB_MASK = 0x00FFFFFF;

    private IconLoader() {
    }

    /**
     * Returns the icon for the given image path (e.g. {@code /images/heroes/Adam.png}, see {@link #resolve}), scaled to size x size, or null if not found.
     */
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

    public static ImageIcon iconForWithDayColor(String imagePath, int size) {
        Color tmp = new JLabel("").getForeground();
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY) {
            tmp = AppColors.SUCCESS;
        }
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY) {
            tmp = AppColors.ERROR;
        }
        return iconFor(imagePath, size, tmp);
    }

    public static ImageIcon iconForButton(String imagePath) {
        Color c = UIManager.getColor("Label.foreground");
        return iconFor(imagePath, TOOLBAR_ICON_SIZE, c);
    }

    /**
     * The icon of an action that follows the selected fortification type: like
     * {@link #iconForButton} for heroes, in the titan color for titans (see
     * {@link FortificationTypeStyle#actionIconColor}).
     */
    public static ImageIcon iconForType(String imagePath, FortificationType type) {
        return iconFor(imagePath, TOOLBAR_ICON_SIZE, FortificationTypeStyle.actionIconColor(type));
    }

    private static ImageIcon loadScaledIcon(String imagePath, int size) {
        URL resource = resolve(imagePath);
        if (resource == null) {
            return null;
        }
        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /**
     * Where an image path points to: a catalog image ({@code /images/heroes|titans|pets|flags/...},
     * see {@link #CATALOG_IMAGE_FOLDERS}) comes from the active catalog ({@link CatalogStore}) - if
     * it is missing there, the folder's placeholder is used instead; every other image (the
     * placeholders, {@code /images/app/...}) comes from the classpath. Null if not found.
     */
    private static URL resolve(String imagePath) {
        String folder = catalogImageFolder(imagePath);
        if (folder == null) {
            URL resource = IconLoader.class.getResource(imagePath);
            if (resource == null) {
                Logger.log("Image not found on classpath: " + imagePath);
            }
            return resource;
        }
        URL catalogImage = CatalogStore.imageUrl(imagePath.substring(1));
        if (catalogImage != null) {
            return catalogImage;
        }
        Logger.log("Image not found in the catalog, using the placeholder: " + imagePath);
        return IconLoader.class.getResource(folder + PLACEHOLDER_FILE_NAME);
    }

    /** The catalog image folder (e.g. {@code /images/heroes/}) of {@code imagePath}; null if it is no catalog image. */
    private static String catalogImageFolder(String imagePath) {
        for (String folder : CATALOG_IMAGE_FOLDERS) {
            if (imagePath.startsWith(folder) && !imagePath.equals(folder + PLACEHOLDER_FILE_NAME)) {
                return folder;
            }
        }
        return null;
    }


    /**
     * Draws {@code icon} into an ARGB {@link BufferedImage} and replaces every fully black pixel's RGB with {@code color}'s, keeping each pixel's original alpha.
     */
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

    public static Image getBackgroundImage() {
        URL resource = IconLoader.class.getResource(BACKGROUND_IMAGE);
        if (resource == null) {
            Logger.log("Image not found on classpath: " + BACKGROUND_IMAGE);
            return null;
        }
        return new ImageIcon(resource).getImage();
    }
}
