package tk.tahri.grafino.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.Component;
import java.util.Objects;

public class TopToolBar extends ToolBar {
    private final Button stepBtn = new Button();
    private final Button transitionBtn = new Button();
    private final Button orBranchBtn = new Button();
    private final Button andBranchBtn = new Button();
    private final Button deleteBtn = new Button("🗑");

    // Undo / Redo
    private final Button undoBtn = new Button("↶");
    private final Button redoBtn = new Button("↷");

    // Zoom & Grid controls
    private final Button zoomInBtn = new Button("＋");
    private final Button zoomOutBtn = new Button("－");
    private final Button zoomFitBtn = new Button("⛶");
    private final Button snapBtn = new Button("🧲");

    // Variable & View toggles
    private final Button addVariableBtn = new Button();
    private final Button removeVariableBtn = new Button();
    private final Button grafcetBtn = new Button();
    private final Button variableBtn = new Button();

    public TopToolBar() {
        stepBtn.getStyleClass().add("toolBarButton");
        var stepImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Step.png"))));
        stepImg.setFitWidth(28);
        stepImg.setFitHeight(28);
        stepBtn.setGraphic(stepImg);
        stepBtn.setTooltip(new Tooltip("Ajouter une Étape"));

        transitionBtn.getStyleClass().add("toolBarButton");
        var transitionImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Transition.png"))));
        transitionImg.setFitWidth(28);
        transitionImg.setFitHeight(28);
        transitionBtn.setGraphic(transitionImg);
        transitionBtn.setTooltip(new Tooltip("Ajouter une Transition"));

        orBranchBtn.getStyleClass().add("toolBarButton");
        var orImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Or_Branch.png"))));
        orImg.setFitWidth(28);
        orImg.setFitHeight(28);
        orBranchBtn.setGraphic(orImg);
        orBranchBtn.setTooltip(new Tooltip("Divergence en OU (Sélection de séquence)"));

        andBranchBtn.getStyleClass().add("toolBarButton");
        var andImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/And_Branch.png"))));
        andImg.setFitWidth(28);
        andImg.setFitHeight(28);
        andBranchBtn.setGraphic(andImg);
        andBranchBtn.setTooltip(new Tooltip("Divergence en ET (Parallélisme / Synchronisation)"));

        deleteBtn.getStyleClass().add("toolBarButton");
        deleteBtn.setStyle("-fx-font-size: 15px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        deleteBtn.setTooltip(new Tooltip("Supprimer l'élément sélectionné (Touche Suppr)"));

        // Undo & Redo
        undoBtn.getStyleClass().add("toolBarButton");
        undoBtn.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -fx-main-color;");
        undoBtn.setTooltip(new Tooltip("Annuler (Ctrl + Z)"));
        undoBtn.setDisable(true);

        redoBtn.getStyleClass().add("toolBarButton");
        redoBtn.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -fx-main-color;");
        redoBtn.setTooltip(new Tooltip("Rétablir (Ctrl + Y)"));
        redoBtn.setDisable(true);

        // Zoom
        zoomInBtn.getStyleClass().add("toolBarButton");
        zoomInBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        zoomInBtn.setTooltip(new Tooltip("Zoom Avant (Ctrl + Molette Haut)"));

        zoomOutBtn.getStyleClass().add("toolBarButton");
        zoomOutBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        zoomOutBtn.setTooltip(new Tooltip("Zoom Arrière (Ctrl + Molette Bas)"));

        zoomFitBtn.getStyleClass().add("toolBarButton");
        zoomFitBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        zoomFitBtn.setTooltip(new Tooltip("Recentrer tout le schéma (Ctrl + 0)"));

        snapBtn.getStyleClass().add("toolBarButton");
        snapBtn.setStyle("-fx-font-size: 14px;");
        snapBtn.setTooltip(new Tooltip("Activer / Désactiver le magnétisme grille"));

        // Variables
        addVariableBtn.getStyleClass().add("toolBarButton");
        var addVariableImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/addVariable.png"))));
        addVariableImg.setFitWidth(28);
        addVariableImg.setFitHeight(28);
        addVariableBtn.setGraphic(addVariableImg);
        addVariableBtn.setTooltip(new Tooltip("Ajouter une Variable"));

        removeVariableBtn.getStyleClass().add("toolBarButton");
        var removeVariableImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/RemoveVariable.png"))));
        removeVariableImg.setFitWidth(28);
        removeVariableImg.setFitHeight(28);
        removeVariableBtn.setGraphic(removeVariableImg);
        removeVariableBtn.setTooltip(new Tooltip("Supprimer la Variable"));

        variableBtn.getStyleClass().add("toolBarButton");
        var variableImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/variables.png"))));
        variableImg.setFitWidth(28);
        variableImg.setFitHeight(28);
        variableBtn.setGraphic(variableImg);
        variableBtn.setTooltip(new Tooltip("Table des Variables (E/S)"));

        grafcetBtn.getStyleClass().add("toolBarButton");
        var grafcetImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/grafcet.png"))));
        grafcetImg.setFitWidth(28);
        grafcetImg.setFitHeight(28);
        grafcetBtn.setGraphic(grafcetImg);
        grafcetBtn.setTooltip(new Tooltip("Éditeur Grafcet"));

        this.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        this.setGrafcetToolsVisible(true);
        this.setPadding(new Insets(6, 10, 6, 10));
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

    public void setOnUndoClick(EventHandler<ActionEvent> event) {
        this.undoBtn.setOnAction(event);
    }

    public void setOnRedoClick(EventHandler<ActionEvent> event) {
        this.redoBtn.setOnAction(event);
    }

    public void setOnZoomInClick(EventHandler<ActionEvent> event) {
        this.zoomInBtn.setOnAction(event);
    }

    public void setOnZoomOutClick(EventHandler<ActionEvent> event) {
        this.zoomOutBtn.setOnAction(event);
    }

    public void setOnZoomFitClick(EventHandler<ActionEvent> event) {
        this.zoomFitBtn.setOnAction(event);
    }

    public void setOnToggleSnapClick(EventHandler<ActionEvent> event) {
        this.snapBtn.setOnAction(event);
    }

    public void setOnAddVariableBtnClick(EventHandler<ActionEvent> event) {
        this.addVariableBtn.setOnAction(event);
    }

    public void setOnRemoveSelectedVariable(EventHandler<ActionEvent> event) {
        this.removeVariableBtn.setOnAction(event);
    }

    public void setOnGrafcetBtnClick(EventHandler<ActionEvent> event) {
        this.grafcetBtn.setOnAction(event);
    }

    public void setOnVariableTableBtnClick(EventHandler<ActionEvent> event) {
        this.variableBtn.setOnAction(event);
    }

    public void setUndoEnabled(boolean enabled) {
        this.undoBtn.setDisable(!enabled);
    }

    public void setRedoEnabled(boolean enabled) {
        this.redoBtn.setDisable(!enabled);
    }

    public void setGrafcetToolsVisible(boolean value) {
        this.getItems().clear();
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (value) {
            this.getItems().addAll(
                    stepBtn, transitionBtn, orBranchBtn, andBranchBtn, deleteBtn,
                    new Separator(),
                    undoBtn, redoBtn,
                    new Separator(),
                    zoomInBtn, zoomOutBtn, zoomFitBtn, snapBtn,
                    spacer,
                    grafcetBtn, variableBtn
            );
        } else {
            this.getItems().addAll(
                    addVariableBtn, removeVariableBtn,
                    spacer,
                    grafcetBtn, variableBtn
            );
        }
    }
}
