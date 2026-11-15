package tk.tahri.grafino.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modélisation et utilitaires pour la gestion des compteurs industriels (CEI 60848 & CEI 61131-3 ST).
 *
 * Prise en charge des 3 volets fondamentaux :
 * 1. Initialisation dans une étape : "C1 := 0"
 * 2. Évolution (Incrémentation / Décrémentation) : "C1 := C1 + 1", "C1 := C1 - 1"
 * 3. Réceptivités de transition testant la valeur : "C1 < 5", "C1 >= 5", "C1 = 10"
 */
public record SfcCounterOperation(
        String counterName,
        OperationType type,
        int operand
) {
    public enum OperationType {
        INITIALIZATION,
        INCREMENT,
        DECREMENT
    }

    public record Comparison(String counterName, String operator, int threshold) {
        public String toStructuredText() {
            return counterName + " " + operator + " " + threshold;
        }
    }

    // Regex pour les assignations de type Structured Text : C1 := ...
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
            "^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:=\\s*(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    // Regex pour les tests de comparaison dans les réceptivités : C1 < 5, C1 >= 10, C = 0...
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(<=|>=|<>|<|>|=)\\s*(-?\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern INCREMENT_PATTERN = Pattern.compile(
            "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\+\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DECREMENT_PATTERN = Pattern.compile(
            "^([a-zA-Z_][a-zA-Z0-9_]*)\\s*-\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE
    );

    public SfcCounterOperation {
        Objects.requireNonNull(counterName, "Le nom du compteur est obligatoire.");
        Objects.requireNonNull(type, "Le type d'opération est obligatoire.");
        counterName = normalizeCounterName(counterName);
    }

    public static String normalizeCounterName(String name) {
        if (name == null || name.isBlank()) {
            return "C1";
        }
        return name.trim();
    }

    /**
     * Génère la chaîne d'action Structured Text d'initialisation (ex: "C1 := 0").
     */
    public static String formatInit(String counterName, int initialValue) {
        return normalizeCounterName(counterName) + " := " + initialValue;
    }

    /**
     * Génère la chaîne d'action Structured Text d'incrémentation (ex: "C1 := C1 + 1").
     */
    public static String formatIncrement(String counterName, int step) {
        String c = normalizeCounterName(counterName);
        int s = Math.max(1, step);
        return c + " := " + c + " + " + s;
    }

    /**
     * Génère la chaîne d'action Structured Text de décrémentation (ex: "C1 := C1 - 1").
     */
    public static String formatDecrement(String counterName, int step) {
        String c = normalizeCounterName(counterName);
        int s = Math.max(1, step);
        return c + " := " + c + " - " + s;
    }

    /**
     * Génère l'expression booléenne Structured Text de test de réceptivité (ex: "C1 < 5", "C1 >= 10").
     */
    public static String formatComparison(String counterName, String operator, int threshold) {
        String c = normalizeCounterName(counterName);
        String op = (operator == null || operator.isBlank()) ? "=" : operator.trim();
        return c + " " + op + " " + threshold;
    }

    /**
     * Retourne la représentation Structured Text canonique de cette opération.
     */
    public String toStructuredText() {
        return switch (type) {
            case INITIALIZATION -> formatInit(counterName, operand);
            case INCREMENT -> formatIncrement(counterName, operand);
            case DECREMENT -> formatDecrement(counterName, operand);
        };
    }

    /**
     * Parse une instruction d'assignation de compteur (ex: "C1 := 0", "C1 := C1 + 1", "C1 := C1 - 1").
     */
    public static Optional<SfcCounterOperation> parse(String actionText) {
        if (actionText == null || actionText.isBlank()) return Optional.empty();
        Matcher m = ASSIGNMENT_PATTERN.matcher(actionText.trim());
        if (!m.matches()) return Optional.empty();

        String varName = m.group(1).trim();
        String expr = m.group(2).trim();

        // 1. Incrément : C1 + k ou k + C1
        Matcher incMatcher = INCREMENT_PATTERN.matcher(expr);
        if (incMatcher.matches() && incMatcher.group(1).equalsIgnoreCase(varName)) {
            int step = Integer.parseInt(incMatcher.group(2));
            return Optional.of(new SfcCounterOperation(varName, OperationType.INCREMENT, step));
        }

        // 2. Décrément : C1 - k
        Matcher decMatcher = DECREMENT_PATTERN.matcher(expr);
        if (decMatcher.matches() && decMatcher.group(1).equalsIgnoreCase(varName)) {
            int step = Integer.parseInt(decMatcher.group(2));
            return Optional.of(new SfcCounterOperation(varName, OperationType.DECREMENT, step));
        }

        // 3. Initialisation / valeur fixe : entier direct
        try {
            int val = Integer.parseInt(expr);
            return Optional.of(new SfcCounterOperation(varName, OperationType.INITIALIZATION, val));
        } catch (NumberFormatException ignored) {}

        return Optional.empty();
    }

    /**
     * Détecte si une chaîne d'action est une assignation Structured Text (ex: "C1 := 0" ou "C1 := C1 + 1").
     */
    public static boolean isAssignment(String actionText) {
        if (actionText == null || actionText.isBlank()) return false;
        return ASSIGNMENT_PATTERN.matcher(actionText.trim()).matches();
    }

    /**
     * Extrait le nom de la variable assignée si l'action est une assignation.
     */
    public static Optional<String> extractAssignedVariable(String actionText) {
        if (actionText == null) return Optional.empty();
        Matcher m = ASSIGNMENT_PATTERN.matcher(actionText.trim());
        if (m.matches()) {
            return Optional.of(m.group(1));
        }
        return Optional.empty();
    }

    /**
     * Recherche toutes les comparaisons de variables présentes dans une expression de réceptivité.
     */
    public static List<Comparison> findComparisons(String expression) {
        List<Comparison> results = new ArrayList<>();
        if (expression == null || expression.isBlank()) return results;

        Matcher m = COMPARISON_PATTERN.matcher(expression);
        while (m.find()) {
            String varName = m.group(1);
            String operator = m.group(2);
            int val = Integer.parseInt(m.group(3));
            results.add(new Comparison(varName, operator, val));
        }
        return results;
    }
}
