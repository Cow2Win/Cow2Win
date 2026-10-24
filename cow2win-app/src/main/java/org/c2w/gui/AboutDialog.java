package org.c2w.gui;

import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.IconLoader;
import org.c2w.gui.common.WebLinks;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.AppVersion;
import org.c2w.infra.GitHubProject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Help" &gt; "About Cow2Win": name and version, contact, project page, license and credits, each as
 * a clickable link (opened via {@link WebLinks}), and a notice on the game's artwork. Modal, not
 * resizable; "Close" and Esc close it. Proper names (Cow2Win, MIT License, ...) are not translated.
 */
final class AboutDialog extends JDialog {

    static final String CONTACT_ADDRESS = "cow2win@gmail.com";
    static final String CONTACT_URL = "mailto:" + CONTACT_ADDRESS;
    static final String PHOSPHOR_ICONS_URL = "https://phosphoricons.com";
    static final String MATERIAL_UI_SWING_URL = "https://github.com/vincenzopalazzo/material-ui-swing";

    private static final String APP_ICON = "/images/app/cow.png";
    private static final int APP_ICON_SIZE = 64;
    private static final float TITLE_FONT_SCALE = 1.8f;
    /** Width of the wrapped tagline and game notice, so they never widen the dialog. */
    private static final int TEXT_WIDTH = 380;
    private static final int GAP = 12;

    private final JLabel versionLabel;
    private final List<JLabel> links = new ArrayList<>();

    AboutDialog(Window owner) {
        super(owner, LanguageService.displayName("about.title"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JLabel name = new JLabel("Cow2Win");
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() * TITLE_FONT_SCALE));
        versionLabel = new JLabel(LanguageService.displayName("about.version", AppVersion.current()));
        JPanel nameAndVersion = new JPanel(new GridLayout(2, 1));
        nameAndVersion.setOpaque(false);
        nameAndVersion.add(name);
        nameAndVersion.add(versionLabel);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP, 0));
        header.setOpaque(false);
        header.add(new JLabel(IconLoader.iconFor(APP_ICON, APP_ICON_SIZE)));
        header.add(nameAndVersion);

        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);
        addRow(rows, "about.contact", CONTACT_ADDRESS, CONTACT_URL);
        addRow(rows, "about.project", GitHubProject.PROJECT_PAGE_URL.replaceFirst("^https://", ""),
                GitHubProject.PROJECT_PAGE_URL);
        addRow(rows, "about.license", "MIT License", GitHubProject.LICENSE_URL);
        addRow(rows, "about.icons", "Phosphor Icons", PHOSPHOR_ICONS_URL);
        addRow(rows, "about.lookAndFeel", "Material-UI-Swing", MATERIAL_UI_SWING_URL);

        JLabel notice = wrapped(LanguageService.displayName("about.gameNotice"));
        notice.setForeground(AppColors.MUTED);
        notice.setFont(notice.getFont().deriveFont(notice.getFont().getSize2D() - 1f));

        Box content = Box.createVerticalBox();
        content.setBorder(BorderFactory.createEmptyBorder(GAP * 2, GAP * 2, GAP, GAP * 2));
        for (JComponent part : List.of(header, wrapped(LanguageService.displayName("about.tagline")), rows, notice)) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (content.getComponentCount() > 0) {
                content.add(Box.createVerticalStrut(GAP + GAP / 2));
            }
            content.add(part);
        }

        JButton close = new JButton(LanguageService.displayName("common.close"));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.add(content, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog over {@code owner} and waits until it is closed. */
    static void open(Window owner) {
        new AboutDialog(owner).setVisible(true);
    }

    /** "Version x.y.z" - package-visible for tests. */
    JLabel versionLabel() {
        return versionLabel;
    }

    /** The link labels in display order - package-visible for tests. */
    List<JLabel> links() {
        return links;
    }

    /** The target of a link label (see {@link #links()}). */
    static String linkTarget(JLabel link) {
        return (String) link.getClientProperty(AboutDialog.class);
    }

    /** One row of {@code rows}: the caption of {@code captionKey} and a link labelled {@code text}. */
    private void addRow(JPanel rows, String captionKey, String text, String url) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = links.size();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, GAP * 2);
        rows.add(new JLabel(LanguageService.displayName(captionKey)), c);
        c.insets = new Insets(2, 0, 2, 0);
        c.weightx = 1;
        JLabel link = link(text, url);
        links.add(link);
        rows.add(link, c);
    }

    /** A label looking like a link - underlined, link color, hand cursor, the URL as tooltip - that opens {@code url}. */
    private JLabel link(String text, String url) {
        JLabel link = new JLabel(text);
        link.setForeground(AppColors.INFO_TEXT);
        link.setFont(link.getFont().deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText(url);
        link.putClientProperty(AboutDialog.class, url);
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                WebLinks.open(AboutDialog.this, url);
            }
        });
        return link;
    }

    /** A label showing {@code text} wrapped to {@link #TEXT_WIDTH}. */
    private static JLabel wrapped(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return new JLabel("<html><div style='width:" + TEXT_WIDTH + "px'>" + escaped + "</div></html>");
    }
}
