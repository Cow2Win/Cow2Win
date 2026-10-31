package org.c2w.gui.manual;

import org.c2w.i18n.LanguageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** {@link ManualWindow}: tree, jumping to a chapter, size and the single instance. Skipped without a display. */
class ManualWindowTest {

    private JFrame owner;

    @BeforeEach
    void createOwner() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        SwingUtilities.invokeAndWait(() -> owner = new JFrame());
    }

    @AfterEach
    void disposeAll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (ManualWindow.openInstance() != null) {
                ManualWindow.openInstance().dispose();
            }
            if (owner != null) {
                owner.dispose();
            }
        });
    }

    @Test
    @DisplayName("Title from the language file, not modal, the ten chapters as top-level tree nodes, fits 1024 x 768")
    void builds() throws Exception {
        AtomicReference<ManualWindow> window = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> window.set(new ManualWindow(owner)));
        try {
            ManualWindow manual = window.get();
            assertEquals(LanguageService.displayName("manual.title"), manual.getTitle());
            assertFalse(manual.isModal());
            TreeNode root = (TreeNode) manual.tree().getModel().getRoot();
            List<String> chapters = new ArrayList<>();
            for (int i = 0; i < root.getChildCount(); i++) {
                chapters.add(root.getChildAt(i).toString());
            }
            assertEquals(ManualChapters.ALL.size(), chapters.size());
            assertEquals("Introduction", chapters.getFirst());
            assertFalse(manual.tree().isRootVisible());
            assertTrue(manual.getWidth() <= 1024 && manual.getHeight() <= 768, manual.getSize().toString());
            assertTrue(manual.textPane().getDocument().getLength() > 1000, "text loaded");
        } finally {
            SwingUtilities.invokeAndWait(() -> window.get().dispose());
        }
    }

    @Test
    @DisplayName("open(..., \"concept\") selects the chapter; a second open reuses the window")
    void openJumpsAndReuses() throws Exception {
        AtomicReference<ManualWindow> first = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> first.set(ManualWindow.open(owner, ManualChapters.CONCEPT)));
        SwingUtilities.invokeAndWait(() -> { }); // the jump runs in invokeLater
        SwingUtilities.invokeAndWait(() -> {
            DefaultMutableTreeNode selected = (DefaultMutableTreeNode) first.get().tree().getLastSelectedPathComponent();
            assertNotNull(selected);
            assertEquals("Stage 2: Strategic concept", selected.toString());
        });

        AtomicReference<ManualWindow> second = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> second.set(ManualWindow.open(owner, ManualChapters.FAQ)));
        SwingUtilities.invokeAndWait(() -> { });
        assertSame(first.get(), second.get());
        SwingUtilities.invokeAndWait(() -> assertEquals("FAQ",
                second.get().tree().getLastSelectedPathComponent().toString()));
    }

    @Test
    @DisplayName("Opened from a modeless dialog the window belongs to its frame, from a modal dialog to that dialog")
    void effectiveOwner() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JDialog modeless = new JDialog(owner, false);
            JDialog modal = new JDialog(owner, true);
            try {
                assertSame(owner, ManualWindow.effectiveOwner(owner));
                assertSame(owner, ManualWindow.effectiveOwner(modeless));
                assertSame(modal, ManualWindow.effectiveOwner(modal));
            } finally {
                modeless.dispose();
                modal.dispose();
            }
        });
    }
}
