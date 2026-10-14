package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tk.tahri.grafino.component.Transition;
import tk.tahri.grafino.component.Variable;

import java.util.List;

public class TransitionProperty extends VBox {

    public TransitionProperty(Transition transition, List<Variable> inputVariables, Runnable onUpdate) {
        this.getStyleClass().add("property-box");
        this.setPrefWidth(380);
        this.setSpacing(12);
        this.setPadding(new Insets(15));

        var title = new Label("Transition : T" + transition.getNumber());
        title.getStyleClass().add("property-title");
        var separator = new Separator();

        // Numéro de transition
        var numberBox = new HBox(10);
        var numberLabel = new Label("Numéro :");
        numberLabel.setPrefWidth(100);
        var numberField = new Spinner<Integer>(0, 9999, transition.getNumber());
        numberField.setEditable(true);
        numberField.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                transition.setNumber(newVal);
                title.setText("Transition : T" + newVal);
                if (onUpdate != null) onUpdate.run();
            }
        });
        numberBox.getChildren().addAll(numberLabel, numberField);

        // Champ Réceptivité
        var recLabel = new Label("Réceptivité (Condition) :");
        recLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        var recField = new TextField(transition.getReceptivity());
        recField.setPromptText("Ex: 1, m . a, bp_depart, 10s/X1");
        recField.setStyle("-fx-font-family: monospace; -fx-font-size: 15px; -fx-font-weight: bold;");
        recField.textProperty().addListener((obs, oldVal, newVal) -> {
            transition.setReceptivity(newVal);
            if (onUpdate != null) onUpdate.run();
        });

        // Boutons d'insertion d'opérateurs
        var opLabel = new Label("Opérateurs logiques :");
        var opsBox = new FlowPane(6, 6);

        Button btnTrue = new Button("= 1 (Toujours vrai)");
        btnTrue.setOnAction(e -> {
            recField.setText("1");
            recField.requestFocus();
        });

        Button btnAnd = new Button("ET ( . )");
        btnAnd.setOnAction(e -> insertText(recField, " . "));

        Button btnOr = new Button("OU ( + )");
        btnOr.setOnAction(e -> insertText(recField, " + "));

        Button btnNot = new Button("NON ( / )");
        btnNot.setOnAction(e -> insertText(recField, "/"));

        Button btnTimer = new Button("Temporisation (5s/X1)");
        btnTimer.setOnAction(e -> insertText(recField, "5s/X" + transition.getNumber()));

        opsBox.getChildren().addAll(btnTrue, btnAnd, btnOr, btnNot, btnTimer);

        // Variables d'entrée (capteurs / boutons)
        var varsLabel = new Label("Variables d'entrée disponibles :");
        varsLabel.setStyle("-fx-font-weight: bold;");

        var varsFlow = new FlowPane(6, 6);
        if (inputVariables != null && !inputVariables.isEmpty()) {
            for (Variable v : inputVariables) {
                if (v.getName() != null && !v.getName().isBlank()) {
                    Button varBtn = new Button(v.getName());
                    varBtn.getStyleClass().add("action-btn");
                    varBtn.setOnAction(e -> insertText(recField, v.getName()));
                    varsFlow.getChildren().add(varBtn);
                }
            }
        } else {
            var emptyLabel = new Label("Aucune variable déclarée dans la table.");
            emptyLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
            varsFlow.getChildren().add(emptyLabel);
        }

        this.getChildren().addAll(
                title,
                separator,
                numberBox,
                new Separator(),
                recLabel,
                recField,
                opLabel,
                opsBox,
                new Separator(),
                varsLabel,
                varsFlow
        );
    }

    private void insertText(TextField field, String insert) {
        int pos = field.getCaretPosition();
        String current = field.getText() != null ? field.getText() : "";
        if (current.equals("1") && !insert.equals("1")) {
            current = "";
            pos = 0;
        }
        String updated = current.substring(0, pos) + insert + current.substring(pos);
        field.setText(updated);
        field.positionCaret(pos + insert.length());
        field.requestFocus();
    }
}
