package tk.tahri.grafino.model;

/**
 * Message d'analyse sémantique ou de conformité d'un programme SFC.
 */
public record SfcValidationMessage(
        Level level,
        String elementId,
        String message
) {
    public enum Level {
        INFO, WARNING, ERROR
    }

    public static SfcValidationMessage error(String elementId, String message) {
        return new SfcValidationMessage(Level.ERROR, elementId, message);
    }

    public static SfcValidationMessage warning(String elementId, String message) {
        return new SfcValidationMessage(Level.WARNING, elementId, message);
    }

    public static SfcValidationMessage info(String elementId, String message) {
        return new SfcValidationMessage(Level.INFO, elementId, message);
    }
}
