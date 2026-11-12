package tk.tahri.grafino.model;

/**
 * Message d'analyse sémantique, topologique ou de conformité normative d'un programme SFC / Grafcet
 * (CEI 60848 & CEI 61131-3), accompagné d'une explication pédagogique pour aider l'étudiant
 * à comprendre son erreur sans la corriger à sa place.
 *
 * @param level           Niveau de sévérité (INFO, WARNING, ERROR)
 * @param elementId       Identifiant de l'élément concerné (ex: "s1", "t2", "l_s1_t2" ou "")
 * @param ruleReference   Référence de la règle normative ou catégorie (ex: "CEI 60848 — Règle 1")
 * @param message         Constat synthétique de l'erreur
 * @param pedagogicalHint Explication pédagogique ("Pourquoi est-ce une erreur ?" et "Comment la résoudre ?")
 */
public record SfcValidationMessage(
        Level level,
        String elementId,
        String ruleReference,
        String message,
        String pedagogicalHint
) {
    public enum Level {
        INFO, WARNING, ERROR
    }

    public SfcValidationMessage {
        level = (level == null) ? Level.INFO : level;
        elementId = (elementId == null) ? "" : elementId;
        ruleReference = (ruleReference == null || ruleReference.isBlank()) ? "CEI 60848 / CEI 61131-3" : ruleReference;
        message = (message == null) ? "" : message;
        pedagogicalHint = (pedagogicalHint == null) ? "" : pedagogicalHint;
    }

    /**
     * Constructeur rétrocompatible à 3 arguments.
     */
    public SfcValidationMessage(Level level, String elementId, String message) {
        this(level, elementId, "CEI 60848 / CEI 61131-3", message, "");
    }

    public boolean isError() {
        return level == Level.ERROR;
    }

    public boolean isWarning() {
        return level == Level.WARNING;
    }

    public boolean isInfo() {
        return level == Level.INFO;
    }

    public boolean hasPedagogicalHint() {
        return pedagogicalHint != null && !pedagogicalHint.isBlank();
    }

    public static SfcValidationMessage error(String elementId, String message) {
        return new SfcValidationMessage(Level.ERROR, elementId, "CEI 60848 / CEI 61131-3", message, "");
    }

    public static SfcValidationMessage error(String elementId, String ruleReference, String message, String pedagogicalHint) {
        return new SfcValidationMessage(Level.ERROR, elementId, ruleReference, message, pedagogicalHint);
    }

    public static SfcValidationMessage warning(String elementId, String message) {
        return new SfcValidationMessage(Level.WARNING, elementId, "CEI 60848 / CEI 61131-3", message, "");
    }

    public static SfcValidationMessage warning(String elementId, String ruleReference, String message, String pedagogicalHint) {
        return new SfcValidationMessage(Level.WARNING, elementId, ruleReference, message, pedagogicalHint);
    }

    public static SfcValidationMessage info(String elementId, String message) {
        return new SfcValidationMessage(Level.INFO, elementId, "CEI 60848 / CEI 61131-3", message, "");
    }

    public static SfcValidationMessage info(String elementId, String ruleReference, String message, String pedagogicalHint) {
        return new SfcValidationMessage(Level.INFO, elementId, ruleReference, message, pedagogicalHint);
    }
}
