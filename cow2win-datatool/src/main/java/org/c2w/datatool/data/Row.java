package org.c2w.datatool.data;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One entry of a table: field values by {@link Fields field name} - {@code String},
 * {@code Integer}, {@code List<String>} or null. The fortification marks of a CowScore entry
 * keep their file order in a map of their own ({@code mark:<fortification id>} fields), JSON
 * fields the tool does not know are kept as they are.
 */
public final class Row {

    private final boolean isNew;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, String> marks = new LinkedHashMap<>();
    private final Map<String, JsonElement> unknownFields = new LinkedHashMap<>();

    public Row(boolean isNew) {
        this.isNew = isNew;
    }

    /** True for a row added in this session (not yet saved) - only those get an editable id. */
    public boolean isNew() {
        return isNew;
    }

    public Object get(String field) {
        if (field.startsWith(Fields.MARK_PREFIX)) {
            return marks.get(field.substring(Fields.MARK_PREFIX.length()));
        }
        return values.get(field);
    }

    /** Sets a field; blank strings and empty marks count as "no value". */
    public void set(String field, Object value) {
        if (value instanceof String s && s.isBlank()) {
            value = null;
        }
        if (field.startsWith(Fields.MARK_PREFIX)) {
            String fortificationId = field.substring(Fields.MARK_PREFIX.length());
            if (value == null) {
                marks.remove(fortificationId);
            } else {
                marks.put(fortificationId, value.toString());
            }
            return;
        }
        values.put(field, value);
    }

    public String getString(String field) {
        Object value = get(field);
        return value == null ? null : value.toString();
    }

    public Integer getInteger(String field) {
        return get(field) instanceof Integer i ? i : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String field) {
        return get(field) instanceof List<?> list ? (List<String>) list : List.of();
    }

    public boolean isBlank(String field) {
        Object value = get(field);
        return value == null || (value instanceof List<?> list && list.isEmpty());
    }

    /** Fortification marks (fortification id to mark name) in file order - live view. */
    public Map<String, String> marks() {
        return marks;
    }

    /** JSON fields not described by the table's schema, written back unchanged after the known ones. */
    public Map<String, JsonElement> unknownFields() {
        return unknownFields;
    }
}
