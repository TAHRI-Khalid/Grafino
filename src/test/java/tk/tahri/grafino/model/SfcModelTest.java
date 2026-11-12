package tk.tahri.grafino.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests du Modèle SFC CEI 61131-3 (Java 25+)")
class SfcModelTest {

    @Test
    @DisplayName("Création d'un bloc d'action normalisé CEI 61131-3")
    void testActionBlocks() {
        // Action continue (N)
        var actionN = new SfcActionBlock(SfcQualifier.N, "MOTEUR_AVANT");
        assertEquals(SfcQualifier.N, actionN.qualifier());
        assertEquals("MOTEUR_AVANT", actionN.variableName());
        assertFalse(actionN.isTimed());

        // Action temporisée (L - Time Limited)
        var actionL = new SfcActionBlock(SfcQualifier.L, "VANNE_EV1", Duration.ofSeconds(3));
        assertTrue(actionL.isTimed());
        assertEquals("T#3s", actionL.formattedDuration());

        // Action temporisée (D - Time Delayed)
        var actionD = new SfcActionBlock(SfcQualifier.D, "CHAUFFAGE", Duration.ofMillis(750));
        assertEquals("T#750ms", actionD.formattedDuration());

        // Erreur si durée absente pour qualificateur temporisé
        assertThrows(IllegalArgumentException.class, () ->
                new SfcActionBlock(SfcQualifier.L, "TEST", null, null)
        );
    }

    @Test
    @DisplayName("Modélisation d'étape avec immutabilité garantie")
    void testSfcStep() {
        var action1 = new SfcActionBlock(SfcQualifier.N, "CYLINDRE_SORTIE");
        var action2 = new SfcActionBlock(SfcQualifier.S, "VOYANT_CYCLE");

        var step = new SfcStep("step_1", "Avance", 1, false, 400.0, 200.0, List.of(action1, action2));

        assertEquals("step_1", step.id());
        assertEquals("Avance", step.name());
        assertEquals(1, step.number());
        assertFalse(step.isInitial());
        assertTrue(step.hasActions());
        assertEquals(2, step.actionBlocks().size());

        // Test immutabilité
        assertThrows(UnsupportedOperationException.class, () ->
                step.actionBlocks().add(new SfcActionBlock(SfcQualifier.R, "TEST"))
        );

        // Test helper withPosition
        var movedStep = step.withPosition(450.0, 250.0);
        assertEquals(450.0, movedStep.x());
        assertEquals(250.0, movedStep.y());
        assertEquals(step.id(), movedStep.id());
    }

    @Test
    @DisplayName("Pattern matching exhaustif avec switch sur sealed SfcElement (Java 25+)")
    void testPatternMatchingOnSealedHierarchy() {
        SfcElement step = new SfcStep("s0", "Init", 0, true, 400.0, 100.0);
        SfcElement trans = new SfcTransition("t1", "T1", 1, "dcy AND capteur_haut", 400.0, 180.0);
        SfcElement branch = new SfcBranch("b1", "AndSync", SfcBranchType.CONVERGENCE_AND, 400.0, 240.0, List.of("s1", "s2"), List.of("t2"));

        assertEquals("Étape initiale Init", describeElement(step));
        assertEquals("Transition T1 avec réceptivité : dcy AND capteur_haut", describeElement(trans));
        assertEquals("Synchronisation parallèle ET", describeElement(branch));
    }

    private String describeElement(SfcElement element) {
        return switch (element) {
            case SfcStep s when s.isInitial() -> "Étape initiale " + s.name();
            case SfcStep s -> "Étape standard " + s.name();
            case SfcTransition t -> "Transition " + t.name() + " avec réceptivité : " + t.conditionST();
            case SfcBranch b when b.isParallel() -> "Synchronisation parallèle ET";
            case SfcBranch b -> "Aiguillage alternatif OU";
        };
    }

    @Test
    @DisplayName("Validation d'un programme SFC conforme CEI 61131-3")
    void testSfcProgramValidation() {
        var vDcy = new IecVariable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "Départ cycle");
        var vMoteur = new IecVariable("MOTEUR", IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, "%Q0.0", "Moteur principal");

        var s0 = new SfcStep("s0", "Init", 0, true, 400.0, 60.0);
        var t1 = new SfcTransition("t1", "T1", 1, "dcy", 400.0, 140.0);
        var s1 = new SfcStep("s1", "Travail", 1, false, 400.0, 220.0, List.of(new SfcActionBlock(SfcQualifier.N, "MOTEUR")));
        var t2 = new SfcTransition("t2", "T2", 2, "NOT dcy", 400.0, 300.0);

