package tk.tahri.grafino.model;

import tk.tahri.grafino.model.validation.SfcActionValidator;
import tk.tahri.grafino.model.validation.SfcReceptivityValidator;
import tk.tahri.grafino.model.validation.SfcTopologyValidator;

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

    // --- Validation de conformité CEI 60848 & CEI 61131-3 ---

    /**
     * Valide le respect des règles fondamentales des normes CEI 60848 (Grafcet) et CEI 61131-3 (SFC)
     * en déléguant aux validateurs spécialisés (topologie, réceptivités, actions).
     */
    public List<SfcValidationMessage> validate() {
        List<SfcValidationMessage> messages = new ArrayList<>();
        SfcTopologyValidator.validate(this, messages);
        SfcReceptivityValidator.validate(this, messages);
        SfcActionValidator.validate(this, messages);
        return List.copyOf(messages);
    }
}
