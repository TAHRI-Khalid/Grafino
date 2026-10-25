package tk.tahri.grafino.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tk.tahri.grafino.model.*;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests de persistance JSON SFC CEI 61131-3 (.sfc / .tk)")
class SfcStorageTest {

    @Test
    @DisplayName("Cycle complet de sauvegarde et chargement avec types IEC et qualificateurs")
    void testSaveAndLoadFullProgram() throws IOException {
        // 1. Variables avec types et portées IEC
        var vDcy = new IecVariable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "FALSE", "Bouton Départ Cycle");
        var vVitesse = new IecVariable("vitesse_cons", IecDataType.INT, IecVariableScope.VAR_INPUT, "%IW2", "1500", "Vitesse broche");
        var vMoteur = new IecVariable("ROTATION_BROCHE", IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, "%Q0.0", "FALSE", "Moteur principal");
        var vChauffe = new IecVariable("CHAUFFE", IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, "%Q0.1", "FALSE", "Résistance chauffante");
        var vTemp = new IecVariable("t_cuisson", IecDataType.TIME, IecVariableScope.VAR, "", "T#10s", "Temporisation cuisson");

        // 2. Étapes avec actions continues et temporisées
        var s0 = new SfcStep("s0", "Step0", 0, true, 400.0, 60.0);

        var a1 = new SfcActionBlock(SfcQualifier.N, "ROTATION_BROCHE");
        var a2 = new SfcActionBlock(SfcQualifier.D, "CHAUFFE", Duration.ofSeconds(5), "dcy");
        var a3 = new SfcActionBlock(SfcQualifier.S, "MEM_EN_COURS");
        var s1 = new SfcStep("s1", "Step1", 1, false, 400.0, 200.0, List.of(a1, a2, a3));

        // 3. Transitions avec condition en Structured Text
        var t1 = new SfcTransition("t1", "T1", 1, "dcy AND vitesse_cons > 1000", 400.0, 130.0);
        var t2 = new SfcTransition("t2", "T2", 2, "NOT dcy", 400.0, 290.0);

        // 4. Liaisons et boucle de retour avec coordonnées Manhattan
        var l1 = new SfcLink("l1", "s0", "t1");
        var l2 = new SfcLink("l2", "t1", "s1");
        var l3 = new SfcLink("l3", "s1", "t2");

        var loopback = new SfcLink("l_loop", "t2", "s0", List.of(
                new SfcPoint(400.0, 310.0),
                new SfcPoint(320.0, 310.0),
                new SfcPoint(320.0, 60.0),
                new SfcPoint(400.0, 60.0)
        ));

        var originalProgram = new SfcProgram(
                "Poste_Automatique_Perçage",
                List.of(vDcy, vVitesse, vMoteur, vChauffe, vTemp),
                List.of(s0, s1),
                List.of(t1, t2),
                List.of(),
                List.of(l1, l2, l3, loopback)
        );

        // 5. Test Sérialisation JSON
        String json = SfcStorage.toJson(originalProgram);
        assertNotNull(json);
        assertTrue(json.contains("Poste_Automatique_Perçage"));
        assertTrue(json.contains("VAR_INPUT"));
        assertTrue(json.contains("ROTATION_BROCHE"));
        assertTrue(json.contains("T#5s"));

        // 6. Test Sauvegarde et Rechargement sur fichier temporaire
        File tempFile = File.createTempFile("test_sfc_", SfcStorage.FILE_EXTENSION_SFC);
        try {
            SfcStorage.save(originalProgram, tempFile);
            assertTrue(tempFile.exists());
            assertTrue(tempFile.length() > 0);

            SfcProgram loaded = SfcStorage.load(tempFile);
            assertNotNull(loaded);

            // Vérifications d'intégrité
            assertEquals("Poste_Automatique_Perçage", loaded.programName());
            assertEquals(5, loaded.variables().size());
            assertEquals(2, loaded.steps().size());
            assertEquals(2, loaded.transitions().size());
            assertEquals(4, loaded.links().size());

            // Variables
            var loadedVitesse = loaded.findVariableByName("vitesse_cons").orElseThrow();
            assertEquals(IecDataType.INT, loadedVitesse.type());
            assertEquals(IecVariableScope.VAR_INPUT, loadedVitesse.scope());
            assertEquals("%IW2", loadedVitesse.hardwareAddress());

            // Étape initiale
            var loadedS0 = loaded.findStepById("s0").orElseThrow();
            assertTrue(loadedS0.isInitial());

            // Étape avec actions multiples et temporisées
            var loadedS1 = loaded.findStepById("s1").orElseThrow();
            assertFalse(loadedS1.isInitial());
            assertEquals(3, loadedS1.actionBlocks().size());

            var actionChauffe = loadedS1.actionBlocks().get(1);
            assertEquals(SfcQualifier.D, actionChauffe.qualifier());
            assertEquals("CHAUFFE", actionChauffe.variableName());
            assertNotNull(actionChauffe.duration());
            assertEquals(5000, actionChauffe.duration().toMillis());
            assertEquals("dcy", actionChauffe.inlineCondition());

            // Transition ST
            var loadedT1 = loaded.findTransitionById("t1").orElseThrow();
            assertEquals("dcy AND vitesse_cons > 1000", loadedT1.conditionST());

            // Boucle de retour Manhattan
            var loadedLoop = loaded.links().stream().filter(l -> l.id().equals("l_loop")).findFirst().orElseThrow();
            assertEquals(4, loadedLoop.intermediatePoints().size());
            assertEquals(320.0, loadedLoop.intermediatePoints().get(1).x());

        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Test
    @DisplayName("Rétrocompatibilité avec les anciens fichiers .tk au format Grafcet")
    void testLegacyFormatCompatibility() {
        String legacyJson = """
                {
                  "name": "Ancien_Grafcet",
                  "variables": [
                    {
                      "name": "m",
                      "type": "BOOL",
                      "direction": "INPUT",
                      "address": "%I0.0",
                      "comment": "Marche"
                    }
                  ],
                  "steps": [
                    {
                      "id": "s0",
                      "number": 0,
                      "initial": true,
                      "x": 400.0,
                      "y": 60.0,
                      "actions": []
                    },
                    {
                      "id": "s1",
                      "number": 1,
                      "initial": false,
                      "x": 400.0,
                      "y": 200.0,
                      "actions": [
                        {
                          "qualifier": "N",
                          "variableName": "KM1"
                        }
                      ]
                    }
                  ],
                  "transitions": [
                    {
                      "id": "t1",
                      "number": 1,
                      "receptivity": "m",
                      "x": 400.0,
                      "y": 130.0
                    }
                  ],
                  "links": [
                    {
                      "id": "l1",
                      "sourceId": "s0",
                      "targetId": "t1",
                      "intermediatePoints": []
                    }
                  ]
                }
                """;

        SfcProgram program = SfcStorage.fromJson(legacyJson);

        assertNotNull(program);
        assertEquals("Ancien_Grafcet", program.programName());
        assertEquals(1, program.variables().size());
        assertEquals(IecVariableScope.VAR_INPUT, program.variables().get(0).scope());

        assertEquals(2, program.steps().size());
        assertTrue(program.steps().get(0).isInitial());
        assertEquals(1, program.steps().get(1).actionBlocks().size());
        assertEquals("KM1", program.steps().get(1).actionBlocks().get(0).variableName());

        assertEquals(1, program.transitions().size());
        assertEquals("m", program.transitions().get(0).conditionST());
    }
}
