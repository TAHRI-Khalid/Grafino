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
    private final TimingSample[] buffer;
    private int head = 0;
    private int count = 0;
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
        this.buffer = new TimingSample[this.maxSamples];
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

        // Ajout O(1) dans le buffer circulaire FIFO
        appendSample(new TimingSample(relativeTimeMs, stepMap, inMap, outMap, counterMap, fired));
    }

    private void appendSample(TimingSample sample) {
        if (count < maxSamples) {
            buffer[(head + count) % maxSamples] = sample;
            count++;
        } else {
            buffer[head] = sample;
            head = (head + 1) % maxSamples;
        }
    }

    private TimingSample sampleAt(int logicalIndex) {
        return buffer[(head + logicalIndex) % maxSamples];
    }

    public synchronized void clear() {
        Arrays.fill(buffer, null);
        head = 0;
        count = 0;
        registeredStepNames.clear();
        registeredInputNames.clear();
        registeredOutputNames.clear();
        registeredCounterNames.clear();
        simulationStartTimeMs = -1;
    }

    public synchronized void resetStartTime() {
        clear();
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    public synchronized void setRecording(boolean recording) {
        this.recording = recording;
    }

    public synchronized List<TimingSample> getSamples() {
        if (count == 0) {
            return List.of();
        }
        List<TimingSample> copy = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            copy.add(sampleAt(i));
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * Retourne uniquement la sous-liste d'échantillons couvrant la fenêtre temporelle [startTimeMs, endTimeMs],
     * incluant l'échantillon immédiatement précédent et suivant pour assurer la continuité du tracé sans copier tout l'historique.
     */
    public synchronized List<TimingSample> getSamplesInWindow(long startTimeMs, long endTimeMs) {
        if (count == 0) {
            return List.of();
        }
        int fromIdx = findFloorIndex(startTimeMs);
        int toIdx = findCeilingIndex(endTimeMs);
        fromIdx = Math.max(0, fromIdx);
        toIdx = Math.min(count, toIdx + 1);
        if (fromIdx >= toIdx) {
            return List.of(sampleAt(count - 1));
        }
        List<TimingSample> window = new ArrayList<>(toIdx - fromIdx);
        for (int i = fromIdx; i < toIdx; i++) {
            window.add(sampleAt(i));
        }
        return Collections.unmodifiableList(window);
    }

    private int findFloorIndex(long targetTimeMs) {
        int low = 0;
        int high = count - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (sampleAt(mid).timestampMs() <= targetTimeMs) {
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
        int high = count - 1;
        int result = high;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (sampleAt(mid).timestampMs() >= targetTimeMs) {
                result = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return result;
    }

    public synchronized int getSampleCount() {
        return count;
    }

    public synchronized long getTotalDurationMs() {
        if (count == 0) return 0;
        return sampleAt(count - 1).timestampMs();
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
        if (count == 0) return Optional.empty();
        TimingSample first = sampleAt(0);
        if (targetTimeMs <= first.timestampMs()) {
            return Optional.of(first);
        }
        TimingSample last = sampleAt(count - 1);
        if (targetTimeMs >= last.timestampMs()) {
            return Optional.of(last);
        }

        // Recherche par dichotomie
        int low = 0;
        int high = count - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            TimingSample midSample = sampleAt(mid);
            long midVal = midSample.timestampMs();
            if (midVal < targetTimeMs) {
                low = mid + 1;
            } else if (midVal > targetTimeMs) {
                high = mid - 1;
            } else {
                return Optional.of(midSample);
            }
        }
        // Prendre le plus proche entre high et low
        TimingSample lowSample = sampleAt(low);
        TimingSample highSample = sampleAt(high);
        long diffLow = Math.abs(lowSample.timestampMs() - targetTimeMs);
        long diffHigh = Math.abs(highSample.timestampMs() - targetTimeMs);
        return Optional.of(diffLow < diffHigh ? lowSample : highSample);
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

        for (int i = 0; i < count; i++) {
            TimingSample sample = sampleAt(i);
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
