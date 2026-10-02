package org.c2w.gui.guild;

import org.c2w.data.model.Pet;
import org.c2w.data.model.TitanElement;
import org.c2w.data.model.WarFlag;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Mutable, editable counterpart of a {@code HeroTeam}/{@code TitanTeam}
 * while a dialog is open (see {@link GuildDraftConverter}).
 *
 * pet/warFlag: the team's optional {@link Pet}/{@link WarFlag} - only used
 * for hero team drafts, always null for titan team drafts (titan teams have
 * neither).
 *
 * totems: the team's totems (see {@code TitanTeam#totems()}) - only used for
 * titan team drafts, always empty for hero team drafts. Kept in
 * {@link TitanElement} order. While editing it may briefly break the rules;
 * a {@code TitanTeam} is therefore always built from a draft via
 * {@link GuildDraftConverter#toTitanTeam}.
 */
public final class TeamDraft<T> {
    public final List<T> members = new ArrayList<>();
    public int totalPower;
    public LocalDate lastModified = LocalDate.now();
    public Pet pet;
    public WarFlag warFlag;
    public final Set<TitanElement> totems = EnumSet.noneOf(TitanElement.class);

    /**
     * Overwrites every field of this draft with {@code source}'s - the one
     * place that knows the full field list, so copying a draft (e.g. into a
     * dialog row and back) can't silently lose a field like pet/warFlag/totems.
     */
    public void copyFrom(TeamDraft<T> source) {
        members.clear();
        members.addAll(source.members);
        totalPower = source.totalPower;
        lastModified = source.lastModified;
        pet = source.pet;
        warFlag = source.warFlag;
        totems.clear();
        totems.addAll(source.totems);
    }
}
