package tk.tahri.grafino.simulation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * État instantané du moteur de simulation Grafcet / SFC (CEI 60848 & CEI 61131-3).
 *
 * Contient le marquage des étapes actives, les horodatages d'activation (pour les temporisateurs),
 * les valeurs des variables d'automatismes (Entrées %I, Sorties %Q, Mémoires %M) et des compteurs.
 */
public class SimulationState {

    private final Set<String> activeStepIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> stepActivationTimes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> booleanVariables = new ConcurrentHashMap<>();
    private final Map<String, Integer> counterVariables = new ConcurrentHashMap<>();
    private final Set<String> activeOutputs = ConcurrentHashMap.newKeySet();
    private final Set<String> setLatchedOutputs = ConcurrentHashMap.newKeySet();
    private long cycleCount = 0;

    public SimulationState() {
    }

    /**
     * Réinitialise complètement l'état de simulation.
     */
    public void reset() {
        activeStepIds.clear();
        stepActivationTimes.clear();
        booleanVariables.clear();
        counterVariables.clear();
        activeOutputs.clear();
        setLatchedOutputs.clear();
        cycleCount = 0;
    }

    // --- Gestion des étapes actives (Marquage CEI 60848) ---

    public boolean isStepActive(String stepId) {
        if (stepId == null) return false;
        return activeStepIds.contains(stepId);
    }

    public void setStepActive(String stepId, boolean active, long currentTimeMs) {
        if (stepId == null) return;
        if (active) {
            if (!activeStepIds.contains(stepId)) {
                activeStepIds.add(stepId);
                stepActivationTimes.put(stepId, currentTimeMs);
            }
        } else {
            activeStepIds.remove(stepId);
            stepActivationTimes.remove(stepId);
        }
    }

    public Set<String> getActiveStepIds() {
        return Collections.unmodifiableSet(activeStepIds);
    }

    public long getStepActivationTime(String stepId) {
        return stepActivationTimes.getOrDefault(stepId, 0L);
    }

    public long getStepActiveDurationMs(String stepId, long currentTimeMs) {
        Long activatedAt = stepActivationTimes.get(stepId);
        if (activatedAt == null || !activeStepIds.contains(stepId)) {
            return 0L;
        }
        return Math.max(0L, currentTimeMs - activatedAt);
    }

    // --- Gestion des variables logiques (%I, %Q, %M) ---

    public boolean getBooleanVariable(String name) {
        if (name == null || name.isBlank()) return false;
        return booleanVariables.getOrDefault(normalizeKey(name), false);
    }

    public void setBooleanVariable(String name, boolean value) {
        if (name == null || name.isBlank()) return;
        booleanVariables.put(normalizeKey(name), value);
    }

    public Map<String, Boolean> getBooleanVariables() {
        return Collections.unmodifiableMap(booleanVariables);
    }

    // --- Gestion des compteurs industriels (INT) ---

    public int getCounterValue(String name) {
        if (name == null || name.isBlank()) return 0;
        return counterVariables.getOrDefault(normalizeKey(name), 0);
    }

    public void setCounterValue(String name, int value) {
        if (name == null || name.isBlank()) return;
        counterVariables.put(normalizeKey(name), value);
    }

    public Map<String, Integer> getCounterVariables() {
        return Collections.unmodifiableMap(counterVariables);
    }

    // --- Gestion des sorties actives (%Q) ---

    public boolean isOutputActive(String outputName) {
        if (outputName == null || outputName.isBlank()) return false;
        return activeOutputs.contains(normalizeKey(outputName));
    }

    public void setOutputActive(String outputName, boolean active) {
        if (outputName == null || outputName.isBlank()) return;
        String key = normalizeKey(outputName);
        if (active) {
            activeOutputs.add(key);
            setBooleanVariable(key, true);
        } else {
            activeOutputs.remove(key);
            setBooleanVariable(key, false);
        }
    }

    public Set<String> getActiveOutputs() {
        return Collections.unmodifiableSet(activeOutputs);
    }

    // Mémorisation Set (S) / Reset (R)
    public boolean isOutputLatchedSet(String outputName) {
        return setLatchedOutputs.contains(normalizeKey(outputName));
    }

    public void latchOutputSet(String outputName, boolean latched) {
        String key = normalizeKey(outputName);
        if (latched) {
            setLatchedOutputs.add(key);
        } else {
            setLatchedOutputs.remove(key);
        }
    }

    // --- Cycles automate ---

    public long getCycleCount() {
        return cycleCount;
    }

    public void incrementCycle() {
        this.cycleCount++;
    }

    public static String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
