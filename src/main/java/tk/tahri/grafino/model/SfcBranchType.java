package tk.tahri.grafino.model;

/**
 * Types de structures d'aiguillage et de synchronisation SFC (CEI 61131-3).
 */
public enum SfcBranchType {
    /** Divergence en OU (Sélection de séquence alternative après une étape). */
    DIVERGENCE_OR,

    /** Convergence en OU (Réunion de séquences alternatives avant une étape). */
    CONVERGENCE_OR,

    /** Divergence en ET (Parallélisme / activation simultanée de branches, double barre). */
    DIVERGENCE_AND,

    /** Convergence en ET (Synchronisation de branches parallèles, double barre). */
    CONVERGENCE_AND;

    public boolean isParallel() {
        return this == DIVERGENCE_AND || this == CONVERGENCE_AND;
    }

    public boolean isAlternative() {
        return this == DIVERGENCE_OR || this == CONVERGENCE_OR;
    }

    public boolean isDivergence() {
        return this == DIVERGENCE_OR || this == DIVERGENCE_AND;
    }

    public boolean isConvergence() {
        return this == CONVERGENCE_OR || this == CONVERGENCE_AND;
    }
}
