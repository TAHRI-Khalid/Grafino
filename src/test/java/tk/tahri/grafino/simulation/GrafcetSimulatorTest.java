package tk.tahri.grafino.simulation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tk.tahri.grafino.model.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests du Moteur de Simulation Synchrone CEI 60848 (Phase 1)")
class GrafcetSimulatorTest {

    @Test
    @DisplayName("Règle 1 : Situation initiale et initialisation des sorties")
    void testInitialSituation() {
        var vDcy = new IecVariable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "Départ cycle");
        var vMoteur = new IecVariable("MOTEUR", IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, "%Q0.0", "Moteur");

        var s0 = new SfcStep("s0", "Init", 0, true, 400.0, 50.0);
        var s1 = new SfcStep("s1", "Marche", 1, false, 400.0, 200.0,
                List.of(new SfcActionBlock(SfcQualifier.N, "MOTEUR")));

        var t1 = new SfcTransition("t1", "T1", 1, "dcy", 400.0, 120.0);
        var t2 = new SfcTransition("t2", "T2", 2, "1", 400.0, 280.0);

        var l1 = new SfcLink("l1", "s0", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");
        var l3 = new SfcLink("l3", "s1", "t2");
        var l4 = new SfcLink("l4", "t2", "s0");

        var program = new SfcProgram("Cycle_Simple", List.of(vDcy, vMoteur),
                List.of(s0, s1), List.of(t1, t2), List.of(), List.of(l1, l2, l3, l4));

        var simulator = new GrafcetSimulator(program);

        // L'étape initiale s0 doit être active, s1 inactive
        assertTrue(simulator.isStepActive("s0"));
        assertFalse(simulator.isStepActive("s1"));
        assertFalse(simulator.isOutputActive("MOTEUR"));
        assertEquals(0, simulator.getState().getCycleCount());
    }

    @Test
    @DisplayName("Règles 2, 3 et 4 : Évolution séquentielle et allumage des sorties continues (N)")
    void testSequentialEvolutionAndContinuousActions() {
        var vDcy = new IecVariable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "Départ");
        var vFin = new IecVariable("fin", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.1", "Fin de course");
        var vMoteur = new IecVariable("MOTEUR", IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, "%Q0.0", "Moteur");

        var s0 = new SfcStep("s0", "Attente", 0, true, 400.0, 50.0);
        var s1 = new SfcStep("s1", "Avance", 1, false, 400.0, 200.0,
                List.of(new SfcActionBlock(SfcQualifier.N, "MOTEUR")));

        var t1 = new SfcTransition("t1", "T1", 1, "dcy", 400.0, 120.0);
        var t2 = new SfcTransition("t2", "T2", 2, "fin", 400.0, 280.0);

        var program = new SfcProgram("Perceuse", List.of(vDcy, vFin, vMoteur),
                List.of(s0, s1), List.of(t1, t2), List.of(),
                List.of(new SfcLink("l1", "s0", "t1"),
                        new SfcLink("l2", "t1", "s1"),
                        new SfcLink("l3", "s1", "t2"),
                        new SfcLink("l4", "t2", "s0")));

        var simulator = new GrafcetSimulator(program);

        // 1. Cycle sans activation de dcy : aucune évolution
        var res1 = simulator.step();
        assertFalse(res1.hasEvolved());
        assertTrue(simulator.isStepActive("s0"));
        assertFalse(simulator.isOutputActive("MOTEUR"));

        // 2. Forçage de l'entrée dcy = true (Simulation du bouton poussoir)
        simulator.setInput("dcy", true);
        var res2 = simulator.step();

        assertTrue(res2.hasEvolved());
        assertEquals(List.of("t1"), res2.firedTransitionIds());
        assertFalse(simulator.isStepActive("s0"));
        assertTrue(simulator.isStepActive("s1"));

        // La sortie MOTEUR doit être allumée en direct !
        assertTrue(simulator.isOutputActive("MOTEUR"));

        // 3. Relâchement du bouton dcy : l'étape 1 reste active et le moteur tourne toujours
        simulator.setInput("dcy", false);
        var res3 = simulator.step();
        assertFalse(res3.hasEvolved());
        assertTrue(simulator.isStepActive("s1"));
        assertTrue(simulator.isOutputActive("MOTEUR"));

        // 4. Activation du capteur fin de course fin = true
        simulator.setInput("fin", true);
        var res4 = simulator.step();

        assertTrue(res4.hasEvolved());
        assertEquals(List.of("t2"), res4.firedTransitionIds());
        assertTrue(simulator.isStepActive("s0"));
        assertFalse(simulator.isStepActive("s1"));

        // La sortie MOTEUR doit s'éteindre immédiatement !
        assertFalse(simulator.isOutputActive("MOTEUR"));
    }

