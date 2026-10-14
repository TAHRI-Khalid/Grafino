package tk.tahri.grafino.storage;

import tk.tahri.grafino.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and loads Grafcet projects in .tk format (standard JSON).
 */
public class GrafcetStorage {

    public static final String FILE_EXTENSION = ".tk";
    public static final String FILE_DESCRIPTION = "Fichier Grafcet Grafino (*.tk)";

    public static void save(GrafcetModel model, File file) throws IOException {
        String json = toJson(model);
        Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
    }

    public static GrafcetModel load(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return fromJson(content);
    }

    public static String toJson(GrafcetModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(escapeJson(model.getName())).append("\",\n");

        // Variables
        sb.append("  \"variables\": [\n");
        for (int i = 0; i < model.getVariables().size(); i++) {
            VariableModel v = model.getVariables().get(i);
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escapeJson(v.getName())).append("\",\n");
            sb.append("      \"type\": \"").append(v.getType().name()).append("\",\n");
            sb.append("      \"direction\": \"").append(v.getDirection().name()).append("\",\n");
            sb.append("      \"address\": \"").append(escapeJson(v.getAddress())).append("\",\n");
            sb.append("      \"comment\": \"").append(escapeJson(v.getComment())).append("\"\n");
            sb.append("    }").append(i < model.getVariables().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        // Steps
        sb.append("  \"steps\": [\n");
        for (int i = 0; i < model.getSteps().size(); i++) {
            StepModel s = model.getSteps().get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escapeJson(s.getId())).append("\",\n");
            sb.append("      \"number\": ").append(s.getNumber()).append(",\n");
            sb.append("      \"initial\": ").append(s.isInitial()).append(",\n");
            sb.append("      \"x\": ").append(s.getX()).append(",\n");
            sb.append("      \"y\": ").append(s.getY()).append(",\n");
            sb.append("      \"actions\": [\n");
            for (int j = 0; j < s.getActions().size(); j++) {
                GrafcetAction a = s.getActions().get(j);
                sb.append("        {\n");
                sb.append("          \"qualifier\": \"").append(escapeJson(a.getQualifier())).append("\",\n");
                sb.append("          \"variableName\": \"").append(escapeJson(a.getVariableName())).append("\"");
                if (a.getParameter() != null) {
                    sb.append(",\n          \"parameter\": \"").append(escapeJson(a.getParameter())).append("\"\n");
                } else {
                    sb.append("\n");
                }
                sb.append("        }").append(j < s.getActions().size() - 1 ? "," : "").append("\n");
            }
            sb.append("      ]\n");
            sb.append("    }").append(i < model.getSteps().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        // Transitions
        sb.append("  \"transitions\": [\n");
        for (int i = 0; i < model.getTransitions().size(); i++) {
            TransitionModel t = model.getTransitions().get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escapeJson(t.getId())).append("\",\n");
            sb.append("      \"number\": ").append(t.getNumber()).append(",\n");
            sb.append("      \"receptivity\": \"").append(escapeJson(t.getReceptivity())).append("\",\n");
            sb.append("      \"x\": ").append(t.getX()).append(",\n");
            sb.append("      \"y\": ").append(t.getY()).append("\n");
            sb.append("    }").append(i < model.getTransitions().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        // Links
        sb.append("  \"links\": [\n");
        for (int i = 0; i < model.getLinks().size(); i++) {
            LinkModel l = model.getLinks().get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escapeJson(l.getId())).append("\",\n");
            sb.append("      \"sourceId\": \"").append(escapeJson(l.getSourceId())).append("\",\n");
            sb.append("      \"targetId\": \"").append(escapeJson(l.getTargetId())).append("\",\n");
            sb.append("      \"intermediatePoints\": [");
            for (int k = 0; k < l.getIntermediatePoints().size(); k++) {
                double[] p = l.getIntermediatePoints().get(k);
                sb.append("[").append(p[0]).append(",").append(p[1]).append("]");
                if (k < l.getIntermediatePoints().size() - 1) sb.append(", ");
            }
            sb.append("]\n");
            sb.append("    }").append(i < model.getLinks().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static GrafcetModel fromJson(String json) {
        GrafcetModel model = new GrafcetModel();
        JsonParser parser = new JsonParser(json);
        JsonValue root = parser.parse();
        if (!(root instanceof JsonObject obj)) {
            return model;
        }

        if (obj.has("name")) {
            model.setName(obj.getString("name"));
        }

        // Variables
        if (obj.has("variables") && obj.get("variables") instanceof JsonArray arr) {
            for (JsonValue v : arr.values) {
                if (v instanceof JsonObject vo) {
                    String name = vo.getString("name", "");
                    String typeStr = vo.getString("type", "BOOL");
                    String dirStr = vo.getString("direction", "INPUT");
                    String addr = vo.getString("address", "");
                    String comm = vo.getString("comment", "");

                    VariableType vt;
                    try { vt = VariableType.valueOf(typeStr); } catch (Exception e) { vt = VariableType.BOOL; }
                    VariableDirection vd;
                    try { vd = VariableDirection.valueOf(dirStr); } catch (Exception e) { vd = VariableDirection.INPUT; }

                    model.getVariables().add(new VariableModel(name, vt, vd, addr, comm));
                }
            }
        }

        // Steps
        if (obj.has("steps") && obj.get("steps") instanceof JsonArray arr) {
            for (JsonValue v : arr.values) {
                if (v instanceof JsonObject so) {
                    String id = so.getString("id", "S_" + System.currentTimeMillis());
                    int number = so.getInt("number", 1);
                    boolean initial = so.getBoolean("initial", false);
                    double x = so.getDouble("x", 100);
                    double y = so.getDouble("y", 100);
                    StepModel step = new StepModel(id, number, initial, x, y);

                    if (so.has("actions") && so.get("actions") instanceof JsonArray actArr) {
                        for (JsonValue av : actArr.values) {
                            if (av instanceof JsonObject ao) {
                                String q = ao.getString("qualifier", "N");
                                String vn = ao.getString("variableName", "");
                                String param = ao.has("parameter") ? ao.getString("parameter") : null;
                                step.getActions().add(new GrafcetAction(q, vn, param));
                            }
                        }
                    }
                    model.getSteps().add(step);
                }
            }
        }

        // Transitions
        if (obj.has("transitions") && obj.get("transitions") instanceof JsonArray arr) {
            for (JsonValue v : arr.values) {
                if (v instanceof JsonObject to) {
                    String id = to.getString("id", "T_" + System.currentTimeMillis());
                    int number = to.getInt("number", 1);
                    String rec = to.getString("receptivity", "1");
                    double x = to.getDouble("x", 100);
                    double y = to.getDouble("y", 160);
                    model.getTransitions().add(new TransitionModel(id, number, rec, x, y));
                }
            }
        }

        // Links
        if (obj.has("links") && obj.get("links") instanceof JsonArray arr) {
            for (JsonValue v : arr.values) {
                if (v instanceof JsonObject lo) {
                    String id = lo.getString("id", "L_" + System.currentTimeMillis());
                    String src = lo.getString("sourceId", "");
                    String tgt = lo.getString("targetId", "");
                    LinkModel link = new LinkModel(id, src, tgt);

                    if (lo.has("intermediatePoints") && lo.get("intermediatePoints") instanceof JsonArray pts) {
                        for (JsonValue pv : pts.values) {
                            if (pv instanceof JsonArray pt && pt.values.size() >= 2) {
                                double px = ((JsonNumber) pt.values.get(0)).value;
                                double py = ((JsonNumber) pt.values.get(1)).value;
                                link.getIntermediatePoints().add(new double[]{px, py});
                            }
                        }
                    }
                    model.getLinks().add(link);
                }
            }
        }

        return model;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ---------------- Lightweight self-contained JSON AST ----------------

    public static abstract class JsonValue {}

    public static class JsonString extends JsonValue {
        public final String value;
        public JsonString(String v) { this.value = v; }
    }

    public static class JsonNumber extends JsonValue {
        public final double value;
        public JsonNumber(double v) { this.value = v; }
    }

    public static class JsonBoolean extends JsonValue {
        public final boolean value;
        public JsonBoolean(boolean v) { this.value = v; }
    }

    public static class JsonNull extends JsonValue {}

    public static class JsonArray extends JsonValue {
        public final List<JsonValue> values = new ArrayList<>();
    }

    public static class JsonObject extends JsonValue {
        public final List<String> keys = new ArrayList<>();
        public final List<JsonValue> values = new ArrayList<>();

        public boolean has(String key) {
            return keys.contains(key);
        }

        public JsonValue get(String key) {
            int idx = keys.indexOf(key);
            return idx >= 0 ? values.get(idx) : null;
        }

        public String getString(String key, String def) {
            JsonValue v = get(key);
            return (v instanceof JsonString s) ? s.value : def;
        }

        public String getString(String key) {
            return getString(key, "");
        }

        public int getInt(String key, int def) {
            JsonValue v = get(key);
            return (v instanceof JsonNumber n) ? (int) n.value : def;
        }

        public double getDouble(String key, double def) {
            JsonValue v = get(key);
            return (v instanceof JsonNumber n) ? n.value : def;
        }

        public boolean getBoolean(String key, boolean def) {
            JsonValue v = get(key);
            return (v instanceof JsonBoolean b) ? b.value : def;
        }
    }

    public static class JsonParser {
        private final String src;
        private int pos = 0;

        public JsonParser(String src) {
            this.src = src != null ? src : "";
        }

        public JsonValue parse() {
            skipWhitespace();
            if (pos >= src.length()) return new JsonNull();
            return parseValue();
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private JsonValue parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return new JsonNull();
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            throw new IllegalArgumentException("Unexpected char '" + c + "' at pos " + pos);
        }

        private JsonObject parseObject() {
            JsonObject obj = new JsonObject();
            pos++; // '{'
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return obj;
            }
            while (pos < src.length()) {
                skipWhitespace();
                JsonString key = parseString();
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ':') pos++;
                JsonValue val = parseValue();
                obj.keys.add(key.value);
                obj.values.add(val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == '}') {
                    pos++;
                    break;
                }
            }
            return obj;
        }

        private JsonArray parseArray() {
            JsonArray arr = new JsonArray();
            pos++; // '['
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return arr;
            }
            while (pos < src.length()) {
                JsonValue val = parseValue();
                arr.values.add(val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == ']') {
                    pos++;
                    break;
                }
            }
            return arr;
        }

        private JsonString parseString() {
            pos++; // opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return new JsonString(sb.toString());
                } else if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
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
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return new JsonString(sb.toString());
        }

        private JsonBoolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return new JsonBoolean(true);
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return new JsonBoolean(false);
            }
            throw new IllegalArgumentException("Unknown boolean literal at pos " + pos);
        }

        private JsonNull parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return new JsonNull();
            }
            throw new IllegalArgumentException("Unknown literal at pos " + pos);
        }

        private JsonNumber parseNumber() {
            int start = pos;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) pos++;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
            }
            double val = Double.parseDouble(src.substring(start, pos));
            return new JsonNumber(val);
        }
    }
}
