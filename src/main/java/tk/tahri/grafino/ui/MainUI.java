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

        // 4. Handlers de la barre d'outils graphique
        setupToolBarEvents(toolBar);

        // 5. Synchronisation de la sélection d'éléments
        setupSelectionListener();

        // 6. Raccourcis clavier (déplacement fin des composants)
        setupKeyboardNavigation();

        // 7. Écoute des modifications de variables pour rafraîchir les panneaux latéraux
        variableTable.setOnVariablesChanged(this::onVariablesUpdated);
        variableTable.setOnValidationError(this::setStatusError);
        onVariablesUpdated();
    }

    private void setupCenterSplitPane() {
        splitPane.setOrientation(Orientation.VERTICAL);

        // En-tête rétractable de la table des variables au style Arduino IDE
        var header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 12, 5, 12));
        header.setStyle("-fx-background-color: " + AppTheme.PANEL_BG + "; -fx-border-color: " + AppTheme.BORDER_COLOR + "; -fx-border-width: 1 0 1 0;");

        tableTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        tableSummaryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toggleTableBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        toggleTableBtn.setOnAction(e -> toggleVariableTable());

        header.getChildren().addAll(tableTitleLabel, tableSummaryLabel, spacer, toggleTableBtn);

        tableContainer.getChildren().addAll(header, variableTable);
        VBox.setVgrow(variableTable, Priority.ALWAYS);

        splitPane.getItems().addAll(grafcet, tableContainer);
        splitPane.setDividerPositions(lastDividerPosition);
    }

    private void setupToolBarEvents(TopToolBar toolBar) {
        toolBar.setOnStepBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition) {
                grafcet.createStep(transition);
            }
        }));

        toolBar.setOnTransitionBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step) {
                grafcet.createTransition(step);
            }
        }));

        toolBar.setOnOrBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step) {
                grafcet.createOrBranch(step);
            }
        }));

        toolBar.setOnAndBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition) {
                grafcet.createAndBranch(transition);
            }
        }));

        toolBar.setOnDeleteBtnClick(event -> grafcet.deleteSelected());
        toolBar.setOnZoomInClick(event -> grafcet.zoomIn());
        toolBar.setOnZoomOutClick(event -> grafcet.zoomOut());
        toolBar.setOnResetZoomClick(event -> grafcet.resetZoom());

        grafcet.setStatusMessageConsumer(this::setStatus);

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
        // Barre de statut supprimée pour maximiser la surface disponible
    }

    private void setStatusError(String errorMessage) {
        tableSummaryLabel.setText("⚠ " + errorMessage);
        tableSummaryLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.RED_DANGER + ";");
    }
}
