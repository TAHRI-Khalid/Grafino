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
 * Harmonisé avec la charte graphique de l'éditeur Arduino.
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
        this.setStyle(AppTheme.SIDEBAR_STYLE);

        // 1. En-tête avec titre, badge et bouton fermer
        var header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        var titleBox = new VBox(2);
        var subTitle = new Label("PROPRIÉTÉS DE LA TRANSITION");
        subTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        var badge = new Label("Transition T" + transition.getNumber());
        badge.setStyle(AppTheme.pillBadgeStyle(AppTheme.ORANGE_LIGHT_BG, AppTheme.ORANGE) +
                "; -fx-border-color: " + AppTheme.ORANGE_BORDER + "; -fx-border-radius: 12px; -fx-font-size: 13px;");
        titleBox.getChildren().addAll(subTitle, badge);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(titleBox, spacer);

        if (onClose != null) {
            var closeBtn = new Button("✕");
            closeBtn.setTooltip(new Tooltip("Fermer le panneau latéral"));
            closeBtn.setStyle(AppTheme.CLOSE_BUTTON_STYLE);
            closeBtn.setOnAction(e -> onClose.run());
            header.getChildren().add(closeBtn);
        }

        this.getChildren().add(header);

        // 2. Carte : Saisie de la réceptivité Structured Text (ST)
        var recCard = new VBox(6);
        recCard.setStyle(AppTheme.CARD_STYLE);

        var recLabel = new Label("Condition / Réceptivité booléenne (ST) :");
        recLabel.setStyle(AppTheme.SECTION_HEADER_STYLE);

        var receptivityField = new TextField(transition.getReceptivity());
        receptivityField.setStyle(AppTheme.MONO_FIELD_STYLE);
        receptivityField.setPromptText("ex: dcy AND NOT arret, 1");

        // Mise à jour instantanée du tracé graphique sur le Canvas
        receptivityField.textProperty().addListener((obs, oldV, newV) -> {
            transition.setReceptivity(newV);
            if (onModified != null) {
                onModified.run();
            }
        });

        recCard.getChildren().addAll(recLabel, receptivityField);
        this.getChildren().add(recCard);

        // 3. Carte : Opérateurs logiques rapides
        var opCard = new VBox(8);
        opCard.setStyle(AppTheme.CARD_STYLE);

        var opLabel = new Label("Opérateurs logiques rapides :");
        opLabel.setStyle(AppTheme.SECTION_HEADER_STYLE);

        var opButtons = new FlowPane(6, 6);
        addOperatorBtn(opButtons, receptivityField, "ET ( . )", " . ");
        addOperatorBtn(opButtons, receptivityField, "OU ( + )", " + ");
        addOperatorBtn(opButtons, receptivityField, "NON ( / )", "/");
        addOperatorBtn(opButtons, receptivityField, "= 1 (Vrai)", "1");
        addOperatorBtn(opButtons, receptivityField, "Front ↑", "↑");
        addOperatorBtn(opButtons, receptivityField, "Front ↓", "↓");
        addOperatorBtn(opButtons, receptivityField, "( )", "()");

        opCard.getChildren().addAll(opLabel, opButtons);
        this.getChildren().add(opCard);

        // 4. Carte : Puces des capteurs d'entrée (%I)
        var inputsCard = new VBox(8);
        inputsCard.setStyle(AppTheme.CARD_STYLE);

        var inputsLabel = new Label("Entrées capteurs disponibles (%I) :");
        inputsLabel.setStyle(AppTheme.SECTION_HEADER_STYLE);
        inputsCard.getChildren().add(inputsLabel);

        if (inputVariables == null || inputVariables.isEmpty()) {
            var emptyLabel = new Label("Aucune entrée %I définie dans la table des variables.");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_HINT + "; -fx-font-style: italic; -fx-padding: 4;");
            inputsCard.getChildren().add(emptyLabel);
        } else {
            var chipsPane = new FlowPane(6, 6);
            for (Variable varInput : inputVariables) {
                var chip = new Button("+ " + varInput.getName());
                chip.setTooltip(new Tooltip(varInput.getAddress() + (varInput.getComment().isEmpty() ? "" : " : " + varInput.getComment())));
                chip.setStyle(AppTheme.CHIP_INPUT_STYLE);
                chip.setOnAction(e -> insertTextAtCursor(receptivityField, varInput.getName()));
                chipsPane.getChildren().add(chip);
            }
            inputsCard.getChildren().add(chipsPane);
        }

        this.getChildren().add(inputsCard);
    }

    private void addOperatorBtn(FlowPane pane, TextField field, String label, String insertValue) {
        var btn = new Button(label);
        btn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
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
