package tk.tahri.grafino.component;

/**
 * Bloc d'action graphique associé à une étape (CEI 61131-3 / CEI 60848).
 *
 * @param qualifier   Qualificateur d'action (N, S, R, L, D, P...)
 * @param variable    Variable automate cible (optionnel si expression de comptage directe)
 * @param parameter   Paramètre optionnel (ex: durée "5s", condition "dcy")
 * @param customText  Texte d'assignation ou expression personnalisée (ex: "C1 := C1 + 1", "C1 := 0")
 */
public record Action(String qualifier, Variable variable, String parameter, String customText) {

    public Action(String qualifier, Variable variable, String parameter) {
        this(qualifier, variable, parameter, null);
    }

    public Action(String qualifier, Variable variable) {
        this(qualifier, variable, null, null);
    }

    public Action(String qualifier, String customText) {
        this(qualifier, null, null, customText);
    }

    public boolean hasParameter() {
        return parameter != null && !parameter.isBlank();
    }

    /**
     * Retourne le libellé à afficher dans le compartiment d'action.
     */
    public String getDisplayText() {
        if (customText != null && !customText.isBlank()) {
            return customText.trim();
        }
        return (variable != null) ? variable.getName().toUpperCase() : "";
    }
}
