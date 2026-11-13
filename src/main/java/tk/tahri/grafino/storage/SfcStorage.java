package tk.tahri.grafino.storage;

import tk.tahri.grafino.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur de persistance pour les programmes SFC (CEI 61131-3) au format JSON standardisé (.sfc et .tk).
 */
public class SfcStorage {

    public static final String FILE_EXTENSION_SFC = ".sfc";
    public static final String FILE_EXTENSION_TK = ".tk";
    public static final String FILE_DESCRIPTION = "Programme SFC CEI 61131-3 (*.sfc, *.tk)";

    public static void save(SfcProgram program, File file) throws IOException {
        save(program, file.toPath());
    }

    public static void save(SfcProgram program, Path path) throws IOException {
        String json = toJson(program);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    public static SfcProgram load(File file) throws IOException {
        return load(file.toPath());
    }

    public static SfcProgram load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return fromJson(json);
    }

    // ---------------- Sérialisation JSON ----------------

    public static String toJson(SfcProgram program) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schemaVersion\": \"1.0-IEC61131-3\",\n");
        sb.append("  \"programName\": \"").append(escape(program.programName())).append("\",\n");

        // 1. Variables IEC
        sb.append("  \"variables\": [\n");
        for (int i = 0; i < program.variables().size(); i++) {
            IecVariable v = program.variables().get(i);
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escape(v.name())).append("\",\n");
            sb.append("      \"type\": \"").append(v.type().name()).append("\",\n");
            sb.append("      \"scope\": \"").append(v.scope().name()).append("\",\n");
            sb.append("      \"hardwareAddress\": \"").append(escape(v.hardwareAddress())).append("\",\n");
            sb.append("      \"initialValue\": \"").append(escape(v.initialValue())).append("\",\n");
            sb.append("      \"description\": \"").append(escape(v.description())).append("\"\n");
            sb.append("    }").append(i < program.variables().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        // 2. Étapes (Steps)
        sb.append("  \"steps\": [\n");
        for (int i = 0; i < program.steps().size(); i++) {
            SfcStep s = program.steps().get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escape(s.id())).append("\",\n");
            sb.append("      \"name\": \"").append(escape(s.name())).append("\",\n");
            sb.append("      \"number\": ").append(s.number()).append(",\n");
            sb.append("      \"isInitial\": ").append(s.isInitial()).append(",\n");
            sb.append("      \"x\": ").append(s.x()).append(",\n");
            sb.append("      \"y\": ").append(s.y()).append(",\n");
            sb.append("      \"actionBlocks\": [\n");
            for (int j = 0; j < s.actionBlocks().size(); j++) {
                SfcActionBlock a = s.actionBlocks().get(j);
                sb.append("        {\n");
                sb.append("          \"qualifier\": \"").append(a.qualifier().name()).append("\",\n");
                sb.append("          \"variableName\": \"").append(escape(a.variableName())).append("\",\n");
                sb.append("          \"durationMs\": ").append(a.duration() != null ? a.duration().toMillis() : "null").append(",\n");
                sb.append("          \"formattedDuration\": ").append(a.duration() != null ? "\"" + a.formattedDuration() + "\"" : "null").append(",\n");
                sb.append("          \"inlineCondition\": ").append(a.inlineCondition() != null ? "\"" + escape(a.inlineCondition()) + "\"" : "null").append("\n");
                sb.append("        }").append(j < s.actionBlocks().size() - 1 ? "," : "").append("\n");
            }
            sb.append("      ]\n");
            sb.append("    }").append(i < program.steps().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        // 3. Transitions
        sb.append("  \"transitions\": [\n");
        for (int i = 0; i < program.transitions().size(); i++) {
            SfcTransition t = program.transitions().get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escape(t.id())).append("\",\n");
            sb.append("      \"name\": \"").append(escape(t.name())).append("\",\n");
            sb.append("      \"number\": ").append(t.number()).append(",\n");
            sb.append("      \"conditionST\": \"").append(escape(t.conditionST())).append("\",\n");
            sb.append("      \"x\": ").append(t.x()).append(",\n");
            sb.append("      \"y\": ").append(t.y()).append("\n");
            sb.append("    }").append(i < program.transitions().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        // 4. Branches (Aiguillages & Synchronisations ET/OU)
        sb.append("  \"branches\": [\n");
        for (int i = 0; i < program.branches().size(); i++) {
            SfcBranch b = program.branches().get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escape(b.id())).append("\",\n");
            sb.append("      \"name\": \"").append(escape(b.name())).append("\",\n");
            sb.append("      \"type\": \"").append(b.type().name()).append("\",\n");
            sb.append("      \"x\": ").append(b.x()).append(",\n");
            sb.append("      \"y\": ").append(b.y()).append(",\n");
            sb.append("      \"upstreamIds\": [");
            for (int u = 0; u < b.upstreamIds().size(); u++) {
                sb.append("\"").append(escape(b.upstreamIds().get(u))).append("\"");
                if (u < b.upstreamIds().size() - 1) sb.append(", ");
            }
            sb.append("],\n");
            sb.append("      \"downstreamIds\": [");
            for (int d = 0; d < b.downstreamIds().size(); d++) {
                sb.append("\"").append(escape(b.downstreamIds().get(d))).append("\"");
                if (d < b.downstreamIds().size() - 1) sb.append(", ");
            }
            sb.append("]\n");
            sb.append("    }").append(i < program.branches().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        // 5. Liaisons orientées (Links)
        sb.append("  \"links\": [\n");
        for (int i = 0; i < program.links().size(); i++) {
            SfcLink l = program.links().get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(escape(l.id())).append("\",\n");
            sb.append("      \"sourceId\": \"").append(escape(l.sourceId())).append("\",\n");
            sb.append("      \"targetId\": \"").append(escape(l.targetId())).append("\",\n");
            sb.append("      \"intermediatePoints\": [");
            for (int p = 0; p < l.intermediatePoints().size(); p++) {
                SfcPoint pt = l.intermediatePoints().get(p);
                sb.append("[").append(pt.x()).append(", ").append(pt.y()).append("]");
                if (p < l.intermediatePoints().size() - 1) sb.append(", ");
            }
            sb.append("]\n");
            sb.append("    }").append(i < program.links().size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ---------------- Désérialisation JSON ----------------

    public static SfcProgram fromJson(String json) {
        JsonValue parsed = JsonParser.parse(json);
        if (!(parsed instanceof JsonValue.JsonObject root)) {
            return SfcProgram.createDefault("Empty_POU");
        }

        String programName = root.getString("programName", root.getString("name", "POU_Main"));

        // 1. Désérialisation des variables
        List<IecVariable> variables = new ArrayList<>();
        root.getArray("variables").ifPresent(arr -> {
            for (JsonValue vVal : arr) {
                if (vVal instanceof JsonValue.JsonObject vObj) {
                    String name = vObj.getString("name", "var");
                    String typeStr = vObj.getString("type", "BOOL");
                    String scopeStr = vObj.getString("scope", vObj.getString("direction", "VAR"));
                    String address = vObj.getString("hardwareAddress", vObj.getString("address", ""));
                    String initVal = vObj.getString("initialValue", "");
                    String desc = vObj.getString("description", vObj.getString("comment", ""));

                    IecDataType type;
                    try {
                        type = IecDataType.valueOf(typeStr.toUpperCase());
                    } catch (Exception ex) {
                        type = IecDataType.BOOL;
                    }

                    IecVariableScope scope = parseScope(scopeStr);
                    variables.add(new IecVariable(name, type, scope, address, initVal, desc));
                }
            }
        });

        // 2. Désérialisation des étapes
        List<SfcStep> steps = new ArrayList<>();
        root.getArray("steps").ifPresent(arr -> {
            for (JsonValue sVal : arr) {
                if (sVal instanceof JsonValue.JsonObject sObj) {
                    String id = sObj.getString("id", "s_" + steps.size());
                    int number = sObj.getInt("number", steps.size());
                    String name = sObj.getString("name", "Step" + number);
                    boolean isInitial = sObj.getBoolean("isInitial", sObj.getBoolean("initial", false));
                    double x = sObj.getDouble("x", 400.0);
                    double y = sObj.getDouble("y", 100.0);

                    List<SfcActionBlock> actionBlocks = new ArrayList<>();
                    // Supporte 'actionBlocks' (SFC IEC) et 'actions' (rétrocompatibilité)
                    var actionsArrOpt = sObj.getArray("actionBlocks").or(() -> sObj.getArray("actions"));
                    actionsArrOpt.ifPresent(aArr -> {
                        for (JsonValue aVal : aArr) {
                            if (aVal instanceof JsonValue.JsonObject aObj) {
                                String qualStr = aObj.getString("qualifier", "N");
                                String varName = aObj.getString("variableName", aObj.getString("variable", ""));
                                String condition = aObj.getString("inlineCondition", null);

                                SfcQualifier qual = parseQualifier(qualStr);

                                Duration duration = null;
                                if (aObj.get("durationMs").isPresent() && aObj.get("durationMs").get() instanceof JsonValue.JsonNumber num) {
                                    duration = Duration.ofMillis((long) num.value());
                                } else if (aObj.getString("formattedDuration", null) != null) {
                                    duration = parseIecDuration(aObj.getString("formattedDuration", ""));
                                } else if (qual.isTimed()) {
                                    duration = Duration.ofSeconds(1); // valeur de secours
                                }

                                if (!varName.isBlank()) {
                                    actionBlocks.add(new SfcActionBlock(qual, varName, duration, condition));
                                }
                            }
                        }
                    });

                    steps.add(new SfcStep(id, name, number, isInitial, x, y, actionBlocks));
                }
            }
        });

        // 3. Désérialisation des transitions
        List<SfcTransition> transitions = new ArrayList<>();
        root.getArray("transitions").ifPresent(arr -> {
            for (JsonValue tVal : arr) {
                if (tVal instanceof JsonValue.JsonObject tObj) {
                    String id = tObj.getString("id", "t_" + transitions.size());
                    int number = tObj.getInt("number", transitions.size() + 1);
                    String name = tObj.getString("name", "T" + number);
                    String conditionST = tObj.getString("conditionST", tObj.getString("receptivity", "TRUE"));
                    double x = tObj.getDouble("x", 400.0);
                    double y = tObj.getDouble("y", 180.0);

                    transitions.add(new SfcTransition(id, name, number, conditionST, x, y));
                }
            }
        });

        // 4. Désérialisation des branches
        List<SfcBranch> branches = new ArrayList<>();
        root.getArray("branches").ifPresent(arr -> {
            for (JsonValue bVal : arr) {
                if (bVal instanceof JsonValue.JsonObject bObj) {
                    String id = bObj.getString("id", "b_" + branches.size());
                    String name = bObj.getString("name", "branch");
                    String typeStr = bObj.getString("type", "DIVERGENCE_OR");
                    double x = bObj.getDouble("x", 400.0);
                    double y = bObj.getDouble("y", 200.0);

                    SfcBranchType type;
                    try {
                        type = SfcBranchType.valueOf(typeStr.toUpperCase());
                    } catch (Exception ex) {
                        type = SfcBranchType.DIVERGENCE_OR;
                    }

                    List<String> up = new ArrayList<>();
                    bObj.getArray("upstreamIds").ifPresent(a -> a.forEach(el -> {
                        if (el instanceof JsonValue.JsonString s) up.add(s.value());
                    }));

                    List<String> down = new ArrayList<>();
                    bObj.getArray("downstreamIds").ifPresent(a -> a.forEach(el -> {
                        if (el instanceof JsonValue.JsonString s) down.add(s.value());
                    }));

                    branches.add(new SfcBranch(id, name, type, x, y, up, down));
                }
            }
        });

        // 5. Désérialisation des liens (Links)
        List<SfcLink> links = new ArrayList<>();
        root.getArray("links").ifPresent(arr -> {
            for (JsonValue lVal : arr) {
                if (lVal instanceof JsonValue.JsonObject lObj) {
                    String id = lObj.getString("id", "l_" + links.size());
                    String sourceId = lObj.getString("sourceId", "");
                    String targetId = lObj.getString("targetId", "");

                    List<SfcPoint> points = new ArrayList<>();
                    lObj.getArray("intermediatePoints").ifPresent(pArr -> {
                        for (JsonValue ptVal : pArr) {
                            if (ptVal instanceof JsonValue.JsonArray ptCoord && ptCoord.size() >= 2) {
                                if (ptCoord.get(0) instanceof JsonValue.JsonNumber nx && ptCoord.get(1) instanceof JsonValue.JsonNumber ny) {
                                    points.add(new SfcPoint(nx.value(), ny.value()));
                                }
                            }
                        }
                    });

                    if (!sourceId.isBlank() && !targetId.isBlank()) {
                        links.add(new SfcLink(id, sourceId, targetId, points));
                    }
                }
            }
        });

        return new SfcProgram(programName, variables, steps, transitions, branches, links);
    }

    // ---------------- Utilitaires d'analyse ----------------

    private static IecVariableScope parseScope(String scopeStr) {
        if (scopeStr == null) return IecVariableScope.VAR;
        String s = scopeStr.toUpperCase().trim();
        return switch (s) {
            case "VAR_INPUT", "INPUT" -> IecVariableScope.VAR_INPUT;
            case "VAR_OUTPUT", "OUTPUT" -> IecVariableScope.VAR_OUTPUT;
            case "VAR_IN_OUT", "IN_OUT" -> IecVariableScope.VAR_IN_OUT;
            default -> IecVariableScope.VAR;
        };
    }

    private static SfcQualifier parseQualifier(String qualStr) {
        if (qualStr == null || qualStr.isBlank()) return SfcQualifier.N;
        try {
            return SfcQualifier.valueOf(qualStr.toUpperCase().trim());
        } catch (Exception ex) {
            return SfcQualifier.N;
        }
    }

    public static Duration parseIecDuration(String iecTime) {
        if (iecTime == null || iecTime.isBlank()) return null;
        String s = iecTime.toUpperCase().trim();
        if (s.startsWith("T#") || s.startsWith("TIME#")) {
            s = s.substring(s.indexOf('#') + 1);
        }

        try {
            if (s.endsWith("MS")) {
                long val = Long.parseLong(s.replace("MS", "").trim());
                return Duration.ofMillis(val);
            } else if (s.endsWith("S")) {
                long val = Long.parseLong(s.replace("S", "").trim());
                return Duration.ofSeconds(val);
            } else if (s.endsWith("M")) {
                long val = Long.parseLong(s.replace("M", "").trim());
                return Duration.ofMinutes(val);
            }
        } catch (Exception ignored) {}

        return Duration.ofSeconds(1);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
