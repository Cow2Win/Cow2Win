package org.c2w.gui.guild;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public final class GuildDraft {
    public String id = "";
    public String name = "";
    public int season;
    public LocalDate seasonStart;
    public final List<MemberDraft> members = new ArrayList<>();
}