        var l1 = new SfcLink("l1", "s0", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");
        var l3 = new SfcLink("l3", "s1", "t2");
        var l4 = new SfcLink("l4", "t2", "s0");

        var program = new SfcProgram(
                "Perceuse_POU",
                List.of(vDcy, vMoteur),
                List.of(s0, s1),
                List.of(t1, t2),
                List.of(),
                List.of(l1, l2, l3, l4)
        );

        assertEquals("Perceuse_POU", program.programName());
        assertEquals(1, program.getInitialSteps().size());
        assertEquals(1, program.getVariablesByScope(IecVariableScope.VAR_INPUT).size());
        assertEquals(1, program.getVariablesByScope(IecVariableScope.VAR_OUTPUT).size());

        // Programme valide sans erreurs (cycle fermé complet)
        var errors = program.validate();
        assertTrue(errors.isEmpty(), "Le programme valide ne devrait avoir aucune erreur.");
    }

    @Test
    @DisplayName("Détection de violation d'alternance stricte et absence d'étape initiale")
    void testSfcViolationsDetection() {
        // 1. Manque d'étape initiale
        var s1 = new SfcStep("s1", "Step1", 1, false, 400.0, 100.0);
        var s2 = new SfcStep("s2", "Step2", 2, false, 400.0, 200.0);
        // Liaison directe étape -> étape (interdite en CEI 61131-3)
        var invalidLink = new SfcLink("bad_link", "s1", "s2");

        var invalidProgram = new SfcProgram("InvalidPOU", List.of(), List.of(s1, s2), List.of(), List.of(), List.of(invalidLink));

        var messages = invalidProgram.validate();
        assertEquals(2, messages.size());

        boolean hasInitialStepError = messages.stream()
                .anyMatch(m -> m.message().contains("étape initiale"));
        boolean hasAlternanceError = messages.stream()
                .anyMatch(m -> m.message().contains("deux étapes ne peuvent être reliées"));

        assertTrue(hasInitialStepError);
        assertTrue(hasAlternanceError);
    }

    @Test
    @DisplayName("Modélisation et formatage des conditions de temporisateur (CEI 60848 & CEI 61131-3)")
    void testTimerConditions() {
        // Format Grafcet générique par défaut
        var timerGrafcet = SfcTimerCondition.ofGrafcet("t", "X1", 5, "s");
        assertEquals("t", timerGrafcet.timerName());
        assertEquals("X1", timerGrafcet.stepReference());
        assertEquals("t/X1/5s", timerGrafcet.toGrafcetFormat());
        assertEquals("t/X1/5s", timerGrafcet.toFormattedString());

        // Format Grafcet avec repère numéroté et millisecondes
        var timerNumbered = SfcTimerCondition.ofGrafcet("t2", "X3", 750, "ms");
        assertEquals("t2/X3/750ms", timerNumbered.toGrafcetFormat());

        // Format Structured Text CEI 61131-3
        var timerST = SfcTimerCondition.ofStructuredText("X1", 5, "s");
        assertEquals("X1.t >= T#5s", timerST.toStructuredTextFormat());
        assertEquals("X1.t >= T#5s", timerST.toFormattedString());

        // Construction via builder statique
        assertEquals("t/X2/10s", SfcTimerCondition.buildCondition("t", "2", 10, "s", false));
        assertEquals("X2.t >= T#10s", SfcTimerCondition.buildCondition("t", "2", 10, "s", true));

        // Détection et parsing depuis une chaîne de réceptivité
        var parsed1 = SfcTimerCondition.findInExpression("t/X1/5s");
        assertTrue(parsed1.isPresent());
        assertEquals("t", parsed1.get().timerName());
        assertEquals("X1", parsed1.get().stepReference());
        assertEquals(5.0, parsed1.get().durationValue());
        assertEquals("s", parsed1.get().durationUnit());

        var parsedCombined = SfcTimerCondition.findInExpression("dcy AND t1/X2/2.5s AND capteur_fin");
        assertTrue(parsedCombined.isPresent());
        assertEquals("t1", parsedCombined.get().timerName());
        assertEquals("X2", parsedCombined.get().stepReference());
        assertEquals(2.5, parsedCombined.get().durationValue());

        var parsedST = SfcTimerCondition.findInExpression("X4.t >= T#500ms");
        assertTrue(parsedST.isPresent());
        assertEquals("X4", parsedST.get().stepReference());
        assertEquals(500.0, parsedST.get().durationValue());
        assertEquals("ms", parsedST.get().durationUnit());
    }

