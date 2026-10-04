package org.c2w.gui.journal;

import org.c2w.infra.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Small Swing helpers shared by the journal windows: background work, errors, colors, renderers. */
final class JournalSwing {

    /** Tints (alpha-blended over the table background) for good/bad outcomes and running battles. */
    static final Color GOOD = new Color(78, 133, 66, 60);
    static final Color BAD = new Color(159, 41, 54, 60);
    static final Color RUNNING = new Color(0, 150, 136, 50);

    private JournalSwing() {
    }

    /** Work for a background thread. */
    @FunctionalInterface
    interface Task<T> {
        T run() throws Exception;
    }

    /**
     * Runs {@code task} in a {@link SwingWorker}; {@code onSuccess} runs on the Swing
     * thread with the result, errors are logged and shown to the user.
     */
    static <T> void background(Component parent, Task<T> task, Consumer<T> onSuccess) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(parent, e.getCause());
                }
            }
        }.execute();
    }

    /** Logs and shows an error of a journal action (a locked journal gets its own message). */
    static void showError(Component parent, Throwable error) {
        Logger.logException("Journal action failed", error);
        String text = error instanceof IllegalArgumentException
                ? JournalTexts.text("journal.error.unexpected", String.valueOf(error.getMessage()))
                : JournalImportDialog.errorText(error);
        JOptionPane.showMessageDialog(parent, text, JournalTexts.text("menu.journal"), JOptionPane.ERROR_MESSAGE);
    }

    /** Yes/No question; true for yes. */
    static boolean confirm(Component parent, String question, String title) {
        return JOptionPane.showConfirmDialog(parent, question, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** {@code tint} alpha-blended over {@code base}. */
    static Color blend(Color base, Color tint) {
        if (tint == null) {
            return base;
        }
        float a = tint.getAlpha() / 255f;
        return new Color(
                Math.round(base.getRed() * (1 - a) + tint.getRed() * a),
                Math.round(base.getGreen() * (1 - a) + tint.getGreen() * a),
                Math.round(base.getBlue() * (1 - a) + tint.getBlue() * a));
    }

    /** Renders numbers with grouping (right-aligned) and dates in the display language. */
    static class TextRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            Object shown = value;
            if (value instanceof java.time.LocalDate date) {
                shown = JournalTexts.date(date);
            } else if (value instanceof Integer number) {
                shown = JournalTexts.number(number);
            }
            super.getTableCellRendererComponent(table, shown, selected, focus, row, column);
            setHorizontalAlignment(value instanceof Integer ? RIGHT : LEFT);
            setIcon(null);
            if (!selected) {
                setBackground(table.getBackground());
            }
            return this;
        }
    }

    /** A table with sensible defaults for the journal windows. */
    static JTable table(javax.swing.table.TableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        TextRenderer renderer = new TextRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Integer.class, renderer);
        table.setDefaultRenderer(java.time.LocalDate.class, renderer);
        return table;
    }
}