    @Test
    @DisplayName("Actions mémorisées Set (S) et Reset (R)")
    void testSetAndResetActions() {
        var simulator = new GrafcetSimulator();

        var actionSet = new SfcActionBlock(SfcQualifier.S, "VOYANT_CYCLE");
        var actionReset = new SfcActionBlock(SfcQualifier.R, "VOYANT_CYCLE");

        simulator.registerStep("s0", "Repos", 0, true, List.of());
        simulator.registerStep("s1", "Etape_Set", 1, false, List.of(actionSet));
        simulator.registerStep("s2", "Etape_Intermediaire", 2, false, List.of());
        simulator.registerStep("s3", "Etape_Reset", 3, false, List.of(actionReset));

        simulator.registerTransition("t1", "T1", 1, "start", Set.of("s0"), Set.of("s1"));
        simulator.registerTransition("t2", "T2", 2, "next1", Set.of("s1"), Set.of("s2"));
        simulator.registerTransition("t3", "T3", 3, "next2", Set.of("s2"), Set.of("s3"));

        simulator.reset();
        assertFalse(simulator.isOutputActive("VOYANT_CYCLE"));

        // Transition vers s1 (Set)
        simulator.setInput("start", true);
        simulator.step();
        assertTrue(simulator.isStepActive("s1"));
        assertTrue(simulator.isOutputActive("VOYANT_CYCLE"));

        // Transition vers s2 (Étape sans action directe sur VOYANT_CYCLE)
        // La sortie doit rester allumée car elle a été mémorisée par S !
        simulator.setInput("next1", true);
        simulator.step();
        assertTrue(simulator.isStepActive("s2"));
        assertTrue(simulator.isOutputActive("VOYANT_CYCLE"));

        // Transition vers s3 (Reset)
        // La sortie doit s'éteindre sur front d'activation de s3
        simulator.setInput("next2", true);
        simulator.step();
        assertTrue(simulator.isStepActive("s3"));
        assertFalse(simulator.isOutputActive("VOYANT_CYCLE"));
    }

    @Test
    @DisplayName("Temporisateurs normalisés CEI 60848 (t/Xi/d) et ST (X1.t >= T#d)")
    void testTimerConditions() {
        var simulator = new GrafcetSimulator();

        simulator.registerStep("s0", "Init", 0, true, List.of());
        simulator.registerStep("s1", "Chauffe", 1, false, List.of(new SfcActionBlock(SfcQualifier.N, "RESISTANCE")));
        simulator.registerStep("s2", "Refroidissement", 2, false, List.of());

        // Condition temporisée : t/X1/2s
        simulator.registerTransition("t1", "T1", 1, "dcy", Set.of("s0"), Set.of("s1"));
        simulator.registerTransition("t2", "T2", 2, "t/X1/2s", Set.of("s1"), Set.of("s2"));

        long t0 = 10000L;
        simulator.reset();

        // 1. Activation de l'étape 1 à t0
        simulator.setInput("dcy", true);
        simulator.step(t0);
        assertTrue(simulator.isStepActive("s1"));
        assertTrue(simulator.isOutputActive("RESISTANCE"));

        // 2. À t0 + 1000ms (1 seconde écoulée sur 2s requises) : transition non franchissable
        var res1s = simulator.step(t0 + 1000L);
        assertFalse(res1s.hasEvolved());
        assertTrue(simulator.isStepActive("s1"));
        assertTrue(simulator.isOutputActive("RESISTANCE"));

        // 3. À t0 + 2000ms (2 secondes atteintes) : transition franchie automatiquement !
        var res2s = simulator.step(t0 + 2000L);
        assertTrue(res2s.hasEvolved());
        assertEquals(List.of("t2"), res2s.firedTransitionIds());
        assertFalse(simulator.isStepActive("s1"));
        assertTrue(simulator.isStepActive("s2"));
        assertFalse(simulator.isOutputActive("RESISTANCE"));
    }

