package tk.tahri.grafino.simulation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Échantillon temporel immuable capturé lors de l'exécution d'un cycle de simulation.
 *
 * @param timestampMs        Temps relatif en millisecondes depuis le démarrage
 * @param stepStates         États booléens des étapes (ex: "X0" -> true)
 * @param inputStates        États booléens des entrées capteurs (%I)
 * @param outputStates       États booléens des sorties préactionneurs (%Q)
 * @param counterValues      Valeurs entières des compteurs industriels (INT)
 * @param firedTransitions   Transitions franchies lors de ce cycle (ex: ["T1"])
 */
public record TimingSample(
        long timestampMs,
        Map<String, Boolean> stepStates,
        Map<String, Boolean> inputStates,
        Map<String, Boolean> outputStates,
        Map<String, Integer> counterValues,
        List<String> firedTransitions
) {
    public TimingSample {
        stepStates = (stepStates != null) ? Map.copyOf(stepStates) : Map.of();
        inputStates = (inputStates != null) ? Map.copyOf(inputStates) : Map.of();
        outputStates = (outputStates != null) ? Map.copyOf(outputStates) : Map.of();
        counterValues = (counterValues != null) ? Map.copyOf(counterValues) : Map.of();
        firedTransitions = (firedTransitions != null) ? List.copyOf(firedTransitions) : List.of();
    }

    public boolean getStepState(String stepName) {
        return stepStates.getOrDefault(stepName, false);
    }

    public boolean getInputState(String inputName) {
        return inputStates.getOrDefault(inputName, false);
    }

    public boolean getOutputState(String outputName) {
        return outputStates.getOrDefault(outputName, false);
    }

    public int getCounterValue(String counterName) {
        return counterValues.getOrDefault(counterName, 0);
    }
}
