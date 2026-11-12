package tk.tahri.grafino.simulation;

import tk.tahri.grafino.model.*;

import java.util.*;

/**
 * Moteur de simulation synchrone du Grafcet / SFC conforme à la norme internationale CEI 60848.
 *
 * Applique rigoureusement les 5 règles fondamentales d'évolution :
 * 1. Situation initiale à l'initialisation.
 * 2. Franchissabilité : transition validée (toutes étapes amont actives) ET réceptivité vraie.
 * 3. Franchissement simultané de toutes les transitions franchissables.
 * 4. Évolution simultanée du marquage : désactivation des étapes amont et activation des étapes aval.
 * 5. Priorité à l'activation en cas de conflit (activation + désactivation simultanée).
 *
 * Entièrement découplé de JavaFX et testable de façon autonome en headless.
 */
public class GrafcetSimulator {

    public record StepNode(
            String id,
            String name,
            int number,
            boolean isInitial,
            List<SfcActionBlock> actionBlocks
    ) {}

    public record TransitionNode(
            String id,
            String name,
            int number,
            String conditionST,
            Set<String> upstreamStepIds,
            Set<String> downstreamStepIds
    ) {}

    private final Map<String, StepNode> steps = new LinkedHashMap<>();
    private final Map<String, TransitionNode> transitions = new LinkedHashMap<>();
    private final Set<String> knownOutputVariables = new LinkedHashSet<>();
    private final Set<String> knownInputVariables = new LinkedHashSet<>();
    private final SimulationState state = new SimulationState();
    private final List<SfcValidationMessage> recordedRuntimeAlerts = new ArrayList<>();

    public GrafcetSimulator() {
    }

    public GrafcetSimulator(SfcProgram program) {
        loadProgram(program);
    }

    /**
     * Charge un programme SFC complet dans le simulateur.
     */
    public void loadProgram(SfcProgram program) {
        steps.clear();
        transitions.clear();
        knownOutputVariables.clear();
        knownInputVariables.clear();

        if (program == null) return;

        // 1. Enregistrement des variables IEC
        for (IecVariable v : program.variables()) {
            if (v.scope() == IecVariableScope.VAR_OUTPUT) {
                knownOutputVariables.add(SimulationState.normalizeKey(v.name()));
            } else if (v.scope() == IecVariableScope.VAR_INPUT) {
                knownInputVariables.add(SimulationState.normalizeKey(v.name()));
            }
        }

        // 2. Enregistrement des étapes
        for (SfcStep s : program.steps()) {
            steps.put(s.id(), new StepNode(s.id(), s.name(), s.number(), s.isInitial(), s.actionBlocks()));
            for (SfcActionBlock a : s.actionBlocks()) {
                if (SfcCounterOperation.parse(a.variableName()).isEmpty()) {
                    knownOutputVariables.add(SimulationState.normalizeKey(a.variableName()));
                }
            }
        }

        // 3. Résolution des liens et des transitions
        Map<String, Set<String>> upstreamOfTransition = new HashMap<>();
        Map<String, Set<String>> downstreamOfTransition = new HashMap<>();

        for (SfcTransition t : program.transitions()) {
            upstreamOfTransition.put(t.id(), new LinkedHashSet<>());
            downstreamOfTransition.put(t.id(), new LinkedHashSet<>());
        }

        // Index des branches
        Map<String, SfcBranch> branchMap = new HashMap<>();
        for (SfcBranch b : program.branches()) {
            branchMap.put(b.id(), b);
        }

        // Analyse des liaisons directes
        for (SfcLink link : program.links()) {
            String src = link.sourceId();
            String tgt = link.targetId();

            if (steps.containsKey(src) && upstreamOfTransition.containsKey(tgt)) {
                // Étape -> Transition
                upstreamOfTransition.get(tgt).add(src);
            } else if (downstreamOfTransition.containsKey(src) && steps.containsKey(tgt)) {
                // Transition -> Étape
                downstreamOfTransition.get(src).add(tgt);
            } else if (steps.containsKey(src) && branchMap.containsKey(tgt)) {
                // Étape -> Branche
                SfcBranch branch = branchMap.get(tgt);
                for (String downId : branch.downstreamIds()) {
                    if (upstreamOfTransition.containsKey(downId)) {
                        upstreamOfTransition.get(downId).add(src);
                    }
                }
            } else if (branchMap.containsKey(src) && upstreamOfTransition.containsKey(tgt)) {
                // Branche -> Transition
                SfcBranch branch = branchMap.get(src);
                for (String upId : branch.upstreamIds()) {
                    if (steps.containsKey(upId)) {
                        upstreamOfTransition.get(tgt).add(upId);
                    }
                }
            } else if (downstreamOfTransition.containsKey(src) && branchMap.containsKey(tgt)) {
                // Transition -> Branche
                SfcBranch branch = branchMap.get(tgt);
                for (String downId : branch.downstreamIds()) {
                    if (steps.containsKey(downId)) {
                        downstreamOfTransition.get(src).add(downId);
                    }
                }
            } else if (branchMap.containsKey(src) && steps.containsKey(tgt)) {
                // Branche -> Étape
                SfcBranch branch = branchMap.get(src);
                for (String upId : branch.upstreamIds()) {
                    if (downstreamOfTransition.containsKey(upId)) {
                        downstreamOfTransition.get(upId).add(tgt);
                    }
                }
            }
        }

        // Enregistrement final des transitions
        for (SfcTransition t : program.transitions()) {
            transitions.put(t.id(), new TransitionNode(
                    t.id(),
                    t.name(),
                    t.number(),
                    t.conditionST(),
                    upstreamOfTransition.getOrDefault(t.id(), Set.of()),
                    downstreamOfTransition.getOrDefault(t.id(), Set.of())
            ));
        }

        reset();
    }

