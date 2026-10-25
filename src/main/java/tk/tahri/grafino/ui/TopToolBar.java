package tk.tahri.grafino.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Objects;

public class TopToolBar extends ToolBar {
    private final Button stepBtn = new Button();
    private final Button transitionBtn = new Button();
    private final Button orBranchBtn = new Button();
    private final Button andBranchBtn = new Button();
    private final Button deleteBtn = new Button();
    private final Button autoLayoutBtn = new Button("📐 Aligner");

    private final Button zoomInBtn = new Button("＋");
    private final Button zoomOutBtn = new Button("－");
    private final Button resetZoomBtn = new Button("100%");

    private final Button addVariableBtn = new Button();
    private final Button removeVariableBtn = new Button();

    public TopToolBar() {
        stepBtn.getStyleClass().add("toolBarButton");
        var stepImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Step.png"))));
        stepImg.setFitWidth(28);
        stepImg.setFitHeight(28);
        stepBtn.setGraphic(stepImg);
        stepBtn.setTooltip(new Tooltip("Créer une Étape (S)"));

        transitionBtn.getStyleClass().add("toolBarButton");
        var transitionImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Transition.png"))));
        transitionImg.setFitWidth(28);
        transitionImg.setFitHeight(28);
        transitionBtn.setGraphic(transitionImg);
        transitionBtn.setTooltip(new Tooltip("Créer une Transition (T)"));

        orBranchBtn.getStyleClass().add("toolBarButton");
        var orImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Or_Branch.png"))));
        orImg.setFitWidth(28);
        orImg.setFitHeight(28);
        orBranchBtn.setGraphic(orImg);
        orBranchBtn.setTooltip(new Tooltip("Divergence en OU (Sélection de séquence - B)"));

        andBranchBtn.getStyleClass().add("toolBarButton");
        var andImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/And_Branch.png"))));
        andImg.setFitWidth(28);
        andImg.setFitHeight(28);
        andBranchBtn.setGraphic(andImg);
        andBranchBtn.setTooltip(new Tooltip("Divergence en ET (Parallélisme / Synchronisation)"));

        // Bouton Supprimer
        deleteBtn.setText("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 3 8 3 8;");
        deleteBtn.setTooltip(new Tooltip("Supprimer l'élément sélectionné (Suppr)"));

        // Bouton Auto-Layout
        autoLayoutBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.18); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9;");
        autoLayoutBtn.setTooltip(new Tooltip("Aligner et centrer automatiquement tout le Grafcet (Ctrl+L)"));

        // Boutons de Zoom
        String zoomBtnStyle = "-fx-background-color: rgba(255, 255, 255, 0.15); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9;";
        zoomInBtn.setStyle(zoomBtnStyle);
        zoomInBtn.setTooltip(new Tooltip("Zoom Avant (Ctrl + Molette Haut)"));

        zoomOutBtn.setStyle(zoomBtnStyle);
        zoomOutBtn.setTooltip(new Tooltip("Zoom Arrière (Ctrl + Molette Bas)"));

        resetZoomBtn.setStyle(zoomBtnStyle);
        resetZoomBtn.setTooltip(new Tooltip("Réinitialiser le zoom à 100%"));

        var sep1 = new Separator(Orientation.VERTICAL);
        sep1.setStyle("-fx-opacity: 0.35;");
        var sep2 = new Separator(Orientation.VERTICAL);
        sep2.setStyle("-fx-opacity: 0.35;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.setStyle("-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";");
        this.setPadding(new Insets(6, 12, 6, 12));

        this.getItems().addAll(
                stepBtn, transitionBtn, orBranchBtn, andBranchBtn,
                sep1,
                deleteBtn,
                autoLayoutBtn,
                spacer,
                sep2,
                zoomOutBtn, resetZoomBtn, zoomInBtn
        );
    }

    public void setOnStepBtnClick(EventHandler<ActionEvent> event) {
        this.stepBtn.setOnAction(event);
    }

    public void setOnTransitionBtnClick(EventHandler<ActionEvent> event) {
        this.transitionBtn.setOnAction(event);
    }

    public void setOnOrBranchBtnClick(EventHandler<ActionEvent> event) {
        this.orBranchBtn.setOnAction(event);
    }

    public void setOnAndBranchBtnClick(EventHandler<ActionEvent> event) {
        this.andBranchBtn.setOnAction(event);
    }

    public void setOnDeleteBtnClick(EventHandler<ActionEvent> event) {
        this.deleteBtn.setOnAction(event);
    }

    public void setOnAutoLayoutClick(EventHandler<ActionEvent> event) {
        this.autoLayoutBtn.setOnAction(event);
    }

    public void setOnZoomInClick(EventHandler<ActionEvent> event) {
        this.zoomInBtn.setOnAction(event);
    }

    public void setOnZoomOutClick(EventHandler<ActionEvent> event) {
        this.zoomOutBtn.setOnAction(event);
    }

    public void setOnResetZoomClick(EventHandler<ActionEvent> event) {
        this.resetZoomBtn.setOnAction(event);
    }

    public void setOnAddVariableBtnClick(EventHandler<ActionEvent> event) {
        this.addVariableBtn.setOnAction(event);
    }

    public void setOnRemoveSelectedVariable(EventHandler<ActionEvent> event) {
        this.removeVariableBtn.setOnAction(event);
    }

    public void setGrafcetToolsVisible(boolean value) {
        // Maintenu pour compatibilité
    }
}
