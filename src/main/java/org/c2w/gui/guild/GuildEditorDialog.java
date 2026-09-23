package org.c2w.gui.guild;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Hero;
import org.c2w.data.model.Titan;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.HeroRepository;
import org.c2w.data.repository.TitanRepository;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;


public final class GuildEditorDialog extends JDialog {

    private static final String BASE_TITLE = "Cow2 - Guild Editor";

    private static final int MAX_MEMBERS = 30;

    private static final String KEY_SAVE_GUILD = "guildEditor.saveGuild";
    private static final String ICON_SAVE_GUILD = "/images/app/save.png";
    private static final String KEY_ADD_MEMBER = "guildEditor.addMember";
    private static final String ICON_ADD_MEMBER = "/images/app/member-new.png";
    private static final String KEY_REMOVE_MEMBER = "guildEditor.removeMember";
    private static final String ICON_REMOVE_MEMBER = "/images/app/member-remove.png";

    private static final int TOOLBAR_ICON_SIZE = 20;

   private static final DateTimeFormatter LAST_SAVED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppContext context;
    private final Runnable onGuildSaved;
    private final List<Hero> heroCatalog = HeroRepository.findAll();
    private final List<Titan> titanCatalog = TitanRepository.findAll();

    private final GuildDraft draft;