    /**
     * Méthode d'enregistrement manuelle de graphe (pour liaison directe UI ou tests personnalisés).
     */
    public void registerStep(String id, String name, int number, boolean isInitial, List<SfcActionBlock> actions) {
        steps.put(id, new StepNode(id, name, number, isInitial, (actions == null) ? List.of() : actions));
        if (actions != null) {
            for (SfcActionBlock a : actions) {
                if (SfcCounterOperation.parse(a.variableName()).isEmpty()) {
                    knownOutputVariables.add(SimulationState.normalizeKey(a.variableName()));
                }
            }
        }
    }

    public void registerInputVariable(String name) {
        if (name != null && !name.isBlank()) {
            knownInputVariables.add(SimulationState.normalizeKey(name));
        }
    }

    public void registerOutputVariable(String name) {
        if (name != null && !name.isBlank()) {
            knownOutputVariables.add(SimulationState.normalizeKey(name));
        }
    }

    public void registerTransition(String id, String name, int number, String conditionST,
                                   Set<String> upstreamStepIds, Set<String> downstreamStepIds) {
        transitions.put(id, new TransitionNode(
                id,
                name,
                number,
                conditionST,
                (upstreamStepIds == null) ? Set.of() : new LinkedHashSet<>(upstreamStepIds),
                (downstreamStepIds == null) ? Set.of() : new LinkedHashSet<>(downstreamStepIds)
        ));
    }

    /**
     * Réinitialise le Grafcet à sa situation initiale selon la règle 1 de la CEI 60848.
     */
    public void reset() {
        long now = System.currentTimeMillis();
        state.reset();
        recordedRuntimeAlerts.clear();

        for (StepNode s : steps.values()) {
            if (s.isInitial()) {
                state.setStepActive(s.id(), true, now);
            }
        }

        // Traiter les actions d'initialisation des étapes initiales (ex: C1 := 0, S)
        for (StepNode s : steps.values()) {
            if (s.isInitial()) {
                applyStepActivationActions(s);
            }
        }

        updateOutputs(now);
    }

