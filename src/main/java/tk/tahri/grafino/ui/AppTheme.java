package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * Charte graphique unifiée Grafino inspirée de l'éditeur Arduino (Arduino IDE 2.x).
 * Centralise les couleurs, typographies, élévations et styles des composants UI.
 */
public final class AppTheme {

    private AppTheme() {
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
            "-fx-font-size: 12px;" +
            "-fx-text-fill: " + TEXT_MAIN + ";";

    public static final String LABEL_MUTED_STYLE =
            "-fx-font-size: 11px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";";

    // Styles des champs de saisie
    public static final String TEXT_FIELD_STYLE =
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-padding: 6px 10px;" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-font-size: 12px;";

    public static final String MONO_FIELD_STYLE =
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-padding: 6px 10px;" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-font-family: 'JetBrains Mono', 'Consolas', 'Menlo', monospace;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;";

    // Styles des boutons
    public static final String PRIMARY_BUTTON_STYLE =
            "-fx-background-color: " + PRIMARY_TEAL + ";" +
            "-fx-text-fill: #FFFFFF;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 12px;" +
            "-fx-padding: 6px 14px;" +
            "-fx-background-radius: 6px;" +
            "-fx-cursor: hand;";

    public static final String SECONDARY_BUTTON_STYLE =
            "-fx-background-color: " + CARD_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-text-fill: " + TEXT_MAIN + ";" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 4px 8px;" +
            "-fx-background-radius: 4px;" +
            "-fx-border-radius: 4px;" +
            "-fx-cursor: hand;";

    public static final String CHIP_INPUT_STYLE =
            "-fx-background-color: " + TEAL_LIGHT_BG + ";" +
            "-fx-border-color: " + TEAL_LIGHT_BORDER + ";" +
            "-fx-text-fill: " + PRIMARY_TEAL_DARK + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 3px 10px;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-radius: 12px;" +
            "-fx-cursor: hand;";

    public static final String CLOSE_BUTTON_STYLE =
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 2px 6px;" +
            "-fx-cursor: hand;";

    public static String pillBadgeStyle(String bgHex, String textHex) {
        return "-fx-background-color: " + bgHex + ";" +
               "-fx-text-fill: " + textHex + ";" +
               "-fx-font-weight: bold;" +
               "-fx-font-size: 11px;" +
               "-fx-padding: 3px 9px;" +
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
