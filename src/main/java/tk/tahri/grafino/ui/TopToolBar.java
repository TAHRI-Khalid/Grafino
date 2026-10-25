package tk.tahri.grafino.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
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
    private final Button addVariableBtn = new Button();
    private final Button removeVariableBtn = new Button();

    public TopToolBar() {
        stepBtn.getStyleClass().add("toolBarButton");
        var stepImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Step.png"))));
        stepImg.setFitWidth(30);
        stepImg.setFitHeight(30);
        stepBtn.setGraphic(stepImg);

        transitionBtn.getStyleClass().add("toolBarButton");
        var transitionImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Transition.png"))));
        transitionImg.setFitWidth(30);
        transitionImg.setFitHeight(30);
        transitionBtn.setGraphic(transitionImg);

        orBranchBtn.getStyleClass().add("toolBarButton");
        var orImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Or_Branch.png"))));
        orImg.setFitWidth(30);
        orImg.setFitHeight(30);
        orBranchBtn.setGraphic(orImg);

        andBranchBtn.getStyleClass().add("toolBarButton");
        var andImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/And_Branch.png"))));
        andImg.setFitWidth(30);
        andImg.setFitHeight(30);
        andBranchBtn.setGraphic(andImg);

        addVariableBtn.getStyleClass().add("toolBarButton");
        var addVariableImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/addVariable.png"))));
        addVariableImg.setFitWidth(30);
        addVariableImg.setFitHeight(30);
        addVariableBtn.setGraphic(addVariableImg);

        removeVariableBtn.getStyleClass().add("toolBarButton");
        var removeVariableImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/RemoveVariable.png"))));
        removeVariableImg.setFitWidth(30);
        removeVariableImg.setFitHeight(30);
        removeVariableBtn.setGraphic(removeVariableImg);

        stepBtn.setTooltip(new Tooltip("Créer une Étape (S)"));
        transitionBtn.setTooltip(new Tooltip("Créer une Transition (T)"));
        orBranchBtn.setTooltip(new Tooltip("Divergence en OU (Sélection de séquence)"));
        andBranchBtn.setTooltip(new Tooltip("Divergence en ET (Parallélisme / Synchronisation)"));

        this.setStyle("-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";");
        this.setPadding(new Insets(6, 12, 6, 12));

        this.getItems().addAll(stepBtn, transitionBtn, orBranchBtn, andBranchBtn);
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

    public void setOnAddVariableBtnClick(EventHandler<ActionEvent> event) {
        this.addVariableBtn.setOnAction(event);
    }

    public void setOnRemoveSelectedVariable(EventHandler<ActionEvent> event) {
        this.removeVariableBtn.setOnAction(event);
    }

    public void setGrafcetToolsVisible(boolean value) {
        // Maintenu pour compatibilité si nécessaire
    }
}
