package tk.tahri.grafino.model.validation;

import tk.tahri.grafino.model.*;

import java.util.*;

/**
 * Validateur topologique d'un programme SFC / Grafcet (CEI 60848).
 * Vérifie : étape initiale, unicité des repères, alternance stricte, continuité des cycles
 * et cohérence des divergences/convergences (ET vs OU).
 */
public final class SfcTopologyValidator {

    private SfcTopologyValidator() {
    }

    public static void validate(SfcProgram program, List<SfcValidationMessage> messages) {
        List<SfcStep> steps = program.steps();
        List<SfcTransition> transitions = program.transitions();
        List<SfcBranch> branches = program.branches();
        List<SfcLink> links = program.links();

        // Règle 1 : Étape initiale obligatoire (CEI 60848 Règle 1)
        if (program.getInitialSteps().isEmpty()) {
            messages.add(SfcValidationMessage.error(
                    "",
                    "CEI 60848 — Règle 1 (Situation initiale)",
                    "Le diagramme SFC doit comporter au moins une étape initiale.",
                    "Sans étape initiale (double carré), l'automate ne sait pas quelle étape activer à la mise sous tension. "
                            + "Sélectionnez votre étape de repos (ex: S0) et cochez 'Étape initiale' dans le panneau latéral droit."
            ));
        }

        // Règle 2 : Unicité des identifiants et numéros d'étapes
        Set<String> stepNames = new HashSet<>();
        Set<Integer> stepNumbers = new HashSet<>();
        for (SfcStep step : steps) {
            if (!stepNames.add(step.name().toUpperCase(Locale.ROOT))) {
                messages.add(SfcValidationMessage.error(
                        step.id(),
                        "CEI 60848 — Identification des étapes",
                        "Nom d'étape dupliqué : " + step.name(),
                        "Chaque étape du Grafcet doit posséder un repère unique pour éviter toute ambiguïté de marquage (Xn). "
                                + "Double-cliquez sur l'étape pour lui attribuer un numéro distinct."
                ));
            } else if (!stepNumbers.add(step.number())) {
                messages.add(SfcValidationMessage.error(
                        step.id(),
                        "CEI 60848 — Identification des étapes",
                        "Numéro d'étape dupliqué : S" + step.number(),
                        "Deux étapes portent le même numéro S" + step.number() + ". "
                                + "Double-cliquez sur l'une d'elles sur le Canvas pour lui attribuer un numéro unique."
                ));
            }
        }

        // Règle 3 : Alternance stricte Étape - Transition & Construction de la topologie
        Map<String, SfcElement> elementMap = new HashMap<>();
        steps.forEach(s -> elementMap.put(s.id(), s));
        transitions.forEach(t -> elementMap.put(t.id(), t));
        branches.forEach(b -> elementMap.put(b.id(), b));

        Map<String, Set<String>> incomingToStep = new HashMap<>();
        Map<String, Set<String>> outgoingFromStep = new HashMap<>();
        Map<String, Set<String>> upstreamStepsOfTrans = new HashMap<>();
        Map<String, Set<String>> downstreamStepsOfTrans = new HashMap<>();
        Map<String, Set<String>> downwardFromStep = new HashMap<>();
        Map<String, Set<String>> downwardFromTrans = new HashMap<>();
        Map<String, Set<String>> downwardIntoStep = new HashMap<>();
        Map<String, Set<String>> downwardIntoTrans = new HashMap<>();

        steps.forEach(s -> {
            incomingToStep.put(s.id(), new LinkedHashSet<>());
            outgoingFromStep.put(s.id(), new LinkedHashSet<>());
            downwardFromStep.put(s.id(), new LinkedHashSet<>());
            downwardIntoStep.put(s.id(), new LinkedHashSet<>());
        });
        transitions.forEach(t -> {
            upstreamStepsOfTrans.put(t.id(), new LinkedHashSet<>());
            downstreamStepsOfTrans.put(t.id(), new LinkedHashSet<>());
            downwardFromTrans.put(t.id(), new LinkedHashSet<>());
            downwardIntoTrans.put(t.id(), new LinkedHashSet<>());
        });

        for (SfcLink link : links) {
            SfcElement source = elementMap.get(link.sourceId());
            SfcElement target = elementMap.get(link.targetId());

            if (source == null || target == null) {
                messages.add(SfcValidationMessage.error(
                        link.id(),
                        "CEI 60848 — Absence de boucle ou de fin de cycle (Liaisons ouvertes)",
                        "Liaison ouverte détectée (élément source ou cible introuvable).",
                        "Cette liaison est ouverte et pointe vers un élément supprimé. Supprimez-la et recréez le raccordement complet entre une étape et une transition."
                ));
                continue;
            }

            if (source instanceof SfcStep && target instanceof SfcStep) {
                messages.add(SfcValidationMessage.error(
                        link.id(),
                        "CEI 60848 — Alternance Étape / Transition",
                        "Violation CEI 61131-3 : deux étapes ne peuvent être reliées directement sans transition intermédiaire.",
                        "Une étape représente un état stable et une transition la condition de changement d'état. "
                                + "Supprimez cette liaison directe et insérez une transition entre les deux étapes."
                ));
            } else if (source instanceof SfcTransition && target instanceof SfcTransition) {
                messages.add(SfcValidationMessage.error(
                        link.id(),
                        "CEI 60848 — Alternance Étape / Transition",
                        "Violation CEI 61131-3 : deux transitions ne peuvent être reliées directement sans étape intermédiaire.",
                        "Deux transitions ne peuvent jamais se suivre directement. Insérez une étape intermédiaire entre elles, "
                                + "ou combinez leurs deux conditions dans une seule transition avec l'opérateur ET (AND / '.')."
                ));
            } else if (source instanceof SfcStep srcStep && target instanceof SfcTransition tgtTrans) {
                outgoingFromStep.get(srcStep.id()).add(tgtTrans.id());
                upstreamStepsOfTrans.get(tgtTrans.id()).add(srcStep.id());
                if (!isAscendingLink(link, srcStep.y(), tgtTrans.y())) {
                    downwardFromStep.get(srcStep.id()).add(tgtTrans.id());
                    downwardIntoTrans.get(tgtTrans.id()).add(srcStep.id());
                }
            } else if (source instanceof SfcTransition srcTrans && target instanceof SfcStep tgtStep) {
                downstreamStepsOfTrans.get(srcTrans.id()).add(tgtStep.id());
                incomingToStep.get(tgtStep.id()).add(srcTrans.id());
                if (!isAscendingLink(link, srcTrans.y(), tgtStep.y())) {
                    downwardFromTrans.get(srcTrans.id()).add(tgtStep.id());
                    downwardIntoStep.get(tgtStep.id()).add(srcTrans.id());
                }
            } else if (source instanceof SfcStep srcStep && target instanceof SfcBranch branch) {
                for (String downId : branch.downstreamIds()) {
                    if (upstreamStepsOfTrans.containsKey(downId)) {
                        outgoingFromStep.get(srcStep.id()).add(downId);
                        upstreamStepsOfTrans.get(downId).add(srcStep.id());
                        downwardFromStep.get(srcStep.id()).add(downId);
                        downwardIntoTrans.get(downId).add(srcStep.id());
                    }
                }
            } else if (source instanceof SfcBranch branch && target instanceof SfcTransition tgtTrans) {
                for (String upId : branch.upstreamIds()) {
                    if (outgoingFromStep.containsKey(upId)) {
                        outgoingFromStep.get(upId).add(tgtTrans.id());
                        upstreamStepsOfTrans.get(tgtTrans.id()).add(upId);
                        downwardFromStep.get(upId).add(tgtTrans.id());
                        downwardIntoTrans.get(tgtTrans.id()).add(upId);
                    }
                }
            } else if (source instanceof SfcTransition srcTrans && target instanceof SfcBranch branch) {
                for (String downId : branch.downstreamIds()) {
                    if (incomingToStep.containsKey(downId)) {
                        downstreamStepsOfTrans.get(srcTrans.id()).add(downId);
                        incomingToStep.get(downId).add(srcTrans.id());
                        downwardFromTrans.get(srcTrans.id()).add(downId);
                        downwardIntoStep.get(downId).add(srcTrans.id());
                    }
                }
            } else if (source instanceof SfcBranch branch && target instanceof SfcStep tgtStep) {
                for (String upId : branch.upstreamIds()) {
                    if (downstreamStepsOfTrans.containsKey(upId)) {
                        downstreamStepsOfTrans.get(upId).add(tgtStep.id());
                        incomingToStep.get(tgtStep.id()).add(upId);
                        downwardFromTrans.get(upId).add(tgtStep.id());
                        downwardIntoStep.get(tgtStep.id()).add(upId);
                    }
                }
            }
        }

        // Règle 4 : Continuité topologique — Absence de boucle ou de fin de cycle (Liaisons ouvertes)
        if (!transitions.isEmpty() || (steps.size() > 1 && !program.getInitialSteps().isEmpty())) {
            for (SfcStep step : steps) {
                if (!step.isInitial() && incomingToStep.get(step.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.error(
                            step.id(),
                            "CEI 60848 — Absence de boucle ou de fin de cycle (Liaisons ouvertes)",
                            "Liaison amont ouverte : l'étape " + step.name() + " (S" + step.number() + ") n'est pas initiale et n'a aucune transition en amont.",
                            "Une étape non initiale ne peut s'activer que par le franchissement d'une transition située au-dessus d'elle. "
                                    + "Reliez une transition en amont de " + step.name() + " ou définissez-la comme étape initiale."
                    ));
                }
                if (outgoingFromStep.get(step.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.error(
                            step.id(),
                            "CEI 60848 — Absence de boucle ou de fin de cycle (Liaisons ouvertes)",
                            "Absence de boucle ou de fin de cycle (Liaisons ouvertes) : l'étape " + step.name() + " (S" + step.number() + ") n'est reliée à aucune transition en aval.",
                            "Un Grafcet industriel décrit un cycle séquentiel fermé. Sans transition aval ni boucle de retour vers l'étape initiale, "
                                    + "l'évolution restera bloquée indéfiniment sur l'étape " + step.name() + ". Ajoutez une transition après " + step.name()
                                    + " et rebouclez vers l'étape initiale."
                    ));
                } else if (step.isInitial() && incomingToStep.get(step.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.error(
                            step.id(),
                            "CEI 60848 — Absence de boucle ou de fin de cycle (Liaisons ouvertes)",
                            "Absence de boucle de cycle (Liaisons ouvertes) : aucune transition de fin de cycle ne reboucle vers l'étape initiale "
                                    + step.name() + " (S" + step.number() + ").",
                            "Pour que le cycle automatisé puisse se répéter après son exécution, reliez la dernière transition de votre séquence "
                                    + "vers le haut de l'étape initiale " + step.name() + " (boucle de reprise orientée vers le haut ▲)."
                    ));
                }
            }

            for (SfcTransition transition : transitions) {
                if (upstreamStepsOfTrans.get(transition.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.error(
                            transition.id(),
                            "CEI 60848 — Absence de boucle ou de fin de cycle (Liaisons ouvertes)",
                            "Liaison amont ouverte : la transition " + transition.name() + " n'a aucune étape en amont et ne pourra jamais être validée.",
                            "Selon la Règle 2 de la norme CEI 60848, une transition ne peut être validée que si toutes ses étapes immédiatement "
                                    + "précédentes sont actives. Reliez la sortie d'une étape au sommet de " + transition.name() + "."
                    ));
                }
                if (downstreamStepsOfTrans.get(transition.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.error(
                            transition.id(),
                            "CEI 60848 — Absence de boucle ou de fin de cycle (Liaisons ouvertes)",
                            "Absence de boucle ou de fin de cycle (Liaisons ouvertes) : la transition " + transition.name() + " n'est reliée à aucune étape en aval.",
                            "Le franchissement d'une transition sans étape aval désactiverait l'étape amont sans activer d'étape suivante "
                                    + "(perte du jeton et arrêt du cycle). Reliez le bas de " + transition.name() + " vers une étape suivante ou rebouclez vers l'étape initiale."
                    ));
                }
            }
        }

        // Règle 5 : Cohérence des divergences et convergences (ET vs OU)
        validateBranchCoherence(steps, transitions, messages, downwardFromStep, downwardFromTrans, downwardIntoStep, downwardIntoTrans);
    }

