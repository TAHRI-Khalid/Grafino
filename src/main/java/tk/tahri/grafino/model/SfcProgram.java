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

    // --- Validation de conformité CEI 60848 & CEI 61131-3 ---

    /**
     * Valide le respect des règles fondamentales des normes CEI 60848 (Grafcet) et CEI 61131-3 (SFC) :
     * 1. Présence d'au moins une étape initiale (Règle 1 CEI 60848).
     * 2. Unicité des noms et numéros d'étapes.
     * 3. Alternance stricte Étape — Transition.
     * 4. Topologie et continuité (transitions sans amont/aval, étapes non initiales inatteignables).
     * 5. Cohérence des structures de divergence et convergence (ET vs OU).
     * 6. Syntaxe des réceptivités, validité des temporisations t/Xi/d et déclaration des capteurs (%I).
     * 7. Cohérence des actions (conflit de portée %I/%Q, symétrie Set/Reset, qualificateur P et initialisation des compteurs).
     */
    public List<SfcValidationMessage> validate() {
        List<SfcValidationMessage> messages = new ArrayList<>();

        // Règle 1 : Étape initiale obligatoire (CEI 60848 Règle 1)
        if (getInitialSteps().isEmpty()) {
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
                messages.add(SfcValidationMessage.warning(
                        link.id(),
                        "Topologie — Liaison orpheline",
                        "Liaison orpheline détectée (élément source ou cible introuvable).",
                        "Cette liaison pointe vers un élément supprimé. Supprimez-la et recréez le raccordement entre une étape et une transition."
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

        // Règle 4 : Continuité topologique (Étapes inatteignables & Transitions non raccordées)
        if (!transitions.isEmpty()) {
            for (SfcStep step : steps) {
                if (!step.isInitial() && incomingToStep.get(step.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.warning(
                            step.id(),
                            "Topologie — Étape inatteignable",
                            "L'étape " + step.name() + " (S" + step.number() + ") n'est pas initiale et n'a aucune transition en amont.",
                            "Une étape non initiale ne peut s'activer que par le franchissement d'une transition située au-dessus d'elle. "
                                    + "Reliez une transition en amont de " + step.name() + " ou définissez-la comme étape initiale."
                    ));
                }
            }

            for (SfcTransition transition : transitions) {
                if (upstreamStepsOfTrans.get(transition.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.error(
                            transition.id(),
                            "CEI 60848 — Règle 2 (Validation de transition)",
                            "La transition " + transition.name() + " n'a aucune étape en amont : elle ne pourra jamais être validée.",
                            "Selon la Règle 2 de la norme CEI 60848, une transition ne peut être validée que si toutes ses étapes immédiatement "
                                    + "précédentes sont actives. Reliez la sortie d'une étape au sommet de " + transition.name() + "."
                    ));
                }
                if (downstreamStepsOfTrans.get(transition.id()).isEmpty()) {
                    messages.add(SfcValidationMessage.warning(
                            transition.id(),
                            "Topologie — Transition en cul-de-sac",
                            "La transition " + transition.name() + " n'est reliée à aucune étape en aval.",
                            "Le franchissement d'une transition sans étape aval désactiverait l'étape amont sans activer d'étape suivante "
                                    + "(perte du jeton). Reliez le bas de " + transition.name() + " vers une étape suivante ou une boucle de reprise."
                    ));
                }
            }
        }

        // Règle 5 : Cohérence des divergences et convergences (ET vs OU)
        validateBranchCoherence(messages, downwardFromStep, downwardFromTrans, downwardIntoStep, downwardIntoTrans);

        // Règle 6 : Validation des transitions (Syntaxe ST, Temporisations t/Xi/d, Capteurs %I et Comparaisons)
        for (SfcTransition transition : transitions) {
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
                var variableOpt = findVariableByName(comp.counterName());
                if (variableOpt.isEmpty() && !variables.isEmpty()) {
                    messages.add(SfcValidationMessage.warning(
                            transition.id(),
                            "CEI 61131-3 — Déclaration des Compteurs",
                            "La transition " + transition.name() + " compare une variable de comptage non déclarée : '" + comp.counterName() + "'.",
                            "Ouvrez la table des variables au bas de l'écran et cliquez sur '+ Compteur (INT)' pour déclarer '"
                                    + comp.counterName() + "'."
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
                validateTransitionSensorVariables(transition, messages);
            }
        }

        // Règle 7 : Validation des actions d'étapes (Portée %I/%Q, Set/Reset et Compteurs)
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
                    // Avertissement pédagogique : qualificateur continu N sur une incrémentation/décrémentation
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
                    // Avertissement pédagogique : compteur modifié mais jamais initialisé dans un cycle complet
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
                    // Action classique sur variable de sortie
                    String rawVarName = action.variableName().trim();
                    var declaredVarOpt = findVariableByName(rawVarName);

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

                    // Vérification de la symétrie Set (S) / Reset (R)
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

        return List.copyOf(messages);
    }

    private boolean isAscendingLink(SfcLink link, double sourceY, double targetY) {
        if (link.intermediatePoints() != null && link.intermediatePoints().size() >= 4) {
            return true;
        }
        return targetY < sourceY;
    }

    /**
     * Détecte les incohérences classiques entre divergences et convergences (Divergence OU fermée par ET, ou Divergence ET fermée par OU).
     */
    private void validateBranchCoherence(
            List<SfcValidationMessage> messages,
            Map<String, Set<String>> downwardFromStep,
            Map<String, Set<String>> downwardFromTrans,
            Map<String, Set<String>> downwardIntoStep,
            Map<String, Set<String>> downwardIntoTrans
    ) {
        // 1. Divergence en OU (Étape avec >= 2 transitions aval descendantes) fermée par une Convergence en ET (Transition avec >= 2 étapes amont)
        for (SfcStep divStep : steps) {
            List<String> branchTransIds = new ArrayList<>(downwardFromStep.getOrDefault(divStep.id(), Set.of()));
            if (branchTransIds.size() < 2) continue;

            // Pour chaque branche issue de divStep, collecter les étapes atteignables en descendant sans traverser de divergence en ET
            List<Set<String>> branchReachableSteps = new ArrayList<>();
            for (String startTransId : branchTransIds) {
                if (downwardFromTrans.getOrDefault(startTransId, Set.of()).size() > 1) {
                    // C'est une divergence en ET immédiate, ne pas confondre
                    branchReachableSteps.add(Set.of());
                    continue;
                }
                Set<String> reachableSteps = collectReachableStepsWithoutAndDivergence(startTransId, downwardFromStep, downwardFromTrans);
                branchReachableSteps.add(reachableSteps);
            }

            for (SfcTransition convTrans : transitions) {
                Set<String> upSteps = downwardIntoTrans.getOrDefault(convTrans.id(), Set.of());
                if (upSteps.size() < 2) continue;

                int matchedBranches = 0;
                for (Set<String> branchSet : branchReachableSteps) {
                    if (upSteps.stream().anyMatch(branchSet::contains)) {
                        matchedBranches++;
                    }
                }
                if (matchedBranches >= 2) {
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

        // 2. Divergence en ET (Transition avec >= 2 étapes aval descendantes) fermée par une Convergence en OU (Étape avec >= 2 transitions amont)
        for (SfcTransition divTrans : transitions) {
            List<String> branchStepIds = new ArrayList<>(downwardFromTrans.getOrDefault(divTrans.id(), Set.of()));
            if (branchStepIds.size() < 2) continue;

            List<Set<String>> branchReachableTrans = new ArrayList<>();
            for (String startStepId : branchStepIds) {
                Set<String> reachableTrans = collectReachableTransitionsWithoutAndConvergence(startStepId, downwardFromStep, downwardFromTrans, downwardIntoTrans);
                branchReachableTrans.add(reachableTrans);
            }

            for (SfcStep convStep : steps) {
                Set<String> upTrans = downwardIntoStep.getOrDefault(convStep.id(), Set.of());
                if (upTrans.size() < 2) continue;

                int matchedBranches = 0;
                for (Set<String> branchSet : branchReachableTrans) {
                    if (upTrans.stream().anyMatch(branchSet::contains)) {
                        matchedBranches++;
                    }
                }
                if (matchedBranches >= 2) {
                    messages.add(SfcValidationMessage.warning(
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
    }

    private Set<String> collectReachableStepsWithoutAndDivergence(
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
            Set<String> nextSteps = downwardFromTrans.getOrDefault(tId, Set.of());
            if (nextSteps.size() > 1) {
                // Divergence en ET rencontrée : ne pas propager comme simple branche OU
                continue;
            }
            for (String sId : nextSteps) {
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

    private Set<String> collectReachableTransitionsWithoutAndConvergence(
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
                    // Convergence en ET légitime rencontrée : les branches se synchronisent ici
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

    /**
     * Vérifie la syntaxe de l'expression de réceptivité d'une transition (parenthèses, opérateurs incomplets).
     */
    private void validateReceptivitySyntax(SfcTransition transition, List<SfcValidationMessage> messages) {
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

        // 1. Équilibre des parenthèses
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

        // 2. Opérateur binaire isolé en début ou fin d'expression (hors '/' ou '!' qui sont des négations unaires)
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

    /**
     * Vérifie que les variables booléennes utilisées dans la réceptivité d'une transition sont déclarées et de bon type.
     */
    private void validateTransitionSensorVariables(SfcTransition transition, List<SfcValidationMessage> messages) {
        String expr = transition.conditionST();
        if (expr == null || expr.isBlank()) return;

        // Retirer les temporisations t/Xi/d et Xi.t >= T#d
        String cleaned = expr.replaceAll("(?i)(?:[a-zA-Z_][a-zA-Z0-9_]*\\s*/\\s*)?(?:[Xx]\\d+|[a-zA-Z_][a-zA-Z0-9_]*|\\d+)\\s*/\\s*\\d+(?:\\.\\d+)?\\s*(?:ms|s|min|m|h)", " ");
        cleaned = cleaned.replaceAll("(?i)(?:[Xx]\\d+|[a-zA-Z_][a-zA-Z0-9_]*)\\.t\\s*>=\\s*(?:T#)?\\d+(?:\\.\\d+)?\\s*(?:ms|s|min|m|h)", " ");
        // Retirer les comparaisons numériques (déjà vérifiées séparément)
        cleaned = cleaned.replaceAll("(?i)\\b[a-zA-Z_][a-zA-Z0-9_]*\\s*(?:<=|>=|<>|!=|<|>|=)\\s*-?\\d+\\b", " ");

        java.util.regex.Matcher tokenMatcher = java.util.regex.Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b").matcher(cleaned);
        Set<String> checkedTokens = new HashSet<>();
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group(1);
            String upper = token.toUpperCase(Locale.ROOT);
            if (!checkedTokens.add(upper)) continue;

            // Ignorer les mots-clés booléens
            if (upper.equals("AND") || upper.equals("OR") || upper.equals("NOT") || upper.equals("TRUE") || upper.equals("FALSE") || upper.equals("XOR")) {
                continue;
            }
            // Ignorer les variables d'activité d'étape (ex: X0, X1, S0, Step0)
            boolean isStepRef = steps.stream().anyMatch(s ->
                    s.name().equalsIgnoreCase(token) || ("X" + s.number()).equalsIgnoreCase(token) || ("S" + s.number()).equalsIgnoreCase(token));
            if (isStepRef) {
                continue;
            }

            var declaredOpt = findVariableByName(token);
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
