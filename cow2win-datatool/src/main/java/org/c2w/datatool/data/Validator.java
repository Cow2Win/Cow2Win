package org.c2w.datatool.data;

import org.c2w.data.model.FortMark;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The check behind "Validate" (and run before every save). Errors prevent saving, warnings
 * are hints. Generic checks come from the {@link ColumnSpec column description} (required,
 * bounds, allowed values, id references and counts), the rest are per-file rules.
 */
final class Validator {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    /** Characters that rarely appear in a battle log - typographic quotes, dashes and the like. */
    private static final String RARE_CHARACTERS = "’‘‚‛“”„«»‹›–—…  ​";

    private final DataSet data;
    private final List<Problem> problems = new ArrayList<>();

    Validator(DataSet data) {
        this.data = data;
    }

    List<Problem> validate() {
        for (TableKind kind : TableKind.values()) {
            List<ColumnSpec> columns = Schema.columns(kind, data);
            List<Row> rows = data.table(kind).rows();
            for (int i = 0; i < rows.size(); i++) {
                for (ColumnSpec column : columns) {
                    checkCell(kind, i, rows.get(i), column);
                }
            }
            checkUniqueKeys(kind);
        }
        checkCatalogs();
        checkFortifications();
        checkCowScores();
        checkCombos();
        return problems;
    }

    // --- generic, from the column description ---

    private void checkCell(TableKind kind, int index, Row row, ColumnSpec column) {
        String field = column.field();
        if (column.type() == ColumnType.DISPLAY_NAME) {
            return;
        }
        Object value = row.get(field);
        if (row.isBlank(field)) {
            if (column.required() && column.type() != ColumnType.ID_LIST) {
                error(kind, index, field, column.header() + " is empty");
            }
            if (column.type() != ColumnType.ID_LIST) {
                return;
            }
        }
        switch (column.type()) {
            case INTEGER -> {
                Integer number = row.getInteger(field);
                if (number == null) {
                    error(kind, index, field, column.header() + " is not a whole number: " + value);
                } else if ((column.min() != null && number < column.min())
                        || (column.max() != null && number > column.max())) {
                    error(kind, index, field, column.header() + " " + number + " is out of range "
                            + range(column));
                }
            }
            case ENUM, MARK -> {
                if (!column.options().contains(value.toString())) {
                    error(kind, index, field, column.header() + ": unknown value " + value);
                }
            }
            case ID_LIST -> {
                List<String> ids = row.getList(field);
                if (ids.size() < column.minCount() || ids.size() > column.maxCount()) {
                    error(kind, index, field, column.header() + ": " + ids.size() + " entries, allowed "
                            + countRange(column));
                }
                Set<String> seen = new HashSet<>();
                for (String id : ids) {
                    if (!seen.add(id)) {
                        error(kind, index, field, column.header() + ": " + id + " is listed twice");
                    }
                    if (!knownIds(column).contains(id)) {
                        error(kind, index, field, column.header() + ": unknown id " + id);
                    }
                }
            }
            case ID_REF -> {
                if (!knownIds(column).contains(value.toString())) {
                    error(kind, index, field, "Unknown " + column.ref().title() + " id " + value);
                }
            }
            default -> {
            }
        }
    }

    private Set<String> knownIds(ColumnSpec column) {
        return column.ref() == null ? new HashSet<>(column.options()) : new HashSet<>(data.table(column.ref()).ids());
    }

    private static String range(ColumnSpec column) {
        if (column.max() == null) {
            return "(at least " + column.min() + ")";
        }
        return "(" + column.min() + "-" + column.max() + ")";
    }

    private static String countRange(ColumnSpec column) {
        return column.maxCount() == Integer.MAX_VALUE ? "at least " + column.minCount()
                : column.minCount() + "-" + column.maxCount();
    }

    private void checkUniqueKeys(TableKind kind) {
        String keyField = kind.keyField();
        Map<Object, Integer> firstIndex = new HashMap<>();
        List<Row> rows = data.table(kind).rows();
        for (int i = 0; i < rows.size(); i++) {
            Object key = rows.get(i).get(keyField);
            if (key == null) {
                continue;
            }
            Integer first = firstIndex.putIfAbsent(key, i);
            if (first != null) {
                error(kind, i, keyField, "Duplicate " + keyField + " " + key + " (also in row " + (first + 1) + ")");
            }
        }
    }