    private static boolean isAscendingLink(SfcLink link, double sourceY, double targetY) {
        if (link.intermediatePoints() != null && link.intermediatePoints().size() >= 4) {
            return true;
        }
        return targetY < sourceY;
    }

    private static void validateBranchCoherence(
            List<SfcStep> steps,
            List<SfcTransition> transitions,
            List<SfcValidationMessage> messages,
            Map<String, Set<String>> downwardFromStep,
            Map<String, Set<String>> downwardFromTrans,
            Map<String, Set<String>> downwardIntoStep,
            Map<String, Set<String>> downwardIntoTrans
    ) {
        Set<String> flaggedConvTransIds = new HashSet<>();
        Set<String> flaggedConvStepIds = new HashSet<>();

        // 1. Divergence en OU fermée par Convergence en ET (Deadlock)
        for (SfcStep divStep : steps) {
            List<String> branchTransIds = new ArrayList<>(downwardFromStep.getOrDefault(divStep.id(), Set.of()));
            if (branchTransIds.size() < 2) continue;

            List<Set<String>> branchReachableSteps = new ArrayList<>();
            for (String startTransId : branchTransIds) {
                branchReachableSteps.add(collectReachableStepsWithoutAndDivergence(startTransId, downwardFromStep, downwardFromTrans));
            }

            for (SfcTransition convTrans : transitions) {
                Set<String> upSteps = downwardIntoTrans.getOrDefault(convTrans.id(), Set.of());
                if (upSteps.size() < 2) continue;

                long matchedBranches = branchReachableSteps.stream()
                        .filter(branchSet -> upSteps.stream().anyMatch(branchSet::contains))
                        .count();
                if (matchedBranches >= 2) {
                    flaggedConvTransIds.add(convTrans.id());
                    messages.add(SfcValidationMessage.error(
                            convTrans.id(),
                            "CEI 60848 — Incohérence Divergence OU / Convergence ET",
                            "Incohérence structurelle sur " + convTrans.name() + " : l'aiguillage en OU issu de "
                                    + divStep.name() + " est refermé par une synchronisation en ET.",
                            "Dans une sélection de séquence en OU (simple trait sous " + divStep.name() + "), une seule branche est activée à la fois. "
                                    + "Or, la convergence en ET (double barre au-dessus de " + convTrans.name() + ") attend que TOUTES les branches amont "
                                    + "soient actives simultanément : la transition " + convTrans.name() + " sera bloquée définitivement (Deadlock). "
                                    + "Terminez chaque branche par sa propre transition avant de rejoindre l'étape commune."
                    ));
                }
            }
        }

        // 2. Divergence en ET fermée par Convergence en OU
        for (SfcTransition divTrans : transitions) {
            List<String> branchStepIds = new ArrayList<>(downwardFromTrans.getOrDefault(divTrans.id(), Set.of()));
            if (branchStepIds.size() < 2) continue;

            List<Set<String>> branchReachableTrans = new ArrayList<>();
            for (String startStepId : branchStepIds) {
                branchReachableTrans.add(collectReachableTransitionsWithoutAndConvergence(startStepId, downwardFromStep, downwardFromTrans, downwardIntoTrans));
            }

            for (SfcStep convStep : steps) {
                Set<String> upTrans = downwardIntoStep.getOrDefault(convStep.id(), Set.of());
                if (upTrans.size() < 2) continue;

                long matchedBranches = branchReachableTrans.stream()
                        .filter(branchSet -> upTrans.stream().anyMatch(branchSet::contains))
                        .count();
                if (matchedBranches >= 2) {
                    flaggedConvStepIds.add(convStep.id());
                    messages.add(SfcValidationMessage.error(
                            convStep.id(),
                            "CEI 60848 — Incohérence Divergence ET / Convergence OU",
                            "Incohérence structurelle sur " + convStep.name() + " : des branches parallèles (divergence en ET sous "
                                    + divTrans.name() + ") convergent en OU sans synchronisation.",
                            "Après une divergence en ET (double barre sous " + divTrans.name() + "), plusieurs branches évoluent en parallèle. "
                                    + "Si elles rejoignent directement l'étape " + convStep.name() + " par un simple OU, les branches ne s'attendront pas "
                                    + "et multiplieront les jetons actifs. Raccordez les étapes de fin de branche vers une unique transition de synchronisation ET."
                    ));
                }
            }
        }

        // 3. Convergence sans divergence commune en amont
        for (SfcTransition convTrans : transitions) {
            Set<String> upSteps = downwardIntoTrans.getOrDefault(convTrans.id(), Set.of());
            if (upSteps.size() < 2 || flaggedConvTransIds.contains(convTrans.id())) continue;
            if (!hasCommonAndDivergenceAncestor(transitions, new ArrayList<>(upSteps), downwardFromStep, downwardFromTrans)) {
                messages.add(SfcValidationMessage.error(
                        convTrans.id(),
                        "CEI 60848 — Convergence en ET sans divergence commune",
                        "Convergence en ET non conforme sur " + convTrans.name() + " : les étapes amont ne proviennent pas d'une même divergence en ET.",
                        "Une convergence en ET doit synchroniser des branches parallèles issues d'une même divergence en ET. "
                                + "Vérifiez que les branches amont ne proviennent pas de diagrammes distincts."
                ));
            }
        }

        for (SfcStep convStep : steps) {
            Set<String> upTrans = downwardIntoStep.getOrDefault(convStep.id(), Set.of());
            if (upTrans.size() < 2 || flaggedConvStepIds.contains(convStep.id())) continue;
            if (!hasCommonOrDivergenceAncestor(steps, new ArrayList<>(upTrans), downwardFromStep, downwardFromTrans)) {
                messages.add(SfcValidationMessage.error(
                        convStep.id(),
                        "CEI 60848 — Convergence en OU sans divergence commune",
                        "Convergence en OU non conforme sur " + convStep.name() + " : les transitions amont ne proviennent pas d'une même divergence en OU.",
                        "Une convergence en OU doit regrouper des séquences alternatives issues d'une même divergence en OU. "
                                + "Vérifiez que les branches amont ne proviennent pas de diagrammes distincts."
                ));
            }
        }
    }

