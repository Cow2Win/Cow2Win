package org.c2w.datatool.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.c2w.infra.JsonSupport;

import java.util.Map;
import java.util.Set;

/**
 * Writes JSON exactly in the layout of the shipped data files: Gson's pretty printing
 * (2 spaces, {@code "key": value}, empty arrays/objects as {@code []}/{@code {}}), no HTML
 * escaping, LF - except that the arrays of the given keys stay on one line
 * ({@code "heroIds": ["krista", "lars"]}).
 */
final class JsonText {

    private static final String INDENT = "  ";

    private JsonText() {
    }

    static String write(JsonElement element, Set<String> inlineArrayKeys, boolean trailingNewline) {
        StringBuilder out = new StringBuilder();
        append(out, element, "", inlineArrayKeys, false);
        if (trailingNewline) {
            out.append('\n');
        }
        return out.toString();
    }

    private static void append(StringBuilder out, JsonElement element, String indent, Set<String> inlineKeys,
                               boolean inline) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            String inner = indent + INDENT;
            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!first) {
                    out.append(",\n");
                }
                first = false;
                out.append(inner).append(JsonSupport.GSON.toJson(entry.getKey())).append(": ");
                append(out, entry.getValue(), inner, inlineKeys, inlineKeys.contains(entry.getKey()));
            }
            out.append('\n').append(indent).append('}');
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) {
                out.append("[]");
                return;
            }
            if (inline) {
                out.append('[');
                for (int i = 0; i < array.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    append(out, array.get(i), indent, inlineKeys, true);
                }
                out.append(']');
                return;
            }
            out.append("[\n");
            String inner = indent + INDENT;
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    out.append(",\n");
                }
                out.append(inner);
                append(out, array.get(i), inner, inlineKeys, false);
            }
            out.append('\n').append(indent).append(']');
        } else {
            out.append(JsonSupport.GSON.toJson(element));
        }
    }
}
