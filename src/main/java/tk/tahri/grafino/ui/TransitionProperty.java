package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.Transition;
import tk.tahri.grafino.component.Variable;

import java.util.List;

/**
 * Panneau latéral droit d'édition d'une transition SFC (CEI 61131-3 / CEI 60848).
 * Permet la saisie assistée de réceptivités avec opérateurs logiques et puces de capteurs d'entrée.
 */
public class TransitionProperty extends VBox {

    public TransitionProperty(Transition transition) {
        this(transition, List.of(), null, null);
    }

    public TransitionProperty(Transition transition, List<Variable> inputVariables, Runnable onModified) {
        this(transition, inputVariables, onModified, null);
    }

    public TransitionProperty(Transition transition, List<Variable> inputVariables, Runnable onModified, Runnable onClose) {
        this.setPrefWidth(350);
        this.setMinWidth(320);
        this.setSpacing(12);
        this.setPadding(new Insets(14));
        this.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; -fx-border-width: 0 0 0 1;");

        // 1. En-tête avec badge et bouton fermer
        var header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        var badge = new Label("Transition T" + transition.getNumber());
        badge.setStyle("-fx-background-color: #F97316; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 4 10 4 10; -fx-background-radius: 6;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(badge, spacer);

        if (onClose != null) {
            var closeBtn = new Button("✖");
            closeBtn.setTooltip(new Tooltip("Fermer le panneau latéral"));
            closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand;");
            closeBtn.setOnAction(e -> onClose.run());
            header.getChildren().add(closeBtn);
        }

        this.getChildren().addAll(header, new Separator());

        // 2. Zone de saisie de la réceptivité
        var recBox = new VBox(6);
        var recLabel = new Label("Condition / Réceptivité booléenne (ST) :");
        recLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 12px;");

        var receptivityField = new TextField(transition.getReceptivity());
        receptivityField.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 6 10 6 10; -fx-background-radius: 6; -fx-border-color: #CBD5E1; -fx-border-radius: 6;");
        receptivityField.setPromptText("ex: dcy AND NOT arret, 1");

        // Mise à jour instantanée du tracé graphique sur le Canvas
        receptivityField.textProperty().addListener((obs, oldV, newV) -> {
            transition.setReceptivity(newV);
            if (onModified != null) {
                onModified.run();
            }
        });

        recBox.getChildren().addAll(recLabel, receptivityField);
        this.getChildren().add(recBox);

        // 3. Boutons d'opérateurs logiques rapides
        var opBox = new VBox(6);
        var opLabel = new Label("Opérateurs logiques rapides :");
        opLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 12px;");

        var opButtons = new FlowPane(6, 6);
        addOperatorBtn(opButtons, receptivityField, "ET ( . )", " . ");
        addOperatorBtn(opButtons, receptivityField, "OU ( + )", " + ");
        addOperatorBtn(opButtons, receptivityField, "NON ( / )", "/");
        addOperatorBtn(opButtons, receptivityField, "= 1 (Vrai)", "1");
        addOperatorBtn(opButtons, receptivityField, "Front ↑", "↑");
        addOperatorBtn(opButtons, receptivityField, "Front ↓", "↓");
        addOperatorBtn(opButtons, receptivityField, "( )", "()");

        opBox.getChildren().addAll(opLabel, opButtons);
        this.getChildren().addAll(opBox, new Separator());

        // 4. Puces des entrées (%I) disponibles dans la table des variables
        var inputsBox = new VBox(6);
        var inputsLabel = new Label("Entrées capteurs disponibles (%I) :");
        inputsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0F766E; -fx-font-size: 12px;");
        inputsBox.getChildren().add(inputsLabel);

        if (inputVariables == null || inputVariables.isEmpty()) {
            var emptyLabel = new Label("Aucune entrée %I définie dans la table des variables.");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-font-style: italic;");
            inputsBox.getChildren().add(emptyLabel);
        } else {
            var chipsPane = new FlowPane(6, 6);
            for (Variable varInput : inputVariables) {
                var chip = new Button("+ " + varInput.getName());
                chip.setTooltip(new Tooltip(varInput.getAddress() + " : " + varInput.getComment()));
                chip.setStyle("-fx-background-color: #ECFDF5; -fx-border-color: #10B981; -fx-border-radius: 12; -fx-background-radius: 12; -fx-text-fill: #065F46; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
                chip.setOnAction(e -> insertTextAtCursor(receptivityField, varInput.getName()));
                chipsPane.getChildren().add(chip);
            }
            inputsBox.getChildren().add(chipsPane);
        }

        this.getChildren().add(inputsBox);
    }

    private void addOperatorBtn(FlowPane pane, TextField field, String label, String insertValue) {
        var btn = new Button(label);
        btn.setStyle("-fx-background-color: #F1F5F9; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px; -fx-text-fill: #334155; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            if ("1".equals(insertValue)) {
                field.setText("1");
                field.requestFocus();
                field.positionCaret(1);
            } else if ("()".equals(insertValue)) {
                insertTextAtCursor(field, "()");
                field.positionCaret(field.getCaretPosition() - 1);
            } else {
                insertTextAtCursor(field, insertValue);
            }
        });
        pane.getChildren().add(btn);
    }

    private void insertTextAtCursor(TextField field, String text) {
        int pos = field.getCaretPosition();
        String current = field.getText() != null ? field.getText() : "";
        String prefix = current.substring(0, pos);
        String suffix = current.substring(pos);

        // Si le champ ne contenait que "1", on le remplace
        if ("1".equals(current.trim())) {
            field.setText(text);
            field.positionCaret(text.length());
        } else {
            field.setText(prefix + text + suffix);
            field.positionCaret(pos + text.length());
        }
        field.requestFocus();
    }
}
