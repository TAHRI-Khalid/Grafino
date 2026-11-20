package tk.tahri.grafino.ui;

import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Charte graphique unifiée Grafino inspirée de l'éditeur Arduino (Arduino IDE 2.x).
 * Centralise les couleurs, typographies, échelles d'affichage (Normal / Confort / Projecteur),
 * élévations et styles des composants UI.
 */
public final class AppTheme {

    public static final double BASE_ROOT_FONT_PX = 13.0;
    private static final String PREF_SCALE_KEY = "ui.displayScale";

    public enum DisplayScale {
        NORMAL("Normale (100%)", 1.0),
        COMFORT("Grande (120%)", 1.2),
        PROJECTOR("Projecteur (140%)", 1.4);

        private final String label;
        private final double factor;

        DisplayScale(String label, double factor) {
            this.label = label;
            this.factor = factor;
        }

        public String getLabel() {
            return label;
        }

        public double getFactor() {
            return factor;
        }
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(AppTheme.class);
    private static final List<WeakReference<Consumer<DisplayScale>>> SCALE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<String, Font> CANVAS_FONT_CACHE = new ConcurrentHashMap<>();
    private static DisplayScale currentScale = loadSavedScale();

    private AppTheme() {
    }

    private static DisplayScale loadSavedScale() {
        String saved = PREFS.get(PREF_SCALE_KEY, DisplayScale.NORMAL.name());
        try {
            return DisplayScale.valueOf(saved);
        } catch (IllegalArgumentException ignored) {
            return DisplayScale.NORMAL;
        }
    }

    public static DisplayScale getDisplayScale() {
        return currentScale;
    }

    public static double getFontScale() {
        return currentScale.getFactor();
    }

    public static void setDisplayScale(DisplayScale scale) {
        if (scale == null || scale == currentScale) {
            return;
        }
        currentScale = scale;
        PREFS.put(PREF_SCALE_KEY, scale.name());
        for (WeakReference<Consumer<DisplayScale>> ref : SCALE_LISTENERS) {
            Consumer<DisplayScale> listener = ref.get();
            if (listener == null) {
                SCALE_LISTENERS.remove(ref);
            } else {
                listener.accept(scale);
            }
        }
    }

    /**
     * Enregistre un écouteur faible (weak) notifié lors d'un changement d'échelle d'affichage.
     * Conserver une référence forte sur le callback tant que le composant est vivant.
     */
    public static void addScaleListener(Consumer<DisplayScale> listener) {
        if (listener != null) {
            SCALE_LISTENERS.add(new WeakReference<>(listener));
        }
    }

    public static double scaled(double basePx) {
        return Math.round(basePx * currentScale.getFactor() * 10.0) / 10.0;
    }

    public static double scaledPanel(double basePx) {
        double panelFactor = 1.0 + (currentScale.getFactor() - 1.0) * 0.75;
        return Math.round(basePx * panelFactor);
    }

    public static double getCanvasFontScale() {
        return 1.0 + (currentScale.getFactor() - 1.0) * 0.65;
    }

    public static double scaledCanvasFont(double basePx) {
        return Math.round(basePx * getCanvasFontScale() * 10.0) / 10.0;
    }

    /**
     * Retourne une police mise en cache pour le rendu Canvas (Grafcet & Chronogramme),
     * adaptée à l'échelle d'affichage courante sans réallocation à chaque frame.
     */
    public static Font canvasFont(String family, FontWeight weight, double basePx) {
        double size = scaledCanvasFont(basePx);
        String fam = family == null ? "" : family;
        String key = fam + "|" + weight.name() + "|" + size;
        return CANVAS_FONT_CACHE.computeIfAbsent(key, k -> Font.font(fam, weight, size));
    }

    public static String rootFontStyle() {
        return String.format(Locale.ROOT, "-fx-font-size: %.1fpx;", BASE_ROOT_FONT_PX * currentScale.getFactor());
    }

    /**
     * Lie dynamiquement les largeurs préférée et minimale d'un panneau latéral à l'échelle UI active.
     */
    public static void bindScaledPanelWidth(Region panel, double basePrefWidth, double baseMinWidth) {
        Runnable apply = () -> {
            panel.setPrefWidth(scaledPanel(basePrefWidth));
            panel.setMinWidth(scaledPanel(baseMinWidth));
        };
        apply.run();
        Consumer<DisplayScale> listener = s -> apply.run();
        panel.getProperties().put("grafino.scaleListener", listener);
        addScaleListener(listener);
    }

    // Couleurs fondamentales Arduino IDE
    public static final String PRIMARY_TEAL = "#008184";
    public static final String PRIMARY_TEAL_DARK = "#006468";
    public static final String ACCENT_TEAL = "#00979D";
    public static final String TEAL_LIGHT_BG = "#E5F5F5";
    public static final String TEAL_LIGHT_BORDER = "#B2DFDB";

    // Sélection graphique
    public static final String SELECTION_YELLOW = "#FFCC00";

    // Couleurs d'état
    public static final String ORANGE = "#E67E22";
    public static final String ORANGE_LIGHT_BG = "#FEF3E2";
    public static final String ORANGE_BORDER = "#F5CBA7";

    public static final String RED_DANGER = "#D9381E";
    public static final String RED_LIGHT_BG = "#FDE8E4";
    public static final String RED_BORDER = "#F5B7B1";

    public static final String GREEN_SUCCESS = "#27AE60";
    public static final String GREEN_LIGHT_BG = "#E8F8F0";
    public static final String GREEN_BORDER = "#A9DFBF";

