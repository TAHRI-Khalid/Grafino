package tk.tahri.grafino.component;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import tk.tahri.grafino.model.IecDataType;
import tk.tahri.grafino.model.IecVariable;
import tk.tahri.grafino.model.IecVariableScope;

import java.util.regex.Pattern;

/**
 * Modèle observable d'une variable d'automatisme pour l'interface JavaFX.
 * Conforme à la norme CEI 61131-3 avec typage IEC, portée et adresse matérielle validée.
 */
public class Variable {

    // Motifs de validation de l'adresse API (CEI 61131-3 : %I, %Q, %M et broches microcontrôleur)
    public static final Pattern IEC_ADDRESS_PATTERN =
            Pattern.compile("^%(?i:[IQM])([XBWDLxbwdl])?\\d+(\\.\\d+)*$");
    public static final Pattern HARDWARE_PIN_PATTERN =
            Pattern.compile("^(?i:PIN\\s*\\d+|D\\d+|A\\d+)$");

    // Enum rétrocompatible
    public enum Type {
        BOOL, INT
    }

    private final SimpleStringProperty name;
    private final SimpleObjectProperty<IecDataType> type;
    private final SimpleObjectProperty<IecVariableScope> scope;
    private final SimpleStringProperty address;
    private final SimpleStringProperty initialValue;
    private final SimpleStringProperty comment;
    private boolean isSyncing = false;

    public Variable(String name, Type type, String address, String comment) {
        this(name,
             (type == Type.INT) ? IecDataType.INT : IecDataType.BOOL,
             inferScope(address),
             address,
             "",
             comment);
    }

    public Variable(String name, IecDataType type, IecVariableScope scope, String address, String initialValue, String comment) {
        this.name = new SimpleStringProperty(name != null ? name : "");
        this.type = new SimpleObjectProperty<>(type != null ? type : IecDataType.BOOL);
        this.scope = new SimpleObjectProperty<>(scope != null ? scope : inferScope(address));
        this.address = new SimpleStringProperty(address != null ? address : "");
        this.initialValue = new SimpleStringProperty(initialValue != null ? initialValue : "");
        this.comment = new SimpleStringProperty(comment != null ? comment : "");

        // 1. Synchronisation automatique de la portée si l'adresse API est saisie/modifiée
        this.address.addListener((obs, oldVal, newVal) -> {
            if (isSyncing || newVal == null || newVal.isBlank()) return;
            isSyncing = true;
            try {
                var deduced = inferScope(newVal);
                if (deduced != this.scope.get()) {
                    this.scope.set(deduced);
                }
            } finally {
                isSyncing = false;
            }
        });

        // 2. Synchronisation automatique de l'adresse API si la portée (scope) est modifiée
        this.scope.addListener((obs, oldScope, newScope) -> {
            if (isSyncing || newScope == null) return;
            isSyncing = true;
            try {
                String targetPrefix = newScope.iecPrefix();
                if (targetPrefix != null && !targetPrefix.isBlank()) {
                    String currentAddr = this.address.get();
                    if (currentAddr == null || currentAddr.isBlank()) {
                        this.address.set(targetPrefix + "0.0");
                    } else {
                        String trimmed = currentAddr.trim().toUpperCase();
                        if (trimmed.startsWith("%I") || trimmed.startsWith("%Q") ||
                            trimmed.startsWith("%M")) {
                            this.address.set(targetPrefix + currentAddr.substring(2));
                        } else {
                            this.address.set(targetPrefix + "0.0");
                        }
                    }
                }
            } finally {
                isSyncing = false;
            }
        });
    }

    public static boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) return false;
        String s = normalizeAddress(address);
        return IEC_ADDRESS_PATTERN.matcher(s).matches() || HARDWARE_PIN_PATTERN.matcher(s).matches();
    }

    public static String normalizeAddress(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // Si l'utilisateur a omis le '%' initial mais a saisi un format IEC valide (ex: I0.0, Q0.1, IW2)
        if (s.matches("^(?i:[IQM])([XBWDLxbwdl])?\\d+(\\.\\d+)*$")) {
            s = "%" + s;
        }
        return s.toUpperCase();
    }

    public static IecVariableScope inferScope(String address) {
        if (address == null) return IecVariableScope.VAR;
        String upper = normalizeAddress(address);
        if (upper.startsWith("%I")) return IecVariableScope.VAR_INPUT;
        if (upper.startsWith("%Q")) return IecVariableScope.VAR_OUTPUT;
        if (upper.startsWith("%M")) return IecVariableScope.VAR;
        return IecVariableScope.VAR;
    }

    public static Variable fromIecVariable(IecVariable iv) {
        return new Variable(
                iv.name(),
                iv.type(),
                iv.scope(),
                iv.hardwareAddress(),
                iv.initialValue(),
                iv.description()
        );
    }

    public IecVariable toIecVariable() {
        return new IecVariable(
                getName(),
                getType(),
                getScope(),
                getAddress(),
                getInitialValue(),
                getComment()
        );
    }

    public boolean isInput() {
        return getScope() == IecVariableScope.VAR_INPUT || getAddress().toUpperCase().startsWith("%I");
    }

    public boolean isOutput() {
        return getScope() == IecVariableScope.VAR_OUTPUT || getAddress().toUpperCase().startsWith("%Q");
    }

    public boolean isInternal() {
        return getScope() == IecVariableScope.VAR || getAddress().toUpperCase().startsWith("%M");
    }

    // --- Getters & Setters JavaFX Properties ---

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public IecDataType getType() {
        return type.get();
    }

    public void setType(IecDataType type) {
        this.type.set(type);
    }

    public SimpleObjectProperty<IecDataType> typeProperty() {
        return type;
    }

    // Surcharges rétrocompatibles pour l'ancien enum Variable.Type
    public void setType(Type legacyType) {
        this.type.set((legacyType == Type.INT) ? IecDataType.INT : IecDataType.BOOL);
    }

    public IecVariableScope getScope() {
        return scope.get();
    }

    public void setScope(IecVariableScope scope) {
        this.scope.set(scope);
    }

    public SimpleObjectProperty<IecVariableScope> scopeProperty() {
        return scope;
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        if (address != null && !address.isBlank()) {
            String normalized = normalizeAddress(address);
            if (isValidAddress(normalized)) {
                this.address.set(normalized);
                return;
            }
        }
        if (this.address.get() == null || this.address.get().isBlank()) {
            this.address.set(getScope().iecPrefix() + "0.0");
        }
    }

    public SimpleStringProperty addressProperty() {
        return address;
    }

    public String getInitialValue() {
        return initialValue.get();
    }

    public void setInitialValue(String initialValue) {
        this.initialValue.set(initialValue);
    }

    public SimpleStringProperty initialValueProperty() {
        return initialValue;
    }

    public String getComment() {
        return comment.get();
    }

    public void setComment(String comment) {
        this.comment.set(comment);
    }

    public SimpleStringProperty commentProperty() {
        return comment;
    }
}
