package org.c2w.gui.manual;

import org.c2w.gui.action.Stage;

import java.util.List;

/**
 * The fixed anchors of the manual's chapters ({@code ##} headings of {@code manual.md}, see
 * {@link ManualRenderer}) - the targets of F1 and of the links inside the manual. They must
 * never change, only the chapter titles may.
 */
public final class ManualChapters {

    public static final String INTRODUCTION = "introduction";
    public static final String GETTING_STARTED = "getting-started";
    public static final String INPUT = "input";
    public static final String CONCEPT = "concept";
    public static final String OUTPUT = "output";
    public static final String COWSCORE = "cowscore";
    public static final String JOURNAL = "journal";
    public static final String SETTINGS = "settings";
    public static final String FAQ = "faq";
    public static final String ABOUT = "about";

    /** Every chapter in the order of the manual. */
    public static final List<String> ALL = List.of(INTRODUCTION, GETTING_STARTED, INPUT, CONCEPT, OUTPUT,
            COWSCORE, JOURNAL, SETTINGS, FAQ, ABOUT);

    private ManualChapters() {
    }

    /**
     * The chapter F1 opens in the main window: the one of the active process stage, "Getting
     * started" without one ({@code null}, e.g. no guild open) or for any other stage.
     */
    public static String forStage(Stage stage) {
        if (stage == null) {
            return GETTING_STARTED;
        }
        return switch (stage) {
            case INPUT -> INPUT;
            case CONCEPT -> CONCEPT;
            case OUTPUT -> OUTPUT;
            default -> GETTING_STARTED;
        };
    }
}
