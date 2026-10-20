package org.c2w.gui;

import org.c2w.data.repository.CatalogUpdateService;
import org.c2w.data.repository.CatalogUpdateService.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CatalogUpdateDialog}: the confirmation decides whether the server is contacted at all -
 * headless, without network, confirmation and progress dialog replaced.
 */
class CatalogUpdateDialogTest {

    @TempDir
    Path temp;

    /** Counts the fetches; every fetch fails like an unreachable server. */
    private final AtomicInteger fetches = new AtomicInteger();
    /** Counts the service steps (check, install) run through the progress dialog. */
    private final AtomicInteger steps = new AtomicInteger();

    private CatalogUpdateDialog dialog(boolean confirmed) {
        CatalogUpdateService service = new CatalogUpdateService(path -> {
            fetches.incrementAndGet();
            throw new IOException("offline");
        }, temp.resolve("catalog"), temp.resolve("previous"), temp.resolve("download"));
        return new CatalogUpdateDialog(service, () -> confirmed, step -> {
            steps.incrementAndGet();
            step.apply(new CatalogUpdateService.Progress() {
                @Override
                public void update(CatalogUpdateService.Phase phase, int done, int total) {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            });
            // Reported as cancelled, so no result message is shown (it would need a display).
            return new Outcome(CatalogUpdateService.Status.CANCELLED, null, null, null, null, null);
        });
    }

    @Test
    @DisplayName("Cancel: nothing is fetched, no step runs")
    void cancelled() {
        dialog(false).run();

        assertEquals(0, steps.get());
        assertEquals(0, fetches.get());
    }

    @Test
    @DisplayName("Update: the check runs exactly once and fetches the manifest")
    void confirmed() {
        dialog(true).run();

        assertEquals(1, steps.get());
        assertEquals(1, fetches.get());
    }
}
