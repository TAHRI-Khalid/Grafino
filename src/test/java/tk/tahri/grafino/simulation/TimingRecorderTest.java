package tk.tahri.grafino.simulation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests unitaires du Chronogramme & TimingRecorder")
class TimingRecorderTest {

    @Test
    @DisplayName("Enregistrement d'échantillons et recherche par dichotomie")
    void testRecordAndFindSample() {
        TimingRecorder recorder = new TimingRecorder(100);
        SimulationState state = new SimulationState();

        state.setStepActive("s0", true, 1000L);
        state.setBooleanVariable("dcy", true);
        state.setBooleanVariable("ROTATION", false);
        state.setCounterValue("C1", 0);

        SimulationStepResult stepResult = new SimulationStepResult(
                true,
                List.of("t1"),
                Set.of("s1"),
                Set.of("s0"),
                Set.of("ROTATION"),
                1L
        );

        recorder.recordTick(
                1000L,
                state,
                stepResult,
                List.of("X0", "X1"),
                List.of("dcy"),
                List.of("ROTATION"),
                List.of("C1")
        );

        assertEquals(1, recorder.getSampleCount());
        assertEquals(0L, recorder.getTotalDurationMs());

        // Deuxième tick après 200 ms
        state.setStepActive("s0", false, 1200L);
        state.setStepActive("s1", true, 1200L);
        state.setBooleanVariable("ROTATION", true);
        state.setCounterValue("C1", 1);

        recorder.recordTick(
                1200L,
                state,
                SimulationStepResult.noEvolution(2L, Set.of("ROTATION")),
                List.of("X0", "X1"),
                List.of("dcy"),
                List.of("ROTATION"),
                List.of("C1")
        );

        assertEquals(2, recorder.getSampleCount());
        assertEquals(200L, recorder.getTotalDurationMs());

        // Recherche de l'échantillon à t = 0 ms
        Optional<TimingSample> sample0 = recorder.findSampleAt(0L);
        assertTrue(sample0.isPresent());
        assertTrue(sample0.get().getStepState("X0"));
        assertFalse(sample0.get().getStepState("X1"));
        assertTrue(sample0.get().getInputState("dcy"));
        assertEquals(1, sample0.get().firedTransitions().size());
        assertEquals("t1", sample0.get().firedTransitions().getFirst());

        // Recherche de l'échantillon à t = 200 ms
        Optional<TimingSample> sample200 = recorder.findSampleAt(200L);
        assertTrue(sample200.isPresent());
        assertFalse(sample200.get().getStepState("X0"));
        assertTrue(sample200.get().getStepState("X1"));
        assertEquals(1, sample200.get().getCounterValue("C1"));

        // Recherche approchée (ex: t = 180 ms -> doit renvoyer celui à 200 ms)
        Optional<TimingSample> approx = recorder.findSampleAt(180L);
        assertTrue(approx.isPresent());
        assertEquals(200L, approx.get().timestampMs());
    }

    @Test
    @DisplayName("Gestion de la capacité maximale du buffer circulaire")
    void testBufferCapacityLimit() {
        TimingRecorder recorder = new TimingRecorder(3);
        SimulationState state = new SimulationState();

        for (int i = 0; i < 5; i++) {
            recorder.recordTick(
                    1000L + (i * 100),
                    state,
                    SimulationStepResult.noEvolution(i, Set.of()),
                    List.of("X0"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        // Le buffer ne doit contenir que 3 échantillons
        assertEquals(3, recorder.getSampleCount());
        // Le premier échantillon doit être celui de t = 200 ms (car 0 et 100 ont été évincés)
        assertEquals(200L, recorder.getSamples().getFirst().timestampMs());
        assertEquals(400L, recorder.getSamples().getLast().timestampMs());
    }

    @Test
    @DisplayName("Exportation CSV formelle avec en-têtes et données horodatées")
    void testExportCsv() {
        TimingRecorder recorder = new TimingRecorder(10);
        SimulationState state = new SimulationState();
        state.setStepActive("s0", true, 0L);
        state.setBooleanVariable("bp_start", true);
        state.setCounterValue("C1", 42);

        recorder.recordTick(
                1000L,
                state,
                SimulationStepResult.noEvolution(1L, Set.of()),
                List.of("X0"),
                List.of("bp_start"),
                List.of("KM1"),
                List.of("C1")
        );

        String csv = recorder.exportToCsv();
        assertNotNull(csv);
        assertTrue(csv.contains("Time_ms;Time_s"));
        assertTrue(csv.contains("Step_X0"));
        assertTrue(csv.contains("Input_bp_start"));
        assertTrue(csv.contains("Output_KM1"));
        assertTrue(csv.contains("Counter_C1"));
        assertTrue(csv.contains("0;0.000;1;1;0;42;"));
    }

    @Test
    @DisplayName("Réinitialisation et activation du mode enregistrement")
    void testResetAndToggleRecording() {
        TimingRecorder recorder = new TimingRecorder();
        SimulationState state = new SimulationState();

        recorder.recordTick(1000L, state, null, List.of("X0"), List.of(), List.of(), List.of());
        assertEquals(1, recorder.getSampleCount());

        recorder.setRecording(false);
        recorder.recordTick(2000L, state, null, List.of("X0"), List.of(), List.of(), List.of());
        // N'a pas dû être enregistré
        assertEquals(1, recorder.getSampleCount());

        recorder.clear();
        assertEquals(0, recorder.getSampleCount());
        assertEquals(0L, recorder.getTotalDurationMs());
    }

    @Test
    @DisplayName("Fenêtrage temporel rapide (getSamplesInWindow) et monotonie après resetStartTime")
    void testWindowedQueryAndResetStartTime() {
        TimingRecorder recorder = new TimingRecorder(5000);
        SimulationState state = new SimulationState();
        state.setStepActive("s0", true, 0L);

        // Simuler 2000 ticks de 100 ms (200 secondes)
        for (int i = 0; i < 2000; i++) {
            recorder.recordTick(
                    10_000L + (i * 100L),
                    state,
                    SimulationStepResult.noEvolution(i, Set.of()),
                    List.of("X0"),
                    List.of("dcy"),
                    List.of("KM1"),
                    List.of()
            );
        }

        assertEquals(2000, recorder.getSampleCount());
        // Extraire uniquement la fenêtre [50s, 60s] (50_000 ms à 60_000 ms)
        List<TimingSample> window = recorder.getSamplesInWindow(50_000L, 60_000L);
        assertFalse(window.isEmpty());
        assertTrue(window.size() <= 105, "Seuls les échantillons de la fenêtre visible doivent être extraits");
        assertTrue(window.getFirst().timestampMs() <= 50_000L);
        assertTrue(window.getLast().timestampMs() >= 60_000L);

        // Vérifier que resetStartTime vide bien les anciens échantillons pour garantir la monotonie temporelle
        recorder.resetStartTime();
        assertEquals(0, recorder.getSampleCount());
        recorder.recordTick(500_000L, state, null, List.of("X0"), List.of(), List.of(), List.of());
        assertEquals(1, recorder.getSampleCount());
        assertEquals(0L, recorder.getTotalDurationMs());
    }
}
