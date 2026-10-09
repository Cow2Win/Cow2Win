package org.c2w.datatool.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts between a data file's JSON array and the tool's {@link Row rows}, driven by a
 * declarative list of JSON fields per file - in the order the fields appear in the files
 * today, which is the order they are written back in. Optional fields are left out when
 * empty.
 */
final class TableCodec {

    /** {@code BOOLEAN} is only written when true (false and missing mean the same). */
    private enum Kind { STRING, INT, BOOLEAN, LIST, INLINE_LIST, BUFF, MARKS }

    private record JsonField(String key, Kind kind, boolean optional) {
    }

    private final List<JsonField> fields;
    private final Set<String> inlineKeys = new HashSet<>();

    private TableCodec(List<JsonField> fields) {
        this.fields = fields;
        for (JsonField field : fields) {
            if (field.kind() == Kind.INLINE_LIST) {
                inlineKeys.add(field.key());
            }
        }
    }

    static TableCodec of(TableKind kind) {
        return new TableCodec(switch (kind) {
            case HEROES -> List.of(str(Fields.ID), opt(Fields.IMAGE, Kind.STRING), field(Fields.ROLES, Kind.LIST));
            case TITANS -> List.of(str(Fields.ID), str(Fields.ELEMENT), field(Fields.ROLES, Kind.LIST),
                    opt(Fields.SUPER_TITAN, Kind.BOOLEAN), opt(Fields.IMAGE, Kind.STRING));
            case PETS, WAR_FLAGS -> List.of(str(Fields.ID), opt(Fields.IMAGE, Kind.STRING));
            case FORTIFICATIONS -> List.of(str(Fields.ID), str(Fields.TYPE), field(Fields.CAPACITY, Kind.INT),
                    field(Fields.CAPTURE_BONUS, Kind.INT), field(Fields.ROW, Kind.INT), field(Fields.COLUMN, Kind.INT),
                    opt(Fields.BUFF, Kind.BUFF), field(Fields.PREREQUISITES, Kind.LIST),
                    field(Fields.STRATEGIC_IMPORTANCE, Kind.INT));
            case HERO_COWSCORE, TITAN_COWSCORE, PET_COWSCORE, WAR_FLAG_COWSCORE ->
                    List.of(str(Fields.ID), opt(Fields.FORT_MARKS, Kind.MARKS));
            case HERO_COMBOS -> List.of(str(Fields.ID), opt(Fields.NAME, Kind.STRING),
                    field(Fields.HERO_IDS, Kind.INLINE_LIST), str(Fields.SOURCE), opt(Fields.DEACTIVATED, Kind.STRING));
            case TITAN_TEMPLATES -> List.of(field(Fields.SLOT, Kind.INT), field(Fields.TITAN_IDS, Kind.INLINE_LIST));
        });
    }

    private static JsonField str(String key) {
        return new JsonField(key, Kind.STRING, false);
    }

    private static JsonField field(String key, Kind kind) {
        return new JsonField(key, kind, false);
    }

    private static JsonField opt(String key, Kind kind) {
        return new JsonField(key, kind, true);
    }

    // --- reading ---

