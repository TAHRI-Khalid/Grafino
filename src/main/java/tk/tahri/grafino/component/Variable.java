package tk.tahri.grafino.component;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import tk.tahri.grafino.model.IecDataType;
import tk.tahri.grafino.model.IecVariable;
import tk.tahri.grafino.model.IecVariableScope;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modèle observable d'une variable d'automatisme pour l'interface JavaFX.
 * Conforme à la norme CEI 61131-3 avec typage IEC, portée et adresse matérielle validée.
 */
public class Variable {

    // Motifs de validation de l'adresse API (CEI 61131-3 : %I, %Q, %M et broches microcontrôleur)
    public static final Pattern IEC_ADDRESS_PATTERN =
            Pattern.compile("^%(?i:[IQM])([XBWDLxbwdl])?\\d+(\\.\\d+)*$");
    private static final Pattern IEC_BIT_ADDRESS_PATTERN =
            Pattern.compile("^%(?i:[IQM])(?:[XBxb])?(\\d+)\\.(\\d+)$");
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
        if (HARDWARE_PIN_PATTERN.matcher(s).matches()) {
            return true;
        }
        Matcher bitMatcher = IEC_BIT_ADDRESS_PATTERN.matcher(s);
        if (bitMatcher.matches()) {
            try {
                int bit = Integer.parseInt(bitMatcher.group(2));
                return bit >= 0 && bit <= 7;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return IEC_ADDRESS_PATTERN.matcher(s).matches();
    }

    /**
     * Formate un index de bit linéaire (0, 1, ..., 7, 8, 9...) en adresse CEI 61131-3 octet.bit
     * (ex: 7 -> "%I0.7", 8 -> "%I1.0").
     */
    public static String formatBitAddress(String prefix, int linearBitIndex) {
        String normalizedPrefix = (prefix != null) ? prefix.trim().toUpperCase() : "%M";
        int safeIndex = Math.max(0, linearBitIndex);
        int byteOffset = safeIndex / 8;
        int bitOffset = safeIndex % 8;
        return normalizedPrefix + byteOffset + "." + bitOffset;
    }

    /**
     * Calcule le prochain index de bit disponible pour un préfixe donné (%I, %Q, %M)
     * en respectant l'adressage octet.bit (8 bits par octet : .0 à .7, puis octet suivant .0).
     */
    public static int nextBitIndex(String prefix, Collection<String> existingAddresses) {
        if (prefix == null || prefix.isBlank() || existingAddresses == null) {
            return 0;
        }
        String normalizedPrefix = prefix.trim().toUpperCase();
        Pattern pattern = Pattern.compile("^" + Pattern.quote(normalizedPrefix) + "X?(\\d+)\\.(\\d+)$");
        int maxLinear = -1;
        for (String addr : existingAddresses) {
            if (addr == null) continue;
            String norm = normalizeAddress(addr);
            Matcher matcher = pattern.matcher(norm);
            if (matcher.matches()) {
                try {
                    int byteNum = Integer.parseInt(matcher.group(1));
                    int bitNum = Integer.parseInt(matcher.group(2));
                    int linear = byteNum * 8 + bitNum;
                    maxLinear = Math.max(maxLinear, linear);
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxLinear + 1;
    }

    /**
     * Retourne la prochaine adresse bit CEI 61131-3 disponible pour un préfixe (%I, %Q, %M).
     */
    public static String nextBitAddress(String prefix, Collection<String> existingAddresses) {
        return formatBitAddress(prefix, nextBitIndex(prefix, existingAddresses));
    }

    /**
     * Retourne le préfixe de taille CEI 61131-3 associé au type de données
     * ("" pour BOOL en format octet.bit, "B" pour BYTE/STRING, "W" pour 16 bits, "D" pour 32 bits, "L" pour 64 bits).
     */
    public static String sizePrefixForType(IecDataType type) {
        if (type == null) return "";
        return switch (type) {
            case BOOL -> "";
            case BYTE, STRING -> "B";
            case WORD, INT, UINT -> "W";
            case DWORD, DINT, UDINT, REAL, TIME -> "D";
            case LREAL -> "L";
        };
    }

    /**
     * Retourne le pas d'adressage en octets selon la taille du type CEI 61131-3.
     */
    public static int byteStepForType(IecDataType type) {
        if (type == null) return 1;
        return switch (type) {
            case BOOL, BYTE, STRING -> 1;
            case WORD, INT, UINT -> 2;
            case DWORD, DINT, UDINT, REAL, TIME -> 4;
            case LREAL -> 8;
        };
    }

    /**
     * Vérifie si une adresse API existante correspond déjà au format du type CEI 61131-3 donné.
     */
    public static boolean addressMatchesType(String address, IecDataType type) {
        if (address == null || address.isBlank() || type == null) return false;
        String norm = normalizeAddress(address);
        if (type == IecDataType.BOOL) {
            Matcher bitMatcher = IEC_BIT_ADDRESS_PATTERN.matcher(norm);
            if (!bitMatcher.matches()) return false;
            try {
                int bit = Integer.parseInt(bitMatcher.group(2));
                return bit >= 0 && bit <= 7;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        String sizePrefix = sizePrefixForType(type);
        return norm.matches("^%[IQM]" + Pattern.quote(sizePrefix) + "\\d+$");
    }

    /**
     * Calcule la prochaine adresse CEI 61131-3 disponible pour une portée (%I, %Q, %M) et un type donnés.
     */
    public static String nextAddressForType(String scopePrefix, IecDataType type, Collection<String> existingAddresses) {
        String normalizedScope = (scopePrefix != null && !scopePrefix.isBlank())
                ? scopePrefix.trim().toUpperCase()
                : "%M";
        if (type == null || type == IecDataType.BOOL) {
            return nextBitAddress(normalizedScope, existingAddresses);
        }

        Set<String> used = new HashSet<>();
        if (existingAddresses != null) {
            for (String addr : existingAddresses) {
                if (addr != null && !addr.isBlank()) {
                    used.add(normalizeAddress(addr));
                }
            }
        }

        String sizePrefix = sizePrefixForType(type);
        int step = byteStepForType(type);
        int offset = 0;
        while (true) {
            String candidate = normalizedScope + sizePrefix + offset;
            if (!used.contains(candidate)) {
                return candidate;
            }
            offset += step;
        }
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
