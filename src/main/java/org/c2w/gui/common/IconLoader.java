package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IconLoader {

    private static final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();

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

    private static ImageIcon loadScaledIcon(String imagePath, int size) {
        URL resource = IconLoader.class.getResource(imagePath);
        if (resource == null) {
            System.err.println("Image not found on classpath: " + imagePath);
            return null;
        }
        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }
}
