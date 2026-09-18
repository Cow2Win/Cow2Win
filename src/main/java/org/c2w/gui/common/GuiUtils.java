package org.c2w.gui.common;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class GuiUtils {
    public static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.GERMANY);
    public static boolean editedGuild = false;
    public static boolean editedLineup = false;

    /**
     * Custom UIManager key - not a built-in Swing/L&amp;F key, namespaced with
     * "Cow2Win." so it can never collide with one - for the background color
     * of TeamsOverviewPanel's hero/titan table cells (see its
     * PowerCellRenderer/FortificationCellRenderer/MembersCellRenderer/
     * PlainCellRenderer). Registered once below in {@link #setGUIConstants()},
     * the same way "TabbedPane.selected" already is, instead of hardcoding the
     * color into each of those renderers.
     */
    public static final String KEY_TEAM_TABLE_CELL_BACKGROUND = "Cow2Win.teamTableCellBackground";

    public static final void setGUIConstants(){
        UIManager.put("TabbedPane.selected", Color.GRAY.darker());
        UIManager.put(KEY_TEAM_TABLE_CELL_BACKGROUND, Color.GRAY.darker());
        UIManager.put("TabbedPane.focus", Color.BLACK);
        UIManager.put("TabbedPane.selectHighlight", Color.GRAY);
        UIManager.put("Table.selectionBackground",Color.BLACK);
        UIManager.put("TextArea.background",Color.GRAY.darker());
        UIManager.put("TextArea.foreground",Color.WHITE);
        UIManager.put("List.background",Color.GRAY.darker());
        UIManager.put("List.foreground",Color.WHITE);
        UIManager.put("List.selectionBackground",Color.BLACK);
        UIManager.put("List.selectionForeground",Color.WHITE);

    }
}