    private static boolean hasCommonAndDivergenceAncestor(
            List<SfcTransition> transitions,
            List<String> stepIds,
            Map<String, Set<String>> downwardFromStep,
            Map<String, Set<String>> downwardFromTrans
    ) {
        for (SfcTransition divTrans : transitions) {
            List<String> branchSteps = new ArrayList<>(downwardFromTrans.getOrDefault(divTrans.id(), Set.of()));
            if (branchSteps.size() < 2) continue;
            List<Set<String>> branchSets = new ArrayList<>();
            for (String startStepId : branchSteps) {
                branchSets.add(collectAllReachableIds(startStepId, true, downwardFromStep, downwardFromTrans));
            }
            if (GraphBranchUtils.canAssignDistinctBranches(stepIds, branchSets)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCommonOrDivergenceAncestor(
            List<SfcStep> steps,
            List<String> transIds,
            Map<String, Set<String>> downwardFromStep,
            Map<String, Set<String>> downwardFromTrans
    ) {
        for (SfcStep divStep : steps) {
            List<String> branchTrans = new ArrayList<>(downwardFromStep.getOrDefault(divStep.id(), Set.of()));
            if (branchTrans.size() < 2) continue;
            List<Set<String>> branchSets = new ArrayList<>();
            for (String startTransId : branchTrans) {
                branchSets.add(collectAllReachableIds(startTransId, false, downwardFromStep, downwardFromTrans));
            }
            if (GraphBranchUtils.canAssignDistinctBranches(transIds, branchSets)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> collectAllReachableIds(
            String startId,
            boolean collectSteps,
            Map<String, Set<String>> downwardFromStep,
            Map<String, Set<String>> downwardFromTrans
    ) {
        Set<String> result = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visitedSteps = new HashSet<>();
        Set<String> visitedTrans = new HashSet<>();

        queue.add(startId);
        if (collectSteps) {
            visitedSteps.add(startId);
            result.add(startId);
        } else {
            visitedTrans.add(startId);
            result.add(startId);
        }

        boolean nextIsStep = collectSteps;
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                String current = queue.poll();
                if (nextIsStep) {
                    for (String tId : downwardFromStep.getOrDefault(current, Set.of())) {
                        if (visitedTrans.add(tId)) {
                            queue.add(tId);
                            if (!collectSteps) result.add(tId);
                        }
                    }
                } else {
                    for (String sId : downwardFromTrans.getOrDefault(current, Set.of())) {
                        if (visitedSteps.add(sId)) {
                            queue.add(sId);
                            if (collectSteps) result.add(sId);
                        }
                    }
                }
            }
            nextIsStep = !nextIsStep;
        }
        return result;
    }

    private static Set<String> collectReachableStepsWithoutAndDivergence(
            String startTransId,
            Map<String, Set<String>> downwardFromStep,
            Map<String, Set<String>> downwardFromTrans
    ) {
        Set<String> visitedSteps = new LinkedHashSet<>();
        Queue<String> queueTrans = new ArrayDeque<>();
        Set<String> visitedTrans = new HashSet<>();
        queueTrans.add(startTransId);
        visitedTrans.add(startTransId);

        while (!queueTrans.isEmpty()) {
            String tId = queueTrans.poll();
            for (String sId : downwardFromTrans.getOrDefault(tId, Set.of())) {
                if (visitedSteps.add(sId)) {
                    for (String nextT : downwardFromStep.getOrDefault(sId, Set.of())) {
                        if (visitedTrans.add(nextT)) {
                            queueTrans.add(nextT);
                        }
                    }
                }
            }
        }
        return visitedSteps;
    }

    private static Set<String> collectReachableTransitionsWithoutAndConvergence(
            String startStepId,
            Map<String, Set<String>> downwardFromStep,
            Map<String, Set<String>> downwardFromTrans,
            Map<String, Set<String>> downwardIntoTrans
    ) {
        Set<String> visitedTrans = new LinkedHashSet<>();
        Queue<String> queueSteps = new ArrayDeque<>();
        Set<String> visitedSteps = new HashSet<>();
        queueSteps.add(startStepId);
        visitedSteps.add(startStepId);

        while (!queueSteps.isEmpty()) {
            String sId = queueSteps.poll();
            for (String tId : downwardFromStep.getOrDefault(sId, Set.of())) {
                if (downwardIntoTrans.getOrDefault(tId, Set.of()).size() > 1) {
                    continue;
                }
                if (visitedTrans.add(tId)) {
                    for (String nextS : downwardFromTrans.getOrDefault(tId, Set.of())) {
                        if (visitedSteps.add(nextS)) {
                            queueSteps.add(nextS);
                        }
                    }
                }
            }
        }
        return visitedTrans;
    }
}
