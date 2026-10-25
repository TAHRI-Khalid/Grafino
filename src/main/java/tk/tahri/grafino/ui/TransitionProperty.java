package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Transition;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.model.SfcCounterOperation;
import tk.tahri.grafino.model.SfcTimerCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Panneau latéral droit d'édition d'une transition SFC (CEI 61131-3 / CEI 60848).
 * Intègre un configurateur intuitif de temporisateur normalisé (format générique par défaut t/X1/5s),
 * un générateur de tests de compteurs (C < 5, C >= 10...), l'insertion d'opérateurs logiques/comparateurs
 * et la liaison avec les variables d'entrée %I.
 */
public class TransitionProperty extends VBox {

    public TransitionProperty(Transition transition) {
        this(transition, List.of(), List.of(), List.of(), null, null);
    }

    public TransitionProperty(Transition transition, List<Variable> inputVariables, Runnable onModified) {
        this(transition, inputVariables, List.of(), List.of(), onModified, null);
    }

    public TransitionProperty(Transition transition, List<Variable> inputVariables, Runnable onModified, Runnable onClose) {
        this(transition, inputVariables, List.of(), List.of(), onModified, onClose);
    }

    public TransitionProperty(Transition transition, List<Variable> inputVariables, List<Step> availableSteps,
                              Runnable onModified, Runnable onClose) {
        this(transition, inputVariables, availableSteps, List.of(), onModified, onClose);
    }

    public TransitionProperty(Transition transition, List<Variable> inputVariables, List<Step> availableSteps,
                              List<Variable> counterVariables, Runnable onModified, Runnable onClose) {
        this.setPrefWidth(360);
        this.setMinWidth(330);
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

        // 2. Carte : Saisie de la réceptivité Structured Text (ST) / Grafcet
        var recCard = new VBox(6);
        recCard.setStyle(AppTheme.CARD_STYLE);

        var recLabel = new Label("Condition / Réceptivité booléenne (ST) :");
        recLabel.setStyle(AppTheme.SECTION_HEADER_STYLE);

        var receptivityField = new TextField(transition.getReceptivity());
        receptivityField.setStyle(AppTheme.MONO_FIELD_STYLE);
        receptivityField.setPromptText("ex: t/X1/5s, C1 < 5, dcy AND NOT arret");

        // Mise à jour instantanée du tracé graphique sur le Canvas
        receptivityField.textProperty().addListener((obs, oldV, newV) -> {
            transition.setReceptivity(newV);
            if (onModified != null) {
                onModified.run();
            }
        });

        recCard.getChildren().addAll(recLabel, receptivityField);
        this.getChildren().add(recCard);

        // 3. Carte : Test de Compteur (CEI 61131-3)
        var counterCard = createCounterConditionCard(counterVariables, receptivityField);
        this.getChildren().add(counterCard);

        // 4. Carte : Temporisateur normalisé (CEI 60848 / CEI 61131-3)
        var timerCard = createTimerCard(transition, availableSteps, receptivityField);
        this.getChildren().add(timerCard);

        // 5. Carte : Opérateurs logiques et de comparaison rapides
        var opCard = new VBox(8);
        opCard.setStyle(AppTheme.CARD_STYLE);

        var opLabel = new Label("Opérateurs logiques & comparateurs :");
        opLabel.setStyle(AppTheme.SECTION_HEADER_STYLE);

        var opButtons = new FlowPane(6, 6);
        addOperatorBtn(opButtons, receptivityField, "ET ( . )", " . ");
        addOperatorBtn(opButtons, receptivityField, "OU ( + )", " + ");
        addOperatorBtn(opButtons, receptivityField, "NON ( / )", "/");
        addOperatorBtn(opButtons, receptivityField, "= 1 (Vrai)", "1");
        addOperatorBtn(opButtons, receptivityField, "Front ↑", "↑");
        addOperatorBtn(opButtons, receptivityField, "Front ↓", "↓");
        addOperatorBtn(opButtons, receptivityField, "( )", "()");
        addOperatorBtn(opButtons, receptivityField, "<", " < ");
        addOperatorBtn(opButtons, receptivityField, ">=", " >= ");
        addOperatorBtn(opButtons, receptivityField, "<=", " <= ");
        addOperatorBtn(opButtons, receptivityField, ">", " > ");
        addOperatorBtn(opButtons, receptivityField, "=", " = ");
        addOperatorBtn(opButtons, receptivityField, "<>", " <> ");
        addOperatorBtn(opButtons, receptivityField, "⏱ t/X/d", "t/X1/5s");

        opCard.getChildren().addAll(opLabel, opButtons);
        this.getChildren().add(opCard);

        // 6. Carte : Puces des capteurs d'entrée (%I)
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

    /**
     * Crée la carte interactive de configuration du test de compteur (C < 5, C >= 10...).
     */
    private VBox createCounterConditionCard(List<Variable> counterVariables, TextField targetField) {
        var card = new VBox(8);
        card.setStyle(AppTheme.CARD_STYLE);

        var header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        var icon = new Label("🔢");
        icon.setStyle("-fx-font-size: 13px;");
        var title = new Label("Test de Compteur (CEI 61131-3) :");
        title.setStyle(AppTheme.SECTION_HEADER_STYLE);
        header.getChildren().addAll(icon, title);
        card.getChildren().add(header);

        // Ligne 1 : Choix du compteur
        var row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        var cLbl = new Label("Compteur :");
        cLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        cLbl.setPrefWidth(70);

        var counterCombo = new ComboBox<String>();
        counterCombo.setEditable(true);
        counterCombo.setPrefWidth(90);
        if (counterVariables != null && !counterVariables.isEmpty()) {
            for (Variable v : counterVariables) {
                counterCombo.getItems().add(v.getName());
            }
        }
        if (counterCombo.getItems().isEmpty()) {
            counterCombo.getItems().addAll("C1", "C2");
        }
        counterCombo.setValue(counterCombo.getItems().getFirst());

        // Ligne 2 : Opérateur
        var opCombo = new ComboBox<String>();
        opCombo.getItems().addAll(
                "< (Boucle reprise)",
                ">= (Fin de lot)",
                "<= (Inférieur ou égal)",
                "> (Strictement supérieur)",
                "= (Égalité)",
                "<> (Différent)"
        );
        opCombo.setValue("< (Boucle reprise)");
        opCombo.setPrefWidth(130);
        HBox.setHgrow(opCombo, Priority.ALWAYS);

        // Ligne 3 : Seuil
        var valField = new TextField("5");
        valField.setStyle(AppTheme.TEXT_FIELD_STYLE);
        valField.setPrefWidth(55);

        row.getChildren().addAll(cLbl, counterCombo, opCombo, valField);
        card.getChildren().add(row);

        // Aperçu dynamique
        var previewBox = new HBox(6);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewBox.setStyle("-fx-background-color: " + AppTheme.TEAL_LIGHT_BG + "; -fx-padding: 6 10; -fx-background-radius: 6;");

        var prevTitle = new Label("Aperçu :");
        prevTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL + ";");

        var prevValue = new Label();
        prevValue.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL + ";");

