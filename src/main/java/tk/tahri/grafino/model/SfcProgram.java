package tk.tahri.grafino.model;

import java.util.*;

/**
 * Unité d'Organisation de Programme (POU - Program Organization Unit) en Sequential Function Chart (SFC)
 * selon la norme internationale CEI 61131-3.
 *
 * Immuable et entièrement testable unitairement sans dépendance JavaFX.
 */
public record SfcProgram(
        String programName,
        List<IecVariable> variables,
        List<SfcStep> steps,
        List<SfcTransition> transitions,
        List<SfcBranch> branches,
        List<SfcLink> links
) {
    public SfcProgram {
        if (programName == null || programName.isBlank()) {
            programName = "Main_SFC";
        }
        variables = (variables == null) ? List.of() : List.copyOf(variables);
        steps = (steps == null) ? List.of() : List.copyOf(steps);
        transitions = (transitions == null) ? List.of() : List.copyOf(transitions);
        branches = (branches == null) ? List.of() : List.copyOf(branches);
        links = (links == null) ? List.of() : List.copyOf(links);
    }

    /**
     * Crée un nouveau programme SFC initialisé avec une étape initiale selon la norme.
     */
    public static SfcProgram createDefault(String name) {
        SfcStep initStep = new SfcStep("step_0", "Step0", 0, true, 400.0, 60.0, List.of());
        return new SfcProgram(name, List.of(), List.of(initStep), List.of(), List.of(), List.of());
    }

    // --- Méthodes de recherche et d'accès rapide ---

    public Optional<SfcStep> findStepById(String id) {
        return steps.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public Optional<SfcStep> findStepByNumber(int number) {
        return steps.stream().filter(s -> s.number() == number).findFirst();
    }

    public Optional<SfcTransition> findTransitionById(String id) {
        return transitions.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public Optional<IecVariable> findVariableByName(String name) {
        return variables.stream().filter(v -> v.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<SfcStep> getInitialSteps() {
        return steps.stream().filter(SfcStep::isInitial).toList();
    }

    public List<IecVariable> getVariablesByScope(IecVariableScope scope) {
        return variables.stream().filter(v -> v.scope() == scope).toList();
    }

    // --- Validation de conformité CEI 61131-3 ---

    /**
     * Valide le respect des règles fondamentales de la norme CEI 61131-3 :
     * 1. Présence d'au moins une étape initiale.
     * 2. Unicité des noms et numéros d'étapes.
     * 3. Alternance stricte (une étape est reliée à une transition, et vice-versa).
     */
    public List<SfcValidationMessage> validate() {
        List<SfcValidationMessage> messages = new ArrayList<>();

        // Règle 1 : Étape initiale obligatoire
        if (getInitialSteps().isEmpty()) {
            messages.add(SfcValidationMessage.error("", "Le diagramme SFC doit comporter au moins une étape initiale."));
        }

        // Règle 2 : Unicité des identifiants d'étapes
        Set<String> stepNames = new HashSet<>();
        for (SfcStep step : steps) {
            if (!stepNames.add(step.name().toUpperCase())) {
                messages.add(SfcValidationMessage.error(step.id(), "Nom d'étape dupliqué : " + step.name()));
            }
        }

        // Règle 3 : Alternance stricte Étape - Transition
        Map<String, SfcElement> elementMap = new HashMap<>();
        steps.forEach(s -> elementMap.put(s.id(), s));
        transitions.forEach(t -> elementMap.put(t.id(), t));
        branches.forEach(b -> elementMap.put(b.id(), b));

        for (SfcLink link : links) {
            SfcElement source = elementMap.get(link.sourceId());
            SfcElement target = elementMap.get(link.targetId());

            if (source == null || target == null) {
                messages.add(SfcValidationMessage.warning(link.id(), "Liaison orpheline détectée (élément source ou cible introuvable)."));
                continue;
            }

            if (source instanceof SfcStep && target instanceof SfcStep) {
                messages.add(SfcValidationMessage.error(link.id(), "Violation CEI 61131-3 : deux étapes ne peuvent être reliées directement sans transition intermédiaire."));
            } else if (source instanceof SfcTransition && target instanceof SfcTransition) {
                messages.add(SfcValidationMessage.error(link.id(), "Violation CEI 61131-3 : deux transitions ne peuvent être reliées directement sans étape intermédiaire."));
            }
        }

        // Règle 4 : Validation des temporisations dans les transitions (CEI 60848 / CEI 61131-3)
        for (SfcTransition transition : transitions) {
            SfcTimerCondition.findInExpression(transition.conditionST()).ifPresent(timer -> {
                boolean stepExists = steps.stream().anyMatch(s ->
                        s.name().equalsIgnoreCase(timer.stepReference()) ||
                        ("X" + s.number()).equalsIgnoreCase(timer.stepReference()) ||
                        String.valueOf(s.number()).equalsIgnoreCase(timer.stepReference())
                );
                if (!stepExists) {
                    messages.add(SfcValidationMessage.warning(transition.id(),
                            "La transition " + transition.name() + " référence une étape inexistante dans sa temporisation : '"
                                    + timer.stepReference() + "'."));
                }
            });
        }

        // Règle 5 : Validation des compteurs (Initialisation, Incrémentation/Décrémentation et Tests de réceptivité)
        for (SfcStep step : steps) {
            for (SfcActionBlock action : step.actionBlocks()) {
                SfcCounterOperation.extractAssignedVariable(action.variableName()).ifPresent(varName -> {
                    boolean varExists = variables.stream().anyMatch(v -> v.name().equalsIgnoreCase(varName));
                    if (!varExists && !variables.isEmpty()) {
                        messages.add(SfcValidationMessage.warning(step.id(),
                                "L'action de comptage dans l'étape " + step.name() + " assigne une variable non déclarée : '" + varName + "'."));
                    }
                    // Avertissement pédagogique : qualificateur continu N sur une incrémentation/décrémentation
                    boolean isEvolution = action.variableName().contains("+") || action.variableName().contains("-");
                    if (isEvolution && action.qualifier() == SfcQualifier.N) {
                        messages.add(SfcValidationMessage.warning(step.id(),
                                "Règle CEI 61131-3 : l'évolution de compteur '" + action.variableName()
                                        + "' dans l'étape " + step.name() + " utilise le qualificateur continu 'N'. Le qualificateur 'P' (Pulse) est recommandé."));
                    }
                });
            }
        }

        for (SfcTransition transition : transitions) {
            for (SfcCounterOperation.Comparison comp : SfcCounterOperation.findComparisons(transition.conditionST())) {
                var variableOpt = findVariableByName(comp.counterName());
                if (variableOpt.isEmpty() && !variables.isEmpty()) {
                    messages.add(SfcValidationMessage.warning(transition.id(),
                            "La transition " + transition.name() + " compare une variable de comptage non déclarée : '" + comp.counterName() + "'."));
                } else if (variableOpt.isPresent() && variableOpt.get().type() == IecDataType.BOOL) {
                    messages.add(SfcValidationMessage.warning(transition.id(),
                            "La variable '" + comp.counterName() + "' testée numériquement dans la transition " + transition.name()
                                    + " est déclarée en BOOL au lieu d'un type entier (INT)."));
                }
            }
        }

        return List.copyOf(messages);
    }
}
