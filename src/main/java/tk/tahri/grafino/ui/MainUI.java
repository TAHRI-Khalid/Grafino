package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.SfcActionBlock;
import tk.tahri.grafino.model.SfcQualifier;
import tk.tahri.grafino.simulation.GrafcetSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fenêtre principale de Grafino avec vue partagée (SplitPane) intégrée :
 * - Vue supérieure : Éditeur graphique interactif Grafcet.
 * - Vue inférieure : Table des variables d'automatismes (E/S & Mémoires CEI 61131-3) pliable/dépliable.
 */
public class MainUI extends BorderPane {

    private final TopToolBar toolBar = new TopToolBar();
    private final Grafcet grafcet = new Grafcet();
    private final VariableTable variableTable = new VariableTable();

    private final SplitPane splitPane = new SplitPane();
    private final VBox tableContainer = new VBox();
    private final Label tableTitleLabel = new Label("📋 Table des Variables d'Automatismes (CEI 61131-3)");
    private final Label tableSummaryLabel = new Label("0 Entrée(s) | 0 Sortie(s) | 0 Mémoire(s)");
    private final Button toggleTableBtn = new Button("▼ Réduire");

    private boolean isTableCollapsed = false;
    private double lastDividerPosition = 0.68;
    private SimulationPanel activeSimulationPanel = null;

    public MainUI() {
        // 1. Barre d'outils supérieure
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
        toolBar.setOnAutoLayoutClick(event -> grafcet.autoLayout());
        toolBar.setOnSimulationBtnClick(event -> toggleSimulation());
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
            if (activeSimulationPanel != null) {
                // En mode simulation, le panneau droit reste le pupitre virtuel interactif
                return;
            }
            if (selectedComponent.isPresent()) {
                switch (selectedComponent.get()) {
                    case Step step -> {
                        var sideProperty = new StepProperty(step, variableTable.getOutputVariables(),
                                variableTable.getCounterVariables(),
                                () -> grafcet.redraw(), () -> this.setRight(null));
                        this.setRight(sideProperty);
                        setStatus("Étape S" + step.getNumber() + " sélectionnée");
                    }
                    case Transition transition -> {
                        List<Step> availableSteps = grafcet.getComponents().stream()
                                .filter(Step.class::isInstance)
                                .map(Step.class::cast)
                                .toList();
                        var sideProperty = new TransitionProperty(transition, variableTable.getInputVariables(), availableSteps,
                                variableTable.getCounterVariables(),
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

        if (activeSimulationPanel != null) {
            // Ne pas écraser le pupitre de simulation
            return;
        }

        // Rafraîchir le panneau latéral si un élément est actuellement sélectionné
        grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step) {
                var sideProperty = new StepProperty(step, variableTable.getOutputVariables(),
                        variableTable.getCounterVariables(),
                        () -> grafcet.redraw(), () -> this.setRight(null));
                this.setRight(sideProperty);
            } else if (component instanceof Transition transition) {
                List<Step> availableSteps = grafcet.getComponents().stream()
                        .filter(Step.class::isInstance)
                        .map(Step.class::cast)
                        .toList();
                var sideProperty = new TransitionProperty(transition, variableTable.getInputVariables(), availableSteps,
                        variableTable.getCounterVariables(),
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

    // ==========================================
    // GESTION DU MODE DE SIMULATION SYNCHRONE
    // ==========================================

    private void toggleSimulation() {
        if (activeSimulationPanel != null) {
            stopSimulation();
        } else {
            startSimulation();
        }
    }

    private void startSimulation() {
        if (activeSimulationPanel != null) {
            stopSimulation();
            return;
        }

        GrafcetSimulator simulator = new GrafcetSimulator();

        // 1. Enregistrement des variables IEC déclarées dans la table
        for (Variable v : variableTable.getInputVariables()) {
            simulator.getState().setBooleanVariable(v.getName(), false);
        }
        for (Variable v : variableTable.getOutputVariables()) {
            simulator.getState().setBooleanVariable(v.getName(), false);
        }
        for (Variable v : variableTable.getCounterVariables()) {
            simulator.getState().setCounterValue(v.getName(), 0);
        }

        // 2. Enregistrement des étapes et conversion de leurs blocs d'actions
        for (StepAndTransitionCommonBehavior comp : grafcet.getComponents()) {
            if (comp instanceof Step step) {
                String id = "s" + step.getNumber();
                List<SfcActionBlock> actionBlocks = new ArrayList<>();
                for (Action a : step.getActions()) {
                    SfcQualifier qual = SfcQualifier.N;
                    if (a.qualifier() != null) {
                        try {
                            qual = SfcQualifier.valueOf(a.qualifier().trim().toUpperCase());
                        } catch (IllegalArgumentException ignored) {}
                    }
                    String varName = a.getDisplayText();
                    if (varName == null || varName.isBlank()) {
                        varName = "ACTION";
                    }
                    java.time.Duration dur = qual.isTimed() ? java.time.Duration.ofSeconds(2) : null;
                    actionBlocks.add(new SfcActionBlock(qual, varName, dur, null));
                }
                simulator.registerStep(id, "Step" + step.getNumber(), step.getNumber(), step.isInitial(), actionBlocks);
            }
        }

        // 3. Enregistrement des transitions avec leurs liaisons amont/aval
        for (StepAndTransitionCommonBehavior comp : grafcet.getComponents()) {
            if (comp instanceof Transition trans) {
                String id = "t" + trans.getNumber();
                Set<String> upStepIds = trans.getParentSteps().stream()
                        .map(s -> "s" + s.getNumber())
                        .collect(Collectors.toSet());
                Set<String> downStepIds = trans.getLinksToChildren().stream()
                        .map(Link::getChildComponent)
                        .filter(Step.class::isInstance)
                        .map(Step.class::cast)
                        .map(s -> "s" + s.getNumber())
                        .collect(Collectors.toSet());

                simulator.registerTransition(id, "T" + trans.getNumber(), trans.getNumber(),
                        trans.getReceptivity(), upStepIds, downStepIds);
            }
        }

        // Initialisation du simulateur à la situation initiale
        simulator.reset();

        // Désélectionner tout élément graphique
        grafcet.getComponents().forEach(c -> c.setSelected(false));
        grafcet.fireSelectionChange(Optional.empty());

        // Créer le pupitre de commande interactif
        activeSimulationPanel = new SimulationPanel(
                simulator,
                grafcet,
                variableTable.getInputVariables(),
                variableTable.getOutputVariables(),
                variableTable.getCounterVariables(),
                this::stopSimulation
        );

        this.setRight(activeSimulationPanel);
        toolBar.setSimulationActive(true);
        setStatus("Simulation synchrone démarrée");
    }

    private void stopSimulation() {
        if (activeSimulationPanel != null) {
            SimulationPanel panel = activeSimulationPanel;
            activeSimulationPanel = null; // Mettre à null en premier pour briser toute récursion
            panel.stop();
        }
        this.setRight(null);
        toolBar.setSimulationActive(false);
        grafcet.clearSimulationVisuals();
        setStatus("Simulation arrêtée");
    }
}
