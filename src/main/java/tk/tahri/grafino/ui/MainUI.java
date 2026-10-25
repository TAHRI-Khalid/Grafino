package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.*;

/**
 * Fenêtre principale de Grafino avec vue partagée (SplitPane) intégrée :
 * - Vue supérieure : Éditeur graphique interactif Grafcet.
 * - Vue inférieure : Table des variables d'automatismes (E/S & Mémoires CEI 61131-3) pliable/dépliable.
 */
public class MainUI extends BorderPane {

    private final Grafcet grafcet = new Grafcet();
    private final VariableTable variableTable = new VariableTable();

    private final SplitPane splitPane = new SplitPane();
    private final VBox tableContainer = new VBox();
    private final Label tableTitleLabel = new Label("📋 Table des Variables d'Automatismes (CEI 61131-3)");
    private final Label tableSummaryLabel = new Label("0 Entrée(s) | 0 Sortie(s) | 0 Mémoire(s)");
    private final Button toggleTableBtn = new Button("▼ Réduire");

    private final Label statusLabel = new Label("Prêt - Modélisation Grafcet / SFC");
    private boolean isTableCollapsed = false;
    private double lastDividerPosition = 0.68;

    public MainUI() {
        // 1. Barre d'outils supérieure
        var toolBar = new TopToolBar();
        this.setTop(toolBar);

        // 2. Initialisation du Grafcet
        grafcet.createInitialStep();
        grafcet.startRender();

        // 3. Configuration de la vue partagée centrale (SplitPane Grafcet / Variables)
        setupCenterSplitPane();
        this.setCenter(splitPane);

        // 4. Barre d'état inférieure
        var statusBar = createStatusBar();
        this.setBottom(statusBar);

        // 5. Handlers de la barre d'outils graphique
        setupToolBarEvents(toolBar);

        // 6. Synchronisation de la sélection d'éléments
        setupSelectionListener();

        // 7. Raccourcis clavier (déplacement fin des composants)
        setupKeyboardNavigation();

        // 8. Écoute des modifications de variables pour rafraîchir les panneaux latéraux
        variableTable.setOnVariablesChanged(this::onVariablesUpdated);
        variableTable.setOnValidationError(this::setStatusError);
        onVariablesUpdated();
    }

