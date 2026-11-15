package tk.tahri.grafino.model.validation;

import tk.tahri.grafino.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validateur des réceptivités de transitions SFC (syntaxe ST, temporisations t/Xi/d, capteurs %I et compteurs).
 */
public final class SfcReceptivityValidator {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    private SfcReceptivityValidator() {
    }

    public static void validate(SfcProgram program, List<SfcValidationMessage> messages) {
        List<SfcStep> steps = program.steps();
        List<IecVariable> variables = program.variables();

        for (SfcTransition transition : program.transitions()) {
            validateReceptivitySyntax(transition, messages);

            SfcTimerCondition.findInExpression(transition.conditionST()).ifPresent(timer -> {
                boolean stepExists = steps.stream().anyMatch(s ->
                        s.name().equalsIgnoreCase(timer.stepReference()) ||
                        ("X" + s.number()).equalsIgnoreCase(timer.stepReference()) ||
                        ("S" + s.number()).equalsIgnoreCase(timer.stepReference()) ||
                        String.valueOf(s.number()).equalsIgnoreCase(timer.stepReference())
                );
                if (!stepExists) {
                    messages.add(SfcValidationMessage.warning(
                            transition.id(),
                            "CEI 60848 — Temporisateur dépendant du temps",
                            "La transition " + transition.name() + " référence une étape inexistante dans sa temporisation : '"
                                    + timer.stepReference() + "'.",
                            "Dans la notation normalisée 't/Xi/durée' (ex: t/X1/5s), Xi doit désigner une étape existante du Grafcet "
                                    + "(généralement l'étape amont directe qui lance le décompte temporel)."
                    ));
                }
            });

            for (SfcCounterOperation.Comparison comp : SfcCounterOperation.findComparisons(transition.conditionST())) {
                var variableOpt = program.findVariableByName(comp.counterName());
                if (variableOpt.isEmpty() && !variables.isEmpty()) {
                    messages.add(SfcValidationMessage.warning(
                            transition.id(),
                            "CEI 61131-3 — Déclaration des Compteurs",
                            "La transition " + transition.name() + " compare une variable de comptage non déclarée : '" + comp.counterName() + "'.",
                            "Ouvrez la table des variables au bas de l'écran pour déclarer '"
                                    + comp.counterName() + "' (type INT)."
                    ));
                } else if (variableOpt.isPresent() && variableOpt.get().type() == IecDataType.BOOL) {
                    messages.add(SfcValidationMessage.warning(
                            transition.id(),
                            "CEI 61131-3 — Typage des Variables",
                            "La variable '" + comp.counterName() + "' testée numériquement dans la transition " + transition.name()
                                    + " est déclarée en BOOL au lieu d'un type entier (INT).",
                            "Une variable booléenne (BOOL) ne peut valoir que 0 (FALSE) ou 1 (TRUE). "
                                    + "Changez le type de '" + comp.counterName() + "' en INT dans la table des variables pour compter des cycles."
                    ));
                }
            }

            if (!variables.isEmpty()) {
                validateTransitionSensorVariables(program, transition, messages);
            }
        }
    }

