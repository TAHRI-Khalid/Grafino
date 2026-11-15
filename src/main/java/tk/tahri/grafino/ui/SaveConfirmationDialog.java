package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;

/**
 * Dialogue modal de confirmation d'enregistrement lors de la fermeture ou du
 * changement de projet, aligné sur la charte graphique Grafino ({@link AppTheme}).
 */
public class SaveConfirmationDialog extends Dialog<MainUI.CloseChoice> {

    private static final String DIALOG_PRIMARY_BTN_STYLE =
            "-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";" +
            "-fx-border-color: " + AppTheme.PRIMARY_TEAL + ";" +
            "-fx-text-fill: #FFFFFF;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-background-radius: 6px;" +
            "-fx-border-radius: 6px;" +
            "-fx-cursor: hand;";

    private static final String DIALOG_PRIMARY_BTN_HOVER_STYLE =
            "-fx-background-color: " + AppTheme.PRIMARY_TEAL_DARK + ";" +
            "-fx-border-color: " + AppTheme.PRIMARY_TEAL_DARK + ";" +
            "-fx-text-fill: #FFFFFF;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-background-radius: 6px;" +
            "-fx-border-radius: 6px;" +
            "-fx-cursor: hand;";

    private static final String DIALOG_DANGER_BTN_STYLE =
            "-fx-background-color: " + AppTheme.CARD_BG + ";" +
            "-fx-border-color: " + AppTheme.RED_BORDER + ";" +
            "-fx-text-fill: " + AppTheme.RED_DANGER + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 7px 14px;" +
            "-fx-background-radius: 6px;" +
            "-fx-border-radius: 6px;" +
            "-fx-cursor: hand;";

    private static final String DIALOG_DANGER_BTN_HOVER_STYLE =
            "-fx-background-color: " + AppTheme.RED_LIGHT_BG + ";" +
            "-fx-border-color: " + AppTheme.RED_DANGER + ";" +
            "-fx-text-fill: " + AppTheme.RED_DANGER + ";" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 7px 14px;" +
            "-fx-background-radius: 6px;" +
            "-fx-border-radius: 6px;" +
            "-fx-cursor: hand;";

    private static final String DIALOG_SECONDARY_BTN_STYLE =
            "-fx-background-color: " + AppTheme.CARD_BG + ";" +
            "-fx-border-color: " + AppTheme.BORDER_COLOR + ";" +
            "-fx-text-fill: " + AppTheme.TEXT_MAIN + ";" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 7px 14px;" +
            "-fx-background-radius: 6px;" +
            "-fx-border-radius: 6px;" +
            "-fx-cursor: hand;";

    private static final String DIALOG_SECONDARY_BTN_HOVER_STYLE =
            "-fx-background-color: " + AppTheme.TEAL_LIGHT_BG + ";" +
            "-fx-border-color: " + AppTheme.ACCENT_TEAL + ";" +
            "-fx-text-fill: " + AppTheme.PRIMARY_TEAL_DARK + ";" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 7px 14px;" +
            "-fx-background-radius: 6px;" +
            "-fx-border-radius: 6px;" +
            "-fx-cursor: hand;";

    private final ButtonType btnSave = new ButtonType("Enregistrer", ButtonBar.ButtonData.YES);
    private final ButtonType btnDiscard = new ButtonType("Abandonner", ButtonBar.ButtonData.NO);
    private final ButtonType btnCancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

    public SaveConfirmationDialog(Window owner, String projectName) {
        if (owner != null) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
        } else {
            initModality(Modality.APPLICATION_MODAL);
        }
        setResizable(false);
        setTitle("Grafino — Enregistrement");

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("grafino-dialog");
        pane.setStyle("-fx-background-color: " + AppTheme.PANEL_BG + "; -fx-padding: 0;");

