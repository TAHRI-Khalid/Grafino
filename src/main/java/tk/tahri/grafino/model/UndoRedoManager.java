package tk.tahri.grafino.model;

import tk.tahri.grafino.storage.GrafcetStorage;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages Undo and Redo operations by keeping snapshots of the Grafcet model state.
 */
public class UndoRedoManager {
    private static final int MAX_HISTORY = 50;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    public void pushSnapshot(GrafcetModel model) {
        if (model == null) return;
        String snapshot = GrafcetStorage.toJson(model);
        // Avoid duplicate identical consecutive snapshots
        if (!undoStack.isEmpty() && undoStack.peek().equals(snapshot)) {
            return;
        }
        undoStack.push(snapshot);
        if (undoStack.size() > MAX_HISTORY) {
            // Remove oldest
            ((ArrayDeque<String>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    public boolean canUndo() {
        return undoStack.size() > 1; // Needs at least 1 previous state to revert to
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public GrafcetModel undo(GrafcetModel currentModel) {
        if (!canUndo()) return null;

        // Current state pushed to redo
        String currentState = undoStack.pop();
        redoStack.push(currentState);

        // Previous state
        String previousState = undoStack.peek();
        return GrafcetStorage.fromJson(previousState);
    }

    public GrafcetModel redo() {
        if (!canRedo()) return null;

        String nextState = redoStack.pop();
        undoStack.push(nextState);
        return GrafcetStorage.fromJson(nextState);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
