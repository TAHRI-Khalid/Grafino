package tk.tahri.grafino.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.Component;
import java.util.Objects;

public class TopToolBar extends ToolBar {
    private final Button stepBtn = new Button();
    private final Button transitionBtn = new Button();
    //private final Button endBtn = new Button();
    //private final Button jumpBtn = new Button();
    private final Button orBranchBtn = new Button();
    private final Button andBranchBtn = new Button();
    private final Button addVariableBtn = new Button();
    private final Button removeVariableBtn = new Button();
    private final Button grafcetBtn = new Button();
    private final Button variableBtn = new Button();

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

        variableBtn.getStyleClass().add("toolBarButton");
        var variableImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/variables.png"))));
        variableImg.setFitWidth(30);
        variableImg.setFitHeight(30);
        variableBtn.setGraphic(variableImg);

        grafcetBtn.getStyleClass().add("toolBarButton");
        var grafcetImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/grafcet.png"))));
        grafcetImg.setFitWidth(30);
        grafcetImg.setFitHeight(30);
        grafcetBtn.setGraphic(grafcetImg);

        this.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        this.setGrafcetToolsVisible(true);
        this.setPadding(new Insets(10));

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

    public void setOnGrafcetBtnClick(EventHandler<ActionEvent> event) {
        this.grafcetBtn.setOnAction(event);
    }

    public void setOnVariableTableBtnClick(EventHandler<ActionEvent> event) {
        this.variableBtn.setOnAction(event);
    }

    public void setGrafcetToolsVisible(boolean value) {
        this.getItems().clear();
        var region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        if (value) {
            this.getItems().addAll(stepBtn, transitionBtn, orBranchBtn, andBranchBtn, region, grafcetBtn, variableBtn);
        } else {
            this.getItems().addAll(addVariableBtn, removeVariableBtn, region, grafcetBtn, variableBtn);
        }
    }
}
