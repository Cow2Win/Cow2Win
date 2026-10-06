package org.c2w.gui.titan;

import org.c2w.data.model.ElementBuff;
import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanElement;
import org.c2w.data.repository.TitanRepository;
import org.c2w.gui.cowscore.AbstractCowScorePanel;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * "Titans" tab of the CowScore dialog - the TITAN-side counterpart of
 * {@link org.c2w.gui.hero.HeroCowScorePanel}, for fortifications of type
 * {@link FortificationType#TITAN} and with element matches instead of role matches.
 * Saving writes every titan to the workspace copy of {@code titanCowScore.json}.
 */
public final class TitanCowScorePanel extends AbstractCowScorePanel<Titan> {

    private final TitanRepository repository;

    public TitanCowScorePanel(TitanRepository repository) {
        super(FortificationType.TITAN);
        if (repository == null) {
            throw new IllegalArgumentException("TitanCowScorePanel needs a TitanRepository");
        }
        this.repository = repository;
        init(repository.findAll());
    }

    @Override
    protected String id(Titan titan) {
        return titan.id();
    }

    @Override
    protected String imagePath(Titan titan) {
        return titan.imagePath();
    }

    @Override
    protected FortMarks fortMarks(Titan titan) {
        return titan.fortMarks();
    }

    @Override
    protected Titan withFortMarks(Titan titan, FortMarks fortMarks) {
        return new Titan(titan.id(), titan.element(), titan.imagePath(), fortMarks);
    }

    @Override
    protected Map<String, FortMarks> loadDefaults() {
        return repository.loadDefaultCowScores();
    }

    /** Only writes {@code titanCowScore.json}, never {@code titans.json} - see {@link TitanRepository#saveCowScores}. */
    @Override
    protected void saveCatalog(List<Titan> updatedCatalog) throws IOException {
        repository.saveCowScores(updatedCatalog);
    }

    @Override
    protected String fileName() {
        return "titanCowScore.json";
    }

    @Override
    public String restoreDefaultsConfirmKey() {
        return "titanBuffFitScores.restoreDefaultsConfirm";
    }

    @Override
    protected List<String> columnHeaderKeys() {
        return List.of("fortMarks.column.buff", "fortMarks.column.rating");
    }

    /** The titan's element matches the fortification's buff - no mark there, the buff already counts. */
    @Override
    protected boolean buffMatches(Titan titan, Fortification fortification) {
        return titan.matchesBuff(fortification.buff());
    }

    @Override
    protected void addRowControls(JPanel row, Titan titan, Fortification fortification, Map<String, FortMark> marks) {
        String elementText = fortification.buff() instanceof ElementBuff elementBuff ? elementLabel(elementBuff.element()) : null;
        boolean buffMatch = elementText != null && buffMatches(titan, fortification);
        addBuffLabel(row, elementText, buffMatch);
        addFortMarkCombo(row, fortification, marks, buffMatch, "fortMarks.buffLockedTooltip.titan");
    }

    /** The localized display name for a {@link TitanElement} (language file key {@code titanElement.<NAME>}). */
    private static String elementLabel(TitanElement element) {
        return LanguageService.displayName("titanElement." + element.name());
    }
}
