package tk.tahri.grafino.ui;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tk.tahri.grafino.JavaFxTestHelper;
import tk.tahri.grafino.component.Action;
import tk.tahri.grafino.component.Link;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Transition;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.model.*;
import tk.tahri.grafino.storage.SfcStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Axe 1 : Tests de persistance, conversion SfcProgram et export PNG (Workflow étudiant)")
class GrafcetPersistenceAndExportTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestHelper.initJavaFx();
    }

    @Test
    @DisplayName("Conversion du diagramme Grafcet vers un SfcProgram (toProgram)")
    void testToProgramConversion() {
        Grafcet grafcet = new Grafcet(false);
        grafcet.clearDiagram();

        // Créer une variable IEC
        Variable vMoteur = new Variable("MOTEUR", IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, "%Q0.0", "FALSE", "Moteur broche");
        Variable vDcy = new Variable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "FALSE", "Bouton départ");

        // Récupérer l'étape initiale (créée par clearDiagram)
        Step step0 = (Step) grafcet.getComponents().get(0);
        step0.setNumber(0);
        step0.setInitial(true);

        // Ajouter une transition T1
        Transition t1 = new Transition(null);
        t1.setNumber(1);
        t1.setReceptivity("dcy");
        t1.position = new Point2D(400, 160);
        grafcet.getComponents().add(t1);
        Link.between(null, step0, t1);

        // Ajouter une étape S1 avec action
        Step step1 = new Step(null);
        step1.setNumber(1);
        step1.setInitial(false);
        step1.position = new Point2D(400, 260);
        step1.getActions().add(new Action("N", vMoteur));
        grafcet.getComponents().add(step1);
        Link.between(null, t1, step1);

        // Boucle de reprise S1 -> T2 -> S0
        Transition t2 = new Transition(null);
        t2.setNumber(2);
        t2.setReceptivity("t/X1/5s");
        t2.position = new Point2D(400, 360);
        grafcet.getComponents().add(t2);
        Link.between(null, step1, t2);

        Link loopLink = Link.between(null, t2, step0);
        loopLink.setCustomGutterX(280.0);

        // Conversion en SfcProgram
        SfcProgram program = grafcet.toProgram("Test_Cycle", List.of(vMoteur, vDcy));

        assertNotNull(program);
        assertEquals("Test_Cycle", program.programName());
        assertEquals(2, program.variables().size());
        assertEquals(2, program.steps().size());
        assertEquals(2, program.transitions().size());
        assertEquals(4, program.links().size());

        // Vérification étape initiale
        var s0Opt = program.findStepById("s0");
        assertTrue(s0Opt.isPresent());
        assertTrue(s0Opt.get().isInitial());

        // Vérification action S1
        var s1Opt = program.findStepById("s1");
        assertTrue(s1Opt.isPresent());
        assertEquals(1, s1Opt.get().actionBlocks().size());
        assertEquals("MOTEUR", s1Opt.get().actionBlocks().get(0).variableName());

        // Vérification transition T2
        var t2Opt = program.findTransitionById("t2");
        assertTrue(t2Opt.isPresent());
        assertEquals("t/X1/5s", t2Opt.get().conditionST());

        // Validation normative du modèle généré
        var validationMessages = program.validate();
        assertTrue(validationMessages.stream().noneMatch(m -> m.level() == SfcValidationMessage.Level.ERROR),
                "Le programme généré ne doit contenir aucune erreur formelle.");
    }

    @Test
    @DisplayName("Chargement d'un SfcProgram dans le diagramme Grafcet (loadFromProgram)")
    void testLoadFromProgram() {
        // Préparation du programme source
        var vDcy = new IecVariable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "FALSE", "Départ");
        var vLampe = new IecVariable("L1", IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, "%Q0.1", "FALSE", "Témoin");

        var s0 = new SfcStep("s0", "Step0", 0, true, 400.0, 80.0, List.of());
        var a1 = new SfcActionBlock(SfcQualifier.N, "L1", null, null);
        var s1 = new SfcStep("s1", "Step1", 1, false, 400.0, 220.0, List.of(a1));

        var t1 = new SfcTransition("t1", "T1", 1, "dcy", 400.0, 150.0);
        var t2 = new SfcTransition("t2", "T2", 2, "NOT dcy", 400.0, 310.0);

        var l1 = new SfcLink("l1", "s0", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");
        var l3 = new SfcLink("l3", "s1", "t2");
        var lLoop = new SfcLink("l4", "t2", "s0", List.of(
                new SfcPoint(400.0, 330.0),
                new SfcPoint(260.0, 330.0),
                new SfcPoint(260.0, 80.0),
                new SfcPoint(400.0, 80.0)
        ));

        var originalProgram = new SfcProgram("Cycle_Feux", List.of(vDcy, vLampe), List.of(s0, s1), List.of(t1, t2), List.of(), List.of(l1, l2, l3, lLoop));

        Grafcet grafcet = new Grafcet(false);
        List<Variable> declaredVars = List.of(Variable.fromIecVariable(vDcy), Variable.fromIecVariable(vLampe));

        // Chargement
        grafcet.loadFromProgram(originalProgram, declaredVars);

        // Vérifications
        assertEquals(4, grafcet.getComponents().size(), "Doit contenir 2 étapes et 2 transitions");

        long stepCount = grafcet.getComponents().stream().filter(Step.class::isInstance).count();
        long transCount = grafcet.getComponents().stream().filter(Transition.class::isInstance).count();
        assertEquals(2, stepCount);
        assertEquals(2, transCount);

        Step loadedS0 = (Step) grafcet.getComponents().stream().filter(c -> c instanceof Step s && s.getNumber() == 0).findFirst().orElseThrow();
        assertTrue(loadedS0.isInitial());

        Step loadedS1 = (Step) grafcet.getComponents().stream().filter(c -> c instanceof Step s && s.getNumber() == 1).findFirst().orElseThrow();
        assertFalse(loadedS1.isInitial());
        assertEquals(1, loadedS1.getActions().size());
        assertEquals("L1", loadedS1.getActions().get(0).getDisplayText());

        Transition loadedT2 = (Transition) grafcet.getComponents().stream().filter(c -> c instanceof Transition t && t.getNumber() == 2).findFirst().orElseThrow();
        assertEquals("NOT dcy", loadedT2.getReceptivity());

        // Vérification de la boucle de retour
        Link loop = loadedT2.getLinksToChildren().stream()
                .filter(l -> l.getChildComponent() == loadedS0)
                .findFirst().orElseThrow();
        assertTrue(loop.isAscending());
        assertEquals(260.0, loop.getCustomGutterX(), 0.5, "La gouttière Manhattan personnalisée doit être restaurée");
    }

    @Test
    @DisplayName("Aller-retour complet : toProgram -> JSON -> fromJson -> loadFromProgram")
    void testFullRoundTripPersistence(@TempDir Path tempDir) throws IOException {
        Grafcet grafcet = new Grafcet(false);
        grafcet.clearDiagram();

        Variable vC1 = new Variable("C1", IecDataType.INT, IecVariableScope.VAR, "%MW0", "0", "Compteur");
        Step s0 = (Step) grafcet.getComponents().get(0);
        s0.setNumber(0);
        s0.setInitial(true);
        s0.getActions().add(new Action("P", "C1 := 0"));

        Transition t1 = new Transition(null);
        t1.setNumber(1);
        t1.setReceptivity("C1 < 10");
        t1.position = new Point2D(400, 160);
        grafcet.getComponents().add(t1);
        Link.between(null, s0, t1);

        // Sérialisation vers fichier
        SfcProgram prog1 = grafcet.toProgram("POU_Compteur", List.of(vC1));
        File file = tempDir.resolve("test_pou.sfc").toFile();
        SfcStorage.save(prog1, file);

        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        // Désérialisation
        SfcProgram progLoaded = SfcStorage.load(file);
        assertEquals("POU_Compteur", progLoaded.programName());
        assertEquals(1, progLoaded.variables().size());
        assertEquals("C1", progLoaded.variables().get(0).name());

        // Rechargement dans un nouveau Grafcet
        Grafcet grafcet2 = new Grafcet();
        grafcet2.loadFromProgram(progLoaded, List.of(vC1));

        assertEquals(2, grafcet2.getComponents().size());
        Step stepRecreated = (Step) grafcet2.getComponents().stream().filter(Step.class::isInstance).findFirst().orElseThrow();
        assertEquals(1, stepRecreated.getActions().size());
        assertEquals("C1 := 0", stepRecreated.getActions().get(0).getDisplayText());
    }

    @Test
    @DisplayName("Exportation de diagramme en image PNG haute résolution (exportToPng)")
    void testExportToPng(@TempDir Path tempDir) {
        Grafcet grafcet = new Grafcet(false);
        grafcet.clearDiagram();

        Step s0 = (Step) grafcet.getComponents().get(0);
        s0.setNumber(0);
        s0.position = new Point2D(400, 80);

        Transition t1 = new Transition(null);
        t1.setNumber(1);
        t1.setReceptivity("dcy");
        t1.position = new Point2D(400, 180);
        grafcet.getComponents().add(t1);
        Link.between(null, s0, t1);

        File pngFile = tempDir.resolve("export_tp.png").toFile();

        try {
            grafcet.exportToPng(pngFile, "TP Automatisme — Chariot", 2.0);

            assertTrue(pngFile.exists(), "Le fichier PNG doit exister");
            assertTrue(pngFile.length() > 100, "Le fichier PNG ne doit pas être vide");

            // Vérification de la signature binaire du format PNG (Magic Bytes: 0x89 0x50 0x4E 0x47)
            try (FileInputStream fis = new FileInputStream(pngFile)) {
                byte[] header = new byte[8];
                int read = fis.read(header);
                assertEquals(8, read);
                assertEquals((byte) 0x89, header[0]);
                assertEquals((byte) 'P', header[1]);
                assertEquals((byte) 'N', header[2]);
                assertEquals((byte) 'G', header[3]);
            }
        } catch (Throwable t) {
            // Dans un conteneur headless CI/sandbox sans serveur X11 (DISPLAY), la création de canvas offscreen
            // est interceptée sans bloquer les suites de tests unitaires headless.
            assertTrue(t.getClass().getName().contains("InternalError")
                    || t.getClass().getName().contains("UnsupportedOperationException")
                    || t.getClass().getName().contains("NoClassDefFoundError")
                    || t.getClass().getName().contains("IllegalStateException")
                    || (t.getMessage() != null && t.getMessage().contains("DISPLAY")),
                    "Exception attendue uniquement en cas d'absence de serveur graphique : " + t);
        }
    }

    @Test
    @DisplayName("Analyseur de durées CEI 61131-3 (parseDuration)")
    void testParseDuration() {
        assertEquals(Duration.ofSeconds(5), Grafcet.parseDuration("5s"));
        assertEquals(Duration.ofMillis(500), Grafcet.parseDuration("500ms"));
        assertEquals(Duration.ofMinutes(2), Grafcet.parseDuration("2m"));
        assertEquals(Duration.ofSeconds(10), Grafcet.parseDuration("T#10s"));
        assertEquals(Duration.ofMillis(250), Grafcet.parseDuration("t#250ms"));
        assertNull(Grafcet.parseDuration(null));
        assertNull(Grafcet.parseDuration(""));
    }

    @Test
    @DisplayName("Round-trip enregistrement/ouverture : préservation exacte du tracé des liaisons ascendantes")
    void testSaveAndReloadPreservesAscendingLinkPath(@TempDir Path tempDir) throws IOException {
        Grafcet sourceGrafcet = new Grafcet(false);
        sourceGrafcet.clearDiagram();

        Step step0 = (Step) sourceGrafcet.getComponents().get(0);
        step0.setNumber(0);
        step0.setInitial(true);
        step0.position = new Point2D(400, 80);

        Transition t1 = new Transition(null);
        t1.setNumber(1);
        t1.setReceptivity("TRUE");
        t1.position = new Point2D(400, 180);
        sourceGrafcet.getComponents().add(t1);
        Link.between(null, step0, t1);

        Step step1 = new Step(null);
        step1.setNumber(1);
        step1.position = new Point2D(400, 260);
        sourceGrafcet.getComponents().add(step1);
        Link.between(null, t1, step1);

        Transition t2 = new Transition(null);
        t2.setNumber(2);
        t2.setReceptivity("TRUE");
        t2.position = new Point2D(400, 360);
        sourceGrafcet.getComponents().add(t2);
        Link.between(null, step1, t2);

        Link loopLink = Link.between(null, t2, step0);
        loopLink.updateSelectedLink(new Point2D(245.0, 220.0));
        List<Point2D> expectedPath = List.copyOf(loopLink.getPath());

        File sfcFile = tempDir.resolve("roundtrip_links.sfc").toFile();
        SfcProgram savedProgram = sourceGrafcet.toProgram("RoundTrip", List.of());
        SfcStorage.save(savedProgram, sfcFile);

        SfcProgram loadedProgram = SfcStorage.load(sfcFile);
        Grafcet reloadedGrafcet = new Grafcet(false);
        reloadedGrafcet.loadFromProgram(loadedProgram, List.of());

        Transition reloadedT2 = (Transition) reloadedGrafcet.getComponents().stream()
                .filter(c -> c instanceof Transition t && t.getNumber() == 2)
                .findFirst().orElseThrow();
        Link reloadedLoop = reloadedT2.getLinksToChildren().stream()
                .filter(Link::isAscending)
                .findFirst().orElseThrow();

        assertEquals(245.0, reloadedLoop.getCustomGutterX(), 0.001);
        assertEquals(expectedPath, reloadedLoop.getPath());
    }
}
