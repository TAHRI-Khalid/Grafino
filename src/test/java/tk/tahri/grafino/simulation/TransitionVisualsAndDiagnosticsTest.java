package tk.tahri.grafino.simulation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tk.tahri.grafino.JavaFxTestHelper;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Transition;
import tk.tahri.grafino.model.*;
import tk.tahri.grafino.ui.Grafcet;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Axe 2 : Tests de validation des transitions triparties et de l'inspection normative")
class TransitionVisualsAndDiagnosticsTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestHelper.initJavaFx();
    }

    @Test
    @DisplayName("Validation tripartite de franchissabilité CEI 60848 (Non validée -> Validée attente réceptivité -> Franchissable)")
    void testTransitionTripartiteEvaluation() {
        var vDcy = new IecVariable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "Départ cycle");
        var vFin = new IecVariable("fin", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.1", "Fin de cycle");

        var s0 = new SfcStep("s0", "Init", 0, true, 400.0, 50.0);
        var s1 = new SfcStep("s1", "Actif", 1, false, 400.0, 200.0);

        var t1 = new SfcTransition("t1", "T1", 1, "dcy", 400.0, 120.0);
        var t2 = new SfcTransition("t2", "T2", 2, "fin", 400.0, 280.0);

        var l1 = new SfcLink("l1", "s0", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");
        var l3 = new SfcLink("l3", "s1", "t2");
        var l4 = new SfcLink("l4", "t2", "s0");

        var program = new SfcProgram("Cycle_Test", List.of(vDcy, vFin),
                List.of(s0, s1), List.of(t1, t2), List.of(), List.of(l1, l2, l3, l4));

        var simulator = new GrafcetSimulator(program);

        // 1. État initial : dcy = false, fin = false
        // T1 est validée (étape s0 amont active), mais sa réceptivité dcy est fausse -> Validée en attente
        assertTrue(simulator.isTransitionValidated("t1"), "T1 doit être validée car s0 est active");
        assertFalse(simulator.isTransitionReceptivityTrue("t1"), "La réceptivité dcy est fausse");
        assertFalse(simulator.isTransitionFireable("t1"), "T1 ne doit pas être franchissable car dcy=0");

        // T2 n'est pas validée (étape s1 amont inactive)
        assertFalse(simulator.isTransitionValidated("t2"), "T2 ne doit pas être validée car s1 est inactive");
        assertFalse(simulator.isTransitionFireable("t2"));

        assertEquals(Set.of("t1"), simulator.getValidatedTransitions());
        assertTrue(simulator.getFireableTransitions().isEmpty());

        // 2. Activation de dcy -> T1 devient Franchissable (Validée + Réceptivité vraie)
        simulator.getState().setBooleanVariable("dcy", true);
        assertTrue(simulator.isTransitionValidated("t1"));
        assertTrue(simulator.isTransitionReceptivityTrue("t1"));
        assertTrue(simulator.isTransitionFireable("t1"), "T1 doit être franchissable (validée ET dcy=1)");
        assertEquals(Set.of("t1"), simulator.getFireableTransitions());

        // 3. Franchissement de T1 (step cyclique)
        simulator.step();
        assertFalse(simulator.isStepActive("s0"));
        assertTrue(simulator.isStepActive("s1"));

        // Maintenant T1 n'est plus validée
        assertFalse(simulator.isTransitionValidated("t1"));
        assertFalse(simulator.isTransitionFireable("t1"));

        // T2 devient validée (s1 active) mais fin=0 -> en attente
        assertTrue(simulator.isTransitionValidated("t2"));
        assertFalse(simulator.isTransitionReceptivityTrue("t2"));
        assertFalse(simulator.isTransitionFireable("t2"));

        // 4. Activation de fin -> T2 devient franchissable
        simulator.getState().setBooleanVariable("fin", true);
        assertTrue(simulator.isTransitionFireable("t2"));
        assertEquals(Set.of("t2"), simulator.getFireableTransitions());
    }

    @Test
    @DisplayName("Inspection normative (Linting) : Détection des erreurs et avertissements CEI 61131-3")
    void testNormativeLintingDiagnostics() {
        // Programme sans étape initiale
        var s1 = new SfcStep("s1", "Step1", 1, false, 400.0, 100.0);
        var t1 = new SfcTransition("t1", "T1", 1, "1", 400.0, 180.0);
        var l1 = new SfcLink("l1", "s1", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");

        var progWithoutInitial = new SfcProgram("NoInit", List.of(),
                List.of(s1), List.of(t1), List.of(), List.of(l1, l2));

        List<SfcValidationMessage> diagnostics = progWithoutInitial.validate();
        assertFalse(diagnostics.isEmpty(), "Des anomalies doivent être rapportées");

        boolean hasInitialStepError = diagnostics.stream()
                .anyMatch(m -> m.isError() && m.message().toLowerCase().contains("initiale"));
        assertTrue(hasInitialStepError, "Une erreur d'absence d'étape initiale doit être présente");

        // Programme avec compteur incrémenté en N (doit être qualifié en P pour la rigueur pédagogique)
        var vCount = new IecVariable("C1", IecDataType.INT, IecVariableScope.VAR, "%MW0", "0", "Compteur");
        var s0 = new SfcStep("s0", "Init", 0, true, 400.0, 50.0,
                List.of(new SfcActionBlock(SfcQualifier.N, "C1 := C1 + 1")));
        var progCounterWarning = new SfcProgram("CounterTest", List.of(vCount),
                List.of(s0), List.of(), List.of(), List.of());

        List<SfcValidationMessage> counterDiagnostics = progCounterWarning.validate();
        boolean hasCounterPulseWarning = counterDiagnostics.stream()
                .anyMatch(m -> m.isWarning() && m.message().contains("P"));
        assertTrue(hasCounterPulseWarning, "Un avertissement sur l'impulsion P doit être émis pour l'incrémentation d'un compteur");
    }

    @Test
    @DisplayName("Sélection d'éléments par référence textuelle sur le Canvas Grafcet")
    void testSelectComponentByReference() {
        Grafcet headlessGrafcet = new Grafcet(false);

        var s0 = new SfcStep("s0", "Step0", 0, true, 400.0, 50.0);
        var s1 = new SfcStep("s1", "Step1", 1, false, 400.0, 200.0);
        var t1 = new SfcTransition("t1", "T1", 1, "1", 400.0, 120.0);
        var prog = new SfcProgram("TestRef", List.of(), List.of(s0, s1), List.of(t1), List.of(), List.of());

        headlessGrafcet.loadFromProgram(prog, List.of());

        // Sélection par ID "s1"
        headlessGrafcet.selectComponentByReference("s1");
        assertTrue(headlessGrafcet.getSelectedComponent().isPresent());
        assertInstanceOf(Step.class, headlessGrafcet.getSelectedComponent().get());
        assertEquals(1, ((Step) headlessGrafcet.getSelectedComponent().get()).getNumber());

        // Sélection par nom de transition "T1"
        headlessGrafcet.selectComponentByReference("T1");
        assertTrue(headlessGrafcet.getSelectedComponent().isPresent());
        assertInstanceOf(Transition.class, headlessGrafcet.getSelectedComponent().get());
        assertEquals(1, ((Transition) headlessGrafcet.getSelectedComponent().get()).getNumber());

        // Sélection par ID "s0"
        headlessGrafcet.selectComponentByReference("s0");
        assertTrue(headlessGrafcet.getSelectedComponent().isPresent());
        assertInstanceOf(Step.class, headlessGrafcet.getSelectedComponent().get());
        assertEquals(0, ((Step) headlessGrafcet.getSelectedComponent().get()).getNumber());
    }
}