    private final DefaultListModel<MemberDraft> memberListModel = new DefaultListModel<>();
    private final JList<MemberDraft> memberList = new JList<>(memberListModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    /** Shows the current member count - see {@link #buildMemberInfoPanel()}/class Javadoc. */
    private final JLabel memberCountLabel = new JLabel();

    /** Shows the guild file's last-saved timestamp - see {@link #buildMemberInfoPanel()}/class Javadoc. */
    private final JLabel lastSavedLabel = new JLabel();

    public GuildEditorDialog(Frame owner, AppContext context, Runnable onGuildSaved) {
        super(owner, BASE_TITLE, false);
        if (context == null) {
            throw new IllegalArgumentException("GuildEditorDialog needs a AppContext");
        }
        this.context = context;
        this.onGuildSaved = onGuildSaved;
        this.draft = GuildDraftConverter.fromGuild(context.guild());

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildToolbarPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);

        showEmptyDetail("No member selected.");
        refreshMemberList();
        updateLastSavedLabel();

        // Refreshes the last-saved timestamp whenever this non-modal dialog
        // regains focus, since the guild file can be saved from elsewhere
        // (ToolbarPanel's "save guild" button) while this dialog
        // stays open - see class Javadoc.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                updateLastSavedLabel();
            }
        });

        setSize(900, 500);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JSplitPane buildMainSplit() {
        JPanel left = new JPanel(new BorderLayout());
        memberList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof MemberDraft m) {
                    setText(m.name.isBlank() ? m.id : m.name);
                }
                return this;
            }
        });
        memberList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onMemberSelected(memberList.getSelectedValue());
            }
        });
        left.add(new JScrollPane(memberList), BorderLayout.CENTER);
        left.add(buildMemberInfoPanel(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detailContainer);
        split.setDividerLocation(200);
        return split;
    }

    private JPanel buildToolbarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE_GUILD, TOOLBAR_ICON_SIZE,IconLoader.BLUE));
        saveButton.setToolTipText(LanguageService.displayName(KEY_SAVE_GUILD));
        saveButton.addActionListener(e -> onSaveGuild());
        buttons.add(saveButton);

        FlatButton addMemberButton = new FlatButton(IconLoader.iconFor(ICON_ADD_MEMBER, TOOLBAR_ICON_SIZE,IconLoader.GREEN));
        addMemberButton.setToolTipText(LanguageService.displayName(KEY_ADD_MEMBER));
        addMemberButton.addActionListener(e -> onAddMember());
        buttons.add(addMemberButton);

        FlatButton removeMemberButton = new FlatButton(IconLoader.iconFor(ICON_REMOVE_MEMBER, TOOLBAR_ICON_SIZE,IconLoader.RED));
        removeMemberButton.setToolTipText(LanguageService.displayName(KEY_REMOVE_MEMBER));
        removeMemberButton.addActionListener(e -> onRemoveMember());
        buttons.add(removeMemberButton);

        panel.add(buttons, BorderLayout.WEST);
        return panel;
    }

    /**
     * Small info panel placed below {@link #memberList} (see
     * {@link #buildMainSplit()}/class Javadoc): {@link #memberCountLabel} on
     * top of {@link #lastSavedLabel}. Both start out blank here - filled in
     * by {@link #refreshMemberList()} (count) and {@link #updateLastSavedLabel()}
     * (timestamp), both called once from the constructor right after this
     * panel is built.
     */
    private JPanel buildMemberInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        panel.add(memberCountLabel);
        panel.add(lastSavedLabel);
        return panel;
    }

    private void updateLastSavedLabel() {
        try {
            var lastModified = Files.getLastModifiedTime(context.guildFilePath());
            String formatted = LAST_SAVED_FORMAT.format(lastModified.toInstant().atZone(ZoneId.systemDefault()));
            lastSavedLabel.setText("Last saved: " + formatted);
        } catch (IOException e) {
            lastSavedLabel.setText("Last saved: never");
        }
    }

    /**
     * Rebuilds the member list alphabetically (by display name, case
     * insensitive, falling back to the id for a blank name - see the cell
     * renderer) - the order within {@link GuildDraft#members} itself is
     * unaffected (insertion order), only the display is sorted.
     */
    private void refreshMemberList() {
        MemberDraft previouslySelected = memberList.getSelectedValue();
        memberListModel.clear();
        draft.members.stream()
                .sorted(Comparator.comparing((MemberDraft m) -> m.name.isBlank() ? m.id : m.name,
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(memberListModel::addElement);
        if (previouslySelected != null && memberListModel.contains(previouslySelected)) {
            memberList.setSelectedValue(previouslySelected, true);
        }
        memberCountLabel.setText("Members: " + draft.members.size() + " / " + MAX_MEMBERS);
    }

    private void onMemberSelected(MemberDraft selected) {
        if (selected == null) {
            showEmptyDetail("No member selected.");
            return;
        }
        detailContainer.removeAll();
        detailContainer.add(new MemberEditorPanel(selected, heroCatalog, titanCatalog),
                BorderLayout.CENTER);
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    private void showEmptyDetail(String message) {
        detailContainer.removeAll();
        JLabel label = new JLabel(message, JLabel.CENTER);
        detailContainer.add(label, BorderLayout.CENTER);
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    private void onAddMember() {
        if (draft.members.size() >= MAX_MEMBERS) {
            JOptionPane.showMessageDialog(this, "A guild has at most " + MAX_MEMBERS + " members.",
                    "Not possible", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String id = JOptionPane.showInputDialog(this, "Member ID (unique):",
                "member" + (draft.members.size() + 1));
        if (id == null || id.isBlank()) {
            return;
        }
        String trimmedId = id.trim();
        boolean duplicate = draft.members.stream().anyMatch(m -> m.id.equals(trimmedId));
        if (duplicate) {
            JOptionPane.showMessageDialog(this, "A member with this ID already exists.",
                    "Not possible", JOptionPane.WARNING_MESSAGE);
            return;
        }
        MemberDraft member = new MemberDraft(trimmedId, trimmedId);
        draft.members.add(member);
        refreshMemberList();
        memberList.setSelectedValue(member, true);
    }

    private void onRemoveMember() {
        MemberDraft selected = memberList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Really remove member '" + selected.id + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        draft.members.remove(selected);
        refreshMemberList();
        showEmptyDetail("No member selected.");
    }

    private void onSaveGuild() {
        try {
            Guild updated = GuildDraftConverter.toGuild(draft);
            GuildRepository.save(updated, context.guildFilePath());
            context.setGuild(updated);
            Logger.log("Saved: " + context.guildFilePath());
            updateLastSavedLabel();
            if (onGuildSaved != null) {
                onGuildSaved.run();
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "The data is invalid:\n" + ex.getMessage(),
                    "Error while saving", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the guild file:\n" + ex.getMessage(),
                    "Error while saving", JOptionPane.ERROR_MESSAGE);
        }
    }
}
