package org.c2w.gui.journal;

import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalCounts;
import org.c2w.service.JournalMaintenanceService;

import java.awt.*;
import java.util.List;

/**
 * The battle action offered by the battle list: deleting battles (after a
 * confirmation naming exactly what goes). Every database access runs in the
 * background; after a change {@code onChanged} runs (on the Swing thread) so
 * all journal windows reload.
 */
final class JournalOperations {

    private final JournalMaintenanceService service;
    private final Runnable onChanged;

    JournalOperations(JournalMaintenanceService service, Runnable onChanged) {
        this.service = service;
        this.onChanged = onChanged;
    }

    JournalMaintenanceService service() {
        return service;
    }

    /** Asks - naming exactly what goes - and deletes the battles. */
    void deleteBattles(Component parent, List<BattleSummary> battles) {
        if (battles.isEmpty()) {
            return;
        }
        List<Integer> ids = battles.stream().map(BattleSummary::battleId).toList();
        JournalSwing.background(parent, () -> service.countBattles(ids), (JournalCounts counts) -> {
            if (!JournalSwing.confirm(parent, JournalTexts.deleteBattlesQuestion(battles, counts),
                    JournalTexts.text("journal.delete.title"))) {
                return;
            }
            JournalSwing.background(parent, () -> service.deleteBattles(ids), deleted -> onChanged.run());
        });
    }
}
