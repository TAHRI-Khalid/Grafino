package tk.tahri.grafino.storage;

import tk.tahri.grafino.model.*;

import java.io.File;
import java.io.IOException;

public class GrafcetStorageTest {

    public static void main(String[] args) {
        System.out.println("=== Test de sérialisation / désérialisation .tk ===");

        GrafcetModel original = new GrafcetModel("Perceuse Automatique");

        // 1. Variables
        original.getVariables().add(new VariableModel("dcy", VariableType.BOOL, VariableDirection.INPUT, "%I0.0", "Départ cycle"));
        original.getVariables().add(new VariableModel("h", VariableType.BOOL, VariableDirection.INPUT, "%I0.1", "Position haute"));
        original.getVariables().add(new VariableModel("b", VariableType.BOOL, VariableDirection.INPUT, "%I0.2", "Position basse"));
        original.getVariables().add(new VariableModel("DESCENDRE", VariableType.BOOL, VariableDirection.OUTPUT, "%Q0.0", "Descente broche"));
        original.getVariables().add(new VariableModel("ROTATION", VariableType.BOOL, VariableDirection.OUTPUT, "%Q0.1", "Moteur rotation"));

        // 2. Étapes
        StepModel s0 = new StepModel("S0", 0, true, 500, 100);
        StepModel s1 = new StepModel("S1", 1, false, 500, 260);
        s1.getActions().add(new GrafcetAction("N", "DESCENDRE"));
        s1.getActions().add(new GrafcetAction("S", "ROTATION"));

        StepModel s2 = new StepModel("S2", 2, false, 500, 420);
        s2.getActions().add(new GrafcetAction("R", "ROTATION"));

        original.getSteps().add(s0);
        original.getSteps().add(s1);
        original.getSteps().add(s2);

        // 3. Transitions
        TransitionModel t1 = new TransitionModel("T1", 1, "dcy . h", 500, 180);
        TransitionModel t2 = new TransitionModel("T2", 2, "b", 500, 340);
        TransitionModel t3 = new TransitionModel("T3", 3, "h", 500, 500);

        original.getTransitions().add(t1);
        original.getTransitions().add(t2);
        original.getTransitions().add(t3);

        // 4. Liaisons
        original.getLinks().add(new LinkModel("L1", "S0", "T1"));
        original.getLinks().add(new LinkModel("L2", "T1", "S1"));
        original.getLinks().add(new LinkModel("L3", "S1", "T2"));
        original.getLinks().add(new LinkModel("L4", "T2", "S2"));
        original.getLinks().add(new LinkModel("L5", "S2", "T3"));

        LinkModel loopBack = new LinkModel("L6", "T3", "S0");
        loopBack.getIntermediatePoints().add(new double[]{540, 564});
        loopBack.getIntermediatePoints().add(new double[]{420, 564});
        loopBack.getIntermediatePoints().add(new double[]{420, 100});
        loopBack.getIntermediatePoints().add(new double[]{540, 100});
        original.getLinks().add(loopBack);

        // Test Sauvegarde
        File tempFile = new File("test_grafcet.tk");
        try {
            GrafcetStorage.save(original, tempFile);
            System.out.println("✓ Sauvegarde réussie dans " + tempFile.getAbsolutePath());

            // Test Chargement
            GrafcetModel loaded = GrafcetStorage.load(tempFile);
            System.out.println("✓ Chargement réussi.");

            // Vérifications d'intégrité
            assert loaded.getName().equals("Perceuse Automatique") : "Nom de projet incorrect";
            assert loaded.getVariables().size() == 5 : "Nombre de variables incorrect";
            assert loaded.getSteps().size() == 3 : "Nombre d'étapes incorrect";
            assert loaded.getTransitions().size() == 3 : "Nombre de transitions incorrect";
            assert loaded.getLinks().size() == 6 : "Nombre de liens incorrect";

            StepModel loadedS0 = loaded.findStepById("S0").orElseThrow();
            assert loadedS0.isInitial() : "S0 devrait être initiale";

            StepModel loadedS1 = loaded.findStepById("S1").orElseThrow();
            assert loadedS1.getActions().size() == 2 : "S1 devrait avoir 2 actions";
            assert loadedS1.getActions().get(1).getQualifier().equals("S") : "Qualificateur action incorrect";

            TransitionModel loadedT1 = loaded.findTransitionById("T1").orElseThrow();
            assert loadedT1.getReceptivity().equals("dcy . h") : "Réceptivité T1 incorrecte";

            LinkModel loadedL6 = loaded.getLinks().stream().filter(l -> l.getId().equals("L6")).findFirst().orElseThrow();
            assert loadedL6.getIntermediatePoints().size() == 4 : "Points de boucle retour incorrects";

            System.out.println("✓ Toutes les assertions sont validées avec succès !");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
