package org.c2w.gui.cowscore;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Finds Swing components in a (never shown) panel - for the CowScore panel and dialog tests. */
public final class CowScoreTestSupport {

    private CowScoreTestSupport() {
    }

    /** Every component of {@code type} below {@code root}, in layout order. */
    public static <C extends Component> List<C> findAll(Container root, Class<C> type) {
        List<C> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <C extends Component> void collect(Container container, Class<C> type, List<C> found) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container nested) {
                collect(nested, type, found);
            }
        }
    }
}
