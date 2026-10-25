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
 * Permet de basculer le statut étape initiale et d'associer des actions avec qualificateurs (N, S, R, L, D, P)
 * et durées temporisées.
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
        this.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; -fx-border-width: 0 0 0 1;");

        // 1. En-tête avec badge et bouton fermer
        var header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        var badge = new Label("Étape S" + step.getNumber());
        badge.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 4 10 4 10; -fx-background-radius: 6;");

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

        // 2. Propriétés de l'étape (Statut Initiale)
        var initCheckBox = new CheckBox("Étape Initiale (Double cadre CEI 60848)");
        initCheckBox.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 12px;");
        initCheckBox.setSelected(step.isInitial());
        initCheckBox.setOnAction(e -> {
            step.setInitial(initCheckBox.isSelected());
            notifyRedraw();
        });

        this.getChildren().addAll(initCheckBox, new Separator());

        // 3. Section d'ajout d'une nouvelle action
        var addActionBox = new VBox(8);
        var actionTitle = new Label("Ajouter une action normalisée :");
        actionTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 12px;");

        // Sélecteur de qualificateur
        var qualLabel = new Label("Qualificateur CEI 61131-3 :");
        qualLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        var qualifierCombo = new ComboBox<String>();
        qualifierCombo.getItems().addAll(
                "N (Continu / Non-stored)",
                "S (Set / Mémorisé)",
                "R (Reset)",
                "L (Time Limited / Limité)",
                "D (Time Delayed / Retardé)",
                "P (Pulse / Impulsion)"
        );
        qualifierCombo.getSelectionModel().select(0);
        qualifierCombo.setMaxWidth(Double.MAX_VALUE);

        // Champ de durée pour qualificateurs temporisés (L et D)
        var durationBox = new HBox(6);
        durationBox.setAlignment(Pos.CENTER_LEFT);
        var durationLabel = new Label("Durée :");
        durationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        var durationField = new TextField("5");
        durationField.setPrefWidth(60);
        var unitCombo = new ComboBox<String>();
        unitCombo.getItems().addAll("s", "ms", "min");
        unitCombo.getSelectionModel().select(0);
        durationBox.getChildren().addAll(durationLabel, durationField, unitCombo);
        durationBox.setVisible(false);
        durationBox.setManaged(false);

        // Affichage dynamique du champ de durée
        qualifierCombo.setOnAction(e -> {
            String selected = qualifierCombo.getSelectionModel().getSelectedItem();
            boolean isTimed = selected != null && (selected.startsWith("L") || selected.startsWith("D"));
            durationBox.setVisible(isTimed);
            durationBox.setManaged(isTimed);
        });

        // Sélecteur de variable de sortie (%Q)
        var varLabel = new Label("Variable cible (%Q) :");
        varLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        var varCombo = new ComboBox<Variable>();
        varCombo.setMaxWidth(Double.MAX_VALUE);
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

        // Bouton d'ajout d'action
        var addBtn = new Button("➕ Associer l'action à l'étape");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 6;");
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

        addActionBox.getChildren().addAll(actionTitle, qualLabel, qualifierCombo, durationBox, varLabel, varCombo, addBtn);
        this.getChildren().addAll(addActionBox, new Separator());

        // 4. Liste des actions associées
        var listTitle = new Label("Actions associées à l'étape :");
        listTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 12px;");

        refreshActionsList(step);

        var scrollPane = new ScrollPane(listOfActions);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().addAll(listTitle, scrollPane);
    }

    private void refreshActionsList(Step step) {
        listOfActions.getChildren().clear();

        if (step.getActions().isEmpty()) {
            var emptyLabel = new Label("Aucune action associée.");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-font-style: italic;");
            listOfActions.getChildren().add(emptyLabel);
            return;
        }

        for (Action action : step.getActions()) {
            var card = new HBox(8);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(6, 8, 6, 8));
            card.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 6; -fx-background-radius: 6;");

            // Pastille du qualificateur
            String qualText = action.qualifier() != null ? action.qualifier().trim() : "N";
            if (action.hasParameter()) {
                qualText += " (" + action.parameter() + ")";
            }
            var qualBadge = new Label(qualText);
            String qualColor = switch (action.qualifier() != null ? action.qualifier().toUpperCase() : "N") {
                case "S" -> "#EF4444"; // Rouge Set
                case "R" -> "#DC2626"; // Rouge foncé Reset
                case "L", "D" -> "#F59E0B"; // Ambre temporisé
                default -> "#0284C7"; // Bleu standard
            };
            qualBadge.setStyle("-fx-background-color: " + qualColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");

            // Nom de la variable
            var varLabel = new Label(action.variable() != null ? action.variable().getName() : "");
            varLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 12px;");

            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Bouton de suppression
            var delBtn = new Button("🗑");
            delBtn.setTooltip(new Tooltip("Supprimer cette action"));
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #EF4444; -fx-font-size: 12px; -fx-cursor: hand;");
            delBtn.setOnAction(e -> {
                step.getActions().remove(action);
                refreshActionsList(step);
                notifyRedraw();
            });

            card.getChildren().addAll(qualBadge, varLabel, spacer, delBtn);
            listOfActions.getChildren().add(card);
        }
    }

    private void notifyRedraw() {
        if (onModified != null) {
            onModified.run();
        }
    }
}
