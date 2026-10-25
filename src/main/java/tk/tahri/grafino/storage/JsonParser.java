package tk.tahri.grafino.storage;

import java.util.*;

/**
 * Analyseur syntaxique JSON léger, récursif et autonome en pur Java.
 */
public class JsonParser {

    private final String src;
    private int pos = 0;

    public JsonParser(String src) {
        this.src = (src == null) ? "" : src;
    }

    public static JsonValue parse(String json) {
        return new JsonParser(json).parseValue();
    }

    public JsonValue parseValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            return JsonValue.JsonNull.INSTANCE;
        }

        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> {
                if (c == '-' || Character.isDigit(c)) {
                    yield parseNumber();
                }
                throw new IllegalArgumentException("Caractère JSON inattendu : '" + c + "' à la position " + pos);
            }
        };
    }

    private JsonValue.JsonObject parseObject() {
        expect('{');
        skipWhitespace();
        Map<String, JsonValue> members = new LinkedHashMap<>();

        if (peek() == '}') {
            expect('}');
            return new JsonValue.JsonObject(members);
        }

        while (true) {
            skipWhitespace();
            JsonValue keyVal = parseString();
            String key = ((JsonValue.JsonString) keyVal).value();

            skipWhitespace();
            expect(':');
            skipWhitespace();

            JsonValue val = parseValue();
            members.put(key, val);

            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == '}') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Attendu ',' ou '}' à la position " + pos + ", trouvé '" + next + "'");
            }
        }
        return new JsonValue.JsonObject(members);
    }

    private JsonValue.JsonArray parseArray() {
        expect('[');
        skipWhitespace();
        List<JsonValue> elements = new ArrayList<>();

        if (peek() == ']') {
            expect(']');
            return new JsonValue.JsonArray(elements);
        }

        while (true) {
            elements.add(parseValue());
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == ']') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Attendu ',' ou ']' à la position " + pos + ", trouvé '" + next + "'");
            }
        }
        return new JsonValue.JsonArray(elements);
    }

    private JsonValue.JsonString parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return new JsonValue.JsonString(sb.toString());
            } else if (c == '\\') {
                if (pos >= src.length()) break;
                char escape = src.charAt(pos++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 <= src.length()) {
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                    }
                    default -> sb.append(escape);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Chaîne JSON non fermée.");
    }

    private JsonValue.JsonNumber parseNumber() {
        int start = pos;
        if (src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        double val = Double.parseDouble(src.substring(start, pos));
        return new JsonValue.JsonNumber(val);
    }

    private JsonValue.JsonBoolean parseBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return JsonValue.JsonBoolean.TRUE;
        } else if (src.startsWith("false", pos)) {
            pos += 5;
            return JsonValue.JsonBoolean.FALSE;
        }
        throw new IllegalArgumentException("Valeur booléenne invalide à la position " + pos);
    }

    private JsonValue.JsonNull parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return JsonValue.JsonNull.INSTANCE;
        }
        throw new IllegalArgumentException("Valeur null invalide à la position " + pos);
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        return (pos < src.length()) ? src.charAt(pos) : '\0';
    }

    private void expect(char expected) {
        if (pos >= src.length() || src.charAt(pos) != expected) {
            throw new IllegalArgumentException("Caractère '" + expected + "' attendu à la position " + pos);
        }
        pos++;
    }
}
