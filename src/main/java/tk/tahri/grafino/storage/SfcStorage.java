package tk.tahri.grafino.storage;

import tk.tahri.grafino.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("schemaVersion", new JsonValue.JsonString("1.0-IEC61131-3"));
        root.put("programName", new JsonValue.JsonString(program.programName() != null ? program.programName() : ""));

        root.put("variables", new JsonValue.JsonArray(program.variables().stream()
                .map(SfcStorage::toJsonVariable)
                .toList()));
        root.put("steps", new JsonValue.JsonArray(program.steps().stream()
                .map(SfcStorage::toJsonStep)
                .toList()));
        root.put("transitions", new JsonValue.JsonArray(program.transitions().stream()
                .map(SfcStorage::toJsonTransition)
                .toList()));
        root.put("branches", new JsonValue.JsonArray(program.branches().stream()
                .map(SfcStorage::toJsonBranch)
                .toList()));
        root.put("links", new JsonValue.JsonArray(program.links().stream()
                .map(SfcStorage::toJsonLink)
                .toList()));

        return new JsonValue.JsonObject(root).toJson();
    }

    private static JsonValue toJsonVariable(IecVariable v) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        map.put("name", new JsonValue.JsonString(nonNull(v.name())));
        map.put("type", new JsonValue.JsonString(v.type().name()));
        map.put("scope", new JsonValue.JsonString(v.scope().name()));
        map.put("hardwareAddress", new JsonValue.JsonString(nonNull(v.hardwareAddress())));
        map.put("initialValue", new JsonValue.JsonString(nonNull(v.initialValue())));
        map.put("description", new JsonValue.JsonString(nonNull(v.description())));
        return new JsonValue.JsonObject(map);
    }

    private static JsonValue toJsonStep(SfcStep s) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        map.put("id", new JsonValue.JsonString(nonNull(s.id())));
        map.put("name", new JsonValue.JsonString(nonNull(s.name())));
        map.put("number", new JsonValue.JsonNumber(s.number()));
        map.put("isInitial", s.isInitial() ? JsonValue.JsonBoolean.TRUE : JsonValue.JsonBoolean.FALSE);
        map.put("x", new JsonValue.JsonNumber(s.x()));
        map.put("y", new JsonValue.JsonNumber(s.y()));
        map.put("actionBlocks", new JsonValue.JsonArray(s.actionBlocks().stream()
                .map(SfcStorage::toJsonActionBlock)
                .toList()));
        return new JsonValue.JsonObject(map);
    }

    private static JsonValue toJsonActionBlock(SfcActionBlock a) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        map.put("qualifier", new JsonValue.JsonString(a.qualifier().name()));
        map.put("variableName", new JsonValue.JsonString(nonNull(a.variableName())));
        map.put("durationMs", a.duration() != null ? new JsonValue.JsonNumber(a.duration().toMillis()) : JsonValue.JsonNull.INSTANCE);
        map.put("formattedDuration", a.duration() != null ? new JsonValue.JsonString(a.formattedDuration()) : JsonValue.JsonNull.INSTANCE);
        map.put("inlineCondition", a.inlineCondition() != null ? new JsonValue.JsonString(a.inlineCondition()) : JsonValue.JsonNull.INSTANCE);
        return new JsonValue.JsonObject(map);
    }

    private static JsonValue toJsonTransition(SfcTransition t) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        map.put("id", new JsonValue.JsonString(nonNull(t.id())));
        map.put("name", new JsonValue.JsonString(nonNull(t.name())));
        map.put("number", new JsonValue.JsonNumber(t.number()));
        map.put("conditionST", new JsonValue.JsonString(nonNull(t.conditionST())));
        map.put("x", new JsonValue.JsonNumber(t.x()));
        map.put("y", new JsonValue.JsonNumber(t.y()));
        return new JsonValue.JsonObject(map);
    }

    private static JsonValue toJsonBranch(SfcBranch b) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        map.put("id", new JsonValue.JsonString(nonNull(b.id())));
        map.put("name", new JsonValue.JsonString(nonNull(b.name())));
        map.put("type", new JsonValue.JsonString(b.type().name()));
        map.put("x", new JsonValue.JsonNumber(b.x()));
        map.put("y", new JsonValue.JsonNumber(b.y()));
        map.put("upstreamIds", new JsonValue.JsonArray(b.upstreamIds().stream()
                .<JsonValue>map(id -> new JsonValue.JsonString(nonNull(id)))
                .toList()));
        map.put("downstreamIds", new JsonValue.JsonArray(b.downstreamIds().stream()
                .<JsonValue>map(id -> new JsonValue.JsonString(nonNull(id)))
                .toList()));
        return new JsonValue.JsonObject(map);
    }

    private static JsonValue toJsonLink(SfcLink l) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        map.put("id", new JsonValue.JsonString(nonNull(l.id())));
        map.put("sourceId", new JsonValue.JsonString(nonNull(l.sourceId())));
        map.put("targetId", new JsonValue.JsonString(nonNull(l.targetId())));
        map.put("intermediatePoints", new JsonValue.JsonArray(l.intermediatePoints().stream()
                .<JsonValue>map(pt -> new JsonValue.JsonArray(List.of(
                        new JsonValue.JsonNumber(pt.x()),
                        new JsonValue.JsonNumber(pt.y()))))
                .toList()));
        return new JsonValue.JsonObject(map);
    }

    private static String nonNull(String s) {
        return (s == null) ? "" : s;
    }

    // ---------------- Désérialisation JSON ----------------

    public static SfcProgram fromJson(String json) {
        JsonValue parsed = JsonParser.parse(json);
        if (!(parsed instanceof JsonValue.JsonObject root)) {
            throw new IllegalArgumentException("Format de projet SFC/Grafcet invalide : objet JSON attendu à la racine.");
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
        if (iecTime == null || iecTime.isBlank()) {
            return null;
        }
        return IecDurationParser.parse(iecTime).orElse(Duration.ofSeconds(1));
    }
}
