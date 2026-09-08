package org.c2w.gui.guild;


import org.c2w.data.model.Hero;
import org.c2w.data.model.Titan;

import java.util.ArrayList;
import java.util.List;

public final class MemberDraft {
    public String id;
    public String name;
    public final List<TeamDraft<Hero>> heroTeams = new ArrayList<>();
    public final List<TeamDraft<Titan>> titanTeams = new ArrayList<>();

    public MemberDraft(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