    List<Row> read(String json) {
        List<Row> rows = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            rows.add(readRow(element.getAsJsonObject()));
        }
        return rows;
    }

    private Row readRow(JsonObject object) {
        Row row = new Row(false);
        Set<String> known = new HashSet<>();
        for (JsonField field : fields) {
            known.add(field.key());
            JsonElement value = object.get(field.key());
            if (value == null || value.isJsonNull()) {
                continue;
            }
            switch (field.kind()) {
                case STRING -> row.set(field.key(), value.getAsString());
                case INT -> row.set(field.key(), value.getAsInt());
                case BOOLEAN -> row.set(field.key(), value.getAsBoolean());
                case LIST, INLINE_LIST -> row.set(field.key(), stringList(value.getAsJsonArray()));
                case BUFF -> readBuff(row, value.getAsJsonObject());
                case MARKS -> {
                    for (Map.Entry<String, JsonElement> mark : value.getAsJsonObject().entrySet()) {
                        row.marks().put(mark.getKey(), mark.getValue().getAsString());
                    }
                }
            }
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!known.contains(entry.getKey())) {
                row.unknownFields().put(entry.getKey(), entry.getValue());
            }
        }
        return row;
    }

    private static void readBuff(Row row, JsonObject buff) {
        row.set(Fields.BUFF_KIND, string(buff, "kind"));
        row.set(Fields.BUFF_EFFECT, string(buff, "effect"));
        JsonElement percent = buff.get("bonusPercent");
        row.set(Fields.BUFF_PERCENT, percent == null || percent.isJsonNull() ? null : percent.getAsInt());
        row.set(Fields.BUFF_ROLE, string(buff, "role"));
        row.set(Fields.BUFF_ELEMENT, string(buff, "element"));
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static List<String> stringList(JsonArray array) {
        List<String> list = new ArrayList<>();
        array.forEach(e -> list.add(e.getAsString()));
        return list;
    }

    // --- writing ---

    String write(List<Row> rows, boolean trailingNewline) {
        JsonArray array = new JsonArray();
        for (Row row : rows) {
            array.add(writeRow(row));
        }
        return JsonText.write(array, inlineKeys, trailingNewline);
    }

    private JsonObject writeRow(Row row) {
        JsonObject object = new JsonObject();
        for (JsonField field : fields) {
            String key = field.key();
            switch (field.kind()) {
                case STRING -> {
                    String value = row.getString(key);
                    if (value != null || !field.optional()) {
                        object.add(key, value == null ? null : new JsonPrimitive(value));
                    }
                }
                case INT -> {
                    Integer value = row.getInteger(key);
                    if (value != null || !field.optional()) {
                        object.add(key, value == null ? null : new JsonPrimitive(value));
                    }
                }
                case BOOLEAN -> {
                    if (row.getBoolean(key)) {
                        object.addProperty(key, true);
                    }
                }
                case LIST, INLINE_LIST -> {
                    List<String> list = row.getList(key);
                    if (!list.isEmpty() || !field.optional()) {
                        JsonArray array = new JsonArray();
                        list.forEach(array::add);
                        object.add(key, array);
                    }
                }
                case BUFF -> {
                    JsonObject buff = writeBuff(row);
                    if (buff != null) {
                        object.add(key, buff);
                    }
                }
                case MARKS -> {
                    if (!row.marks().isEmpty()) {
                        JsonObject marks = new JsonObject();
                        row.marks().forEach(marks::addProperty);
                        object.add(key, marks);
                    }
                }
            }
        }
        row.unknownFields().forEach(object::add);
        return object;
    }

    /** No buff kind means no buff object; otherwise only the role or element matching the kind. */
    private static JsonObject writeBuff(Row row) {
        String kind = row.getString(Fields.BUFF_KIND);
        if (kind == null) {
            return null;
        }
        JsonObject buff = new JsonObject();
        buff.addProperty("kind", kind);
        if (row.getString(Fields.BUFF_EFFECT) != null) {
            buff.addProperty("effect", row.getString(Fields.BUFF_EFFECT));
        }
        if (row.getInteger(Fields.BUFF_PERCENT) != null) {
            buff.addProperty("bonusPercent", row.getInteger(Fields.BUFF_PERCENT));
        }
        if (Fields.BUFF_KIND_ROLE.equals(kind) && row.getString(Fields.BUFF_ROLE) != null) {
            buff.addProperty("role", row.getString(Fields.BUFF_ROLE));
        }
        if (Fields.BUFF_KIND_ELEMENT.equals(kind) && row.getString(Fields.BUFF_ELEMENT) != null) {
            buff.addProperty("element", row.getString(Fields.BUFF_ELEMENT));
        }
        return buff;
    }
}
