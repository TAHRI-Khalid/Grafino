package tk.tahri.grafino.model;

/**
 * Types de données élémentaires normalisés selon la norme CEI 61131-3.
 */
public enum IecDataType {
    BOOL("FALSE", 1),
    BYTE("16#00", 8),
    WORD("16#0000", 16),
    DWORD("16#0000_0000", 32),
    INT("0", 16),
    DINT("0", 32),
    UINT("0", 16),
    UDINT("0", 32),
    REAL("0.0", 32),
    LREAL("0.0", 64),
    TIME("T#0s", 32),
    STRING("''", -1);

    private final String defaultValue;
    private final int bitSize;

    IecDataType(String defaultValue, int bitSize) {
        this.defaultValue = defaultValue;
        this.bitSize = bitSize;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public int bitSize() {
        return bitSize;
    }
}