    /**
     * Modifie l'état d'une variable d'entrée capteur (%I).
     */
    public void setInput(String name, boolean value) {
        state.setBooleanVariable(name, value);
    }

    public boolean getInput(String name) {
        return state.getBooleanVariable(name);
    }

    public void toggleInput(String name) {
        setInput(name, !getInput(name));
    }

    public boolean isOutputActive(String name) {
        return state.isOutputActive(name);
    }

    public boolean isStepActive(String stepId) {
        return state.isStepActive(stepId);
    }

    public boolean isStepActiveByNumber(int stepNumber) {
        for (StepNode s : steps.values()) {
            if (s.number() == stepNumber) {
                return state.isStepActive(s.id());
            }
        }
        return false;
    }

    // ==========================================
    // REQUÊTES PÉDAGOGIQUES SUR LES TRANSITIONS (AXE 2)
    // ==========================================

    /**
     * Une transition est validée si TOUTES ses étapes amont directes sont actives (CEI 60848).
     */
    public boolean isTransitionValidated(String transitionId) {
        TransitionNode t = transitions.get(transitionId);
        if (t == null || t.upstreamStepIds().isEmpty()) {
            return false;
        }
        for (String upId : t.upstreamStepIds()) {
            if (!state.isStepActive(upId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Indique si la réceptivité associée à la transition est actuellement vraie.
     */
    public boolean isTransitionReceptivityTrue(String transitionId) {
        return isTransitionReceptivityTrue(transitionId, System.currentTimeMillis());
    }

    public boolean isTransitionReceptivityTrue(String transitionId, long currentTimeMs) {
        TransitionNode t = transitions.get(transitionId);
        if (t == null) return false;
        return ReceptivityEvaluator.evaluate(t.conditionST(), state, currentTimeMs, this::resolveStepReference);
    }

    /**
     * Une transition est franchissable ssi elle est Validée ET sa réceptivité est Vraie (Règle 2).
     */
    public boolean isTransitionFireable(String transitionId) {
        return isTransitionFireable(transitionId, System.currentTimeMillis());
    }

    public boolean isTransitionFireable(String transitionId, long currentTimeMs) {
        return isTransitionValidated(transitionId) && isTransitionReceptivityTrue(transitionId, currentTimeMs);
    }

    public Set<String> getValidatedTransitions() {
        Set<String> set = new LinkedHashSet<>();
        for (String id : transitions.keySet()) {
            if (isTransitionValidated(id)) {
                set.add(id);
            }
        }
        return set;
    }

    public Set<String> getTrueReceptivityTransitions() {
        return getTrueReceptivityTransitions(System.currentTimeMillis());
    }

    public Set<String> getTrueReceptivityTransitions(long currentTimeMs) {
        Set<String> set = new LinkedHashSet<>();
        for (String id : transitions.keySet()) {
            if (isTransitionReceptivityTrue(id, currentTimeMs)) {
                set.add(id);
            }
        }
        return set;
    }

    public Set<String> getFireableTransitions() {
        return getFireableTransitions(System.currentTimeMillis());
    }

    public Set<String> getFireableTransitions(long currentTimeMs) {
        Set<String> set = new LinkedHashSet<>();
        for (TransitionNode t : transitions.values()) {
            if (isTransitionFireable(t, currentTimeMs)) {
                set.add(t.id());
            }
        }
        return set;
    }

    public SimulationState getState() {
        return state;
    }

    public Set<String> getKnownOutputVariables() {
        return Collections.unmodifiableSet(knownOutputVariables);
    }

    public Set<String> getKnownInputVariables() {
        return Collections.unmodifiableSet(knownInputVariables);
    }

    /**
     * Exécute UN cycle d'évolution synchrone selon les règles CEI 60848.
     */
    public SimulationStepResult step() {
        return step(System.currentTimeMillis());
    }

    public SimulationStepResult step(long currentTimeMs) {
        List<String> firedTransitions = new ArrayList<>();
        Set<String> stepsToDeactivate = new LinkedHashSet<>();
        Set<String> stepsToActivate = new LinkedHashSet<>();

        // Règle 2 : Détection des transitions franchissables
        Map<String, List<TransitionNode>> firedByUpstreamStep = new HashMap<>();
        for (TransitionNode t : transitions.values()) {
            if (isTransitionFireable(t, currentTimeMs)) {
                firedTransitions.add(t.id());
                stepsToDeactivate.addAll(t.upstreamStepIds());
                stepsToActivate.addAll(t.downstreamStepIds());
                for (String upStepId : t.upstreamStepIds()) {
                    firedByUpstreamStep.computeIfAbsent(upStepId, k -> new ArrayList<>()).add(t);
                }
            }
        }

        // Détection pédagogique : Conflit de non-exclusivité sur une divergence en OU
        for (var entry : firedByUpstreamStep.entrySet()) {
            List<TransitionNode> simultaneous = entry.getValue();
            if (simultaneous.size() > 1) {
                StepNode divStep = steps.get(entry.getKey());
                String stepLabel = (divStep != null) ? ("S" + divStep.number()) : entry.getKey();
                String transNames = String.join(", ", simultaneous.stream().map(TransitionNode::name).toList());
                recordRuntimeAlert(SfcValidationMessage.warning(
                        entry.getKey(),
                        "CEI 60848 — Exclusivité d'aiguillage en OU",
                        "Les transitions " + transNames + " sous l'étape " + stepLabel + " ont été franchies simultanément.",
                        "Dans une sélection de séquence (divergence en OU sous " + stepLabel + "), si vous souhaitez qu'une seule branche "
                                + "s'active à la fois, rendez leurs réceptivités mutuellement exclusives (ex: 'a' et 'NOT a')."
                ));
            }
        }

        if (firedTransitions.isEmpty()) {
            // Aucune transition franchie : simple rafraîchissement des sorties temporisées
            updateOutputs(currentTimeMs);
            return SimulationStepResult.noEvolution(state.getCycleCount(), state.getActiveOutputs());
        }

        // Règle 5 : Priorité à l'activation en cas de conflit
        Set<String> effectiveDeactivated = new LinkedHashSet<>(stepsToDeactivate);
        effectiveDeactivated.removeAll(stepsToActivate);

        Set<String> newlyActivated = new LinkedHashSet<>();
        for (String stepId : stepsToActivate) {
            if (!state.isStepActive(stepId) || stepsToDeactivate.contains(stepId)) {
                newlyActivated.add(stepId);
            }
        }

        // Règles 3 & 4 : Évolution simultanée du marquage
        for (String stepId : effectiveDeactivated) {
            state.setStepActive(stepId, false, currentTimeMs);
        }

        for (String stepId : stepsToActivate) {
            if (stepsToDeactivate.contains(stepId)) {
                // Réactivation sur boucle : réinitialisation du timer d'activation
                state.setStepActive(stepId, false, currentTimeMs);
            }
            state.setStepActive(stepId, true, currentTimeMs);
        }

        // Exécution des actions sur activation des nouvelles étapes
        for (String stepId : newlyActivated) {
            StepNode node = steps.get(stepId);
            if (node != null) {
                applyStepActivationActions(node);
            }
        }

        updateOutputs(currentTimeMs);
        state.incrementCycle();

        return new SimulationStepResult(
                true,
                firedTransitions,
                newlyActivated,
                effectiveDeactivated,
                state.getActiveOutputs(),
                state.getCycleCount()
        );
    }

    private boolean isTransitionFireable(TransitionNode t, long currentTimeMs) {
        // Condition de validation : TOUTES les étapes amont doivent être actives
        if (t.upstreamStepIds().isEmpty()) {
            return false;
        }
        for (String upId : t.upstreamStepIds()) {
            if (!state.isStepActive(upId)) {
                return false;
            }
        }

        // Condition de réceptivité : l'expression doit être VRAIE
        return ReceptivityEvaluator.evaluate(t.conditionST(), state, currentTimeMs, this::resolveStepReference);
    }

    private void applyStepActivationActions(StepNode step) {
        for (SfcActionBlock action : step.actionBlocks()) {
            String varName = action.variableName();

            // 1. Opération de compteur (ex: C1 := 0, C1 := C1 + 1)
            var counterOp = SfcCounterOperation.parse(varName);
            if (counterOp.isPresent()) {
                var op = counterOp.get();
                int current = state.getCounterValue(op.counterName());
                int next = switch (op.type()) {
                    case INITIALIZATION -> op.operand();
                    case INCREMENT -> current + op.operand();
                    case DECREMENT -> current - op.operand();
                };
                state.setCounterValue(op.counterName(), next);
                continue;
            }

            // 2. Qualificateurs mémorisés S / R
            if (action.qualifier() == SfcQualifier.S) {
                state.latchOutputSet(varName, true);
            } else if (action.qualifier() == SfcQualifier.R) {
                state.latchOutputSet(varName, false);
            }
        }
    }

    private void updateOutputs(long currentTimeMs) {
        Set<String> outputsToTurnOn = new HashSet<>();

        // 1. Ajouter les sorties mémorisées par S (Set)
        for (String outVar : knownOutputVariables) {
            if (state.isOutputLatchedSet(outVar)) {
                outputsToTurnOn.add(outVar);
            }
        }

        // 2. Parcourir les étapes actuellement actives
        for (String activeStepId : state.getActiveStepIds()) {
            StepNode step = steps.get(activeStepId);
            if (step == null) continue;

            long elapsedMs = state.getStepActiveDurationMs(activeStepId, currentTimeMs);

            for (SfcActionBlock action : step.actionBlocks()) {
                String varName = SimulationState.normalizeKey(action.variableName());

                // Ne pas traiter les assignations de compteurs comme des sorties
                if (SfcCounterOperation.parse(action.variableName()).isPresent()) {
                    continue;
                }

                switch (action.qualifier()) {
                    case N, P, P1 -> outputsToTurnOn.add(varName);
                    case L -> {
                        if (action.duration() != null && elapsedMs < action.duration().toMillis()) {
                            outputsToTurnOn.add(varName);
                        }
                    }
                    case D -> {
                        if (action.duration() != null && elapsedMs >= action.duration().toMillis()) {
                            outputsToTurnOn.add(varName);
                        }
                    }
                    default -> {}
                }
            }
        }

        // 3. Mettre à jour l'état de chaque sortie connue
        for (String outVar : knownOutputVariables) {
            state.setOutputActive(outVar, outputsToTurnOn.contains(outVar));
        }

        // Ajouter les sorties dynamiques non répertoriées dans variables
        for (String out : outputsToTurnOn) {
            state.setOutputActive(out, true);
        }

        // 4. Détection pédagogique de conflit Set (S) / Reset (R) simultané dans les étapes actives
        detectSimultaneousSetResetConflicts();
    }

    private void detectSimultaneousSetResetConflicts() {
        Map<String, String> setByStep = new HashMap<>();
        Map<String, String> resetByStep = new HashMap<>();

        for (String activeStepId : state.getActiveStepIds()) {
            StepNode step = steps.get(activeStepId);
            if (step == null) continue;
            for (SfcActionBlock action : step.actionBlocks()) {
                String normVar = SimulationState.normalizeKey(action.variableName());
                if (action.qualifier() == SfcQualifier.S) {
                    setByStep.put(normVar, "S" + step.number());
                } else if (action.qualifier() == SfcQualifier.R) {
                    resetByStep.put(normVar, "S" + step.number());
                }
            }
        }

        for (String varName : setByStep.keySet()) {
            if (resetByStep.containsKey(varName)) {
                String sSet = setByStep.get(varName);
                String sReset = resetByStep.get(varName);
                recordRuntimeAlert(SfcValidationMessage.error(
                        "",
                        "CEI 61131-3 — Conflit simultané Set (S) / Reset (R)",
                        "Conflit sur la sortie '" + varName + "' : commandée simultanément à 1 (S dans " + sSet
                                + ") et à 0 (R dans " + sReset + ").",
                        "Deux étapes actives en même temps envoient des ordres contradictoires (Set et Reset) sur '" + varName + "'. "
                                + "Assurez-vous que les étapes de mise à 1 et de remise à 0 ne soient jamais actives simultanément."
                ));
            }
        }
    }

    private void recordRuntimeAlert(SfcValidationMessage alert) {
        boolean alreadyPresent = recordedRuntimeAlerts.stream()
                .anyMatch(m -> m.message().equals(alert.message()));
        if (!alreadyPresent) {
            recordedRuntimeAlerts.add(alert);
        }
    }

    /**
     * Retourne la liste complète des diagnostics d'exécution (alertes enregistrées durant le cycle + diagnostics d'attente).
     */
    public List<SfcValidationMessage> getRuntimeDiagnostics() {
        List<SfcValidationMessage> result = new ArrayList<>(recordedRuntimeAlerts);

        // 1. Vérifier si le Grafcet a perdu tout marquage actif
        if (!steps.isEmpty() && state.getActiveStepIds().isEmpty()) {
            result.add(SfcValidationMessage.error(
                    "",
                    "CEI 60848 — Extinction du marquage",
                    "Aucune étape n'est actuellement active dans le Grafcet.",
                    "Vérifiez qu'au moins une étape initiale (double carré) est définie et que toutes vos transitions aboutissent bien à une étape aval."
            ));
        }

        // 2. Détecter les synchronisations en ET (Convergence ET) en attente d'une branche parallèle
        for (TransitionNode t : transitions.values()) {
            if (t.upstreamStepIds().size() >= 2) {
                List<String> activeUp = new ArrayList<>();
                List<String> missingUp = new ArrayList<>();
                for (String upId : t.upstreamStepIds()) {
                    StepNode sn = steps.get(upId);
                    String label = (sn != null) ? ("S" + sn.number()) : upId;
                    if (state.isStepActive(upId)) {
                        activeUp.add(label);
                    } else {
                        missingUp.add(label);
                    }
                }
                if (!activeUp.isEmpty() && !missingUp.isEmpty()) {
                    result.add(SfcValidationMessage.info(
                            t.id(),
                            "CEI 60848 — Synchronisation ET (Règle 2)",
                            "Convergence ET sur " + t.name() + " en attente : " + String.join(", ", activeUp)
                                    + " active(s), mais attend " + String.join(", ", missingUp) + ".",
                            "Une transition de synchronisation en ET (double barre) ne devient validée que lorsque TOUTES ses étapes amont "
                                    + "sont actives en même temps."
                    ));
                }
            }
        }

        return List.copyOf(result);
    }

    /**
     * Résout une référence d'étape textuelle (ex: "X1", "x1", "Step1", "1", "s1") vers l'identifiant technique d'étape.
     */
    public Optional<String> resolveStepReference(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        String trimmed = ref.trim();

        // 1. Id direct
        if (steps.containsKey(trimmed)) {
            return Optional.of(trimmed);
        }

        // 2. Format X<num> ou x<num>
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("x")) {
            try {
                int num = Integer.parseInt(trimmed.substring(1));
                return findStepIdByNumber(num);
            } catch (NumberFormatException ignored) {}
        }

        // 3. Numéro brut (ex: "1")
        try {
            int num = Integer.parseInt(trimmed);
            return findStepIdByNumber(num);
        } catch (NumberFormatException ignored) {}

        // 4. Par nom symbolique
        for (StepNode s : steps.values()) {
            if (s.name().equalsIgnoreCase(trimmed)) {
                return Optional.of(s.id());
            }
        }

        return Optional.empty();
    }

    private Optional<String> findStepIdByNumber(int number) {
        for (StepNode s : steps.values()) {
            if (s.number() == number) {
                return Optional.of(s.id());
            }
        }
        return Optional.empty();
    }
}
