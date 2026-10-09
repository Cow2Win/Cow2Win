package org.c2w.gui.titan;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Titan;
import org.c2w.data.repository.TitanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link TitanCowScorePanel} with a real titan catalog in a temporary workspace - headless, never shown. */
class TitanCowScorePanelTest {

    @TempDir
    Path workspace;

    @Test
    @DisplayName("copying a titan with new marks keeps its roles and superTitan")
    void copyKeepsRolesAndSuperTitan() {
        TitanRepository repository = new TitanRepository(workspace);
        TitanCowScorePanel panel = new TitanCowScorePanel(repository);
        Titan araji = repository.findById("araji").orElseThrow();
        FortMarks marks = new FortMarks(Map.of("bridge", FortMark.POSITIVE));

        Titan copy = panel.withFortMarks(araji, marks);

        assertEquals(marks, copy.fortMarks());
        assertEquals(araji.roles(), copy.roles());
        assertTrue(copy.superTitan());
        assertEquals(araji.element(), copy.element());
        assertEquals(araji.imagePath(), copy.imagePath());
    }

    @Test
    @DisplayName("saving every titan (after restoring the defaults) keeps roles and superTitan, in memory and after a reload")
    void saveKeepsRolesAndSuperTitan() throws Exception {
        TitanRepository repository = new TitanRepository(workspace);
        Map<String, Titan> before = new HashMap<>();
        repository.findAll().forEach(t -> before.put(t.id(), t));
        TitanCowScorePanel panel = new TitanCowScorePanel(repository);

        panel.restoreDefaults();
        panel.save();

        for (TitanRepository repo : new TitanRepository[]{repository, new TitanRepository(workspace)}) {
            for (Titan titan : repo.findAll()) {
                Titan original = before.get(titan.id());
                assertEquals(original.roles(), titan.roles(), titan.id());
                assertEquals(original.superTitan(), titan.superTitan(), titan.id());
            }
        }
    }
}
