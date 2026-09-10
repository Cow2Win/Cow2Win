package org.c2w.gui.fort;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.AppContext;
import org.c2w.util.Config;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class FortificationPanel extends JPanel {
    private JPanel headerPanel;
    private JLabel displayLbl;
    private JLabel bufflbl;
    private JLabel buffPercentLbl;
    private JLabel powerlbl;
    private JList<JLabel> slotlabel;
    private Fortification fortification;
    private final int filledSlots;
    private final int totalPower;
    /** Change of {@link #totalPower} against the baseline loaded from disk (see AppContext#loadedFortificationBaseline) - only shown when {@link #showChanges} is true (see {@link #getPowerLabel()}). */
    private final int totalPowerDiff;
    /** True while the "Changes" checkbox (see FortificationMapPanel#buildTypeFilterPanel) is selected - then {@link #getPowerLabel()} shows {@link #totalPowerDiff} instead of {@link #totalPower}. */
    private final boolean showChanges;
    private final int buffPercent;
    /** Change of {@link #buffPercent} against the baseline loaded from disk (see AppContext#loadedFortificationBaseline) - only shown when {@link #showChanges} is true (see {@link #getBuffPercentLabel()}). */
    private final int buffPercentDiff;
    private final AppContext appContext;
    private final FortificationMapPanel fortificationMapPanel;
    private ImageIcon slot_set;
    private ImageIcon slot_open;


    public FortificationPanel(Fortification fortification, int filledSlots, int totalPower, int totalPowerDiff,
                              boolean showChanges, int buffPercent, int buffPercentDiff,
                              AppContext appContext,
                              FortificationMapPanel fortificationMapPanel){
        this.fortification = fortification;
        this.filledSlots = Math.min(filledSlots, fortification.capacity());
        this.totalPower = totalPower;
        this.totalPowerDiff = totalPowerDiff;
        this.showChanges = showChanges;
        this.buffPercent = buffPercent;
        this.buffPercentDiff = buffPercentDiff;
        this.appContext = appContext;
        this.fortificationMapPanel = fortificationMapPanel;
        init();
    }


    /**
     * Initializes the panel layout and components.
     */
    private void init(){
        slot_open = IconLoader.iconFor(fortification.type().getSlot_open(), 24,fortification.type().getColor() );
        slot_set = IconLoader.iconFor(fortification.type().getSlot_set(), 24,fortification.type().getColor() );
        setLayout(new FlowLayout());
        add(getHeaderPanel());


    }

    /**
     * Gets or creates the display label showing the fortification name.
     *
     * @return The fortification name label
     */
    private JLabel getDisplayLbl(){
        if(displayLbl == null){
            displayLbl = new JLabel(LanguageService.displayName(fortification.id()),JLabel.CENTER);
            displayLbl.setBackground(fortification.type().getColor());
            displayLbl.setForeground(Color.WHITE);
            displayLbl.setOpaque(true);
            displayLbl.setPreferredSize(new Dimension(160,20));
            displayLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            displayLbl.setToolTipText("Click to assign teams to this fortification");
            displayLbl.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openEntryDialog();
                }
            });
        }
        return displayLbl;
    }


    private void openEntryDialog(){
        Window window = SwingUtilities.getWindowAncestor(this);
        Frame owner = window instanceof Frame f ? f : null;
        FortificationEntryDialog dialog = new FortificationEntryDialog(owner, fortification, appContext,
                fortificationMapPanel::refreshAfterExternalSave);
        dialog.setVisible(true);
    }

    /**
     * Gets or creates the header shown at the top of this panel: the name
     * label (see {@link #getDisplayLbl()}) with the buff percentage label
     * (see {@link #getBuffPercentLabel()}) directly underneath it - a small
     * {@link GridLayout}(2, 1) sub-panel, so the percentage always stays
     * directly under the name no matter how much horizontal space the
     * surrounding {@link FlowLayout} (see {@link #init()}) actually gives
     * this panel; a {@link FlowLayout} re-wraps its own direct children on
     * every resize, which would not reliably keep the two stacked if they
     * were added to it separately instead of nested in their own panel.
     *
     * @return The header panel (name + buff percentage)
     */
    private JPanel getHeaderPanel(){
        if(headerPanel == null){
            headerPanel = new JPanel(new GridLayout(3, 1));
            headerPanel.add(getDisplayLbl());

            JPanel powerPanel = new JPanel(new GridLayout(1,2));
            powerPanel.add(getBuffPercentLabel());
            powerPanel.add(getPowerLabel());
            headerPanel.add(powerPanel);

            headerPanel.add(getSlotPanel());
        }
        return headerPanel;
    }

    /**
     * Gets or creates the label displaying the buff percentage - either
     * {@link #buffPercent} (default), e.g. "35%", or, while the "Changes"
     * checkbox is selected (see {@link #showChanges}), {@link #buffPercentDiff}
     * against the baseline loaded from disk, colored {@link IconLoader#GREEN}
     * for a gain, {@link IconLoader#RED} for a loss (see {@link #diffColor}) -
     * shown directly under the name label (see {@link #getHeaderPanel()}).
     * (the buff's own descriptive text, e.g. "+10% Power" - currently
     * unused, not added to this panel by {@link #init()}).
     *
     * @return The buff percentage/change label
     */
    private JLabel getBuffPercentLabel(){
        if(buffPercentLbl == null){
            buffPercentLbl = new JLabel("",JLabel.LEFT);
            if(fortification.buff() != null){
                String text = showChanges ? formatPercentDiff(buffPercentDiff) : (buffPercent + "%");
                Color color = showChanges ? diffColor(buffPercentDiff) : fortification.type().getColor();
                buffPercentLbl.setText(text);
                buffPercentLbl.setToolTipText(fortification.buff().display());
                buffPercentLbl.setForeground(color);
            }

        }
        return buffPercentLbl;
    }

    /** "+5%"/"-5%"/"0%". */
    private static String formatPercentDiff(int diff) {
        return (diff > 0 ? "+" : "") + diff + "%";
    }

    /**
     * Gets or creates the label showing this fortification's power - either
     * {@link #totalPower} (default) or, while the "Changes" checkbox is
     * selected (see {@link #showChanges}), {@link #totalPowerDiff} against
     * the baseline loaded from disk, colored {@link IconLoader#GREEN} for a
     * gain, {@link IconLoader#RED} for a loss (see {@link #diffColor}).
     *
     * @return The power/change label
     */
    private JLabel getPowerLabel(){
        if(powerlbl == null){
            String text = showChanges ? formatPowerDiff(totalPowerDiff) : Config.NUMBER_FORMAT.format(totalPower);
            Color color = showChanges ? diffColor(totalPowerDiff) : fortification.type().getColor();
            powerlbl = new JLabel(text, JLabel.RIGHT);
            powerlbl.setForeground(color);
        }
        return powerlbl;
    }

    /** "+1.234"/"-1.234"/"0" - {@link Config#NUMBER_FORMAT} already prefixes a negative diff with "-", so only the "+" for a positive diff needs adding here. */
    private static String formatPowerDiff(int diff) {
        String formatted = Config.NUMBER_FORMAT.format(diff);
        return diff > 0 ? "+" + formatted : formatted;
    }

    /** {@link IconLoader#GREEN} for a gain, {@link IconLoader#RED} for a loss, this fortification's own type color when unchanged. */
    private Color diffColor(int diff) {
        if (diff > 0) {
            return IconLoader.GREEN;
        }
        if (diff < 0) {
            return IconLoader.RED;
        }
        return fortification.type().getColor();
    }

    /**
     * Creates a panel displaying slot indicators.
     * Shows filled slots (occupancy) and empty slots (available capacity).
     *
     * @return A panel containing slot buttons
     */
    private JPanel getSlotPanel(){
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p,BoxLayout.LINE_AXIS));
        for (int i = 0; i < fortification.capacity() ; i++) {
            p.add(new FlatButton(i<filledSlots ?  slot_set : slot_open, false));
            //p.add(getSlot(i<filledSlots));
        }

        return p;
    }

    private JButton getSlot(boolean open){
        ImageIcon icon = open ?  slot_set : slot_open;
        JButton button = new JButton(icon);
        button.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
        return button;
    }

}

