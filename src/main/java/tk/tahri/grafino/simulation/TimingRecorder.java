package tk.tahri.grafino.simulation;

import java.util.*;

/**
 * Enregistreur temporel multi-voies pour l'analyse dynamique du Grafcet / SFC.
 *
 * Capture à fréquence régulière les états logiques des étapes, des entrées (%I),
 * des sorties (%Q) et des compteurs sans allocation mémoire excessive.
 */
public class TimingRecorder {

    public static final int DEFAULT_MAX_SAMPLES = 10_000;

    private final int maxSamples;
    private final List<TimingSample> samples = new ArrayList<>();
    private final Set<String> registeredStepNames = new LinkedHashSet<>();
    private final Set<String> registeredInputNames = new LinkedHashSet<>();
    private final Set<String> registeredOutputNames = new LinkedHashSet<>();
    private final Set<String> registeredCounterNames = new LinkedHashSet<>();

    private boolean recording = true;
    private long simulationStartTimeMs = -1;

    public TimingRecorder() {
        this(DEFAULT_MAX_SAMPLES);
    }

    public TimingRecorder(int maxSamples) {
        this.maxSamples = Math.max(1, maxSamples);
    }

    /**
     * Enregistre un échantillon instantané de l'état du simulateur.
     */
    public synchronized void recordTick(
            long currentWallClockTimeMs,
            SimulationState state,
            SimulationStepResult stepResult,
            List<String> stepNames,
            List<String> inputNames,
            List<String> outputNames,
            List<String> counterNames
    ) {
        if (!recording || state == null) {
            return;
        }

        if (simulationStartTimeMs < 0) {
            simulationStartTimeMs = currentWallClockTimeMs;
        }
        long relativeTimeMs = Math.max(0, currentWallClockTimeMs - simulationStartTimeMs);

        // Mémoriser les canaux déclarés
        if (stepNames != null) registeredStepNames.addAll(stepNames);
        if (inputNames != null) registeredInputNames.addAll(inputNames);
        if (outputNames != null) registeredOutputNames.addAll(outputNames);
        if (counterNames != null) registeredCounterNames.addAll(counterNames);

        // Capture des étapes
        Map<String, Boolean> stepMap = new LinkedHashMap<>();
        for (String stepName : registeredStepNames) {
            // Un nom d'étape peut être "X0", "X1", ou "s0", "s1"
            String normalizedId = stepName.toLowerCase().startsWith("x")
                    ? "s" + stepName.substring(1)
                    : stepName;
            boolean active = state.isStepActive(normalizedId) || state.isStepActive(stepName);
            stepMap.put(stepName, active);
        }

        // Capture des entrées (%I)
        Map<String, Boolean> inMap = new LinkedHashMap<>();
        for (String in : registeredInputNames) {
            inMap.put(in, state.getBooleanVariable(in));
        }

        // Capture des sorties (%Q)
        Map<String, Boolean> outMap = new LinkedHashMap<>();
        for (String out : registeredOutputNames) {
            outMap.put(out, state.isOutputActive(out));
        }

        // Capture des compteurs (INT)
        Map<String, Integer> counterMap = new LinkedHashMap<>();
        for (String c : registeredCounterNames) {
            counterMap.put(c, state.getCounterValue(c));
        }

        // Transitions franchies lors de ce tick
        List<String> fired = (stepResult != null && stepResult.firedTransitionIds() != null)
                ? stepResult.firedTransitionIds()
                : List.of();

        // Ajout avec gestion de la taille maximale (file circulaire FIFO)
        if (samples.size() >= maxSamples) {
            samples.removeFirst();
        }
        samples.add(new TimingSample(relativeTimeMs, stepMap, inMap, outMap, counterMap, fired));
    }

    public synchronized void clear() {
        samples.clear();
        simulationStartTimeMs = -1;
    }

