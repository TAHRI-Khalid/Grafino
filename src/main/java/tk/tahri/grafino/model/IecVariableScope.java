package tk.tahri.grafino.model;

/**
 * Portée et classe de stockage des variables selon la norme CEI 61131-3.
 */
public enum IecVariableScope {
    /** Entrées physiques de l'automate (%I...). */
    VAR_INPUT("%I", "Entrée API"),

    /** Sorties physiques de l'automate (%Q...). */
    VAR_OUTPUT("%Q", "Sortie API"),

    /** Mémoires internes locales au bloc (%M...). */
    VAR("%M", "Mémoire interne"),

    /** Paramètres transmis par référence. */
    VAR_IN_OUT("", "Paramètre In/Out");

    private final String iecPrefix;
    private final String label;

    IecVariableScope(String iecPrefix, String label) {
        this.iecPrefix = iecPrefix;
        this.label = label;
    }

    public String iecPrefix() {
        return iecPrefix;
    }

    public String label() {
        return label;
    }
}
