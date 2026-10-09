package org.c2w.datatool.data;

import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.ComboSource;
import org.c2w.data.model.FortMark;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Role;
import org.c2w.data.model.TitanElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.c2w.datatool.data.ColumnSpec.of;
import static org.c2w.datatool.data.ColumnType.*;

/** The column description of every tab. */
public final class Schema {

    /** Ids of existing entries are referenced by the users' workspaces and stay fixed. */
    private static final Predicate<Row> NEW_ROWS_ONLY = Row::isNew;

    private Schema() {
    }

    public static List<ColumnSpec> columns(TableKind kind, DataSet data) {
        List<ColumnSpec> columns = new ArrayList<>();
        switch (kind) {
            case HEROES -> {
                catalogStart(columns, true);
                columns.add(of("Roles", Fields.ROLES, ID_LIST).options(names(Role.values())).count(1, Integer.MAX_VALUE));
            }
            case TITANS -> {
                catalogStart(columns, true);
                columns.add(of("Element", Fields.ELEMENT, ENUM).options(names(TitanElement.values())).asRequired());
            }
            case PETS, WAR_FLAGS -> catalogStart(columns, true);
            case FORTIFICATIONS -> {
                catalogStart(columns, false);
                columns.add(of("Type", Fields.TYPE, ENUM).options(names(FortificationType.values())).asRequired());
                columns.add(of("Capacity", Fields.CAPACITY, INTEGER).bounds(1, null).asRequired());
                columns.add(of("Capture bonus", Fields.CAPTURE_BONUS, INTEGER).bounds(0, null).asRequired());
                columns.add(of("Row", Fields.ROW, INTEGER).bounds(0, null).asRequired());
                columns.add(of("Column", Fields.COLUMN, INTEGER).bounds(0, null).asRequired());
                columns.add(of("Buff kind", Fields.BUFF_KIND, ENUM)
                        .options(List.of(Fields.BUFF_KIND_ROLE, Fields.BUFF_KIND_ELEMENT)));
                Predicate<Row> hasBuff = row -> !row.isBlank(Fields.BUFF_KIND);
                columns.add(of("Buff effect", Fields.BUFF_EFFECT, ENUM).options(names(BuffEffect.values()))
                        .editableIf(hasBuff));
                columns.add(of("Buff %", Fields.BUFF_PERCENT, INTEGER).bounds(1, null).editableIf(hasBuff));
                columns.add(of("Buff role", Fields.BUFF_ROLE, ENUM).options(names(Role.values()))
                        .editableIf(row -> Fields.BUFF_KIND_ROLE.equals(row.getString(Fields.BUFF_KIND))));
                columns.add(of("Buff element", Fields.BUFF_ELEMENT, ENUM).options(names(TitanElement.values()))
                        .editableIf(row -> Fields.BUFF_KIND_ELEMENT.equals(row.getString(Fields.BUFF_KIND))));
                columns.add(of("Prerequisites", Fields.PREREQUISITES, ID_LIST).ref(TableKind.FORTIFICATIONS));
                columns.add(of("Strategic importance", Fields.STRATEGIC_IMPORTANCE, INTEGER).bounds(1, 10).asRequired());
            }
            case HERO_COWSCORE, TITAN_COWSCORE, PET_COWSCORE, WAR_FLAG_COWSCORE -> {
                columns.add(of("ID", Fields.ID, ID_REF).ref(kind.catalogOfCowScore()).asRequired()
                        .editableIf(NEW_ROWS_ONLY));
                columns.add(of("Name", Fields.DISPLAY_NAME, DISPLAY_NAME).ref(kind.catalogOfCowScore()));
                for (Row fortification : data.table(TableKind.FORTIFICATIONS).rows()) {
                    String id = fortification.getString(Fields.ID);
                    if (id != null && kind.markedFortificationType().name().equals(fortification.getString(Fields.TYPE))) {
                        String header = fortification.getString(Language.EN.field());
                        columns.add(of(header == null ? id : header, Fields.mark(id), MARK)
                                .options(names(FortMark.values())));
                    }
                }
            }
            case HERO_COMBOS -> {
                columns.add(of("ID", Fields.ID, TEXT).asRequired().editableIf(NEW_ROWS_ONLY));
                columns.add(of("Heroes", Fields.HERO_IDS, ID_LIST).ref(TableKind.HEROES).count(2, 5).asOrderable());
                columns.add(of("Name", Fields.NAME, TEXT));
                columns.add(of("Source", Fields.SOURCE, ENUM).options(names(ComboSource.values())).asRequired());
                columns.add(of("Deactivated", Fields.DEACTIVATED, TEXT));
            }
            case TITAN_TEMPLATES -> {
                columns.add(of("Slot", Fields.SLOT, INTEGER).bounds(1, 5).asRequired());
                columns.add(of("Titans", Fields.TITAN_IDS, ID_LIST).ref(TableKind.TITANS).count(1, 5).asOrderable());
            }
        }
        return columns;
    }

    private static void catalogStart(List<ColumnSpec> columns, boolean withImage) {
        columns.add(of("ID", Fields.ID, TEXT).asRequired().editableIf(NEW_ROWS_ONLY));
        if (withImage) {
            columns.add(of("Avatar", Fields.IMAGE, IMAGE));
        }
        for (Language language : Language.values()) {
            columns.add(of(language.header(), language.field(), NAME).language(language).asRequired());
        }
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
