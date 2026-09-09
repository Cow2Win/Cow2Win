package org.c2w.data.model;

import org.c2w.gui.common.IconLoader;

import java.awt.*;

/** Whether a fortification is defended by a hero team or a titan team. */
public enum FortificationType {
    HERO("/images/app/square.png","/images/app/square-team.png", Color.BLACK),
    TITAN("/images/app/hexagon.png","/images/app/hexagon-team.png", IconLoader.PURPLE);

    private String slot_open;
    private String slot_set;
    private Color color;

    FortificationType(String slot_open, String slot_set, Color color){
        this.slot_open = slot_open;
        this.slot_set = slot_set;
        this.color = color;
    }

    public String getSlot_open() {
        return slot_open;
    }

    public String getSlot_set() {
        return slot_set;
    }

    public Color getColor() {
        return color;
    }
}