    private static void validateReceptivitySyntax(SfcTransition transition, List<SfcValidationMessage> messages) {
        String raw = transition.conditionST();
        if (raw == null || raw.isBlank()) {
            messages.add(SfcValidationMessage.warning(
                    transition.id(),
                    "CEI 60848 — Réceptivité vide",
                    "La transition " + transition.name() + " possède une réceptivité vide.",
                    "Indiquez une condition booléenne (ex: 'dcy') ou '1' (toujours vraie) pour expliciter la condition de franchissement."
            ));
            return;
        }

        String trimmed = raw.trim();

        int depth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth < 0) break;
            }
        }
        if (depth != 0) {
            messages.add(SfcValidationMessage.error(
                    transition.id(),
                    "CEI 61131-3 — Syntaxe de Réceptivité ST",
                    "Parenthèses non équilibrées dans la réceptivité de " + transition.name() + " : '" + trimmed + "'.",
                    "Vérifiez que chaque parenthèse ouvrante '(' est bien refermée par une parenthèse ')' correspondante."
            ));
            return;
        }

        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("AND ") || upper.startsWith("OR ") || upper.startsWith(".") || upper.startsWith("+") || upper.startsWith("*") || upper.startsWith("&") || upper.startsWith("|")
                || upper.endsWith(" AND") || upper.endsWith(" OR") || upper.endsWith(".") || upper.endsWith("+") || upper.endsWith("*") || upper.endsWith("&") || upper.endsWith("|")
                || upper.equals("AND") || upper.equals("OR") || upper.equals("NOT") || upper.equals("/") || upper.equals("!")) {
            messages.add(SfcValidationMessage.error(
                    transition.id(),
                    "CEI 61131-3 — Syntaxe de Réceptivité ST",
                    "Expression logique incomplète dans la transition " + transition.name() + " : '" + trimmed + "'.",
                    "Un opérateur logique (AND / '.', OR / '+', NOT / '/') ne peut pas rester isolé sans variable capteur associée "
                            + "(ex: écrivez 'dcy AND capteur_haut')."
            ));
        }
    }

    private static void validateTransitionSensorVariables(
            SfcProgram program,
            SfcTransition transition,
            List<SfcValidationMessage> messages
    ) {
        String expr = transition.conditionST();
        if (expr == null || expr.isBlank()) return;

        String cleaned = expr.replaceAll("(?i)(?:[a-zA-Z_][a-zA-Z0-9_]*\\s*/\\s*)?(?:[Xx]\\d+|[a-zA-Z_][a-zA-Z0-9_]*|\\d+)\\s*/\\s*\\d+(?:\\.\\d+)?\\s*(?:ms|s|min|m|h)", " ");
        cleaned = cleaned.replaceAll("(?i)(?:[Xx]\\d+|[a-zA-Z_][a-zA-Z0-9_]*)\\.t\\s*>=\\s*(?:T#)?\\d+(?:\\.\\d+)?\\s*(?:ms|s|min|m|h)", " ");
        cleaned = cleaned.replaceAll("(?i)\\b[a-zA-Z_][a-zA-Z0-9_]*\\s*(?:<=|>=|<>|!=|<|>|=)\\s*-?\\d+\\b", " ");

        Matcher tokenMatcher = TOKEN_PATTERN.matcher(cleaned);
        Set<String> checkedTokens = new HashSet<>();
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group(1);
            String upper = token.toUpperCase(Locale.ROOT);
            if (!checkedTokens.add(upper)) continue;

            if (upper.equals("AND") || upper.equals("OR") || upper.equals("NOT") || upper.equals("TRUE") || upper.equals("FALSE") || upper.equals("XOR")) {
                continue;
            }
            boolean isStepRef = program.steps().stream().anyMatch(s ->
                    s.name().equalsIgnoreCase(token) || ("X" + s.number()).equalsIgnoreCase(token) || ("S" + s.number()).equalsIgnoreCase(token));
            if (isStepRef) {
                continue;
            }

            var declaredOpt = program.findVariableByName(token);
            if (declaredOpt.isEmpty()) {
                messages.add(SfcValidationMessage.warning(
                        transition.id(),
                        "CEI 61131-3 — Déclaration des Capteurs (%I)",
                        "La transition " + transition.name() + " utilise le capteur '" + token
                                + "' qui n'est pas déclaré dans la table des variables.",
                        "Déclarez '" + token + "' comme entrée capteur (bouton '+ Entrée %I', type BOOL) dans la table des variables "
                                + "pour pouvoir le piloter depuis le pupitre de simulation."
                ));
            } else {
                IecVariable v = declaredOpt.get();
                if (v.type() == IecDataType.INT || v.type() == IecDataType.DINT || v.type() == IecDataType.REAL) {
                    messages.add(SfcValidationMessage.warning(
                            transition.id(),
                            "CEI 61131-3 — Typage des Réceptivités",
                            "La variable numérique '" + v.name() + "' (" + v.type() + ") est utilisée directement comme condition booléenne dans "
                                    + transition.name() + ".",
                            "Une réceptivité attend une valeur booléenne (VRAI / FAUX). Pour tester une variable numérique ou un compteur, "
                                    + "ajoutez un opérateur de comparaison (ex: '" + v.name() + " >= 5' ou '" + v.name() + " < 3')."
                    ));
                }
            }
        }
    }
}
