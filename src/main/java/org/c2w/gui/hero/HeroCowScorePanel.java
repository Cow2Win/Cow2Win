package org.c2w.gui.hero;

import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortMarks;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Hero;
import org.c2w.data.model.Role;
import org.c2w.data.model.RoleBuff;
import org.c2w.data.repository.HeroRepository;
import org.c2w.gui.cowscore.AbstractCowScorePanel;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * "Heroes" tab of the CowScore dialog: one {@link FortMark} (neutral / positive /
 * negative) per hero and fortification of type {@link FortificationType#HERO}. Each row
 * also shows the role the fortification's {@link RoleBuff} asks for, with the automatic,
 * read-only BUFF marker when the hero's role matches - then the mark is locked to
 * "Positive (buff)" and none is kept (the buff already counts). Saving writes every hero to the
 * workspace copy of {@code cowScore.json} (see {@code FortMarkFiles} for the format).
 */
public final class HeroCowScorePanel extends AbstractCowScorePanel<Hero> {

    private final HeroRepository repository;

    public HeroCowScorePanel(HeroRepository repository) {
        super(FortificationType.HERO);
        if (repository == null) {
            throw new IllegalArgumentException("HeroCowScorePanel needs a HeroRepository");
        }
        this.repository = repository;
        init(repository.findAll());
    }

    @Override
    protected String id(Hero hero) {
        return hero.id();
    }

    @Override
    protected String imagePath(Hero hero) {
        return hero.imagePath();
    }

    @Override
    protected FortMarks fortMarks(Hero hero) {
        return hero.fortMarks();
    }

    @Override
    protected Hero withFortMarks(Hero hero, FortMarks fortMarks) {
        return new Hero(hero.id(), hero.roles(), hero.imagePath(), fortMarks);
    }

    @Override
    protected Map<String, FortMarks> loadDefaults() {
        return repository.loadDefaultCowScores();
    }

    /** Only writes {@code cowScore.json}, never {@code heroes.json} - see {@link HeroRepository#saveCowScores}. */
    @Override
    protected void saveCatalog(List<Hero> updatedCatalog) throws IOException {
        repository.saveCowScores(updatedCatalog);
    }

    @Override
    protected String fileName() {
        return "cowScore.json";
    }

    @Override
    public String restoreDefaultsConfirmKey() {
        return "heroBuffFitScores.restoreDefaultsConfirm";
    }

    @Override
    protected List<String> columnHeaderKeys() {
        return List.of("fortMarks.column.buff", "fortMarks.column.rating");
    }

    /** The hero's role matches the fortification's buff - no mark there, the buff already counts. */
    @Override
    protected boolean buffMatches(Hero hero, Fortification fortification) {
        return hero.matchesBuff(fortification.buff());
    }

    @Override
    protected void addRowControls(JPanel row, Hero hero, Fortification fortification, Map<String, FortMark> marks) {
        String roleText = fortification.buff() instanceof RoleBuff roleBuff ? roleLabel(roleBuff.role()) : null;
        boolean buffMatch = roleText != null && buffMatches(hero, fortification);
        addBuffLabel(row, roleText, buffMatch);
        addFortMarkCombo(row, fortification, marks, buffMatch, "fortMarks.buffLockedTooltip.hero");
    }

    /** The localized display name for a {@link Role} (language file key {@code role.<NAME>}). */
    private static String roleLabel(Role role) {
        return LanguageService.displayName("role." + role.name());
    }
}