    // Neutres & Surfaces
    public static final String PANEL_BG = "#F7F9FA";
    public static final String CARD_BG = "#FFFFFF";
    public static final String BORDER_COLOR = "#E1E4E8";
    public static final String BORDER_LIGHT = "#ECEFF1";

    public static final String TEXT_MAIN = "#232527";
    public static final String TEXT_MUTED = "#62666B";
    public static final String TEXT_HINT = "#8C9095";

    // Styles de conteneurs
    public static final String SIDEBAR_STYLE =
            "-fx-background-color: " + PANEL_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-width: 0 0 0 1px;";

    public static final String CARD_STYLE =
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 10px;";

    public static final String SECTION_HEADER_STYLE =
            "-fx-font-weight: bold;" +
            "-fx-font-size: 1.04em;" +
            "-fx-text-fill: " + TEXT_MAIN + ";";

    public static final String LABEL_MUTED_STYLE =
            "-fx-font-size: 0.96em;" +
            "-fx-text-fill: " + TEXT_MUTED + ";";

    // Styles des champs de saisie
    public static final String TEXT_FIELD_STYLE =
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-padding: 6px 10px;" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-font-size: 1.0em;";

    public static final String MONO_FIELD_STYLE =
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-padding: 6px 10px;" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-font-family: 'JetBrains Mono', 'Consolas', 'Menlo', monospace;" +
            "-fx-font-size: 1.08em;" +
            "-fx-font-weight: bold;";

    // Styles des boutons
    public static final String PRIMARY_BUTTON_STYLE =
            "-fx-background-color: " + PRIMARY_TEAL + ";" +
            "-fx-text-fill: #FFFFFF;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 1.0em;" +
            "-fx-padding: 7px 14px;" +
            "-fx-background-radius: 6px;" +
            "-fx-cursor: hand;";

    public static final String PRIMARY_BUTTON_HOVER_STYLE =
            "-fx-background-color: " + PRIMARY_TEAL_DARK + ";" +
            "-fx-text-fill: #FFFFFF;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 1.0em;" +
            "-fx-padding: 7px 14px;" +
            "-fx-background-radius: 6px;" +
            "-fx-cursor: hand;";

    public static final String SECONDARY_BUTTON_STYLE =
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-font-size: 0.96em;" +
            "-fx-padding: 5px 10px;" +
            "-fx-background-radius: 4px;" +
            "-fx-border-radius: 4px;" +
            "-fx-cursor: hand;";

    public static final String SECONDARY_BUTTON_HOVER_STYLE =
            "-fx-background-color: " + TEAL_LIGHT_BG + ";" +
            "-fx-border-color: " + ACCENT_TEAL + ";" +
            "-fx-text-fill: " + PRIMARY_TEAL_DARK + ";" +
            "-fx-font-size: 0.96em;" +
            "-fx-padding: 5px 10px;" +
            "-fx-background-radius: 4px;" +
            "-fx-border-radius: 4px;" +
            "-fx-cursor: hand;";

    public static final String CHIP_INPUT_STYLE =
            "-fx-background-color: " + TEAL_LIGHT_BG + ";" +
            "-fx-border-color: " + TEAL_LIGHT_BORDER + ";" +
            "-fx-text-fill: " + PRIMARY_TEAL_DARK + ";" +
            "-fx-font-size: 1.15em;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 5px 12px;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-radius: 12px;" +
            "-fx-cursor: hand;";

    public static final String CHIP_INPUT_HOVER_STYLE =
            "-fx-background-color: " + PRIMARY_TEAL + ";" +
            "-fx-border-color: " + PRIMARY_TEAL_DARK + ";" +
            "-fx-text-fill: #FFFFFF;" +
            "-fx-font-size: 1.15em;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 5px 12px;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-radius: 12px;" +
            "-fx-cursor: hand;";

    public static final String CLOSE_BUTTON_STYLE =
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 1.15em;" +
            "-fx-padding: 2px 6px;" +
            "-fx-cursor: hand;";

    public static final String RADIO_BUTTON_STYLE =
            "-fx-font-size: 0.96em;" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-cursor: hand;";

    public static final String RADIO_BUTTON_BOLD_STYLE =
            "-fx-font-size: 0.96em;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-cursor: hand;";

    public static String pillBadgeStyle(String bgHex, String textHex) {
        return "-fx-background-color: " + bgHex + ";" +
               "-fx-text-fill: " + textHex + ";" +
               "-fx-font-weight: bold;" +
               "-fx-font-size: 0.92em;" +
               "-fx-padding: 4px 10px;" +
               "-fx-background-radius: 12px;";
    }

    public static String qualifierBadgeStyle(String qualifier) {
        if (qualifier == null) qualifier = "N";
        return switch (qualifier.toUpperCase()) {
            case "S" -> pillBadgeStyle(GREEN_LIGHT_BG, GREEN_SUCCESS) + "; -fx-border-color: " + GREEN_BORDER + "; -fx-border-radius: 12px;";
            case "R" -> pillBadgeStyle(RED_LIGHT_BG, RED_DANGER) + "; -fx-border-color: " + RED_BORDER + "; -fx-border-radius: 12px;";
            case "L", "D", "SD", "DS", "SL" -> pillBadgeStyle(ORANGE_LIGHT_BG, ORANGE) + "; -fx-border-color: " + ORANGE_BORDER + "; -fx-border-radius: 12px;";
            default -> pillBadgeStyle(TEAL_LIGHT_BG, PRIMARY_TEAL_DARK) + "; -fx-border-color: " + TEAL_LIGHT_BORDER + "; -fx-border-radius: 12px;";
        };
    }
}
