package tk.tahri.grafino.model;

public enum VariableDirection {
    INPUT("Entrée (%I)"),
    OUTPUT("Sortie (%Q)"),
    INTERNAL("Interne (%M)");

    private final String label;

    VariableDirection(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