    public synchronized void resetStartTime() {
        samples.clear();
        simulationStartTimeMs = -1;
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    public synchronized void setRecording(boolean recording) {
        this.recording = recording;
    }

    public synchronized List<TimingSample> getSamples() {
        return List.copyOf(samples);
    }

    /**
     * Retourne uniquement la sous-liste d'échantillons couvrant la fenêtre temporelle [startTimeMs, endTimeMs],
     * incluant l'échantillon immédiatement précédent et suivant pour assurer la continuité du tracé sans copier tout l'historique.
     */
    public synchronized List<TimingSample> getSamplesInWindow(long startTimeMs, long endTimeMs) {
        if (samples.isEmpty()) {
            return List.of();
        }
        int n = samples.size();
        int fromIdx = findFloorIndex(startTimeMs);
        int toIdx = findCeilingIndex(endTimeMs);
        fromIdx = Math.max(0, fromIdx);
        toIdx = Math.min(n, toIdx + 1);
        if (fromIdx >= toIdx) {
            return List.of(samples.getLast());
        }
        return List.copyOf(samples.subList(fromIdx, toIdx));
    }

    private int findFloorIndex(long targetTimeMs) {
        int low = 0;
        int high = samples.size() - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (samples.get(mid).timestampMs() <= targetTimeMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    private int findCeilingIndex(long targetTimeMs) {
        int low = 0;
        int high = samples.size() - 1;
        int result = high;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (samples.get(mid).timestampMs() >= targetTimeMs) {
                result = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return result;
    }

    public synchronized int getSampleCount() {
        return samples.size();
    }

    public synchronized long getTotalDurationMs() {
        if (samples.isEmpty()) return 0;
        return samples.getLast().timestampMs();
    }

    public synchronized Set<String> getRegisteredStepNames() {
        return Collections.unmodifiableSet(registeredStepNames);
    }

    public synchronized Set<String> getRegisteredInputNames() {
        return Collections.unmodifiableSet(registeredInputNames);
    }

    public synchronized Set<String> getRegisteredOutputNames() {
        return Collections.unmodifiableSet(registeredOutputNames);
    }

    public synchronized Set<String> getRegisteredCounterNames() {
        return Collections.unmodifiableSet(registeredCounterNames);
    }

    /**
     * Recherche l'échantillon le plus proche d'un temps donné (en millisecondes).
     */
    public synchronized Optional<TimingSample> findSampleAt(long targetTimeMs) {
        if (samples.isEmpty()) return Optional.empty();
        if (targetTimeMs <= samples.getFirst().timestampMs()) {
            return Optional.of(samples.getFirst());
        }
        if (targetTimeMs >= samples.getLast().timestampMs()) {
            return Optional.of(samples.getLast());
        }

        // Recherche par dichotomie
        int low = 0;
        int high = samples.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = samples.get(mid).timestampMs();
            if (midVal < targetTimeMs) {
                low = mid + 1;
            } else if (midVal > targetTimeMs) {
                high = mid - 1;
            } else {
                return Optional.of(samples.get(mid));
            }
        }
        // Prendre le plus proche entre high et low
        long diffLow = Math.abs(samples.get(low).timestampMs() - targetTimeMs);
        long diffHigh = Math.abs(samples.get(high).timestampMs() - targetTimeMs);
        return Optional.of(diffLow < diffHigh ? samples.get(low) : samples.get(high));
    }

    /**
     * Exporte les données temporelles au format standard CSV pour Excel / Python.
     */
    public synchronized String exportToCsv() {
        StringBuilder sb = new StringBuilder();
        // En-têtes de colonnes
        sb.append("Time_ms;Time_s");
        for (String s : registeredStepNames) sb.append(";Step_").append(s);
        for (String in : registeredInputNames) sb.append(";Input_").append(in);
        for (String out : registeredOutputNames) sb.append(";Output_").append(out);
        for (String c : registeredCounterNames) sb.append(";Counter_").append(c);
        sb.append(";Fired_Transitions\n");

        for (TimingSample sample : samples) {
            sb.append(sample.timestampMs()).append(";");
            sb.append(String.format(Locale.US, "%.3f", sample.timestampMs() / 1000.0));

            for (String s : registeredStepNames) {
                sb.append(";").append(sample.getStepState(s) ? 1 : 0);
            }
            for (String in : registeredInputNames) {
                sb.append(";").append(sample.getInputState(in) ? 1 : 0);
            }
            for (String out : registeredOutputNames) {
                sb.append(";").append(sample.getOutputState(out) ? 1 : 0);
            }
            for (String c : registeredCounterNames) {
                sb.append(";").append(sample.getCounterValue(c));
            }
            sb.append(";").append(String.join(",", sample.firedTransitions()));
            sb.append("\n");
        }
        return sb.toString();
    }
}
