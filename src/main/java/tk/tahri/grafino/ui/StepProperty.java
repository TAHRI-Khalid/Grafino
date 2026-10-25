package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import tk.tahri.grafino.component.Action;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.model.SfcCounterOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * Panneau latéral droit d'édition d'une étape SFC (CEI 61131-3 / CEI 60848).
 * Comprend la gestion des actions classiques (%Q), des opérations sur compteurs industriels
 * (Initialisation, Incrémentation, Décrémentation avec qualificateur impulsionnel 'P')
 * et l'état d'étape initiale.
 */
public class StepProperty extends VBox {

    private final VBox listOfActions = new VBox(6);
    private final Runnable onModified;

    public StepProperty(Step step, List<Variable> outputVariables) {
        this(step, outputVariables, List.of(), null, null);
    }

    public StepProperty(Step step, List<Variable> outputVariables, Runnable onModified) {
        this(step, outputVariables, List.of(), onModified, null);
    }

    public StepProperty(Step step, List<Variable> outputVariables, Runnable onModified, Runnable onClose) {
        this(step, outputVariables, List.of(), onModified, onClose);
    }

    public StepProperty(Step step, List<Variable> outputVariables, List<Variable> counterVariables,
                        Runnable onModified, Runnable onClose) {
        this.onModified = onModified;
        this.setPrefWidth(360);
        this.setMinWidth(330);
        this.setSpacing(12);
        this.setPadding(new Insets(14));
        this.setStyle(AppTheme.SIDEBAR_STYLE);

        // 1. En-tête avec titre, badge et bouton fermer
        var header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        var titleBox = new VBox(2);
        var subTitle = new Label("PROPRIÉTÉS DE L'ÉTAPE");
        subTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        var badge = new Label("Étape S" + step.getNumber());
        badge.setStyle(AppTheme.pillBadgeStyle(AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK) +
                "; -fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + "; -fx-border-radius: 12px; -fx-font-size: 13px;");
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

        // 2. Carte : Statut Étape Initiale
        var initCard = new VBox(6);
        initCard.setStyle(AppTheme.CARD_STYLE);

        var initCheckBox = new CheckBox("Étape Initiale (Double cadre CEI 60848)");
        initCheckBox.setStyle("-fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + "; -fx-font-size: 12px; -fx-cursor: hand;");
        initCheckBox.setSelected(step.isInitial());
        initCheckBox.setOnAction(e -> {
            step.setInitial(initCheckBox.isSelected());
            notifyRedraw();
        });
        initCard.getChildren().add(initCheckBox);
        this.getChildren().add(initCard);

        // 3. Carte : Opérations sur Compteur (CEI 61131-3)
        var counterCard = createCounterActionCard(step, counterVariables);
        this.getChildren().add(counterCard);

        // 4. Carte : Ajout d'une action de commande classique (%Q)
        var addActionCard = new VBox(8);
        addActionCard.setStyle(AppTheme.CARD_STYLE);

        var actionTitle = new Label("Action de sortie classique (%Q) :");
        actionTitle.setStyle(AppTheme.SECTION_HEADER_STYLE);

        // Sélecteur de qualificateur
        var qualLabel = new Label("Qualificateur :");
        qualLabel.setStyle(AppTheme.LABEL_MUTED_STYLE);

        var qualifierCombo = new ComboBox<String>();
        qualifierCombo.getItems().addAll(
                "N (Continu / Non-stored)",
                "S (Set / Mémorisé)",
                "R (Reset / Remise à 0)",
                "L (Time Limited / Limité)",
                "D (Time Delayed / Retardé)",
                "P (Pulse / Impulsion)"
        );
        qualifierCombo.getSelectionModel().select(0);
        qualifierCombo.setMaxWidth(Double.MAX_VALUE);
        qualifierCombo.setStyle("-fx-font-size: 11px;");

        // Champ de durée pour qualificateurs temporisés (L et D)
        var durationBox = new HBox(6);
        durationBox.setAlignment(Pos.CENTER_LEFT);
        var durationLabel = new Label("Durée :");
        durationLabel.setStyle(AppTheme.LABEL_MUTED_STYLE);
        var durationField = new TextField("5");
        durationField.setPrefWidth(60);
        durationField.setStyle(AppTheme.TEXT_FIELD_STYLE);
        var unitCombo = new ComboBox<String>();
        unitCombo.getItems().addAll("s", "ms", "min");
        unitCombo.getSelectionModel().select(0);
        unitCombo.setStyle("-fx-font-size: 11px;");
        durationBox.getChildren().addAll(durationLabel, durationField, unitCombo);
        durationBox.setVisible(false);
        durationBox.setManaged(false);

        qualifierCombo.setOnAction(e -> {
            String selected = qualifierCombo.getSelectionModel().getSelectedItem();
            boolean isTimed = selected != null && (selected.startsWith("L") || selected.startsWith("D"));
            durationBox.setVisible(isTimed);
            durationBox.setManaged(isTimed);
        });

        // Sélecteur de variable de sortie (%Q)
        var varLabel = new Label("Variable cible (%Q) :");
        varLabel.setStyle(AppTheme.LABEL_MUTED_STYLE);

        var varCombo = new ComboBox<Variable>();
        varCombo.setMaxWidth(Double.MAX_VALUE);
        varCombo.setStyle("-fx-font-size: 11px;");
        if (outputVariables != null) {
            varCombo.getItems().addAll(outputVariables);
        }
        varCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Variable v) {
                return (v != null) ? v.getName() + " (" + v.getAddress() + ")" : "";
            }
            @Override
            public Variable fromString(String string) {
                return null;
            }
        });
        if (!varCombo.getItems().isEmpty()) {
            varCombo.getSelectionModel().select(0);
        }

