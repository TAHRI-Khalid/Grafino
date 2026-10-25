package tk.tahri.grafino.simulation;

import java.util.List;
import java.util.Set;

/**
 * Rapport d'exécution d'un cycle d'évolution synchrone selon la CEI 60848.
 *
 * @param hasEvolved           Vrai si le marquage a évolué lors de ce cycle
 * @param firedTransitionIds   Liste des transitions franchies simultanément
 * @param activatedStepIds     Étapes nouvellement activées
 * @param deactivatedStepIds   Étapes désactivées
 * @param activeOutputs        Sorties (%Q) actives à la fin du cycle
 * @param cycleNumber          Numéro d'itération du cycle automate
 */
public record SimulationStepResult(
        boolean hasEvolved,
        List<String> firedTransitionIds,
        Set<String> activatedStepIds,
        Set<String> deactivatedStepIds,
        Set<String> activeOutputs,
        long cycleNumber
) {
    public static SimulationStepResult noEvolution(long cycleNumber, Set<String> activeOutputs) {
        return new SimulationStepResult(false, List.of(), Set.of(), Set.of(), activeOutputs, cycleNumber);
    }
}
