package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.Action;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Variable;

import java.util.List;

public class StepProperty extends VBox {

    private final VBox listOfActions = new VBox(8);
    private final Runnable onUpdate;

    public StepProperty(Step step, List<Variable> outputVariables, Runnable onUpdate) {
        this.onUpdate = onUpdate;
        this.getStyleClass().add("property-box");
        this.setPrefWidth(380);
        this.setSpacing(12);
        this.setPadding(new Insets(15));

        var title = new Label("Étape : " + step.getNumber());
        title.getStyleClass().add("property-title");
        var separator = new Separator();

        // Numéro d'étape
        var numberBox = new HBox(10);
        var numberLabel = new Label("Numéro :");
        numberLabel.setPrefWidth(100);
        var numberField = new Spinner<Integer>(0, 9999, step.getNumber());
        numberField.setEditable(true);
        numberField.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                step.setNumber(newVal);
                title.setText("Étape : " + newVal);
                if (onUpdate != null) onUpdate.run();
            }
        });
        numberBox.getChildren().addAll(numberLabel, numberField);

        // Case à cocher Étape Initiale (CEI 60848)
        var initCheckBox = new CheckBox("Étape Initiale (Double encadrement)");
        initCheckBox.setSelected(step.isInitial());
        initCheckBox.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-main-color;");
        initCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            step.setInitial(newVal);
            if (onUpdate != null) onUpdate.run();
        });

        // Section Actions
        var actionsHeader = new Label("Actions associées :");
        actionsHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        var qualLabel = new Label("Qualificateur d'action (CEI 60848) :");
        var qualifierComboBox = new ComboBox<String>();
        qualifierComboBox.getItems().addAll(
                "N (Normal / Continu)",
                "S (Set / Mémorisation)",
                "R (Reset / Désactivation)",
                "D (Delayed / Retardée)",
                "P (Pulse / Impulsion)"
        );
        qualifierComboBox.getSelectionModel().select(0);
        qualifierComboBox.setMaxWidth(Double.MAX_VALUE);

        var actionsBox = new FlowPane(6, 6);
        actionsBox.getStyleClass().add("actions-box");

        if (outputVariables != null && !outputVariables.isEmpty()) {
            outputVariables.forEach(variable -> {
                if (variable.getName() != null && !variable.getName().isBlank()) {
                    var btnAction = new Button("+ " + variable.getName());
                    btnAction.getStyleClass().add("action-btn");
                    btnAction.setOnAction(actionEvent -> {
                        String selected = qualifierComboBox.getSelectionModel().getSelectedItem();
                        String qualCode = selected.substring(0, 1);
                        var action = new Action(qualCode, variable);
                        if (!step.getActions().contains(action)) {
                            step.getActions().add(action);
                            showActions(step);
                            if (onUpdate != null) onUpdate.run();
                        }
                    });
                    actionsBox.getChildren().add(btnAction);
                }
            });
        } else {
            var emptyLabel = new Label("Aucune variable de sortie disponible.");
            emptyLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
            actionsBox.getChildren().add(emptyLabel);
        }

        showActions(step);

        this.getChildren().addAll(
                title,
                separator,
                numberBox,
                initCheckBox,
                new Separator(),
                actionsHeader,
                qualLabel,
                qualifierComboBox,
                actionsBox,
                new Separator(),
                listOfActions
        );
    }

    private void showActions(Step step) {
        listOfActions.getChildren().clear();
        step.getActions().forEach(action -> {
            var actionView = new ActionView(step, action, listOfActions, onUpdate);
            listOfActions.getChildren().add(actionView);
        });
    }
}

class ActionView extends HBox {
    public ActionView(Step step, Action action, VBox container, Runnable onUpdate) {
        this.getStyleClass().add("action-control");
        var btnDelete = new Button("✕");

        btnDelete.setOnAction(actionEvent -> {
            step.getActions().remove(action);
            container.getChildren().remove(this);
            if (onUpdate != null) onUpdate.run();
        });

        String qual = action.qualifier() != null ? action.qualifier() : "N";
        var actionQualifier = new Label("[" + qual + "] ");
        actionQualifier.setStyle("-fx-font-weight: bold; -fx-text-fill: #FFCC00;");

        var output = new Label(action.variable() != null ? action.variable().getName() : "");
        output.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");

        this.setSpacing(8);
        var region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        this.getChildren().addAll(actionQualifier, output, region, btnDelete);
    }
}
