package tk.tahri.grafino.model;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modélisation d'une condition temporisée dans une réceptivité de transition
 * conforme aux normes CEI 60848 (Grafcet standard) et CEI 61131-3 (SFC / Structured Text).
 *
 * Exemples normalisés pris en charge :
 * - Format Grafcet CEI 60848 (format par défaut de Grafino) : "t/X1/5s", "t1/X2/500ms"
 * - Format Structured Text CEI 61131-3 : "X1.t >= T#5s", "Step1.t >= T#2s"
 *
 * @param timerName     Repère du temporisateur (ex: "t", "t1", "T_ev1"). Par défaut "t"
 * @param stepReference Nom ou repère de l'étape associée (ex: "X1", "Step1")
 * @param duration      Durée de la temporisation
 * @param durationUnit  Unité temporelle originale ("ms", "s", "min", "h")
 * @param durationValue Valeur numérique de la durée
 * @param format        Format de notation (GRAFCET ou STRUCTURED_TEXT)
 */
public record SfcTimerCondition(
        String timerName,
        String stepReference,
        Duration duration,
        String durationUnit,
        double durationValue,
        Format format
) {
    public enum Format {
        GRAFCET,
        STRUCTURED_TEXT
    }

    // Regex normalisée pour CEI 60848 : [timerName /] stepRef / value unit (ex: t/X1/5s ou t1/X2/500ms ou X1/2s)
    private static final Pattern GRAFCET_PATTERN = Pattern.compile(
            "(?:([a-zA-Z_][a-zA-Z0-9_]*)\\s*/\\s*)?([Xx]\\d+|[a-zA-Z_][a-zA-Z0-9_]*|\\d+)\\s*/\\s*(\\d+(?:\\.\\d+)?)\\s*(ms|s|min|m|h)",
            Pattern.CASE_INSENSITIVE
    );

    // Regex pour CEI 61131-3 ST : stepRef.t >= [T#]value unit (ex: X1.t >= T#5s)
    private static final Pattern ST_PATTERN = Pattern.compile(
            "([Xx]\\d+|[a-zA-Z_][a-zA-Z0-9_]*)\\.t\\s*>=\\s*(?:T#)?(\\d+(?:\\.\\d+)?)\\s*(ms|s|min|m|h)",
            Pattern.CASE_INSENSITIVE
    );

    public SfcTimerCondition {
        Objects.requireNonNull(stepReference, "La référence de l'étape est obligatoire.");
        Objects.requireNonNull(duration, "La durée est obligatoire.");
        timerName = (timerName == null || timerName.isBlank()) ? "t" : timerName.trim();
        durationUnit = (durationUnit == null || durationUnit.isBlank()) ? "s" : durationUnit.trim().toLowerCase(Locale.ROOT);
        format = (format == null) ? Format.GRAFCET : format;
    }

    /**
     * Crée une condition temporisée normalisée selon la notation Grafcet CEI 60848 (t/Xi/d).
     */
    public static SfcTimerCondition ofGrafcet(String timerName, String stepRef, double value, String unit) {
        Duration dur = parseDuration(value, unit);
        return new SfcTimerCondition(timerName, normalizeStepRef(stepRef), dur, unit, value, Format.GRAFCET);
    }

    /**
     * Crée une condition temporisée normalisée selon la notation Structured Text CEI 61131-3 (Xi.t >= T#d).
     */
    public static SfcTimerCondition ofStructuredText(String stepRef, double value, String unit) {
        Duration dur = parseDuration(value, unit);
        return new SfcTimerCondition("t", normalizeStepRef(stepRef), dur, unit, value, Format.STRUCTURED_TEXT);
    }

    /**
     * Parse une condition temporisée (équivalent à findInExpression).
     */
    public static Optional<SfcTimerCondition> parse(String expression) {
        return findInExpression(expression);
    }

    /**
     * Recherche et extrait la première condition temporisée présente dans une expression de réceptivité.
     */
    public static Optional<SfcTimerCondition> findInExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return Optional.empty();
        }

        // Essai format Grafcet CEI 60848 (prioritaire)
        Matcher grafcetMatcher = GRAFCET_PATTERN.matcher(expression);
        if (grafcetMatcher.find()) {
            String timer = grafcetMatcher.group(1);
            String step = grafcetMatcher.group(2);
            double val = Double.parseDouble(grafcetMatcher.group(3));
            String unit = grafcetMatcher.group(4);
            return Optional.of(new SfcTimerCondition(
                    timer != null ? timer : "t",
                    normalizeStepRef(step),
                    parseDuration(val, unit),
                    unit,
                    val,
                    Format.GRAFCET
            ));
        }

        // Essai format Structured Text CEI 61131-3
        Matcher stMatcher = ST_PATTERN.matcher(expression);
        if (stMatcher.find()) {
            String step = stMatcher.group(1);
            double val = Double.parseDouble(stMatcher.group(2));
            String unit = stMatcher.group(3);
            return Optional.of(new SfcTimerCondition(
                    "t",
                    normalizeStepRef(step),
                    parseDuration(val, unit),
                    unit,
                    val,
                    Format.STRUCTURED_TEXT
            ));
        }

        return Optional.empty();
    }

    /**
     * Formate la temporisation selon la norme CEI 60848 (ex: "t/X1/5s" ou "t1/X2/500ms").
     */
    public String toGrafcetFormat() {
        String t = (timerName == null || timerName.isBlank()) ? "t" : timerName;
        return t + "/" + stepReference + "/" + formatValue(durationValue) + durationUnit;
    }

    /**
     * Formate la temporisation selon la norme CEI 61131-3 ST (ex: "X1.t >= T#5s").
     */
    public String toStructuredTextFormat() {
        return stepReference + ".t >= T#" + formatValue(durationValue) + durationUnit;
    }

    /**
     * Formate l'expression selon le format spécifié par l'instance.
     */
    public String toFormattedString() {
        return format == Format.STRUCTURED_TEXT ? toStructuredTextFormat() : toGrafcetFormat();
    }

    /**
     * Génère la chaîne de condition à partir des paramètres bruts.
     */
    public static String buildCondition(String timerName, String stepRef, double value, String unit, boolean isStructuredText) {
        String cleanStep = normalizeStepRef(stepRef);
        String valStr = formatValue(value);
        String cleanUnit = (unit == null || unit.isBlank()) ? "s" : unit.trim().toLowerCase(Locale.ROOT);
        if (isStructuredText) {
            return cleanStep + ".t >= T#" + valStr + cleanUnit;
        } else {
            String t = (timerName == null || timerName.isBlank()) ? "t" : timerName.trim();
            return t + "/" + cleanStep + "/" + valStr + cleanUnit;
        }
    }

    public static Duration parseDuration(double value, String unit) {
        if (value < 0) {
            throw new IllegalArgumentException("La durée d'une temporisation ne peut pas être négative.");
        }
        String u = (unit == null) ? "s" : unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "ms" -> Duration.ofMillis((long) value);
            case "min", "m" -> Duration.ofMillis((long) (value * 60_000));
            case "h" -> Duration.ofMillis((long) (value * 3_600_000));
            default -> Duration.ofMillis((long) (value * 1000)); // "s" par défaut
        };
    }

    public static String normalizeStepRef(String stepRef) {
        if (stepRef == null || stepRef.isBlank()) {
            return "X1";
        }
        String trimmed = stepRef.trim();
        // Si uniquement un chiffre (ex: "1"), préfixer par "X" (ex: "X1")
        if (trimmed.matches("^\\d+$")) {
            return "X" + trimmed;
        }
        // Si commence par un 'x' minuscule suivi d'un chiffre, majusculer en 'X'
        if (trimmed.matches("(?i)^x\\d+$")) {
            return "X" + trimmed.substring(1);
        }
        return trimmed;
    }

    private static String formatValue(double value) {
        if (value == (long) value) {
            return String.format(Locale.ROOT, "%d", (long) value);
        } else {
            return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }
}
