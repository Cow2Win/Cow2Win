package org.c2w.datatool.gui;

import org.c2w.datatool.data.DataSet;

import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** The tool window; its title shows the resources folder being edited. */
public final class DataToolFrame extends JFrame {

    public DataToolFrame(DataSet data) {
        super("Cow2Win data tool - " + data.root());
        DataToolPanel panel = new DataToolPanel(data);
        setContentPane(panel);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (panel.confirmClose()) {
                    dispose();
                }
            }
        });
        setSize(1400, 850);
        setLocationRelativeTo(null);
    }
}
