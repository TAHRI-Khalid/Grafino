package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import tk.tahri.grafino.component.Action;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Variable;

import java.util.List;

/**
 * Panneau latéral droit d'édition d'une étape SFC (CEI 61131-3 / CEI 60848).
 * Harmonisé avec la charte graphique de l'éditeur Arduino.
 */
public class StepProperty extends VBox {

    private final VBox listOfActions = new VBox(6);
    private final Runnable onModified;

    public StepProperty(Step step, List<Variable> outputVariables) {
        this(step, outputVariables, null, null);
    }

    public StepProperty(Step step, List<Variable> outputVariables, Runnable onModified) {
        this(step, outputVariables, onModified, null);
    }

    public StepProperty(Step step, List<Variable> outputVariables, Runnable onModified, Runnable onClose) {
        this.onModified = onModified;
        this.setPrefWidth(350);
        this.setMinWidth(320);
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

        // 3. Carte : Ajout d'une nouvelle action normalisée
        var addActionCard = new VBox(8);
        addActionCard.setStyle(AppTheme.CARD_STYLE);

        var actionTitle = new Label("Nouvelle action CEI 61131-3 :");
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
        var addBtn = new Button("＋ Associer l'action");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
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

        // 4. Liste des actions associées
        var listTitle = new Label("Actions associées :");
        listTitle.setStyle(AppTheme.SECTION_HEADER_STYLE);

        refreshActionsList(step);

        var scrollPane = new ScrollPane(listOfActions);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-width: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().addAll(listTitle, scrollPane);
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

            // Nom de la variable et adresse
            var varInfoBox = new VBox(1);
            var varName = new Label(action.variable() != null ? action.variable().getName() : "");
            varName.setStyle("-fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + "; -fx-font-size: 12px;");

            var varAddr = new Label(action.variable() != null ? action.variable().getAddress() : "");
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
