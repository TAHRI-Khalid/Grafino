package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.Action;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.model.SfcCounterOperation;

import java.util.List;

/**
 * Panneau latéral droit d'édition d'une étape SFC (CEI 61131-3 / CEI 60848).
 * Organisé en accordéons ergonomiques repliables :
 *  - Statut Étape Initiale (fixe en haut)
 *  - Accordéon 1 : Action de sortie classique %Q (déplié par défaut)
 *  - Accordéon 2 : Opération sur Compteur CEI 61131-3 (replié par défaut)
 *  - Accordéon 3 : Liste des actions associées avec badge du total (déplié par défaut)
 */
public class StepProperty extends VBox {

    private final VBox listOfActions = new VBox(6);
    private final Runnable onModified;
    private CollapsibleSection actionsSection;

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
        this.setPrefWidth(390);
        this.setMinWidth(350);
        this.setSpacing(10);
        this.setPadding(new Insets(12));
        this.setStyle(AppTheme.SIDEBAR_STYLE);

        // 1. En-tête fixe avec titre, badge et bouton fermer
        var header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        var titleBox = new VBox(2);
        var subTitle = new Label("PROPRIÉTÉS DE L'ÉTAPE");
        subTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        var badge = new Label("Étape S" + step.getNumber());
        badge.setStyle(AppTheme.pillBadgeStyle(AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK) +
                "; -fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + "; -fx-border-radius: 12px; -fx-font-size: 14px;");
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

        // 2. Carte fixe : Statut Étape Initiale
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

        // 3. Conteneur déroulant (ScrollPane) pour les accordéons
        var accordionBox = new VBox(10);

        // --- Accordéon 1 : Action de sortie classique (%Q) ---
        var classicActionNode = createClassicActionContent(step, outputVariables);
        String outBadgeText = (outputVariables != null && !outputVariables.isEmpty())
                ? outputVariables.size() + " %Q"
                : null;
        var classicActionSection = new CollapsibleSection(
                "⚡",
                "Action de sortie (%Q)",
                outBadgeText,
                classicActionNode,
                true // Déplié par défaut
        );
        accordionBox.getChildren().add(classicActionSection);

        // --- Accordéon 2 : Opération sur Compteur CEI 61131-3 ---
        var counterNode = createCounterActionContent(step, counterVariables);
        String counterBadgeText = (counterVariables != null && !counterVariables.isEmpty())
                ? counterVariables.size() + " INT"
                : null;
        var counterSection = new CollapsibleSection(
                "🔢",
                "Opération sur Compteur",
                counterBadgeText,
                counterNode,
                false // Replié par défaut pour économiser l'espace vertical
        );
        accordionBox.getChildren().add(counterSection);

        // --- Accordéon 3 : Liste des actions associées ---
        actionsSection = new CollapsibleSection(
                "📋",
                "Actions associées",
                step.getActions().size() + " action(s)",
                listOfActions,
                true // Déplié par défaut pour voir l'effet immédiat des ajouts
        );
        refreshActionsList(step);
        accordionBox.getChildren().add(actionsSection);

        // ScrollPane pour garantir la navigation sur petits écrans
        var scrollPane = new ScrollPane(accordionBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-width: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().add(scrollPane);
    }

    /**
     * Crée le formulaire d'association d'action classique de commande (%Q).
     */
    private Node createClassicActionContent(Step step, List<Variable> outputVariables) {
        var box = new VBox(8);

        // Qualificateur
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

        // Variable cible (%Q) sous forme de boutons cliquables (similaire aux entrées %I)
        var varLabel = new Label("Sorties préactionneurs disponibles (%Q) :");
        varLabel.setStyle(AppTheme.LABEL_MUTED_STYLE);

        box.getChildren().addAll(qualLabel, qualifierCombo, durationBox, varLabel);

        if (outputVariables == null || outputVariables.isEmpty()) {
            var emptyLabel = new Label("Aucune sortie %Q définie dans la table.");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_HINT + "; -fx-font-style: italic;");
            box.getChildren().add(emptyLabel);
        } else {
            var chipsPane = new FlowPane(5, 5);
            for (Variable varOutput : outputVariables) {
                var chip = new Button(varOutput.getName());
                chip.setMnemonicParsing(false);
                chip.setTooltip(new Tooltip(varOutput.getAddress() + (varOutput.getComment().isEmpty() ? "" : " : " + varOutput.getComment())));
                chip.setStyle(AppTheme.CHIP_INPUT_STYLE);
                chip.setOnMouseEntered(e -> chip.setStyle(AppTheme.CHIP_INPUT_HOVER_STYLE));
                chip.setOnMouseExited(e -> chip.setStyle(AppTheme.CHIP_INPUT_STYLE));
                chip.setOnAction(e -> {
                    String qualRaw = qualifierCombo.getSelectionModel().getSelectedItem();
                    String qual = (qualRaw != null) ? qualRaw.substring(0, 1) : "N";

                    String param = null;
                    if (qual.equals("L") || qual.equals("D")) {
                        param = durationField.getText().trim() + unitCombo.getSelectionModel().getSelectedItem();
                    }

                    Action newAction = new Action(qual, varOutput, param);
                    step.getActions().add(newAction);
                    refreshActionsList(step);
                    notifyRedraw();
                });
                chipsPane.getChildren().add(chip);
            }
            box.getChildren().add(chipsPane);
        }

        return box;
    }

