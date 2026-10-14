package tk.tahri.grafino.model;

public class UndoRedoManagerTest {

    public static void main(String[] args) {
        System.out.println("=== Test du gestionnaire Undo / Redo ===");
        UndoRedoManager manager = new UndoRedoManager();

        // État 1 : Projet avec 1 étape
        GrafcetModel m1 = new GrafcetModel("V1");
        m1.getSteps().add(new StepModel("S0", 0, true, 100, 100));
        manager.pushSnapshot(m1);

        assert !manager.canUndo() : "Ne devrait pas pouvoir annuler sur le premier état";
        assert !manager.canRedo() : "Rien à rétablir";

        // État 2 : Ajout d'une transition
        GrafcetModel m2 = new GrafcetModel("V2");
        m2.getSteps().add(new StepModel("S0", 0, true, 100, 100));
        m2.getTransitions().add(new TransitionModel("T1", 1, "dcy", 100, 180));
        manager.pushSnapshot(m2);

        assert manager.canUndo() : "Devrait pouvoir annuler vers l'état 1";
        assert !manager.canRedo() : "Rien à rétablir encore";

        // Test Annuler
        GrafcetModel reverted = manager.undo(m2);
        assert reverted != null : "Modèle restauré null";
        assert reverted.getSteps().size() == 1 : "Devrait avoir 1 étape";
        assert reverted.getTransitions().isEmpty() : "Devrait avoir 0 transition après annulation";
        assert manager.canRedo() : "Devrait maintenant pouvoir rétablir";

        // Test Rétablir
        GrafcetModel restored = manager.redo();
        assert restored != null : "Modèle rétabli null";
        assert restored.getSteps().size() == 1 : "Devrait avoir 1 étape";
        assert restored.getTransitions().size() == 1 : "Devrait avoir 1 transition";
        assert restored.getTransitions().get(0).getReceptivity().equals("dcy") : "Réceptivité incorrecte";

        System.out.println("✓ Undo et Redo validés avec succès !");
    }
}
