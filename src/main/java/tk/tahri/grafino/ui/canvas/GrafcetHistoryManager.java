package tk.tahri.grafino.ui.canvas;

import tk.tahri.grafino.model.SfcProgram;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Gestionnaire d'historique Annuler / Rétablir (Undo / Redo) basé sur des instantanés immuables {@link SfcProgram}.
 */
public final class GrafcetHistoryManager {

    public static final int DEFAULT_MAX_HISTORY = 50;

    private final int maxHistory;
    private final Deque<SfcProgram> undoStack = new ArrayDeque<>();
    private final Deque<SfcProgram> redoStack = new ArrayDeque<>();
    private Runnable onHistoryChanged = () -> {};

    public GrafcetHistoryManager() {
        this(DEFAULT_MAX_HISTORY);
    }

    public GrafcetHistoryManager(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
    }

    public void setOnHistoryChanged(Runnable onHistoryChanged) {
        this.onHistoryChanged = onHistoryChanged != null ? onHistoryChanged : () -> {};
    }

    /**
     * Enregistre l'état précédant une modification et vide la pile Rétablir.
     */
    public void recordSnapshot(SfcProgram beforeState) {
        if (beforeState == null) {
            return;
        }
        if (!undoStack.isEmpty() && undoStack.peek().equals(beforeState)) {
            return;
        }
        undoStack.push(beforeState);
        while (undoStack.size() > maxHistory) {
            undoStack.removeLast();
        }
        redoStack.clear();
        onHistoryChanged.run();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public int getUndoCount() {
        return undoStack.size();
    }

    public int getRedoCount() {
        return redoStack.size();
    }

    /**
     * Dépile l'état précédent et empile l'état courant dans la pile Rétablir.
     */
    public Optional<SfcProgram> undo(SfcProgram currentState) {
        if (undoStack.isEmpty()) {
            return Optional.empty();
        }
        SfcProgram previous = undoStack.pop();
        if (currentState != null) {
            redoStack.push(currentState);
        }
        onHistoryChanged.run();
        return Optional.of(previous);
    }

    /**
     * Dépile l'état rétabli et empile l'état courant dans la pile Annuler.
     */
    public Optional<SfcProgram> redo(SfcProgram currentState) {
        if (redoStack.isEmpty()) {
            return Optional.empty();
        }
        SfcProgram next = redoStack.pop();
        if (currentState != null) {
            undoStack.push(currentState);
        }
        onHistoryChanged.run();
        return Optional.of(next);
    }

    public void clear() {
        if (undoStack.isEmpty() && redoStack.isEmpty()) {
            return;
        }
        undoStack.clear();
        redoStack.clear();
        onHistoryChanged.run();
    }
}
