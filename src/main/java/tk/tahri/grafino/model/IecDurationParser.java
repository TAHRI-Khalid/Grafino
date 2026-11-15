package tk.tahri.grafino.model;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Utilitaire centralisé de parsing des durées CEI 61131-3 / CEI 60848
 * (formats {@code T#5s}, {@code TIME#500ms}, {@code 2m}, {@code 1.5s}, etc.).
 */
public final class IecDurationParser {

    private IecDurationParser() {
    }

    /**
     * Analyse une chaîne de durée CEI et retourne la {@link Duration} correspondante si valide.
     */
    public static Optional<Duration> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        String upper = s.toUpperCase(Locale.ROOT);
        if (upper.startsWith("T#") || upper.startsWith("TIME#")) {
            s = s.substring(s.indexOf('#') + 1).trim();
        }
        if (s.isEmpty()) {
            return Optional.empty();
        }

        String lower = s.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith("ms")) {
                double val = Double.parseDouble(s.substring(0, s.length() - 2).trim());
                return Optional.of(fromValueAndUnit(val, "ms"));
            }
            if (lower.endsWith("min")) {
                double val = Double.parseDouble(s.substring(0, s.length() - 3).trim());
                return Optional.of(fromValueAndUnit(val, "min"));
            }
            if (lower.endsWith("s")) {
                double val = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return Optional.of(fromValueAndUnit(val, "s"));
            }
            if (lower.endsWith("m")) {
                double val = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return Optional.of(fromValueAndUnit(val, "m"));
            }
            if (lower.endsWith("h")) {
                double val = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return Optional.of(fromValueAndUnit(val, "h"));
            }
            double val = Double.parseDouble(s);
            return Optional.of(fromValueAndUnit(val, "s"));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    /**
     * Construit une {@link Duration} à partir d'une valeur numérique positive et d'une unité CEI.
     */
    public static Duration fromValueAndUnit(double value, String unit) {
        if (value < 0) {
            throw new IllegalArgumentException("La durée d'une temporisation ne peut pas être négative.");
        }
        String u = (unit == null) ? "s" : unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "ms" -> Duration.ofMillis((long) value);
            case "min", "m" -> Duration.ofMillis((long) (value * 60_000));
            case "h" -> Duration.ofMillis((long) (value * 3_600_000));
            default -> Duration.ofMillis((long) (value * 1000));
        };
    }
}