    /**
     * Crée le formulaire interactif pour les opérations de compteur (C := 0, C := C + 1, C := C - 1).
     */
    private Node createCounterActionContent(Step step, List<Variable> counterVariables) {
        var box = new VBox(8);

        if (counterVariables == null || counterVariables.isEmpty()) {
            var emptyLabel = new Label("Aucun compteur (variable INT) défini dans la table.");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_HINT + "; -fx-font-style: italic;");
            emptyLabel.setWrapText(true);
            box.getChildren().add(emptyLabel);
            return box;
        }

        // Ligne 1 : Choix du compteur
        var counterRow = new HBox(8);
        counterRow.setAlignment(Pos.CENTER_LEFT);
        var cLbl = new Label("Compteur :");
        cLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);
        cLbl.setPrefWidth(85);

        var counterCombo = new ComboBox<String>();
        counterCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(counterCombo, Priority.ALWAYS);

        for (Variable v : counterVariables) {
            counterCombo.getItems().add(v.getName());
        }
        String defaultCounter = counterCombo.getItems().getFirst();
        counterCombo.setValue(defaultCounter);
        counterRow.getChildren().addAll(cLbl, counterCombo);
        box.getChildren().add(counterRow);

        // Ligne 2 : Type d'opération
        var opGroup = new ToggleGroup();
        var rbInc = new RadioButton("Incrémenter (C := C + 1)");
        rbInc.setToggleGroup(opGroup);
        rbInc.setSelected(true);
        rbInc.setStyle(AppTheme.RADIO_BUTTON_BOLD_STYLE);

        var rbInit = new RadioButton("Initialiser (C := 0)");
        rbInit.setToggleGroup(opGroup);
        rbInit.setStyle(AppTheme.RADIO_BUTTON_STYLE);

        var rbDec = new RadioButton("Décrémenter (C := C - 1)");
        rbDec.setToggleGroup(opGroup);
        rbDec.setStyle(AppTheme.RADIO_BUTTON_STYLE);

        var opBox = new VBox(4, rbInc, rbInit, rbDec);
        box.getChildren().add(opBox);

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
        box.getChildren().add(valRow);

        // Ligne 4 : Qualificateur (P par défaut avec recommandation pédagogique)
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
        box.getChildren().add(qualRow);

        // Ligne 5 : Aperçu dynamique
        var previewBox = new HBox(6);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewBox.setStyle("-fx-background-color: " + AppTheme.TEAL_LIGHT_BG + "; -fx-padding: 6 10; -fx-background-radius: 6;");

        var prevTitle = new Label("Aperçu :");
        prevTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL + ";");

        var prevValue = new Label();
        prevValue.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL + ";");

        previewBox.getChildren().addAll(prevTitle, prevValue);
        box.getChildren().add(previewBox);

        Runnable updatePreview = () -> {
            String cName = counterCombo.getValue() != null && !counterCombo.getValue().isBlank()
                    ? counterCombo.getValue().trim() : defaultCounter;
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

        // Ligne 6 : Bouton d'ajout
        var addCounterBtn = new Button("➕ Associer l'action de comptage");
        addCounterBtn.setMaxWidth(Double.MAX_VALUE);
        addCounterBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
        addCounterBtn.setOnMouseEntered(e -> addCounterBtn.setStyle(AppTheme.PRIMARY_BUTTON_HOVER_STYLE));
        addCounterBtn.setOnMouseExited(e -> addCounterBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE));
        addCounterBtn.setOnAction(e -> {
            String cName = counterCombo.getValue() != null && !counterCombo.getValue().isBlank()
                    ? counterCombo.getValue().trim() : defaultCounter;
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
        box.getChildren().add(addCounterBtn);

        return box;
    }

    private void refreshActionsList(Step step) {
        listOfActions.getChildren().clear();

        if (actionsSection != null) {
            actionsSection.setBadge(step.getActions().size() + " action(s)");
        }

        if (step.getActions().isEmpty()) {
            var emptyLabel = new Label("Aucune action associée à cette étape.");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_HINT + "; -fx-font-style: italic; -fx-padding: 4;");
            listOfActions.getChildren().add(emptyLabel);
            return;
        }

        for (Action action : step.getActions()) {
            var card = new HBox(8);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(6, 10, 6, 10));
            String cardStyle = "-fx-background-color: " + AppTheme.CARD_BG + ";" +
                    "-fx-border-color: " + AppTheme.BORDER_COLOR + ";" +
                    "-fx-border-radius: 6px;" +
                    "-fx-background-radius: 6px;";
            String cardHoverStyle = "-fx-background-color: " + AppTheme.PANEL_BG + ";" +
                    "-fx-border-color: " + AppTheme.ACCENT_TEAL + ";" +
                    "-fx-border-radius: 6px;" +
                    "-fx-background-radius: 6px;";
            card.setStyle(cardStyle);
            card.setOnMouseEntered(e -> card.setStyle(cardHoverStyle));
            card.setOnMouseExited(e -> card.setStyle(cardStyle));

            String qual = action.qualifier() != null ? action.qualifier().trim() : "N";
            String qualText = qual;
            if (action.hasParameter()) {
                qualText += " (" + action.parameter() + ")";
            }
            var qualBadge = new Label(qualText);
            qualBadge.setStyle(AppTheme.qualifierBadgeStyle(qual));

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

            var delBtn = new Button("🗑");
            delBtn.setTooltip(new Tooltip("Supprimer cette action"));
            String delStyle = "-fx-background-color: transparent; -fx-text-fill: " + AppTheme.RED_DANGER + "; -fx-font-size: 12px; -fx-padding: 4px 6px; -fx-background-radius: 4px; -fx-cursor: hand;";
            String delHoverStyle = "-fx-background-color: " + AppTheme.RED_LIGHT_BG + "; -fx-text-fill: " + AppTheme.RED_DANGER + "; -fx-font-size: 12px; -fx-padding: 4px 6px; -fx-background-radius: 4px; -fx-cursor: hand;";
            delBtn.setStyle(delStyle);
            delBtn.setOnMouseEntered(e -> delBtn.setStyle(delHoverStyle));
            delBtn.setOnMouseExited(e -> delBtn.setStyle(delStyle));
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
