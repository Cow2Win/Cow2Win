package org.c2w.gui.flag;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.WarFlag;
import org.c2w.data.repository.WarFlagRepository;
import org.c2w.gui.cowscore.AbstractCowScorePanel;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * "War flags" tab of the CowScore dialog - the war flag counterpart of
 * {@link org.c2w.gui.pet.PetCowScorePanel}: a "marked" checkbox per fortification of type
 * {@link FortificationType#HERO}. Saving writes the workspace copy of
 * {@code warFlagCowScore.json} (see {@code FortMarkFiles}).
 */
public final class WarFlagCowScorePanel extends AbstractCowScorePanel<WarFlag> {

    private final WarFlagRepository repository;

    public WarFlagCowScorePanel(WarFlagRepository repository) {
        super(FortificationType.HERO);
        if (repository == null) {
            throw new IllegalArgumentException("WarFlagCowScorePanel needs a WarFlagRepository");
        }
        this.repository = repository;
        init(repository.findAll(), 180);
    }

    @Override
    protected String id(WarFlag warFlag) {
        return warFlag.id();
    }

    @Override
    protected String imagePath(WarFlag warFlag) {
        return warFlag.imagePath();
    }

    @Override
    protected FortMarks fortMarks(WarFlag warFlag) {
        return warFlag.fortMarks();
    }

    @Override
    protected WarFlag withFortMarks(WarFlag warFlag, FortMarks fortMarks) {
        return new WarFlag(warFlag.id(), warFlag.imagePath(), fortMarks);
    }

    @Override
    protected Map<String, FortMarks> loadDefaults() {
        return repository.loadDefaultCowScores();
    }

    @Override
    protected void saveCatalog(List<WarFlag> updatedCatalog) throws IOException {
        repository.saveCowScores(updatedCatalog);
    }

    @Override
    protected String fileName() {
        return "warFlagCowScore.json";
    }

    @Override
    public String restoreDefaultsConfirmKey() {
        return "warFlagBuffFitScores.restoreDefaultsConfirm";
    }

    /** War flags are only ever marked {@link FortMark#POSITIVE}. */
    @Override
    protected Map<String, FortMark> workingCopy(FortMarks fortMarks) {
        return positiveMarksOnly(fortMarks);
    }

    @Override
    protected void addRowControls(JPanel row, WarFlag warFlag, Fortification fortification, Map<String, FortMark> marks) {
        addMarkedCheckBox(row, fortification, marks);
    }
}
