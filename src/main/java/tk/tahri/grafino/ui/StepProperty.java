package tk.tahri.grafino.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.Action;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Variable;

import java.util.List;

public class StepProperty extends VBox {

    private VBox listOfActions = new VBox(5);

    public StepProperty(Step step, List<Variable> outputVaribles) {
        this.setPrefWidth(400);
        var title = new Label("Step : S" + step.getNumber());
        title.getStyleClass().add("title");
        var separator = new Separator();

        this.getChildren().addAll(title, separator, listOfActions);
        showActions(step);

        var label = new Label("Actions :");
        label.setPrefWidth(270);

        var qualifierComboBox = new ComboBox<String>();
        qualifierComboBox.getItems().addAll("Normal", "Set", "Reset");
        qualifierComboBox.getSelectionModel().select(0);
        qualifierComboBox.setMaxWidth(Double.MAX_VALUE);

        var actionBox = new TilePane();
        actionBox.setAlignment(Pos.CENTER);

        outputVaribles.forEach(variable -> {
            var btnAction = new Button(variable.getName());
            actionBox.getChildren().add(btnAction);
            btnAction.setMnemonicParsing(false);
            btnAction.setWrapText(true);
            btnAction.setOnAction(actionEvent -> {
                var action = new Action(
                        qualifierComboBox.getSelectionModel().getSelectedItem(),
                        variable
                );
                if (!step.getActions().contains(action)) {
                    step.getActions().add(action);
                    showActions(step);
                }
            });
        });

        this.getChildren().addAll(label, qualifierComboBox, actionBox);
    }

    private void showActions(Step step) {
        listOfActions.getChildren().clear();
        step.getActions().forEach(action -> {
            var actionView = new ActionView(step,action,listOfActions);
            listOfActions.getChildren().add(actionView);
        });
    }
}

class ActionView extends HBox {
    public ActionView(Step step, Action action, VBox container) {
        this.getStyleClass().add("action-control");
        var btnDelete = new Button("x");

        btnDelete.setOnAction(actionEvent -> {
            step.getActions().remove(action);
            container.getChildren().remove(this);
        });

        var output = new Label(action.variable().getName());
        var actionQualifier = new Label(action.qualifier().charAt(0)+" : ");
        this.setSpacing(5);
        var region=new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        this.getChildren().addAll(actionQualifier, output, region, btnDelete);
    }
}
