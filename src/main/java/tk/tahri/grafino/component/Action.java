package tk.tahri.grafino.component;

/**
 * Bloc d'action graphique associé à une étape.
 *
 * @param qualifier Qualificateur d'action (N, S, R, L, D, P)
 * @param variable  Variable automate cible
 * @param parameter Paramètre optionnel (ex: durée "5s", condition "dcy")
 */
public record Action(String qualifier, Variable variable, String parameter) {

    public Action(String qualifier, Variable variable) {
        this(qualifier, variable, null);
    }

    public boolean hasParameter() {
        return parameter != null && !parameter.isBlank();
    }
}
