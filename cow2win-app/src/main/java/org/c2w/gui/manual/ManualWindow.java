package org.c2w.gui.manual;

import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.InfoArea;
import org.c2w.gui.common.WebLinks;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;

/**
 * "Help" &gt; "Manual": the manual ({@link ManualRenderer}) in a non-modal window - its chapters as
 * a tree on the left, the text on the right. Only one window at a time: {@link #open} brings an open
 * one to the front and jumps to the chapter. The manual is in English only; for any other interface
 * language a hint above says so. Esc closes it.
 */
public final class ManualWindow extends JDialog {

    /** Size the window opens with - fits a 1024 x 768 screen. */
    static final Dimension PREFERRED_SIZE = new Dimension(1000, 700);
    private static final int TREE_WIDTH = 260;
    private static final String ENGLISH = "english";
    /** Share of the text color in the background of code. */
    private static final double CODE_TINT = 0.12;

    /** The open window, if any. */
    private static ManualWindow instance;

    private final JEditorPane textPane = new JEditorPane();
    private final JTree tree;
    /** The manual - null if it could not be loaded. */
    private final ManualRenderer manual;

    ManualWindow(Window owner) {
        super(owner, LanguageService.displayName("manual.title"), ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.manual = loadManual();

        textPane.setContentType("text/html");
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                followLink(e.getDescription());
            }
        });
        showText();
        JScrollPane textScroll = new JScrollPane(textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        textScroll.setBorder(BorderFactory.createEmptyBorder());
        textScroll.getViewport().setOpaque(false);
        textScroll.setOpaque(false);

        this.tree = new JTree(buildTree());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
        tree.addTreeSelectionListener(e -> scrollToSelection());
        // Clicking the already selected chapter again jumps there again (no selection event).
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null && path.equals(tree.getSelectionPath())) {
                    scrollToSelection();
                }
            }
        });
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, textScroll);
        split.setDividerLocation(TREE_WIDTH);
        split.setBorder(BorderFactory.createEmptyBorder());

        JPanel content = new JPanel(new BorderLayout());
        if (!ENGLISH.equals(LanguageService.configuredLanguage())) {
            InfoArea hint = new InfoArea(1);
            hint.setText(LanguageService.displayName("manual.englishOnly"));
            content.add(InfoArea.framed(hint), BorderLayout.NORTH);
        }
        content.add(split, BorderLayout.CENTER);
        setContentPane(content);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(PREFERRED_SIZE.width, screen.width), Math.min(PREFERRED_SIZE.height, screen.height));
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the manual at {@code chapterId} (an anchor, see {@link ManualChapters}; null = the
     * beginning) over {@code owner}. The window belongs to the main window - or, opened from a modal
     * dialog, to that dialog, which would block it otherwise. An open window is reused unless it
     * belongs to another owner: then it is replaced, so there is still only one.
     */
    public static ManualWindow open(Window owner, String chapterId) {
        owner = effectiveOwner(owner);
        if (instance != null && (!instance.isDisplayable() || instance.getOwner() != owner)) {
            instance.dispose();
            instance = null;
        }
        if (instance == null) {
            instance = new ManualWindow(owner);
            instance.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    if (instance == e.getWindow()) {
                        instance = null;
                    }
                }
            });
        }
        ManualWindow window = instance;
        window.setVisible(true);
        window.toFront();
        // Scroll only once the text is laid out.
        SwingUtilities.invokeLater(() -> window.showChapter(chapterId));
        return window;
    }

    /** {@code window} itself if it is a modal dialog, otherwise the frame it belongs to (or {@code window} if none). */
    static Window effectiveOwner(Window window) {
        if (window instanceof Dialog dialog && dialog.isModal()) {
            return window;
        }
        for (Window w = window; w != null; w = w.getOwner()) {
            if (w instanceof Frame) {
                return w;
            }
        }
        return window;
    }

    /** The open window, if any - package-visible for tests. */
    static ManualWindow openInstance() {
        return instance;
    }

    /** Selects {@code anchor} in the tree and scrolls there; null or an unknown anchor scrolls to the top. */
    void showChapter(String anchor) {
        DefaultMutableTreeNode node = anchor == null ? null : findNode(anchor);
        if (node == null) {
            tree.clearSelection();
            textPane.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
            return;
        }
        TreePath path = new TreePath(node.getPath());
        if (path.equals(tree.getSelectionPath())) {
            scrollToSelection();
        } else {
            tree.setSelectionPath(path);
        }
        tree.scrollPathToVisible(path);
    }

    /** The tree of chapters - package-visible for tests. */
    JTree tree() {
        return tree;
    }

    /** The text pane - package-visible for tests. */
    JEditorPane textPane() {
        return textPane;
    }

    private static ManualRenderer loadManual() {
        try {
            return ManualRenderer.load();
        } catch (IOException | RuntimeException e) {
            Logger.logException("Manual could not be loaded", e);
            return null;
        }
    }

    /** The manual as HTML (relative images resolved against its URL), or the load error as plain text. */
    private void showText() {
        if (manual == null) {
            textPane.setContentType("text/plain");
            textPane.setText(LanguageService.displayName("manual.loadError"));
            return;
        }
        HTMLDocument document = (HTMLDocument) textPane.getEditorKit().createDefaultDocument();
        document.setBase(manual.base());
        textPane.setDocument(document);
        textPane.setText(manual.html(style()));
        textPane.setCaretPosition(0);
    }

    /** Interface font and the colors of the current look and feel. */
    private static ManualRenderer.Style style() {
        Font font = UIManager.getFont("Label.font");
        Color foreground = UIManager.getColor("Label.foreground");
        Color background = UIManager.getColor("Panel.background");
        Color textColor = foreground != null ? foreground : Color.WHITE;
        // The background with a touch of the text color - code stands out a little.
        Color code = background != null ? AppColors.mix(background, textColor, CODE_TINT)
                : AppColors.SURFACE_SELECTED;
        return new ManualRenderer.Style(font != null ? font.getFamily() : "SansSerif",
                font != null ? font.getSize() : 12, AppColors.hex(textColor), AppColors.hex(AppColors.ACCENT_TEXT),
                AppColors.hex(AppColors.INFO_TEXT), AppColors.hex(code), AppColors.hex(AppColors.MUTED));
    }

    /** Chapters ({@code ##}) with their sections ({@code ###}) below a hidden root. */
    private DefaultTreeModel buildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        DefaultMutableTreeNode chapter = null;
        List<ManualRenderer.Heading> outline = manual == null ? List.of() : manual.outline();
        for (ManualRenderer.Heading heading : outline) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Entry(heading));
            if (heading.level() == 2 || chapter == null) {
                root.add(node);
                chapter = node;
            } else {
                chapter.add(node);
            }
        }
        return new DefaultTreeModel(root);
    }

    private DefaultMutableTreeNode findNode(String anchor) {
        Enumeration<?> nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot()).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof Entry entry && entry.heading().anchor().equals(anchor)) {
                return node;
            }
        }
        return null;
    }

    private void scrollToSelection() {
        TreePath path = tree.getSelectionPath();
        if (path != null && ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() instanceof Entry entry) {
            textPane.scrollToReference(entry.heading().anchor());
        }
    }

    /** A link in the text: "#anchor" jumps inside the manual, anything else opens in the browser / mail program. */
    private void followLink(String target) {
        if (target == null) {
            return;
        }
        if (target.startsWith("#")) {
            showChapter(target.substring(1));
        } else {
            WebLinks.open(this, target);
        }
    }

    /** A tree node: shows the heading's text. */
    private record Entry(ManualRenderer.Heading heading) {
        @Override
        public String toString() {
            return heading.text();
        }
    }
}
