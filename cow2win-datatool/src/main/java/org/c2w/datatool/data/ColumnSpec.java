package org.c2w.datatool.data;

import java.util.List;
import java.util.function.Predicate;

/**
 * Declarative description of one table column.
 *
 * @param header    column title
 * @param field     row field (see {@link Fields})
 * @param type      display, editor and checks
 * @param required  an empty value is an error
 * @param min       lower bound of an {@link ColumnType#INTEGER} value, or null
 * @param max       upper bound of an {@link ColumnType#INTEGER} value, or null
 * @param options   allowed values of {@link ColumnType#ENUM}/{@link ColumnType#MARK} columns and of
 *                  {@link ColumnType#ID_LIST} columns over an enum; empty otherwise
 * @param ref       catalog the ids of an {@link ColumnType#ID_LIST}/{@link ColumnType#ID_REF} column
 *                  refer to (null when {@code options} lists them)
 * @param minCount  minimum number of ids of an {@link ColumnType#ID_LIST} column
 * @param maxCount  maximum number of ids of an {@link ColumnType#ID_LIST} column
 * @param orderable the order of the ids can be changed in the selection dialog
 * @param language  language of a {@link ColumnType#NAME} column, null otherwise
 * @param editable  whether the cell of a row can be edited
 */
public record ColumnSpec(String header, String field, ColumnType type, boolean required, Integer min, Integer max,
                         List<String> options, TableKind ref, int minCount, int maxCount, boolean orderable,
                         Language language, Predicate<Row> editable) {

    private static final Predicate<Row> ALWAYS = row -> true;

    public static ColumnSpec of(String header, String field, ColumnType type) {
        return new ColumnSpec(header, field, type, false, null, null, List.of(), null, 0, Integer.MAX_VALUE,
                false, null, ALWAYS);
    }

    public ColumnSpec asRequired() {
        return new ColumnSpec(header, field, type, true, min, max, options, ref, minCount, maxCount, orderable,
                language, editable);
    }

    public ColumnSpec bounds(Integer min, Integer max) {
        return new ColumnSpec(header, field, type, required, min, max, options, ref, minCount, maxCount, orderable,
                language, editable);
    }

    public ColumnSpec options(List<String> options) {
        return new ColumnSpec(header, field, type, required, min, max, List.copyOf(options), ref, minCount, maxCount,
                orderable, language, editable);
    }

    public ColumnSpec ref(TableKind ref) {
        return new ColumnSpec(header, field, type, required, min, max, options, ref, minCount, maxCount, orderable,
                language, editable);
    }

    public ColumnSpec count(int minCount, int maxCount) {
        return new ColumnSpec(header, field, type, minCount > 0, min, max, options, ref, minCount, maxCount,
                orderable, language, editable);
    }

    public ColumnSpec asOrderable() {
        return new ColumnSpec(header, field, type, required, min, max, options, ref, minCount, maxCount, true,
                language, editable);
    }

    public ColumnSpec language(Language language) {
        return new ColumnSpec(header, field, type, required, min, max, options, ref, minCount, maxCount, orderable,
                language, editable);
    }

    public ColumnSpec editableIf(Predicate<Row> editable) {
        return new ColumnSpec(header, field, type, required, min, max, options, ref, minCount, maxCount, orderable,
                language, editable);
    }

    public boolean isEditable(Row row) {
        return type != ColumnType.DISPLAY_NAME && editable.test(row);
    }
}
