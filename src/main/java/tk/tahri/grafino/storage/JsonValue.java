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

    record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        public JsonObject {
            members = (members == null) ? Map.of() : Map.copyOf(members);
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
