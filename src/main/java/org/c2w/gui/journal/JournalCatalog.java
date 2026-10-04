package org.c2w.gui.journal;

import org.c2w.data.journal.FightUnit;
import org.c2w.data.journal.NameMappingKind;
import org.c2w.data.journal.UnitKind;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.TitanElement;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;

import javax.swing.*;
import java.util.*;

/**
 * Catalog entries (display name and image) for the journal GUI: the choices for
 * mapping an unknown name, and the images of units in the battle detail.
 */
final class JournalCatalog {

    static final int ICON_SIZE = 24;

    private JournalCatalog() {
    }

    /** All catalog entries of a kind, by display name. */
    static List<NamesStepPanel.CatalogChoice> choices(Catalog catalog, NameMappingKind kind) {
        List<NamesStepPanel.CatalogChoice> result = new ArrayList<>();
        switch (kind) {
            case FORTIFICATION -> {
                for (Fortification f : FortificationRepository.findAll()) {
                    result.add(new NamesStepPanel.CatalogChoice(f.id(), LanguageService.displayName(f.id()), null));
                }
            }
            case HERO -> catalog.heroes().findAll().forEach(h -> result.add(
                    new NamesStepPanel.CatalogChoice(h.id(), LanguageService.displayName(h.id()), icon(h.imagePath()))));
            case PET -> catalog.pets().findAll().forEach(p -> result.add(
                    new NamesStepPanel.CatalogChoice(p.id(), LanguageService.displayName(p.id()), icon(p.imagePath()))));
            case TITAN -> catalog.titans().findAll().forEach(t -> result.add(
                    new NamesStepPanel.CatalogChoice(t.id(), LanguageService.displayName(t.id()), icon(t.imagePath()))));
            case TOTEM -> {
                for (TitanElement element : TitanElement.values()) {
                    result.add(new NamesStepPanel.CatalogChoice(element.name(), TotemTexts.gameName(element), null));
                }
            }
        }
        result.sort(Comparator.comparing(NamesStepPanel.CatalogChoice::label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Display name of a mapping target, e.g. "Dante" for a hero id or the totem's game name. */
    static String label(NameMappingKind kind, String id) {
        if (kind == NameMappingKind.TOTEM) {
            try {
                return TotemTexts.gameName(TitanElement.valueOf(id));
            } catch (IllegalArgumentException e) {
                return id;
            }
        }
        return LanguageService.displayName(id);
    }

    /** Image of a mapping target, {@code null} if there is none. */
    static Icon icon(Catalog catalog, NameMappingKind kind, String id) {
        if (id == null) {
            return null;
        }
        return switch (kind) {
            case HERO -> catalog.heroes().findById(id).map(h -> icon(h.imagePath())).orElse(null);
            case PET -> catalog.pets().findById(id).map(p -> icon(p.imagePath())).orElse(null);
            case TITAN -> catalog.titans().findById(id).map(t -> icon(t.imagePath())).orElse(null);
            case FORTIFICATION, TOTEM -> null;
        };
    }

    /** Image of a unit of a battle log, {@code null} for totems and unknown names. */
    static Icon icon(Catalog catalog, FightUnit unit) {
        if (unit.catalogId() == null) {
            return null;
        }
        return switch (unit.kind()) {
            case HERO -> icon(catalog, NameMappingKind.HERO, unit.catalogId());
            case PET -> icon(catalog, NameMappingKind.PET, unit.catalogId());
            case TITAN -> icon(catalog, NameMappingKind.TITAN, unit.catalogId());
            case TOTEM -> null;
        };
    }

    /** The unit's name: catalog name in the display language if known, else the raw name. */
    static String unitName(FightUnit unit) {
        if (unit.kind() == UnitKind.TOTEM) {
            return unit.totemElement() == null ? unit.name() : TotemTexts.gameName(unit.totemElement());
        }
        return unit.catalogId() == null ? unit.name() : LanguageService.displayName(unit.catalogId());
    }

    private static Icon icon(String imagePath) {
        try {
            return imagePath == null ? null : IconLoader.iconFor(imagePath, ICON_SIZE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
