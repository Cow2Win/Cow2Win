package org.c2w.gui.guild;

import org.c2w.data.model.Hero;
import org.c2w.data.model.Titan;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.LanguageService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * Editor for ONE guild member: id/name fields plus, below them, ONE fixed
 * row per team - 3 hero team rows ("Heroes 1".."Heroes 3") and 2 titan team
 * rows ("Titans 1"/"Titans 2", see MAX_HERO_TEAMS/MAX_TITAN_TEAMS), all
 * simply stacked in one panel (see {@link #buildTeamRows}) - no tabs and no
 * "add team"/"remove team" buttons: the row count is fixed, memberDraft's
 * heroTeams/titanTeams are padded with empty TeamDraft entries as needed
 * when a member is opened (see {@link #ensureTeamCount}).
 *
 * Each row is ONE {@link TeamEditorPanel} (team label, power field and 5
 * selection combo boxes in a single FlowLayout row, see there). A team
 * WITHOUT power (totalPower == 0) counts as "empty": it does not need to be
 * removed separately, it is simply dropped on save (see
 * {@link GuildDraftConverter#toGuild}) - any selections still made in such a
 * team's combo boxes are discarded along with it.
 *
 * Writes id/name changes back into the given MemberDraft immediately, just
 * like TeamEditorPanel does for its teams - the MemberDraft is therefore
 * always the current state of this member, without a separate "apply" step.
 */
final class MemberEditorPanel extends JPanel {

    private static final int MAX_HERO_TEAMS = 3;
    private static final int MAX_TITAN_TEAMS = 2;

    /** Target size of the slot icons in the team rows. */
    private static final int ICON_SIZE = 32;

    /**
     * Language file key (see resources/language/*.txt) for the placeholder
     * text of an empty/unselected slot in a team row's combo boxes,
     * resolved via {@link LanguageService#displayName} - generic "nothing
     * selected here" key.
     */
    private static final String KEY_NO_SELECTION = "common.none";


    private static final String KEY_HEROES = "teamsOverview.heroes";
    private static final String KEY_TITANS = "teamsOverview.titans";

    private final MemberDraft memberDraft;

    MemberEditorPanel(MemberDraft memberDraft, List<Hero> heroCatalog, List<Titan> titanCatalog) {
        super(new BorderLayout(8, 8));
        this.memberDraft = memberDraft;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ensureTeamCount(memberDraft.heroTeams, MAX_HERO_TEAMS);
        ensureTeamCount(memberDraft.titanTeams, MAX_TITAN_TEAMS);

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(new JScrollPane(buildTeamRows(heroCatalog, titanCatalog)), BorderLayout.CENTER);
    }

    /** Pads teams with empty TeamDraft entries as needed, until exactly maxCount entries exist (see class Javadoc). */
    private static <T> void ensureTeamCount(List<TeamDraft<T>> teams, int maxCount) {
        while (teams.size() < maxCount) {
            teams.add(new TeamDraft<>());
        }
    }

    /**
     * Left-aligned header layout (FlowLayout.LEFT instead of GridBagLayout
     * without weightx) - GridBagLayout would otherwise center the
     * components as a block across the panel's full available width once it
     * gets wider than their content (e.g. because of the wide team rows
     * below).
     */
    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("Name:"));
        JTextField nameField = new JTextField(memberDraft.name, 20);
        nameField.getDocument().addDocumentListener(onChange(() -> memberDraft.name = nameField.getText().trim()));
        header.add(nameField);
        return header;
    }

    /**
     * Builds one TeamEditorPanel row per hero/titan team slot, all stacked
     * (see class Javadoc) - hero slots' combo boxes are sorted by name,
     * titan slots' combo boxes by element, then name (see
     * {@link TeamEditorPanel}).
     */
    private JPanel buildTeamRows(List<Hero> heroCatalog, List<Titan> titanCatalog) {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        for (int i = 0; i < memberDraft.heroTeams.size(); i++) {
            TeamDraft<Hero> teamDraft = memberDraft.heroTeams.get(i);
            TeamEditorPanel<Hero> row = new TeamEditorPanel<>(
                     heroCatalog, MemberEditorPanel::heroLabel,
                    h -> IconLoader.iconFor(h.imagePath(), ICON_SIZE), null, teamDraft,
                    LanguageService.displayName(KEY_NO_SELECTION),
                    Comparator.comparing(MemberEditorPanel::heroLabel));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            rows.add(row);
        }

        for (int i = 0; i < memberDraft.titanTeams.size(); i++) {
            TeamDraft<Titan> teamDraft = memberDraft.titanTeams.get(i);
            TeamEditorPanel<Titan> row = new TeamEditorPanel<>(
                     titanCatalog, MemberEditorPanel::titanLabel,
                    t -> IconLoader.iconFor(t.imagePath(), ICON_SIZE), null, teamDraft,
                    LanguageService.displayName(KEY_NO_SELECTION),
                    Comparator.comparing(Titan::element).thenComparing(MemberEditorPanel::titanLabel));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            rows.add(row);
        }

        return rows;
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

    private static String titanLabel(Titan titan) {
        return LanguageService.displayName(titan.id());
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }
}
