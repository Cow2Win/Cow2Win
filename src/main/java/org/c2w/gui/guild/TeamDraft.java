package org.c2w.gui.guild;

import org.c2w.data.model.Pet;
import org.c2w.data.model.WarFlag;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, editable counterpart of a {@code HeroTeam}/{@code TitanTeam}
 * while a dialog is open (see {@link GuildDraftConverter}).
 *
 * pet/warFlag: the team's optional {@link Pet}/{@link WarFlag} - only used
 * for hero team drafts, always null for titan team drafts (titan teams have
 * neither).
 */
public final class TeamDraft<T> {
    public final List<T> members = new ArrayList<>();
    public int totalPower;
    public LocalDate lastModified = LocalDate.now();
    public Pet pet;
    public WarFlag warFlag;

    /**
     * Overwrites every field of this draft with {@code source}'s - the one
     * place that knows the full field list, so copying a draft (e.g. into a
     * dialog row and back) can't silently lose a field like pet/warFlag.
     */
    public void copyFrom(TeamDraft<T> source) {
        members.clear();
        members.addAll(source.members);
        totalPower = source.totalPower;
        lastModified = source.lastModified;
        pet = source.pet;
        warFlag = source.warFlag;
    }
}
