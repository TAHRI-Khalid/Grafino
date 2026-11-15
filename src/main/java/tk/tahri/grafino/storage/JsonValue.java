package tk.tahri.grafino.storage;

import java.util.*;

/**
 * Hiérarchie scellée représentant les types de valeurs JSON (RFC 8259) en Java 25+.
 */
public sealed interface JsonValue permits
        JsonValue.JsonObject,
        JsonValue.JsonArray,
        JsonValue.JsonString,
        JsonValue.JsonNumber,
        JsonValue.JsonBoolean,
        JsonValue.JsonNull {

    default String toJson() {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, 0);
        sb.append('\n');
        return sb.toString();
    }

    private void appendJson(StringBuilder sb, int indent) {
        switch (this) {
            case JsonObject(var members) -> {
                if (members.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append("{\n");
                int i = 0;
                int size = members.size();
                for (var entry : members.entrySet()) {
                    sb.append("  ".repeat(indent + 1))
                            .append('"').append(escapeString(entry.getKey())).append("\": ");
                    entry.getValue().appendJson(sb, indent + 1);
                    if (++i < size) {
                        sb.append(',');
                    }
                    sb.append('\n');
                }
                sb.append("  ".repeat(indent)).append('}');
            }
            case JsonArray(var elements) -> {
                if (elements.isEmpty()) {
                    sb.append("[]");
                    return;
                }
                boolean inlinePrimitives = elements.stream().allMatch(e -> e instanceof JsonNumber || e instanceof JsonString || e instanceof JsonBoolean || e instanceof JsonNull);
                if (inlinePrimitives && elements.size() <= 4 && indent >= 2) {
                    sb.append('[');
                    for (int i = 0; i < elements.size(); i++) {
                        elements.get(i).appendJson(sb, indent);
                        if (i < elements.size() - 1) sb.append(", ");
                    }
                    sb.append(']');
                    return;
                }
                sb.append("[\n");
                for (int i = 0; i < elements.size(); i++) {
                    sb.append("  ".repeat(indent + 1));
                    elements.get(i).appendJson(sb, indent + 1);
                    if (i < elements.size() - 1) {
                        sb.append(',');
                    }
                    sb.append('\n');
                }
                sb.append("  ".repeat(indent)).append(']');
            }
            case JsonString(var value) -> sb.append('"').append(escapeString(value)).append('"');
            case JsonNumber(var value) -> {
                if (!Double.isFinite(value)) {
                    sb.append("null");
                } else if (value == Math.rint(value) && Math.abs(value) <= Long.MAX_VALUE) {
                    sb.append((long) value);
                } else {
                    sb.append(value);
                }
            }
            case JsonBoolean(var value) -> sb.append(value);
            case JsonNull _ -> sb.append("null");
        }
    }

    private static String escapeString(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        public JsonObject {
            members = (members == null) ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        public Optional<JsonValue> get(String key) {
            return Optional.ofNullable(members.get(key));
        }

        public String getString(String key, String defaultValue) {
            return get(key).filter(v -> v instanceof JsonString)
                    .map(v -> ((JsonString) v).value())
                    .orElse(defaultValue);
        }

        public double getDouble(String key, double defaultValue) {
            return get(key).filter(v -> v instanceof JsonNumber)
                    .map(v -> ((JsonNumber) v).value())
                    .orElse(defaultValue);
        }

        public int getInt(String key, int defaultValue) {
            return (int) getDouble(key, defaultValue);
        }

        public boolean getBoolean(String key, boolean defaultValue) {
            return get(key).filter(v -> v instanceof JsonBoolean)
                    .map(v -> ((JsonBoolean) v).value())
                    .orElse(defaultValue);
        }

        public Optional<JsonObject> getObject(String key) {
            return get(key).filter(v -> v instanceof JsonObject).map(v -> (JsonObject) v);
        }

        public Optional<JsonArray> getArray(String key) {
            return get(key).filter(v -> v instanceof JsonArray).map(v -> (JsonArray) v);
        }
    }

    record JsonArray(List<JsonValue> elements) implements JsonValue, Iterable<JsonValue> {
        public JsonArray {
            elements = (elements == null) ? List.of() : List.copyOf(elements);
        }

        public int size() {
            return elements.size();
        }

        public JsonValue get(int index) {
            return elements.get(index);
        }

        @Override
        public Iterator<JsonValue> iterator() {
            return elements.iterator();
        }
    }

    record JsonString(String value) implements JsonValue {
        public JsonString {
            Objects.requireNonNull(value, "Une chaîne JSON ne peut avoir une valeur null.");
        }
    }

    record JsonNumber(double value) implements JsonValue {}

    record JsonBoolean(boolean value) implements JsonValue {
        public static final JsonBoolean TRUE = new JsonBoolean(true);
        public static final JsonBoolean FALSE = new JsonBoolean(false);
    }

    record JsonNull() implements JsonValue {
        public static final JsonNull INSTANCE = new JsonNull();
    }
}