    // --- catalogs ---

    private void checkCatalogs() {
        Map<String, TableKind> catalogOfId = new HashMap<>();
        for (TableKind kind : TableKind.CATALOGS) {
            List<Row> rows = data.table(kind).rows();
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                String id = row.getString(Fields.ID);
                if (id == null) {
                    continue;
                }
                TableKind other = catalogOfId.putIfAbsent(id, kind);
                if (other != null && other != kind) {
                    error(kind, i, Fields.ID, "Id " + id + " is also used in " + other.title()
                            + " (all ids share one key space in the language files)");
                }
                if (row.isNew()) {
                    for (Language language : Language.values()) {
                        if (data.language(language).containsKey(id)) {
                            error(kind, i, Fields.ID, "Key collision: " + id + " is already a key in "
                                    + language.relativePath().getFileName());
                            break;
                        }
                    }
                }
                checkIdPattern(kind, i, id);
                checkImage(kind, i, row);
                checkNameCharacters(kind, i, row);
            }
        }
    }

    private void checkIdPattern(TableKind kind, int index, String id) {
        if (!ID_PATTERN.matcher(id).matches() || !id.startsWith(kind.idPrefix())) {
            warning(kind, index, Fields.ID, "Id " + id + " deviates from the usual pattern (lower case, a-z0-9-"
                    + (kind.idPrefix().isEmpty() ? "" : ", prefix " + kind.idPrefix()) + ")");
        }
    }

    private void checkImage(TableKind kind, int index, Row row) {
        if (kind.imageFolder() == null) {
            return;
        }
        String image = row.getString(Fields.IMAGE);
        if (image == null) {
            warning(kind, index, Fields.IMAGE, "No avatar - the app shows placeholder.png");
        } else if (!data.imageAvailable(kind, image)) {
            warning(kind, index, Fields.IMAGE, "Image " + image + " is missing in images/" + kind.imageFolder()
                    + " - the app shows placeholder.png");
        }
    }

    private void checkNameCharacters(TableKind kind, int index, Row row) {
        for (Language language : Language.values()) {
            String name = row.getString(language.field());
            if (name == null) {
                continue;
            }
            for (char c : name.toCharArray()) {
                if (RARE_CHARACTERS.indexOf(c) >= 0) {
                    warning(kind, index, language.field(), language.header() + " contains '" + c
                            + "' (U+" + String.format("%04X", (int) c) + "), which rarely appears in a battle log"
                            + " - check against the game's log (PATCH-CHECKLIST section 6)");
                    break;
                }
            }
        }
    }

    // --- fortifications ---

    private void checkFortifications() {
        List<Row> rows = data.table(TableKind.FORTIFICATIONS).rows();
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            checkBuff(i, row);
            Integer r = row.getInteger(Fields.ROW);
            Integer c = row.getInteger(Fields.COLUMN);
            if (r != null && c != null) {
                // The map shows one fortification type at a time - a hero and a titan fortification
                // may share a cell (ether-prism and shooting-range do).
                Integer other = positions.putIfAbsent(row.getString(Fields.TYPE) + "/" + r + "/" + c, i);
                if (other != null) {
                    error(TableKind.FORTIFICATIONS, i, Fields.ROW, "Same map position row " + r + ", column " + c
                            + " as row " + (other + 1) + " (same type " + row.getString(Fields.TYPE) + ")");
                }
            }
        }
        checkReachability(rows);
    }

    private void checkBuff(int index, Row row) {
        String kind = row.getString(Fields.BUFF_KIND);
        if (kind == null) {
            if (!row.isBlank(Fields.BUFF_EFFECT) || !row.isBlank(Fields.BUFF_PERCENT)
                    || !row.isBlank(Fields.BUFF_ROLE) || !row.isBlank(Fields.BUFF_ELEMENT)) {
                warning(TableKind.FORTIFICATIONS, index, Fields.BUFF_KIND,
                        "Buff values without buff kind are not saved");
            }
            return;
        }
        if (row.isBlank(Fields.BUFF_EFFECT)) {
            error(TableKind.FORTIFICATIONS, index, Fields.BUFF_EFFECT, "Buff incomplete: effect missing");
        }
        if (row.isBlank(Fields.BUFF_PERCENT)) {
            error(TableKind.FORTIFICATIONS, index, Fields.BUFF_PERCENT, "Buff incomplete: percentage missing");
        }
        if (Fields.BUFF_KIND_ROLE.equals(kind) && row.isBlank(Fields.BUFF_ROLE)) {
            error(TableKind.FORTIFICATIONS, index, Fields.BUFF_ROLE, "Buff incomplete: role missing");
        }
        if (Fields.BUFF_KIND_ELEMENT.equals(kind) && row.isBlank(Fields.BUFF_ELEMENT)) {
            error(TableKind.FORTIFICATIONS, index, Fields.BUFF_ELEMENT, "Buff incomplete: element missing");
        }
    }

    /**
     * Fortifications without prerequisites are attackable from the start; every other one
     * must be reachable from those through the prerequisites (any one of them unlocks it).
     */
    private void checkReachability(List<Row> rows) {
        Set<String> reachable = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (Row row : rows) {
            if (row.getString(Fields.ID) != null && row.getList(Fields.PREREQUISITES).isEmpty()) {
                reachable.add(row.getString(Fields.ID));
                queue.add(row.getString(Fields.ID));
            }
        }
        while (!queue.isEmpty()) {
            String unlocked = queue.poll();
            for (Row row : rows) {
                String id = row.getString(Fields.ID);
                if (id != null && !reachable.contains(id) && row.getList(Fields.PREREQUISITES).contains(unlocked)) {
                    reachable.add(id);
                    queue.add(id);
                }
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i).getString(Fields.ID);
            if (id != null && !reachable.contains(id)) {
                warning(TableKind.FORTIFICATIONS, i, Fields.PREREQUISITES,
                        id + " cannot be reached from a start fortification through its prerequisites");
            }
        }
    }

    // --- CowScore files ---

    private void checkCowScores() {
        for (TableKind kind : TableKind.values()) {
            if (!kind.isCowScore()) {
                continue;
            }
            Set<String> fortifications = new HashSet<>();
            for (Row fortification : data.table(TableKind.FORTIFICATIONS).rows()) {
                if (kind.markedFortificationType().name().equals(fortification.getString(Fields.TYPE))) {
                    fortifications.add(fortification.getString(Fields.ID));
                }
            }
            List<Row> rows = data.table(kind).rows();
            for (int i = 0; i < rows.size(); i++) {
                for (Map.Entry<String, String> mark : rows.get(i).marks().entrySet()) {
                    if (!fortifications.contains(mark.getKey())) {
                        error(kind, i, Fields.ID, "Mark for " + mark.getKey() + ", which is no "
                                + kind.markedFortificationType() + " fortification");
                    } else if (!isFortMark(mark.getValue())) {
                        error(kind, i, Fields.mark(mark.getKey()), "Unknown mark " + mark.getValue());
                    }
                }
            }
        }
    }

    private static boolean isFortMark(String value) {
        for (FortMark mark : FortMark.values()) {
            if (mark.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    // --- hero combos ---

    private void checkCombos() {
        List<Row> rows = data.table(TableKind.HERO_COMBOS).rows();
        for (int i = 0; i < rows.size(); i++) {
            String deactivated = rows.get(i).getString(Fields.DEACTIVATED);
            if (deactivated != null) {
                try {
                    LocalDate.parse(deactivated);
                } catch (DateTimeParseException e) {
                    error(TableKind.HERO_COMBOS, i, Fields.DEACTIVATED, "Deactivated is no date (yyyy-MM-dd): "
                            + deactivated);
                }
            }
        }
    }

    private void error(TableKind kind, int row, String field, String message) {
        problems.add(new Problem(Problem.Severity.ERROR, kind, row, field, message));
    }

    private void warning(TableKind kind, int row, String field, String message) {
        problems.add(new Problem(Problem.Severity.WARNING, kind, row, field, message));
    }
}
