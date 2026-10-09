package org.c2w.gui.pet;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Pet;
import org.c2w.data.repository.PetRepository;
import org.c2w.gui.cowscore.AbstractCowScorePanel;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * "Pets" tab of the CowScore dialog: for every fortification of type
 * {@link FortificationType#HERO} a "marked" checkbox - a pet marked for a fortification
 * adds a bonus to its team's CowScore there (see {@code TeamScoreCalculator}). Saving
 * writes the workspace copy of {@code petCowScore.json} (see {@code FortMarkFiles}).
 */
public final class PetCowScorePanel extends AbstractCowScorePanel<Pet> {

    private final PetRepository repository;

    public PetCowScorePanel(PetRepository repository) {
        super(FortificationType.HERO);
        if (repository == null) {
            throw new IllegalArgumentException("PetCowScorePanel needs a PetRepository");
        }
        this.repository = repository;
        init(repository.findAll());
    }

    @Override
    protected String id(Pet pet) {
        return pet.id();
    }

    @Override
    protected String imagePath(Pet pet) {
        return pet.imagePath();
    }

    @Override
    protected FortMarks fortMarks(Pet pet) {
        return pet.fortMarks();
    }

    @Override
    protected Pet withFortMarks(Pet pet, FortMarks fortMarks) {
        return new Pet(pet.id(), pet.imagePath(), fortMarks);
    }

    @Override
    protected Map<String, FortMarks> loadDefaults() {
        return repository.loadDefaultCowScores();
    }

    @Override
    protected void saveCatalog(List<Pet> updatedCatalog) throws IOException {
        repository.saveCowScores(updatedCatalog);
    }

    @Override
    protected String fileName() {
        return "petCowScore.json";
    }

    @Override
    public String restoreDefaultsConfirmKey() {
        return "petBuffFitScores.restoreDefaultsConfirm";
    }

    /** Pets are only ever marked {@link FortMark#POSITIVE}. */
    @Override
    protected Map<String, FortMark> workingCopy(FortMarks fortMarks) {
        return positiveMarksOnly(fortMarks);
    }

    @Override
    protected List<String> columnHeaderKeys() {
        return List.of("fortMarks.column.positiveRating");
    }

    @Override
    protected void addRowControls(JPanel row, Pet pet, Fortification fortification, Map<String, FortMark> marks) {
        addMarkedCheckBox(row, fortification, marks);
    }
}
