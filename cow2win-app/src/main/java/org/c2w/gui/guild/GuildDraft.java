package org.c2w.gui.guild;

import java.util.ArrayList;
import java.util.List;


public final class GuildDraft {
    public String id = "";
    public String name = "";
    /** Game guild id (Weltenschlacht journal), not editable in the dialog - carried through unchanged. */
    public Long gameGuildId;
    /** Set once when the guild is created - only carried through, never edited here. */
    public boolean guildMaster;
    public final List<MemberDraft> members = new ArrayList<>();
}