    @Test
    @DisplayName("Validation formelle d'une transition temporisée avec étape existante ou inconnue")
    void testTimerValidationInProgram() {
        var s0 = new SfcStep("s0", "Init", 0, true, 400.0, 60.0);
        var s1 = new SfcStep("s1", "Step1", 1, false, 400.0, 220.0);

        // Transition avec temporisation valide référençant X0 (s0) dans un cycle rebouclé
        var tValid = new SfcTransition("t1", "T1", 1, "t/X0/3s", 400.0, 140.0);
        var tLoop = new SfcTransition("t2", "T2", 2, "1", 400.0, 300.0);
        var l1 = new SfcLink("l1", "s0", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");
        var l3 = new SfcLink("l3", "s1", "t2");
        var l4 = new SfcLink("l4", "t2", "s0");

        var validProg = new SfcProgram("ProgValid", List.of(), List.of(s0, s1), List.of(tValid, tLoop), List.of(), List.of(l1, l2, l3, l4));
        assertTrue(validProg.validate().isEmpty(), "La transition temporisée valide ne doit générer aucun avertissement.");

        // Transition avec temporisation référençant une étape inexistante X99
        var tInvalid = new SfcTransition("t2", "T2", 2, "t/X99/5s", 400.0, 140.0);
        var lInvalid = new SfcLink("l3", "s0", "t2");
        var invalidProg = new SfcProgram("ProgInvalid", List.of(), List.of(s0, s1), List.of(tInvalid), List.of(), List.of(lInvalid));

        var msgs = invalidProg.validate();
        assertFalse(msgs.isEmpty());
        assertTrue(msgs.stream().anyMatch(m -> m.message().contains("référence une étape inexistante dans sa temporisation : 'X99'")));
    }

    @Test
    @DisplayName("Modélisation des opérations et conditions de compteur (CEI 61131-3)")
    void testCounterOperations() {
        // Initialisation
        assertEquals("C1 := 0", SfcCounterOperation.formatInit("C1", 0));
        assertEquals("C2 := 10", SfcCounterOperation.formatInit("C2", 10));

        // Incrémentation et Décrémentation
        assertEquals("C1 := C1 + 1", SfcCounterOperation.formatIncrement("C1", 1));
        assertEquals("C1 := C1 + 2", SfcCounterOperation.formatIncrement("C1", 2));
        assertEquals("C1 := C1 - 1", SfcCounterOperation.formatDecrement("C1", 1));

        // Test de condition dans les réceptivités
        assertEquals("C1 < 5", SfcCounterOperation.formatComparison("C1", "<", 5));
        assertEquals("C1 >= 5", SfcCounterOperation.formatComparison("C1", ">=", 5));

        // Détection d'assignation
        assertTrue(SfcCounterOperation.isAssignment("C1 := 0"));
        assertTrue(SfcCounterOperation.isAssignment("C1 := C1 + 1"));
        assertFalse(SfcCounterOperation.isAssignment("MOTEUR_AVANT"));

        assertEquals("C1", SfcCounterOperation.extractAssignedVariable("C1 := C1 + 1").orElse(""));

        // Détection de comparaisons multiples
        var comps = SfcCounterOperation.findComparisons("dcy AND (C1 < 5) OR C2 >= 10");
        assertEquals(2, comps.size());
        assertEquals("C1", comps.get(0).counterName());
        assertEquals("<", comps.get(0).operator());
        assertEquals(5, comps.get(0).threshold());
        assertEquals("C2", comps.get(1).counterName());
        assertEquals(">=", comps.get(1).operator());
        assertEquals(10, comps.get(1).threshold());
    }

    @Test
    @DisplayName("Validation formelle des compteurs et avertissements pédagogiques (qualificateur N vs P)")
    void testCounterValidationInProgram() {
        var vC1 = new IecVariable("C1", IecDataType.INT, IecVariableScope.VAR, "%MW0", "0", "Compteur cycle");
        var vBool = new IecVariable("FLAG", IecDataType.BOOL, IecVariableScope.VAR, "%M0.0", "FALSE", "Drapeau");

        // Étape 1 avec incrémentation sous qualificateur continu 'N' (faute classique d'automatisme)
        var actionBad = new SfcActionBlock(SfcQualifier.N, "C1 := C1 + 1");
        var s0 = new SfcStep("s0", "Init", 0, true, 400.0, 60.0, List.of(actionBad));

        // Transition avec test de variable booléenne avec comparateur numérique
        var t1 = new SfcTransition("t1", "T1", 1, "FLAG < 5", 400.0, 140.0);
        var s1 = new SfcStep("s1", "Step1", 1, false, 400.0, 220.0);
        var l1 = new SfcLink("l1", "s0", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");

        var prog = new SfcProgram("ProgCounter", List.of(vC1, vBool), List.of(s0, s1), List.of(t1), List.of(), List.of(l1, l2));
        var msgs = prog.validate();

        assertFalse(msgs.isEmpty());

        // Avertissement pédagogique qualificateur 'P'
        assertTrue(msgs.stream().anyMatch(m -> m.message().contains("qualificateur continu 'N'")
                && m.message().contains("qualificateur 'P' (Pulse) est recommandé")));

        // Avertissement comparaison sur type BOOL
        assertTrue(msgs.stream().anyMatch(m -> m.message().contains("est déclarée en BOOL au lieu d'un type entier")));
    }
}