    @Test
    @DisplayName("Compteurs industriels : initialisation, incrément et réceptivité comparative")
    void testCounterOperationsAndComparisons() {
        var simulator = new GrafcetSimulator();

        // s0 : Init compteur C1 := 0
        var actInit = new SfcActionBlock(SfcQualifier.P, "C1 := 0");
        simulator.registerStep("s0", "Init", 0, true, List.of(actInit));

        // s1 : Incrément C1 := C1 + 1
        var actInc = new SfcActionBlock(SfcQualifier.P, "C1 := C1 + 1");
        simulator.registerStep("s1", "Comptage", 1, false, List.of(actInc));

        // s2 : Fin de lot
        simulator.registerStep("s2", "FinLot", 2, false, List.of());

        // Transitions :
        // T1 : dcy (s0 -> s1)
        // T2_boucle : C1 < 3 (s1 -> s1 via reprise)
        // T2_fin : C1 >= 3 (s1 -> s2)
        simulator.registerTransition("t1", "T1", 1, "dcy", Set.of("s0"), Set.of("s1"));
        simulator.registerTransition("t_boucle", "TBoucle", 2, "C1 < 3", Set.of("s1"), Set.of("s1"));
        simulator.registerTransition("t_fin", "TFin", 3, "C1 >= 3", Set.of("s1"), Set.of("s2"));

        simulator.reset();
        assertEquals(0, simulator.getState().getCounterValue("C1"));

        // Départ de cycle
        simulator.setInput("dcy", true);
        simulator.step();
        assertTrue(simulator.isStepActive("s1"));
        assertEquals(1, simulator.getState().getCounterValue("C1"));

        // Boucle 1 : C1 passe à 2
        simulator.step();
        assertTrue(simulator.isStepActive("s1"));
        assertEquals(2, simulator.getState().getCounterValue("C1"));

        // Boucle 2 : C1 passe à 3
        simulator.step();
        assertTrue(simulator.isStepActive("s1"));
        assertEquals(3, simulator.getState().getCounterValue("C1"));

        // Fin de lot : C1 >= 3 est vraie, transition t_fin franchie vers s2
        var resFin = simulator.step();
        assertTrue(resFin.hasEvolved());
        assertEquals(List.of("t_fin"), resFin.firedTransitionIds());
        assertTrue(simulator.isStepActive("s2"));
        assertFalse(simulator.isStepActive("s1"));
    }

    @Test
    @DisplayName("Synchronisation et Parallélisme en ET (Divergence & Convergence)")
    void testParallelBranchesAnd() {
        var simulator = new GrafcetSimulator();

        simulator.registerStep("s0", "Init", 0, true, List.of());
        simulator.registerStep("s1", "Branche1", 1, false, List.of());
        simulator.registerStep("s2", "Branche2", 2, false, List.of());
        simulator.registerStep("s3", "AttenteSynchro", 3, false, List.of());

        // T1 diverge vers s1 ET s2
        simulator.registerTransition("t1", "T1", 1, "start", Set.of("s0"), Set.of("s1", "s2"));

        // T2 converge depuis s1 ET s2 vers s3
        simulator.registerTransition("t2", "T2", 2, "synchro", Set.of("s1", "s2"), Set.of("s3"));

        simulator.reset();

        // 1. Franchissement de la divergence ET
        simulator.setInput("start", true);
        simulator.step();

        // Les deux branches doivent être actives simultanément
        assertTrue(simulator.isStepActive("s1"));
        assertTrue(simulator.isStepActive("s2"));
        assertFalse(simulator.isStepActive("s0"));

        // 2. Convergence ET : tant que s1 ou s2 manque, impossible de franchir
        simulator.setInput("synchro", true);
        // Si on désactive manuellement s2 pour simuler un retard
        simulator.getState().setStepActive("s2", false, 0);
        var resIncomplete = simulator.step();
        assertFalse(resIncomplete.hasEvolved());

        // Réactivation de s2 (les deux étapes sont actives)
        simulator.getState().setStepActive("s2", true, 0);
        var resSynchro = simulator.step();
        assertTrue(resSynchro.hasEvolved());
        assertEquals(List.of("t2"), resSynchro.firedTransitionIds());

        // s1 et s2 sont désactivées simultanément, s3 est activée
        assertFalse(simulator.isStepActive("s1"));
        assertFalse(simulator.isStepActive("s2"));
        assertTrue(simulator.isStepActive("s3"));
    }

    @Test
    @DisplayName("Évaluateur booléen complexe avec NOT (/), AND (.), OR (+) et parenthèses")
    void testBooleanReceptivityExpressions() {
        var state = new SimulationState();
        state.setBooleanVariable("a", true);
        state.setBooleanVariable("b", false);
        state.setBooleanVariable("c", true);

        // Opérateur ET (.)
        assertTrue(ReceptivityEvaluator.evaluate("a . c", state, 0, null));
        assertFalse(ReceptivityEvaluator.evaluate("a . b", state, 0, null));

        // Opérateur OU (+)
        assertTrue(ReceptivityEvaluator.evaluate("a + b", state, 0, null));
        assertFalse(ReceptivityEvaluator.evaluate("b + 0", state, 0, null));

        // Négation Grafcet (/b ou NOT b)
        assertTrue(ReceptivityEvaluator.evaluate("/b", state, 0, null));
        assertFalse(ReceptivityEvaluator.evaluate("/a", state, 0, null));
        assertTrue(ReceptivityEvaluator.evaluate("NOT b", state, 0, null));

        // Expression combinée avec parenthèses : (a + b) . /b . c
        assertTrue(ReceptivityEvaluator.evaluate("(a + b) . /b . c", state, 0, null));
    }