        previewBox.getChildren().addAll(prevTitle, prevValue);
        card.getChildren().add(previewBox);

        Runnable updatePreview = () -> {
            String cName = counterCombo.getValue() != null && !counterCombo.getValue().isBlank()
                    ? counterCombo.getValue().trim() : "C1";
            String opRaw = opCombo.getValue() != null ? opCombo.getValue().split(" ")[0] : "<";
            int val = 5;
            try {
                val = Integer.parseInt(valField.getText().trim());
            } catch (Exception ignored) {}

            String condition = SfcCounterOperation.formatComparison(cName, opRaw, val);
            prevValue.setText(condition);
        };

        counterCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        opCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        valField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        // Bouton d'insertion
        var insertBtn = new Button("➕ Insérer le test de comptage");
        insertBtn.setMaxWidth(Double.MAX_VALUE);
        insertBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
        insertBtn.setOnAction(e -> {
            updatePreview.run();
            insertTextAtCursor(targetField, prevValue.getText());
        });
        card.getChildren().add(insertBtn);

        return card;
    }

    /**
     * Crée la carte interactive de configuration du temporisateur normalisé.
     */
    private VBox createTimerCard(Transition transition, List<Step> availableSteps, TextField targetField) {
        var card = new VBox(8);
        card.setStyle(AppTheme.CARD_STYLE);

        var cardHeader = new HBox(6);
        cardHeader.setAlignment(Pos.CENTER_LEFT);
        var icon = new Label("⏱");
        icon.setStyle("-fx-font-size: 13px;");
        var title = new Label("Temporisateur (CEI 60848 / 61131-3) :");
        title.setStyle(AppTheme.SECTION_HEADER_STYLE);
        cardHeader.getChildren().addAll(icon, title);
        card.getChildren().add(cardHeader);

        // Détection de l'étape amont directe
        Optional<Step> primaryParent = transition.getPrimaryParentStep();
        List<Step> parentSteps = transition.getParentSteps();

        // Récupération de toutes les étapes candidates
        List<Step> candidateSteps = new ArrayList<>();
        if (availableSteps != null && !availableSteps.isEmpty()) {
            candidateSteps.addAll(availableSteps);
        } else {
            candidateSteps.addAll(parentSteps);
        }

        // Ligne 1 : Étape associée (Xi)
        var stepRow = new HBox(8);
        stepRow.setAlignment(Pos.CENTER_LEFT);
        var stepLbl = new Label("Étape active (Xi) :");
        stepLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        stepLbl.setPrefWidth(110);

        var stepCombo = new ComboBox<String>();
        stepCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(stepCombo, Priority.ALWAYS);

        final String defaultStepSelection = primaryParent
                .map(p -> "X" + p.getNumber())
                .orElse("X1");

        for (Step s : candidateSteps) {
            String label = "X" + s.getNumber();
            if (primaryParent.isPresent() && primaryParent.get() == s) {
                label += " (Amont direct)";
            }
            stepCombo.getItems().add(label);
        }

        if (stepCombo.getItems().isEmpty()) {
            stepCombo.getItems().addAll("X1", "X2", "X0");
        }

        // Présélection de l'étape amont
        String selectedItem = stepCombo.getItems().stream()
                .filter(item -> item.startsWith(defaultStepSelection))
                .findFirst()
                .orElse(stepCombo.getItems().getFirst());
        stepCombo.setValue(selectedItem);

        stepRow.getChildren().addAll(stepLbl, stepCombo);
        card.getChildren().add(stepRow);

        // Ligne 2 : Durée et unité
        var durationRow = new HBox(8);
        durationRow.setAlignment(Pos.CENTER_LEFT);
        var durLbl = new Label("Durée écoulée (d) :");
        durLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        durLbl.setPrefWidth(110);

        var durationField = new TextField("5");
        durationField.setStyle(AppTheme.TEXT_FIELD_STYLE);
        durationField.setPrefWidth(70);
        durationField.setPromptText("ex: 5");

        var unitCombo = new ComboBox<String>();
        unitCombo.getItems().addAll("s", "ms", "min");
        unitCombo.setValue("s");
        unitCombo.setPrefWidth(75);

        durationRow.getChildren().addAll(durLbl, durationField, unitCombo);
        card.getChildren().add(durationRow);

        // Ligne 3 : Repère du temporisateur (ex: t ou t1)
        var repRow = new HBox(8);
        repRow.setAlignment(Pos.CENTER_LEFT);
        var repLbl = new Label("Repère du timer :");
        repLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        repLbl.setPrefWidth(110);

        var timerNameField = new TextField("t");
        timerNameField.setStyle(AppTheme.TEXT_FIELD_STYLE);
        timerNameField.setPrefWidth(70);
        timerNameField.setPromptText("t, t1...");

        var repHint = new Label("(générique 't' par défaut)");
        repHint.setStyle("-fx-font-size: 10px; -fx-text-fill: " + AppTheme.TEXT_HINT + "; -fx-font-style: italic;");

        repRow.getChildren().addAll(repLbl, timerNameField, repHint);
        card.getChildren().add(repRow);

        // Ligne 4 : Choix du format (Grafcet générique par défaut)
        var formatRow = new VBox(4);
        var toggleGroup = new ToggleGroup();

        var rbGrafcet = new RadioButton("Grafcet standard (t/Xi/d)");
        rbGrafcet.setToggleGroup(toggleGroup);
        rbGrafcet.setSelected(true); // FORMAT PAR DÉFAUT
        rbGrafcet.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        var rbST = new RadioButton("SFC CEI 61131-3 (Xi.t >= T#d)");
        rbST.setToggleGroup(toggleGroup);
        rbST.setStyle("-fx-font-size: 11px;");

        formatRow.getChildren().addAll(rbGrafcet, rbST);
        card.getChildren().add(formatRow);

        // Ligne 5 : Aperçu dynamique de la condition
        var previewBox = new HBox(6);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewBox.setStyle("-fx-background-color: " + AppTheme.TEAL_LIGHT_BG + "; -fx-padding: 6 10; -fx-background-radius: 6;");

        var prevTitle = new Label("Aperçu :");
        prevTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL + ";");

        var prevValue = new Label();
        prevValue.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL + ";");

        previewBox.getChildren().addAll(prevTitle, prevValue);
        card.getChildren().add(previewBox);

        // Fonction locale de calcul de l'expression
        Runnable updatePreview = () -> {
            String stepVal = stepCombo.getValue() != null ? stepCombo.getValue().split(" ")[0] : "X1";
            String durText = durationField.getText() != null ? durationField.getText().trim() : "5";
            double val = 5.0;
            try {
                val = Double.parseDouble(durText);
            } catch (NumberFormatException ignored) {}
            String unit = unitCombo.getValue() != null ? unitCombo.getValue() : "s";
            String timerRep = timerNameField.getText() != null && !timerNameField.getText().isBlank() ? timerNameField.getText().trim() : "t";
            boolean isST = rbST.isSelected();

            String generated = SfcTimerCondition.buildCondition(timerRep, stepVal, val, unit, isST);
            prevValue.setText(generated);
        };

        // Écouteurs de mise à jour de l'aperçu
        stepCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        durationField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        unitCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        timerNameField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        rbGrafcet.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        rbST.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        // Ligne 6 : Bouton d'insertion stylisé
        var insertBtn = new Button("➕ Insérer le temporisateur");
        insertBtn.setMaxWidth(Double.MAX_VALUE);
        insertBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
        insertBtn.setOnAction(e -> {
            updatePreview.run();
            insertTextAtCursor(targetField, prevValue.getText());
        });
        card.getChildren().add(insertBtn);

        return card;
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

        // Si le champ ne contenait que "1", on le remplace directement
        if ("1".equals(current.trim())) {
            field.setText(text);
            field.positionCaret(text.length());
        } else {
            // Si on insère après un identifiant sans opérateur, ajouter un point ET logique " . "
            field.setText(prefix + text + suffix);
            field.positionCaret(pos + text.length());
        }
        field.requestFocus();
    }
}
