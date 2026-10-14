package tk.tahri.grafino.model;

import java.util.Objects;

public class GrafcetAction {
    private String qualifier; // "N", "S", "R", "D", "P"
    private String variableName;
    private String parameter; // e.g., "5s" for delayed, or null

    public GrafcetAction() {
        this("N", "", null);
    }

    public GrafcetAction(String qualifier, String variableName) {
        this(qualifier, variableName, null);
    }

    public GrafcetAction(String qualifier, String variableName, String parameter) {
        this.qualifier = qualifier == null || qualifier.isBlank() ? "N" : qualifier.trim();
        this.variableName = variableName != null ? variableName.trim() : "";
        this.parameter = parameter;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        this.parameter = parameter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GrafcetAction that = (GrafcetAction) o;
        return Objects.equals(qualifier, that.qualifier) &&
                Objects.equals(variableName, that.variableName) &&
                Objects.equals(parameter, that.parameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualifier, variableName, parameter);
    }

    @Override
    public String toString() {
        return (qualifier.equals("N") ? "" : qualifier + ": ") + variableName +
                (parameter != null && !parameter.isBlank() ? " (" + parameter + ")" : "");
    }
}
