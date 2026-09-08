package org.c2w.gui.guild;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class TeamDraft<T> {
    public final List<T> members = new ArrayList<>();
    public int totalPower;
    public LocalDate lastModified = LocalDate.now();
}
