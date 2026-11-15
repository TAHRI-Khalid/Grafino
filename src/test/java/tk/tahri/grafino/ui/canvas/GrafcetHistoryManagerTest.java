package tk.tahri.grafino.ui.canvas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tk.tahri.grafino.model.SfcProgram;
import tk.tahri.grafino.model.SfcStep;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests du gestionnaire d'historique Annuler / Rétablir (Undo / Redo)")
class GrafcetHistoryManagerTest {

    private static SfcProgram programWithSteps(int count) {
        List<SfcStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(new SfcStep("s" + i, "Step" + i, i, i == 0, 100.0, 60.0 + i * 80.0, List.of()));
        }
        return new SfcProgram("Test", List.of(), steps, List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("Cycle nominal Undo / Redo et effacement du Redo après nouvelle modification")
    void testUndoRedoAndBranchClear() {
        GrafcetHistoryManager history = new GrafcetHistoryManager();
        AtomicInteger notifications = new AtomicInteger();
        history.setOnHistoryChanged(notifications::incrementAndGet);

        SfcProgram s1 = programWithSteps(1);
        SfcProgram s2 = programWithSteps(2);
        SfcProgram s3 = programWithSteps(3);

        history.recordSnapshot(s1);
        history.recordSnapshot(s2);
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(2, history.getUndoCount());

        SfcProgram afterUndo1 = history.undo(s3).orElseThrow();
        assertEquals(2, afterUndo1.steps().size());
        assertTrue(history.canRedo());

        SfcProgram afterRedo = history.redo(afterUndo1).orElseThrow();
        assertEquals(3, afterRedo.steps().size());
        assertFalse(history.canRedo());

        // Undo puis nouvelle modification -> la pile Redo doit être vidée
        history.undo(s3);
        assertTrue(history.canRedo());
        history.recordSnapshot(s2);
        assertFalse(history.canRedo());
        assertTrue(notifications.get() >= 5);
    }

    @Test
    @DisplayName("Borne maximale de l'historique, déduplication et clear()")
    void testMaxHistoryBoundDeduplicationAndClear() {
        GrafcetHistoryManager history = new GrafcetHistoryManager(2);

        SfcProgram s1 = programWithSteps(1);
        SfcProgram s2 = programWithSteps(2);
        SfcProgram s3 = programWithSteps(3);

        // Doublon consécutif ignoré
        history.recordSnapshot(s1);
        history.recordSnapshot(s1);
        assertEquals(1, history.getUndoCount());

        history.recordSnapshot(s2);
        history.recordSnapshot(s3);
        assertEquals(2, history.getUndoCount());

        // s1 a été évincé par la borne de 2
        assertEquals(3, history.undo(programWithSteps(4)).orElseThrow().steps().size());
        assertEquals(2, history.undo(s3).orElseThrow().steps().size());
        assertTrue(history.undo(s2).isEmpty());

        history.clear();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }
}
