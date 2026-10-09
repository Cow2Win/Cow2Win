package org.c2w.data.model;

import java.util.HashSet;
import java.util.List;

/**
 * One saved team lineup ("template") that a team row can be filled with in
 * one keystroke (F1-F5, see {@code org.c2w.gui.guild.TeamEditorPanel}):
 * an ordered list of hero or titan ids, stored in template slot
 * {@link #MIN_SLOT}..{@link #MAX_SLOT} of {@code heroTemplates.json} or
 * {@code titanTemplates.json} (see {@code org.c2w.data.repository.TeamTemplateRepository}).
 *
 * <p>The member ids are deliberately NOT checked against the catalog here -
 * the catalog may change, so unknown ids are kept in the file and only
 * skipped when the template is applied (see {@code org.c2w.domain.TeamTemplates}).
 *
 * @param slot      template slot, {@link #MIN_SLOT} to {@link #MAX_SLOT} (= F-key number)
 * @param memberIds {@link #MIN_MEMBERS} to {@link #MAX_MEMBERS} distinct ids, in slot order
 */
public record TeamTemplate(int slot, List<String> memberIds) {

    public static final int MIN_SLOT = 1;
    public static final int MAX_SLOT = 5;
    public static final int MIN_MEMBERS = 1;
    public static final int MAX_MEMBERS = 5;

    public TeamTemplate {
        if (slot < MIN_SLOT || slot > MAX_SLOT) {
            throw new IllegalArgumentException("template slot must be " + MIN_SLOT + " to " + MAX_SLOT + ", is " + slot);
        }
        if (memberIds == null || memberIds.size() < MIN_MEMBERS || memberIds.size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("template " + slot + " needs " + MIN_MEMBERS + " to " + MAX_MEMBERS
                    + " ids, has " + (memberIds == null ? 0 : memberIds.size()));
        }
        if (memberIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("template " + slot + " contains a blank id");
        }
        if (new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new IllegalArgumentException("template " + slot + " lists an id twice " + memberIds);
        }
        memberIds = List.copyOf(memberIds);
    }
}