        // Bouton d'association d'action (Style Arduino Teal)
        var addBtn = new Button("＋ Associer l'action %Q");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        addBtn.setOnAction(e -> {
            Variable selectedVar = varCombo.getSelectionModel().getSelectedItem();
            if (selectedVar == null) {
                return;
            }

            String qualRaw = qualifierCombo.getSelectionModel().getSelectedItem();
            String qual = (qualRaw != null) ? qualRaw.substring(0, 1) : "N";

            String param = null;
            if (qual.equals("L") || qual.equals("D")) {
                param = durationField.getText().trim() + unitCombo.getSelectionModel().getSelectedItem();
            }

            Action newAction = new Action(qual, selectedVar, param);
            step.getActions().add(newAction);
            refreshActionsList(step);
            notifyRedraw();
        });

        addActionCard.getChildren().addAll(actionTitle, qualLabel, qualifierCombo, durationBox, varLabel, varCombo, addBtn);
        this.getChildren().add(addActionCard);

        // 5. Liste des actions associées
        var listTitle = new Label("Actions associées à l'étape :");
        listTitle.setStyle(AppTheme.SECTION_HEADER_STYLE);

        refreshActionsList(step);

        var scrollPane = new ScrollPane(listOfActions);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-width: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().addAll(listTitle, scrollPane);
    }

    /**
     * Crée la carte interactive pour les actions sur compteurs (C := 0, C := C + 1, C := C - 1).
     */
    private VBox createCounterActionCard(Step step, List<Variable> counterVariables) {
        var card = new VBox(8);
        card.setStyle(AppTheme.CARD_STYLE);

        var header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        var icon = new Label("🔢");
        icon.setStyle("-fx-font-size: 13px;");
        var title = new Label("Opération sur Compteur (CEI 61131-3) :");
        title.setStyle(AppTheme.SECTION_HEADER_STYLE);
        header.getChildren().addAll(icon, title);
        card.getChildren().add(header);

        // Ligne 1 : Choix du compteur
        var counterRow = new HBox(8);
        counterRow.setAlignment(Pos.CENTER_LEFT);
        var cLbl = new Label("Compteur :");
        cLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        cLbl.setPrefWidth(85);

        var counterCombo = new ComboBox<String>();
        counterCombo.setEditable(true);
        counterCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(counterCombo, Priority.ALWAYS);

        if (counterVariables != null && !counterVariables.isEmpty()) {
            for (Variable v : counterVariables) {
                counterCombo.getItems().add(v.getName());
            }
        }
        if (counterCombo.getItems().isEmpty()) {
            counterCombo.getItems().addAll("C1", "C2");
        }
        counterCombo.setValue(counterCombo.getItems().getFirst());
        counterRow.getChildren().addAll(cLbl, counterCombo);
        card.getChildren().add(counterRow);

        // Ligne 2 : Type d'opération
        var opGroup = new ToggleGroup();
        var rbInc = new RadioButton("Incrémenter (C := C + 1)");
        rbInc.setToggleGroup(opGroup);
        rbInc.setSelected(true);
        rbInc.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        var rbInit = new RadioButton("Initialiser (C := 0)");
        rbInit.setToggleGroup(opGroup);
        rbInit.setStyle("-fx-font-size: 11px;");

        var rbDec = new RadioButton("Décrémenter (C := C - 1)");
        rbDec.setToggleGroup(opGroup);
        rbDec.setStyle("-fx-font-size: 11px;");

        var opBox = new VBox(4, rbInc, rbInit, rbDec);
        card.getChildren().add(opBox);

        // Ligne 3 : Valeur / Pas
        var valRow = new HBox(8);
        valRow.setAlignment(Pos.CENTER_LEFT);
        var valLbl = new Label("Valeur / Pas :");
        valLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        valLbl.setPrefWidth(85);

        var valField = new TextField("1");
        valField.setStyle(AppTheme.TEXT_FIELD_STYLE);
        valField.setPrefWidth(70);

        valRow.getChildren().addAll(valLbl, valField);
        card.getChildren().add(valRow);

        // Ligne 4 : Qualificateur (P par défaut avec note pédagogique)
        var qualRow = new HBox(8);
        qualRow.setAlignment(Pos.CENTER_LEFT);
        var qLbl = new Label("Qualificateur :");
        qLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        qLbl.setPrefWidth(85);

        var qualCombo = new ComboBox<String>();
        qualCombo.getItems().addAll("P (Pulse / Impulsion - Recommandé)", "N (Continu)");
        qualCombo.setValue("P (Pulse / Impulsion - Recommandé)");
        qualCombo.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        HBox.setHgrow(qualCombo, Priority.ALWAYS);

        qualRow.getChildren().addAll(qLbl, qualCombo);
        card.getChildren().add(qualRow);

        // Ligne 5 : Aperçu dynamique
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
            int val = 1;
            try {
                val = Integer.parseInt(valField.getText().trim());
            } catch (Exception ignored) {}

            String qual = qualCombo.getValue() != null && qualCombo.getValue().startsWith("P") ? "P" : "N";
            String actionText;
            if (rbInit.isSelected()) {
                actionText = SfcCounterOperation.formatInit(cName, val);
            } else if (rbDec.isSelected()) {
                actionText = SfcCounterOperation.formatDecrement(cName, val);
            } else {
                actionText = SfcCounterOperation.formatIncrement(cName, val);
            }

            prevValue.setText("[" + qual + "] " + actionText);
        };

        // Écouteurs de mise à jour
        counterCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        valField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        qualCombo.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        rbInc.selectedProperty().addListener((obs, o, n) -> {
            if (rbInc.isSelected()) valField.setText("1");
            updatePreview.run();
        });
        rbInit.selectedProperty().addListener((obs, o, n) -> {
            if (rbInit.isSelected()) valField.setText("0");
            updatePreview.run();
        });
        rbDec.selectedProperty().addListener((obs, o, n) -> {
            if (rbDec.isSelected()) valField.setText("1");
            updatePreview.run();
        });
        updatePreview.run();

        // Ligne 6 : Bouton d'ajout d'action de comptage
        var addCounterBtn = new Button("➕ Associer l'action de comptage");
        addCounterBtn.setMaxWidth(Double.MAX_VALUE);
        addCounterBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
        addCounterBtn.setOnAction(e -> {
            String cName = counterCombo.getValue() != null && !counterCombo.getValue().isBlank()
                    ? counterCombo.getValue().trim() : "C1";
            int val = 1;
            try {
                val = Integer.parseInt(valField.getText().trim());
            } catch (Exception ignored) {}

            String qual = qualCombo.getValue() != null && qualCombo.getValue().startsWith("P") ? "P" : "N";
            String actionText;
            if (rbInit.isSelected()) {
                actionText = SfcCounterOperation.formatInit(cName, val);
            } else if (rbDec.isSelected()) {
                actionText = SfcCounterOperation.formatDecrement(cName, val);
            } else {
                actionText = SfcCounterOperation.formatIncrement(cName, val);
            }

            Action newAction = new Action(qual, actionText);
            step.getActions().add(newAction);
            refreshActionsList(step);
            notifyRedraw();
        });
        card.getChildren().add(addCounterBtn);

        return card;
    }

    private void refreshActionsList(Step step) {
        listOfActions.getChildren().clear();

        if (step.getActions().isEmpty()) {
            var emptyLabel = new Label("Aucune action associée à cette étape.");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_HINT + "; -fx-font-style: italic; -fx-padding: 8;");
            listOfActions.getChildren().add(emptyLabel);
            return;
        }

        for (Action action : step.getActions()) {
            var card = new HBox(8);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(6, 10, 6, 10));
            card.setStyle("-fx-background-color: " + AppTheme.CARD_BG + ";" +
                    "-fx-border-color: " + AppTheme.BORDER_COLOR + ";" +
                    "-fx-border-radius: 6px;" +
                    "-fx-background-radius: 6px;");

            // Pastille du qualificateur avec style unifié
            String qual = action.qualifier() != null ? action.qualifier().trim() : "N";
            String qualText = qual;
            if (action.hasParameter()) {
                qualText += " (" + action.parameter() + ")";
            }
            var qualBadge = new Label(qualText);
            qualBadge.setStyle(AppTheme.qualifierBadgeStyle(qual));

            // Libellé de l'action / variable
            var varInfoBox = new VBox(1);
            String displayName = action.getDisplayText();
            var varName = new Label(displayName);
            varName.setStyle("-fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + "; -fx-font-size: 12px;");

            String subtitle = (action.variable() != null) ? action.variable().getAddress() : "Action Structured Text";
            var varAddr = new Label(subtitle);
            varAddr.setStyle("-fx-font-size: 10px; -fx-text-fill: " + AppTheme.TEXT_MUTED + "; -fx-font-family: 'JetBrains Mono', monospace;");
            varInfoBox.getChildren().addAll(varName, varAddr);

            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Bouton de suppression discret
            var delBtn = new Button("🗑");
            delBtn.setTooltip(new Tooltip("Supprimer cette action"));
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + AppTheme.RED_DANGER + "; -fx-font-size: 12px; -fx-cursor: hand;");
            delBtn.setOnAction(e -> {
                step.getActions().remove(action);
                refreshActionsList(step);
                notifyRedraw();
            });

            card.getChildren().addAll(qualBadge, varInfoBox, spacer, delBtn);
            listOfActions.getChildren().add(card);
        }
    }

    private void notifyRedraw() {
        if (onModified != null) {
            onModified.run();
        }
    }
}
