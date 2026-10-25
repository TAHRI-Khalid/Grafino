package tk.tahri.grafino.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Bloc d'action associé à une étape SFC (CEI 61131-3).
 *
 * @param qualifier       Qualificateur normalisé (N, S, R, L, D, P, etc.)
 * @param variableName    Nom de la variable booléenne cible ou identifiant de l'action
 * @param duration        Durée pour les qualificateurs temporisés (L, D, SD, DS, SL) ou null
 * @param inlineCondition Condition booléenne optionnelle (expression ST / Structured Text)
 */
public record SfcActionBlock(
        SfcQualifier qualifier,
        String variableName,
        Duration duration,
        String inlineCondition
) {
    public SfcActionBlock {
        Objects.requireNonNull(qualifier, "Le qualificateur ne peut être nul.");
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("Le nom de la variable d'action est obligatoire.");
        }
        variableName = variableName.trim();
        inlineCondition = (inlineCondition != null && !inlineCondition.isBlank()) ? inlineCondition.trim() : null;

        if (qualifier.isTimed() && duration == null) {
            throw new IllegalArgumentException("Une durée doit être spécifiée pour le qualificateur temporisé : " + qualifier);
        }
    }

    /**
     * Constructeur pratique pour les actions simples (ex: N, S, R).
     */
    public SfcActionBlock(SfcQualifier qualifier, String variableName) {
        this(qualifier, variableName, null, null);
    }

    /**
     * Constructeur pratique pour les actions temporisées (L, D).
     */
    public SfcActionBlock(SfcQualifier qualifier, String variableName, Duration duration) {
        this(qualifier, variableName, duration, null);
    }

    public boolean isTimed() {
        return qualifier.isTimed() && duration != null;
    }

    public boolean isConditional() {
        return inlineCondition != null;
    }

    /**
     * Formate la durée au format CEI 61131-3 (ex: T#5s, T#200ms).
     */
    public String formattedDuration() {
        if (duration == null) {
            return "";
        }
        long millis = duration.toMillis();
        if (millis % 1000 == 0) {
            return "T#" + (millis / 1000) + "s";
        }
        return "T#" + millis + "ms";
    }
}
