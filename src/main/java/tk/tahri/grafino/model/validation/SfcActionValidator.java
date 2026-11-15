package tk.tahri.grafino.model.validation;

import tk.tahri.grafino.model.*;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validateur des actions d'étapes SFC (portée %I/%Q, symétrie Set/Reset et compteurs).
 */
public final class SfcActionValidator {

    private SfcActionValidator() {
    }

    public static void validate(SfcProgram program, List<SfcValidationMessage> messages) {
        List<SfcStep> steps = program.steps();
        List<IecVariable> variables = program.variables();

        Set<String> setVariables = new HashSet<>();
        Set<String> resetVariables = new HashSet<>();
        Set<String> initializedCounters = new HashSet<>();

        for (SfcStep step : steps) {
            for (SfcActionBlock action : step.actionBlocks()) {
                SfcCounterOperation.parse(action.variableName()).ifPresent(op -> {
                    if (op.type() == SfcCounterOperation.OperationType.INITIALIZATION) {
                        initializedCounters.add(op.counterName().toUpperCase(Locale.ROOT));
                    }
                });
                if (action.qualifier() == SfcQualifier.S) {
                    setVariables.add(action.variableName().trim().toUpperCase(Locale.ROOT));
                } else if (action.qualifier() == SfcQualifier.R) {
                    resetVariables.add(action.variableName().trim().toUpperCase(Locale.ROOT));
                }
            }
        }

        for (SfcStep step : steps) {
            for (SfcActionBlock action : step.actionBlocks()) {
                var assignedCounterOpt = SfcCounterOperation.extractAssignedVariable(action.variableName());
                if (assignedCounterOpt.isPresent()) {
                    String varName = assignedCounterOpt.get();
                    boolean varExists = variables.stream().anyMatch(v -> v.name().equalsIgnoreCase(varName));
                    if (!varExists && !variables.isEmpty()) {
                        messages.add(SfcValidationMessage.warning(
                                step.id(),
                                "CEI 61131-3 — Déclaration des Compteurs",
                                "L'action de comptage dans l'étape " + step.name() + " assigne une variable non déclarée : '" + varName + "'.",
                                "Déclarez le compteur '" + varName + "' (type INT, portée VAR %M) dans la table des variables."
                        ));
                    }
                    boolean isEvolution = action.variableName().contains("+") || action.variableName().contains("-");
                    if (isEvolution && action.qualifier() == SfcQualifier.N) {
                        messages.add(SfcValidationMessage.warning(
                                step.id(),
                                "CEI 61131-3 — Qualificateur impulsionnel P",
                                "Règle CEI 61131-3 : l'évolution de compteur '" + action.variableName()
                                        + "' dans l'étape " + step.name() + " utilise le qualificateur continu 'N'. Le qualificateur 'P' (Pulse) est recommandé.",
                                "Avec le qualificateur continu 'N', le compteur s'incrémenterait à chaque cycle d'horloge de l'automate "
                                        + "tant que l'étape reste active ! Utilisez le qualificateur 'P' (front montant) pour ne compter qu'une seule fois par passage dans l'étape."
                        ));
                    }
                    if (isEvolution && steps.size() > 1 && !initializedCounters.contains(varName.toUpperCase(Locale.ROOT))) {
                        messages.add(SfcValidationMessage.warning(
                                step.id(),
                                "CEI 61131-3 — Initialisation de Compteur",
                                "Le compteur '" + varName + "' est modifié dans l'étape " + step.name()
                                        + " mais n'est initialisé (ex: " + varName + " := 0) dans aucune étape.",
                                "Sans initialisation ('" + varName + " := 0' dans l'étape initiale ou en amont de la boucle), "
                                        + "le compteur conservera sa valeur précédente à la fin du cycle."
                        ));
                    }
                } else {
                    String rawVarName = action.variableName().trim();
                    var declaredVarOpt = program.findVariableByName(rawVarName);

                    if (declaredVarOpt.isPresent()) {
                        IecVariable declaredVar = declaredVarOpt.get();
                        if (declaredVar.scope() == IecVariableScope.VAR_INPUT) {
                            messages.add(SfcValidationMessage.error(
                                    step.id(),
                                    "CEI 61131-3 — Conflit de portée Entrée / Sortie",
                                    "L'étape " + step.name() + " tente de commander l'entrée capteur '" + declaredVar.name()
                                            + "' (" + declaredVar.hardwareAddress() + ") comme une sortie.",
                                    "Une entrée (%I) est un signal en lecture seule provenant d'un capteur (bouton, détecteur). "
                                            + "Une action d'étape ne peut piloter qu'une sortie préactionneur (%Q) ou une mémoire interne (%M)."
                            ));
                        }
                    } else if (!variables.isEmpty()) {
                        messages.add(SfcValidationMessage.warning(
                                step.id(),
                                "CEI 61131-3 — Déclaration des Sorties (%Q)",
                                "L'action '" + rawVarName + "' de l'étape " + step.name()
                                        + " référence une sortie non déclarée dans la table des variables.",
                                "Ajoutez '" + rawVarName + "' dans la table des variables (bouton '+ Sortie %Q') pour l'associer "
                                        + "à une adresse matérielle automate (ex: %Q0.0)."
                        ));
                    }

                    String upperVar = rawVarName.toUpperCase(Locale.ROOT);
                    if (steps.size() > 1) {
                        if (action.qualifier() == SfcQualifier.S && !resetVariables.contains(upperVar)) {
                            messages.add(SfcValidationMessage.warning(
                                    step.id(),
                                    "CEI 61131-3 — Symétrie Set (S) / Reset (R)",
                                    "La sortie '" + rawVarName + "' est mémorisée à 1 (qualificateur S) dans l'étape " + step.name()
                                            + ", mais aucune étape ne la remet à 0 (qualificateur R).",
                                    "Une action mémorisée par 'S' (Set) reste active après la désactivation de l'étape. "
                                            + "Ajoutez une action 'R' (Reset) sur '" + rawVarName + "' dans l'étape où le préactionneur doit s'arrêter, "
                                            + "ou utilisez le qualificateur continu 'N'."
                            ));
                        } else if (action.qualifier() == SfcQualifier.R && !setVariables.contains(upperVar)) {
                            messages.add(SfcValidationMessage.warning(
                                    step.id(),
                                    "CEI 61131-3 — Symétrie Set (S) / Reset (R)",
                                    "L'étape " + step.name() + " remet à 0 (qualificateur R) la sortie '" + rawVarName
                                            + "' qui n'est jamais mémorisée par un qualificateur S (Set).",
                                    "Le qualificateur 'R' (Reset) est conçu pour éteindre une sortie activée par 'S' (Set). "
                                            + "Si la sortie est pilotée en mode continu ('N'), elle s'éteint déjà automatiquement dès que son étape se désactive."
                            ));
                        }
                    }
                }
            }
        }
    }
}