    @Test
    @DisplayName("Variables : Déduplication et insensibilité à la casse des entrées et sorties")
    void testVariableCaseInsensitivityAndRegistration() {
        var simulator = new GrafcetSimulator();
        simulator.registerInputVariable("e_0");
        simulator.registerInputVariable("E_0");
        simulator.registerOutputVariable("s_0");
        simulator.registerOutputVariable("S_0");

        assertEquals(1, simulator.getKnownInputVariables().size());
        assertTrue(simulator.getKnownInputVariables().contains("E_0"));

        assertEquals(1, simulator.getKnownOutputVariables().size());
        assertTrue(simulator.getKnownOutputVariables().contains("S_0"));

        simulator.setInput("e_0", true);
        assertTrue(simulator.getInput("E_0"));
        assertTrue(simulator.getInput("e_0"));
    }

    @Test
    @DisplayName("Qualificateurs impulsionnels P, P1 (activation) et P0 (désactivation)")
    void testPulseQualifiersSemantics() {
        var simulator = new GrafcetSimulator();
        simulator.registerOutputVariable("IMP_ACT");
        simulator.registerOutputVariable("IMP_DEACT");

        simulator.registerStep("s0", "Init", 0, true, List.of());
        simulator.registerStep("s1", "Work", 1, false, List.of(
                new SfcActionBlock(SfcQualifier.P, "IMP_ACT"),
                new SfcActionBlock(SfcQualifier.P0, "IMP_DEACT")
        ));
        simulator.registerStep("s2", "End", 2, false, List.of());

        simulator.registerTransition("t1", "T1", 1, "go", Set.of("s0"), Set.of("s1"));
        simulator.registerTransition("t2", "T2", 2, "done", Set.of("s1"), Set.of("s2"));

        simulator.reset();
        assertFalse(simulator.isOutputActive("IMP_ACT"));
        assertFalse(simulator.isOutputActive("IMP_DEACT"));

        // Activation de s1 : IMP_ACT doit être à 1 uniquement sur ce cycle
        simulator.setInput("go", true);
        simulator.step();
        assertTrue(simulator.isStepActive("s1"));
        assertTrue(simulator.isOutputActive("IMP_ACT"));
        assertFalse(simulator.isOutputActive("IMP_DEACT"));

        // Cycle suivant sans transition : IMP_ACT doit retomber à 0 même si s1 reste active
        simulator.step();
        assertTrue(simulator.isStepActive("s1"));
        assertFalse(simulator.isOutputActive("IMP_ACT"));

        // Désactivation de s1 : IMP_DEACT (P0) doit être à 1 uniquement sur ce cycle
        simulator.setInput("done", true);
        simulator.step();
        assertTrue(simulator.isStepActive("s2"));
        assertTrue(simulator.isOutputActive("IMP_DEACT"));

        simulator.step();
        assertFalse(simulator.isOutputActive("IMP_DEACT"));
    }

    @Test
    @DisplayName("Réceptivités : Adresses matérielles %I0.0 et temporisateurs ST imbriqués")
    void testHardwareAddressAndEmbeddedStTimerReceptivity() {
        var state = new SimulationState();
        state.setBooleanVariable("%I0.0", true);
        state.setBooleanVariable("%I0.1", false);
        state.setStepActive("s1", true, 1000L);

        assertTrue(ReceptivityEvaluator.evaluate("%I0.0", state, 1000L, null));
        assertFalse(ReceptivityEvaluator.evaluate("%I0.0.%I0.1", state, 1000L, null));
        assertTrue(ReceptivityEvaluator.evaluate("%I0.0 + %I0.1", state, 1000L, null));

        // Temporisateur ST imbriqué : %I0.0 AND X1.t >= T#5s
        assertFalse(ReceptivityEvaluator.evaluate("%I0.0 AND X1.t >= T#5s", state, 3000L,
                ref -> "X1".equalsIgnoreCase(ref) ? Optional.of("s1") : Optional.empty()));
        assertTrue(ReceptivityEvaluator.evaluate("%I0.0 AND X1.t >= T#5s", state, 6000L,
                ref -> "X1".equalsIgnoreCase(ref) ? Optional.of("s1") : Optional.empty()));
    }
}
