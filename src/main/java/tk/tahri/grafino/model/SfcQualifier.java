package tk.tahri.grafino.model;

/**
 * Qualificateurs d'actions normalisés selon la norme CEI 61131-3 (SFC).
 */
public enum SfcQualifier {
    /** Non-stored : action continue tant que l'étape associée est active. */
    N("Non-stored", false),

    /** Set / Stored : l'action est activée et mémorisée à 1 (maintien après désactivation de l'étape). */
    S("Set / Mémorisé", false),

    /** Reset : l'action mémorisée est réinitialisée à 0. */
    R("Reset", false),

    /** Pulse : impulsion d'un cycle automate lors de l'activation de l'étape. */
    P("Pulse", false),

    /** Pulse Rising : impulsion lors de la transition d'activation de l'étape. */
    P1("Pulse Rising Edge", false),

    /** Pulse Falling : impulsion lors de la transition de désactivation de l'étape. */
    P0("Pulse Falling Edge", false),

    /** Time Limited : active pendant un temps maximal défini (T#...) tant que l'étape reste active. */
    L("Time Limited", true),

    /** Time Delayed : active après un délai d'attente (T#...) tant que l'étape reste active. */
    D("Time Delayed", true),

    /** Stored and Time Delayed : mémorisée à 1 après l'écoulement d'un délai défini. */
    SD("Stored and Time Delayed", true),

    /** Delayed and Stored : retardée puis mémorisée. */
    DS("Delayed and Stored", true),

    /** Stored and Time Limited : mémorisée pendant une durée limitée. */
    SL("Stored and Time Limited", true);

    private final String description;
    private final boolean timed;

    SfcQualifier(String description, boolean timed) {
        this.description = description;
        this.timed = timed;
    }

    public String description() {
        return description;
    }

    public boolean isTimed() {
        return timed;
    }
}
