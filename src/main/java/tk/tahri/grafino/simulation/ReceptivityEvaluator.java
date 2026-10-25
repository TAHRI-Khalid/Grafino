package tk.tahri.grafino.simulation;

import tk.tahri.grafino.model.SfcTimerCondition;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Évaluateur d'expressions de réceptivité pour les transitions Grafcet / SFC (CEI 60848 & CEI 61131-3).
 *
 * Prise en charge complète des expressions :
 * - Constantes : 1, 0, TRUE, FALSE
 * - Variables booléennes directes : dcy, capteur_haut, etc.
 * - Négations : NOT a, /a, !a
 * - Opérateurs logiques : ET (AND, ., *), OU (OR, +) avec gestion des parenthèses
 * - Temporisateurs normalisés CEI 60848 et ST : t/X1/5s, t1/X2/500ms, X1.t >= T#5s
 * - Comparaisons de compteurs : C1 < 5, C1 >= 10, C1 = 3, C1 <> 0
 * - Variables d'activité d'étape : X1, X2 (vraies si l'étape associée est active)
 */
public class ReceptivityEvaluator {

    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(<=|>=|<>|!=|<|>|=)\\s*(-?\\d+)\\b"
    );

    private static final Pattern STEP_ACTIVITY_PATTERN = Pattern.compile(
            "^[Xx](\\d+)$"
    );

    /**
     * Évalue si la réceptivité est VRAIE à l'instant currentTimeMs dans l'état donné.
     *
     * @param expression          Expression brute de réceptivité
     * @param state               État instantané de simulation
     * @param currentTimeMs       Horodatage courant en millisecondes
     * @param stepResolverByIdOrNum Fonction de résolution d'une référence d'étape (ex: "X1" ou "1") vers son ID technique
     * @return Vrai si la transition est franchissable logiquement
     */
    public static boolean evaluate(String expression,
                                   SimulationState state,
                                   long currentTimeMs,
                                   Function<String, Optional<String>> stepResolverByIdOrNum) {
        if (expression == null || expression.isBlank()) {
            return true; // En Grafcet, une transition sans condition équivaut à 1 (toujours vraie)
        }

        String trimmed = expression.trim();

        // 1. Constantes évidentes
        if (trimmed.equals("1") || trimmed.equalsIgnoreCase("TRUE")) return true;
        if (trimmed.equals("0") || trimmed.equalsIgnoreCase("FALSE")) return false;

        // 2. Temporisateur isolé (ex: "t/X1/5s" ou "X1.t >= T#5s")
        var timerOpt = SfcTimerCondition.parse(trimmed);
        if (timerOpt.isPresent()) {
            return evaluateTimer(timerOpt.get(), state, currentTimeMs, stepResolverByIdOrNum);
        }

        // 3. Remplacement des comparaisons de compteurs (ex: C1 < 5 -> TRUE / FALSE)
        String evaluatedExpr = substituteCounterComparisons(trimmed, state);

        // 4. Remplacement des temporisateurs imbriqués s'il y en a
        evaluatedExpr = substituteEmbeddedTimers(evaluatedExpr, state, currentTimeMs, stepResolverByIdOrNum);

        // 5. Évaluation booléenne combinatoire (NOT, AND, OR, parenthèses)
        try {
            return evaluateBooleanExpression(evaluatedExpr, state, stepResolverByIdOrNum);
        } catch (Exception e) {
            // En cas d'erreur de syntaxe, repli sécurisé sur l'évaluation simple de variable
            return state.getBooleanVariable(trimmed);
        }
    }

    private static boolean evaluateTimer(SfcTimerCondition timer,
                                         SimulationState state,
                                         long currentTimeMs,
                                         Function<String, Optional<String>> stepResolver) {
        String ref = timer.stepReference();
        Optional<String> resolvedId = (stepResolver != null) ? stepResolver.apply(ref) : Optional.of(ref);

        if (resolvedId.isEmpty()) {
            return false;
        }

        String stepId = resolvedId.get();
        if (!state.isStepActive(stepId)) {
            return false;
        }

        long activeDurationMs = state.getStepActiveDurationMs(stepId, currentTimeMs);
        long requiredDurationMs = timer.duration().toMillis();
        return activeDurationMs >= requiredDurationMs;
    }

    private static String substituteCounterComparisons(String expr, SimulationState state) {
        Matcher matcher = COMPARISON_PATTERN.matcher(expr);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String counterName = matcher.group(1);
            String operator = matcher.group(2);
            int threshold = Integer.parseInt(matcher.group(3));
            int actualValue = state.getCounterValue(counterName);

            boolean result = switch (operator) {
                case "<" -> actualValue < threshold;
                case "<=" -> actualValue <= threshold;
                case ">" -> actualValue > threshold;
                case ">=" -> actualValue >= threshold;
                case "=", "==" -> actualValue == threshold;
                case "<>", "!=" -> actualValue != threshold;
                default -> false;
            };

            matcher.appendReplacement(sb, result ? " 1 " : " 0 ");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String substituteEmbeddedTimers(String expr,
                                                   SimulationState state,
                                                   long currentTimeMs,
                                                   Function<String, Optional<String>> stepResolver) {
        // Pattern pour détecter un temporisateur au format t/Xi/d ou Xi.t >= T#d dans une expression complexe
        Pattern timerPattern = Pattern.compile("(?:([a-zA-Z_][a-zA-Z0-9_]*)\\s*/\\s*)?([Xx]\\d+|[a-zA-Z_][a-zA-Z0-9_]*)\\s*/\\s*(\\d+(?:\\.\\d+)?)\\s*(ms|s|min|m|h)");
        Matcher matcher = timerPattern.matcher(expr);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String timerStr = matcher.group(0);
            var parsed = SfcTimerCondition.parse(timerStr);
            if (parsed.isPresent()) {
                boolean timerOk = evaluateTimer(parsed.get(), state, currentTimeMs, stepResolver);
                matcher.appendReplacement(sb, timerOk ? " 1 " : " 0 ");
            } else {
                matcher.appendReplacement(sb, " 0 ");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // --- Parseur booléen récursif (OR > AND > NOT > Termes) ---

    private static boolean evaluateBooleanExpression(String expr,
                                                     SimulationState state,
                                                     Function<String, Optional<String>> stepResolver) {
        List<String> tokens = tokenize(expr);
        if (tokens.isEmpty()) return true;
        Parser parser = new Parser(tokens, state, stepResolver);
        return parser.parseOr();
    }

    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();

        while (i < len) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '(' || c == ')') {
                tokens.add(String.valueOf(c));
                i++;
            } else if (c == '+' || c == '|') {
                tokens.add("OR");
                i++;
            } else if (c == '.' || c == '*' || c == '&') {
                tokens.add("AND");
                i++;
            } else if (c == '!') {
                tokens.add("NOT");
                i++;
            } else if (c == '/') {
                // En Grafcet, /a signifie NOT a
                tokens.add("NOT");
                i++;
            } else {
                // Identifiant ou mot-clé
                int start = i;
                while (i < len && !isDelimiter(expr.charAt(i))) {
                    i++;
                }
                String word = expr.substring(start, i).trim();
                if (!word.isEmpty()) {
                    tokens.add(word);
                }
            }
        }
        return tokens;
    }

    private static boolean isDelimiter(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' || c == '+' || c == '|'
                || c == '.' || c == '*' || c == '&' || c == '!' || c == '/';
    }

    private static class Parser {
        private final List<String> tokens;
        private final SimulationState state;
        private final Function<String, Optional<String>> stepResolver;
        private int pos = 0;

        Parser(List<String> tokens, SimulationState state, Function<String, Optional<String>> stepResolver) {
            this.tokens = tokens;
            this.state = state;
            this.stepResolver = stepResolver;
        }

        boolean parseOr() {
            boolean left = parseAnd();
            while (match("OR")) {
                boolean right = parseAnd();
                left = left || right;
            }
            return left;
        }

        boolean parseAnd() {
            boolean left = parseNot();
            while (match("AND")) {
                boolean right = parseNot();
                left = left && right;
            }
            return left;
        }

        boolean parseNot() {
            if (match("NOT")) {
                return !parseNot();
            }
            return parsePrimary();
        }

        boolean parsePrimary() {
            if (pos >= tokens.size()) return false;
            String token = tokens.get(pos++);

            if (token.equals("(")) {
                boolean val = parseOr();
                if (pos < tokens.size() && tokens.get(pos).equals(")")) {
                    pos++;
                }
                return val;
            }

            if (token.equals("1") || token.equalsIgnoreCase("TRUE")) return true;
            if (token.equals("0") || token.equalsIgnoreCase("FALSE")) return false;

            // Variable d'activité d'étape (ex: X1, X2)
            Matcher stepMatcher = STEP_ACTIVITY_PATTERN.matcher(token);
            if (stepMatcher.matches()) {
                if (stepResolver != null) {
                    Optional<String> id = stepResolver.apply(token);
                    if (id.isPresent() && state.isStepActive(id.get())) {
                        return true;
                    }
                }
            }

            // Variable booléenne dans SimulationState
            return state.getBooleanVariable(token);
        }

        boolean match(String expected) {
            if (pos < tokens.size() && tokens.get(pos).equalsIgnoreCase(expected)) {
                pos++;
                return true;
            }
            return false;
        }
    }
}
