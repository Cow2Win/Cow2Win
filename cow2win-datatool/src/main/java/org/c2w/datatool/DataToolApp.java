package org.c2w.datatool;

import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;
import org.c2w.datatool.data.DataSet;
import org.c2w.datatool.gui.DataToolFrame;
import org.c2w.gui.common.MeasuredCheckBoxUI;
import org.c2w.gui.common.MeasuredLabelUI;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Master data tool for the system data shipped with Cow2Win (heroes, titans, pets, war flags,
 * fortifications, CowScore defaults, hero combos, titan templates, display names, avatars).
 * Edits only {@code cow2win-app/src/main/resources} - never a workspace. Started from the IDE,
 * not shipped.
 *
 * <p>Program argument (optional): the repository or the resources folder. Without one, the
 * folder is searched upwards from the working directory, then asked for.
 */
public final class DataToolApp {

    private DataToolApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            Path root = locateResources(args);
            if (root == null) {
                return;
            }
            try {
                new DataToolFrame(DataSet.load(root)).setVisible(true);
            } catch (IOException | RuntimeException e) {
                JOptionPane.showMessageDialog(null, "Could not load " + root + ":\n" + e, "Data tool",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /** Same look and feel as the app (see C2WApp), Swing's default if it cannot be installed. */
    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new MaterialLookAndFeel(new MaterialOceanicTheme()));
            UIManager.put("LabelUI", MeasuredLabelUI.class.getName());
            UIManager.put("CheckBoxUI", MeasuredCheckBoxUI.class.getName());
            UIManager.put("Viewport.background", UIManager.getColor("Table.background"));
        } catch (Exception e) {
            System.err.println("Material look and feel not available, using the default: " + e);
        }
    }

    private static Path locateResources(String[] args) {
        try {
            Optional<Path> found = ResourceFolder.find(args, Path.of(""));
            if (found.isPresent()) {
                return found.get();
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Data tool", JOptionPane.WARNING_MESSAGE);
        }
        JFileChooser chooser = new JFileChooser(Path.of("").toAbsolutePath().toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose cow2win-app/src/main/resources");
        while (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            Path chosen = chooser.getSelectedFile().toPath();
            String problem = ResourceFolder.check(chosen);
            if (problem == null) {
                return chosen.toAbsolutePath().normalize();
            }
            JOptionPane.showMessageDialog(null, problem, "Data tool", JOptionPane.WARNING_MESSAGE);
        }
        return null;
    }
}