        URL cssUrl = getClass().getResource("/css/style.css");
        if (cssUrl != null) {
            pane.getStylesheets().add(cssUrl.toExternalForm());
        }

        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldWin, newWin) -> attachAppIcon(newWin));
                attachAppIcon(newScene.getWindow());
            }
        });

        pane.setHeader(null);
        pane.setGraphic(null);
        pane.setContent(buildContent(projectName));

        pane.getButtonTypes().setAll(btnDiscard, btnCancel, btnSave);
        configureButtons(pane);

        setResultConverter(buttonType -> {
            if (buttonType == btnSave) {
                return MainUI.CloseChoice.SAVE;
            }
            if (buttonType == btnDiscard) {
                return MainUI.CloseChoice.DISCARD;
            }
            return MainUI.CloseChoice.CANCEL;
        });
    }

    private VBox buildContent(String projectName) {
        Region topAccent = new Region();
        topAccent.setPrefHeight(4);
        topAccent.setMaxWidth(Double.MAX_VALUE);
        topAccent.setStyle("-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";");

        StackPane iconBadge = new StackPane();
        iconBadge.setPrefSize(44, 44);
        iconBadge.setMinSize(44, 44);
        iconBadge.setStyle(
                "-fx-background-color: " + AppTheme.TEAL_LIGHT_BG + ";" +
                "-fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + ";" +
                "-fx-border-radius: 10px;" +
                "-fx-background-radius: 10px;"
        );
        Label iconLabel = new Label("💾");
        iconLabel.setStyle("-fx-font-size: 20px;");
        iconBadge.getChildren().add(iconLabel);

        Label titleLabel = new Label("Enregistrer les modifications ?");
        titleLabel.setStyle(
                "-fx-font-size: 15.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + AppTheme.TEXT_MAIN + ";"
        );

        String displayName = (projectName == null || projectName.isBlank()) ? "Nouveau_Projet" : projectName;
        Label projectPrefix = new Label("Projet :");
        projectPrefix.setStyle(AppTheme.LABEL_MUTED_STYLE);

        Label projectBadge = new Label(displayName);
        projectBadge.setStyle(
                AppTheme.pillBadgeStyle(AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK) +
                "; -fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + "; -fx-border-radius: 12px;"
        );

        HBox projectRow = new HBox(8, projectPrefix, projectBadge);
        projectRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(6, titleLabel, projectRow);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        HBox headerBox = new HBox(14, iconBadge, titleBox);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(18, 22, 16, 22));
        headerBox.setStyle(
                "-fx-background-color: " + AppTheme.CARD_BG + ";" +
                "-fx-border-color: " + AppTheme.BORDER_COLOR + ";" +
                "-fx-border-width: 0 0 1px 0;"
        );

        Label messageLabel = new Label(
                "Des modifications n'ont pas été enregistrées.\n" +
                "Voulez-vous enregistrer vos changements avant de continuer, ou les abandonner ?"
        );
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(390);
        messageLabel.setStyle(
                "-fx-font-size: 13px;" +
                "-fx-text-fill: " + AppTheme.TEXT_MUTED + ";" +
                "-fx-line-spacing: 3px;"
        );

        VBox bodyBox = new VBox(messageLabel);
        bodyBox.setPadding(new Insets(16, 22, 14, 22));
        bodyBox.setStyle("-fx-background-color: " + AppTheme.PANEL_BG + ";");

        VBox root = new VBox(topAccent, headerBox, bodyBox);
        root.setPrefWidth(440);
        return root;
    }

    private void configureButtons(DialogPane pane) {
        styleDialogButton(pane.lookupButton(btnSave), DIALOG_PRIMARY_BTN_STYLE, DIALOG_PRIMARY_BTN_HOVER_STYLE, true, false);
        styleDialogButton(pane.lookupButton(btnDiscard), DIALOG_DANGER_BTN_STYLE, DIALOG_DANGER_BTN_HOVER_STYLE, false, false);
        styleDialogButton(pane.lookupButton(btnCancel), DIALOG_SECONDARY_BTN_STYLE, DIALOG_SECONDARY_BTN_HOVER_STYLE, false, true);
    }

    private static void styleDialogButton(Node node, String normalStyle, String hoverStyle, boolean isDefault, boolean isCancel) {
        if (!(node instanceof Button button)) {
            return;
        }
        button.setStyle(normalStyle);
        button.setDefaultButton(isDefault);
        button.setCancelButton(isCancel);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(normalStyle));
    }

    private void attachAppIcon(Window window) {
        if (!(window instanceof Stage stage) || !stage.getIcons().isEmpty()) {
            return;
        }
        URL iconUrl = getClass().getResource("/icons/app-icon.png");
        if (iconUrl != null) {
            stage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
    }

    public ButtonType getSaveButtonType() {
        return btnSave;
    }

    public ButtonType getDiscardButtonType() {
        return btnDiscard;
    }

    public ButtonType getCancelButtonType() {
        return btnCancel;
    }
}