    private void setupCenterSplitPane() {
        splitPane.setOrientation(Orientation.VERTICAL);

        // En-tête rétractable de la table des variables
        var header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 8, 4, 8));
        header.setStyle("-fx-background-color: #E2E8F0; -fx-border-color: #CBD5E1; -fx-border-width: 1 0 1 0;");

        tableTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1E293B;");
        tableSummaryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toggleTableBtn.setStyle("-fx-font-size: 11px; -fx-background-radius: 4; -fx-padding: 2 8 2 8;");
        toggleTableBtn.setOnAction(e -> toggleVariableTable());

        header.getChildren().addAll(tableTitleLabel, tableSummaryLabel, spacer, toggleTableBtn);

        tableContainer.getChildren().addAll(header, variableTable);
        VBox.setVgrow(variableTable, Priority.ALWAYS);

        splitPane.getItems().addAll(grafcet, tableContainer);
        splitPane.setDividerPositions(lastDividerPosition);
    }

    private ToolBar createStatusBar() {
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
        var statusBar = new ToolBar(statusLabel);
        statusBar.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        return statusBar;
    }

    private void setupToolBarEvents(TopToolBar toolBar) {
        toolBar.setOnStepBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && transition.getLinksToChildren().isEmpty()) {
                grafcet.createStep(transition);
                setStatus("Étape créée après la transition T" + transition.getNumber());
            }
        }));

        toolBar.setOnTransitionBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && step.getLinksToChildren().isEmpty()) {
                grafcet.createTransition(step);
                setStatus("Transition créée après l'étape " + step.getNumber());
            }
        }));

        toolBar.setOnOrBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && !step.getLinksToChildren().isEmpty()) {
                grafcet.createTransition(step);
                setStatus("Branche OU créée (Sélection de séquence)");
            }
        }));

        toolBar.setOnAndBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && !transition.getLinksToChildren().isEmpty()) {
                grafcet.createStep(transition);
                setStatus("Branche ET créée (Synchronisation)");
            }
        }));

        // Ajout & suppression rapide de variables
        toolBar.setOnAddVariableBtnClick(event -> {
            showVariableTable();
            variableTable.addVariable();
            setStatus("Nouvelle variable ajoutée");
        });

        toolBar.setOnRemoveSelectedVariable(event -> {
            variableTable.removeSelectedVariable();
            setStatus("Variable supprimée");
        });
    }

    private void setupSelectionListener() {
        grafcet.setOnSelectionChange(selectedComponent -> {
            if (selectedComponent.isPresent()) {
                switch (selectedComponent.get()) {
                    case Step step -> {
                        var sideProperty = new StepProperty(step, variableTable.getOutputVariables(),
                                () -> grafcet.redraw(), () -> this.setRight(null));
                        this.setRight(sideProperty);
                        setStatus("Étape S" + step.getNumber() + " sélectionnée");
                    }
                    case Transition transition -> {
                        var sideProperty = new TransitionProperty(transition, variableTable.getInputVariables(),
                                () -> grafcet.redraw(), () -> this.setRight(null));
                        this.setRight(sideProperty);
                        setStatus("Transition T" + transition.getNumber() + " sélectionnée");
                    }
                }
            } else {
                this.setRight(null);
                setStatus("Prêt");
            }
        });
    }

    private void setupKeyboardNavigation() {
        this.setOnKeyPressed(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            int step = event.isControlDown() ? 10 : 2;
            switch (event.getCode()) {
                case UP -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() - step));
                case DOWN -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() + step));
                case LEFT -> component.updatePositionAndLinks(new Point2D(component.getX() - step, component.getY()));
                case RIGHT -> component.updatePositionAndLinks(new Point2D(component.getX() + step, component.getY()));
                default -> { return; }
            }
            event.consume();
        }));
    }

    private void onVariablesUpdated() {
        long inCount = variableTable.getInputVariables().size();
        long outCount = variableTable.getOutputVariables().size();
        long intCount = variableTable.getVariables().size() - inCount - outCount;
        tableSummaryLabel.setText(inCount + " Entrée(s) %I | " + outCount + " Sortie(s) %Q | " + intCount + " Interne(s)");

        // Rafraîchir le panneau latéral si un élément est actuellement sélectionné
        grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step) {
                var sideProperty = new StepProperty(step, variableTable.getOutputVariables(),
                        () -> grafcet.redraw(), () -> this.setRight(null));
                this.setRight(sideProperty);
            } else if (component instanceof Transition transition) {
                var sideProperty = new TransitionProperty(transition, variableTable.getInputVariables(),
                        () -> grafcet.redraw(), () -> this.setRight(null));
                this.setRight(sideProperty);
            }
        });
    }

    private void toggleVariableTable() {
        if (isTableCollapsed) {
            showVariableTable();
        } else {
            collapseVariableTable();
        }
    }

    private void showVariableTable() {
        if (isTableCollapsed) {
            variableTable.setVisible(true);
            variableTable.setManaged(true);
            splitPane.setDividerPositions(lastDividerPosition > 0.1 && lastDividerPosition < 0.9 ? lastDividerPosition : 0.68);
            toggleTableBtn.setText("▼ Réduire");
            isTableCollapsed = false;
        }
    }

    private void collapseVariableTable() {
        if (!isTableCollapsed) {
            double[] pos = splitPane.getDividerPositions();
            if (pos.length > 0) {
                lastDividerPosition = pos[0];
            }
            variableTable.setVisible(false);
            variableTable.setManaged(false);
            splitPane.setDividerPositions(0.96);
            toggleTableBtn.setText("▲ Agrandir");
            isTableCollapsed = true;
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
    }

    private void setStatusError(String errorMessage) {
        statusLabel.setText(errorMessage);
        statusLabel.setStyle("-fx-text-fill: #FCA5A5; -fx-font-weight: bold; -fx-font-size: 12px;");
    }
}
