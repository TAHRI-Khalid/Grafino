package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.SfcProgram;
import tk.tahri.grafino.simulation.TimingRecorder;
import tk.tahri.grafino.simulation.TimingSample;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fenêtre principale de Grafino avec vue partagée (SplitPane) intégrée :
 * - Vue supérieure : Éditeur graphique interactif Grafcet.
 * - Vue inférieure : Table des variables d'automatismes (E/S & Mémoires CEI 61131-3) pliable/dépliable.
 */
public class MainUI extends BorderPane {

    private final AppMenuBar menuBar = new AppMenuBar();
    private final Grafcet grafcet = new Grafcet();
    private final VariableTable variableTable = new VariableTable();
    private final TimingRecorder timingRecorder = new TimingRecorder();
    private final TimingDiagramPanel timingDiagramPanel;

    private enum BottomViewMode { VARIABLES, TIMING }
    private BottomViewMode currentBottomView = BottomViewMode.VARIABLES;
    private final Button tabVariablesBtn = new Button("📋 Variables (CEI 61131-3)");
    private final Button tabTimingBtn = new Button("📈 Chronogramme d'Évolution");
    private final StackPane bottomContentArea = new StackPane();

    private final SplitPane splitPane = new SplitPane();
    private final VBox tableContainer = new VBox();
    private final Label tableSummaryLabel = new Label("0 Entrée(s) | 0 Sortie(s) | 0 Mémoire(s)");
    private final Button toggleTableBtn = new Button("▲ Agrandir");

    private boolean isTableCollapsed = true;
    private double lastDividerPosition = 0.68;
    private final ProjectSessionController sessionController;
    private final SimulationCoordinator simulationCoordinator;

    public enum CloseChoice {
        SAVE,
        DISCARD,
        CANCEL
    }

    @FunctionalInterface
    public interface CloseConfirmationHandler {
        CloseChoice prompt(String projectName);
    }

    private CloseConfirmationHandler defaultCloseConfirmationHandler() {
        return projectName -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Fermeture de Grafino");
            alert.setHeaderText("Enregistrer les modifications apportées au projet '" + projectName + "' ?");
            alert.setContentText("Des modifications n'ont pas été enregistrées.\n"
                    + "Voulez-vous enregistrer les changements avant de fermer, ou abandonner les modifications ?");

            ButtonType btnSave = new ButtonType("Enregistrer", ButtonBar.ButtonData.YES);
            ButtonType btnDiscard = new ButtonType("Abandonner", ButtonBar.ButtonData.NO);
            ButtonType btnCancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnSave, btnDiscard, btnCancel);

            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == btnCancel) {
                return CloseChoice.CANCEL;
            } else if (choice.get() == btnSave) {
                return CloseChoice.SAVE;
            } else {
                return CloseChoice.DISCARD;
            }
        };
    }

    public MainUI() {
        this.timingDiagramPanel = new TimingDiagramPanel(timingRecorder, this::onRestoreTimingState);
        this.sessionController = new ProjectSessionController(
                this,
                grafcet,
                variableTable,
                defaultCloseConfirmationHandler(),
                fc -> fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null),
                this::setStatus,
                this::setStatusError
        );
        this.simulationCoordinator = new SimulationCoordinator(
                this,
                grafcet,
                variableTable,
                menuBar,
                timingRecorder,
                sessionController::getCurrentProjectName,
                sessionController::updateWindowTitle,
                () -> {
                    if (!isTableCollapsed && currentBottomView == BottomViewMode.TIMING) {
                        timingDiagramPanel.redraw();
                    }
                },
                this::setStatus
        );
        this.sessionController.setHooks(
                simulationCoordinator::refreshDiagnostics,
                () -> {
                    if (simulationCoordinator.isSimulationActive()) {
                        simulationCoordinator.stopSimulation();
                    }
                }
        );

        // 1. Barre de menus principale
        this.setTop(menuBar);

        // 2. Initialisation du Grafcet
        grafcet.setVariablesSupplier(variableTable::getVariables);
        grafcet.setOnHistoryChanged(() -> menuBar.updateUndoRedoState(grafcet.canUndo(), grafcet.canRedo()));
        grafcet.clearDiagram();
        grafcet.startRender();
        sessionController.setLastSavedProgram(
                grafcet.toProgram(sessionController.getCurrentProjectName(), variableTable.getVariables()));

        // 3. Configuration de la vue partagée centrale (SplitPane Grafcet / Variables)
        setupCenterSplitPane();
        this.setCenter(splitPane);

        // 4. Handlers de la barre de menus
        setupMenuBarEvents(menuBar);

        // 5. Synchronisation de la sélection d'éléments
        setupSelectionListener();

        // 6. Raccourcis clavier (navigation fine & commandes globales)
        setupKeyboardNavigation();

        // 7. Écoute des modifications structurelles du Grafcet pour le linting normatif
        grafcet.setOnStructureChanged(this::refreshDiagnostics);

        // 8. Écoute des modifications de variables pour rafraîchir les panneaux latéraux
        variableTable.setOnVariablesChanged(this::onVariablesUpdated);
        variableTable.setOnValidationError(this::setStatusError);
        onVariablesUpdated();

        // 9. Titre dynamique de fenêtre lié au fichier chargé & interception fermeture
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldW, newW) -> {
                    updateWindowTitle();
                    if (newW instanceof Stage stage) {
                        stage.setOnCloseRequest(event -> {
                            if (!confirmSaveOrDiscardBeforeClosing()) {
                                event.consume();
                            }
                        });
                    }
                });
            }
        });

        // 10. Premier calcul de linting normatif
        refreshDiagnostics();
    }

    private void setupCenterSplitPane() {
        splitPane.setOrientation(Orientation.VERTICAL);

        // En-tête rétractable avec onglets (Variables vs Chronogramme)
        var header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 12, 4, 12));
        header.setStyle("-fx-background-color: " + AppTheme.PANEL_BG + "; -fx-border-color: " + AppTheme.BORDER_COLOR + "; -fx-border-width: 1 0 1 0; -fx-cursor: hand;");

        tabVariablesBtn.setOnAction(e -> {
            switchBottomView(BottomViewMode.VARIABLES);
            e.consume();
        });
        tabTimingBtn.setOnAction(e -> {
            switchBottomView(BottomViewMode.TIMING);
            e.consume();
        });
        updateBottomTabStyles();

        var tabsBox = new HBox(4);
        tabsBox.setAlignment(Pos.CENTER_LEFT);
        tabsBox.getChildren().addAll(tabVariablesBtn, tabTimingBtn);

        tableSummaryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toggleTableBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        toggleTableBtn.setOnAction(e -> {
            toggleVariableTable();
            e.consume();
        });

        header.setOnMouseClicked(e -> {
            if (e.getTarget() != toggleTableBtn && e.getTarget() != tabVariablesBtn && e.getTarget() != tabTimingBtn) {
                toggleVariableTable();
            }
        });

        header.getChildren().addAll(tabsBox, new Separator(Orientation.VERTICAL), tableSummaryLabel, spacer, toggleTableBtn);

        // Zone de contenu inférieure : commutable entre VariableTable et TimingDiagramPanel
        bottomContentArea.getChildren().addAll(variableTable, timingDiagramPanel);
        VBox.setVgrow(bottomContentArea, Priority.ALWAYS);

        timingDiagramPanel.setVisible(false);
        timingDiagramPanel.setManaged(false);

        tableContainer.getChildren().addAll(header, bottomContentArea);

        // Fermé par défaut au démarrage : restreindre strictement la hauteur maximale de tableContainer
        // à sa taille préférée (hauteur de l'en-tête seul) et empêcher le redimensionnement automatique avec le parent
        bottomContentArea.setVisible(false);
        bottomContentArea.setManaged(false);
        tableContainer.setMaxHeight(Region.USE_PREF_SIZE);
        SplitPane.setResizableWithParent(tableContainer, false);

        splitPane.getItems().addAll(grafcet, tableContainer);
        splitPane.setDividerPositions(1.0);

        javafx.application.Platform.runLater(() -> {
            if (isTableCollapsed) {
                tableContainer.setMaxHeight(Region.USE_PREF_SIZE);
                SplitPane.setResizableWithParent(tableContainer, false);
                splitPane.setDividerPositions(1.0);
            }
        });
    }

    private void switchBottomView(BottomViewMode mode) {
        currentBottomView = mode;
        updateBottomTabStyles();

        if (mode == BottomViewMode.VARIABLES) {
            variableTable.setVisible(true);
            variableTable.setManaged(true);
            timingDiagramPanel.setVisible(false);
            timingDiagramPanel.setManaged(false);
            onVariablesUpdated();
        } else {
            variableTable.setVisible(false);
            variableTable.setManaged(false);
            timingDiagramPanel.setVisible(true);
            timingDiagramPanel.setManaged(true);
            timingDiagramPanel.redraw();
            tableSummaryLabel.setText("📈 Chronogramme multi-voies : " + timingRecorder.getSampleCount() + " échantillons");
            tableSummaryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.PRIMARY_TEAL_DARK + ";");
        }

        if (isTableCollapsed) {
            showVariableTable();
        }
    }

    private void updateBottomTabStyles() {
        tabVariablesBtn.getStyleClass().removeAll("bottom-tab-active", "bottom-tab-inactive");
        tabTimingBtn.getStyleClass().removeAll("bottom-tab-active", "bottom-tab-inactive");
        tabVariablesBtn.getStyleClass().add(currentBottomView == BottomViewMode.VARIABLES ? "bottom-tab-active" : "bottom-tab-inactive");
        tabTimingBtn.getStyleClass().add(currentBottomView == BottomViewMode.TIMING ? "bottom-tab-active" : "bottom-tab-inactive");
    }

    private void onRestoreTimingState(TimingSample sample) {
        if (sample == null) return;
        Set<String> activeSteps = sample.stepStates().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<String> activeOuts = sample.outputStates().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        grafcet.updateSimulationVisuals(activeSteps, activeOuts);
        setStatus("État temporel restauré : t = " + String.format(Locale.US, "%.2fs", sample.timestampMs() / 1000.0));
    }

    private void setupMenuBarEvents(AppMenuBar menuBar) {
        menuBar.setOnUndoClick(event -> grafcet.undo());
        menuBar.setOnRedoClick(event -> grafcet.redo());
        menuBar.setOnAutoLayoutClick(event -> grafcet.autoLayout());
        menuBar.setOnDeleteClick(event -> grafcet.deleteSelected());
        menuBar.setOnDeselectClick(event -> {
            grafcet.getComponents().forEach(c -> c.setSelected(false));
            grafcet.fireSelectionChange(Optional.empty());
        });
        menuBar.setOnToggleVariableTable(event -> {
            switchBottomView(BottomViewMode.VARIABLES);
            toggleVariableTable();
        });
        menuBar.setOnToggleTimingDiagram(event -> {
            switchBottomView(BottomViewMode.TIMING);
            if (isTableCollapsed) {
                showVariableTable();
            }
        });
        menuBar.setOnDiagnosticsClick(event -> toggleDiagnosticsPanel());
        menuBar.setOnSimulationBtnClick(event -> toggleSimulation());
        menuBar.setOnZoomInClick(event -> grafcet.zoomIn());
        menuBar.setOnZoomOutClick(event -> grafcet.zoomOut());
        menuBar.setOnResetZoomClick(event -> grafcet.resetZoom());

        // Handlers du menu Fichier & Persistance (Axe 1)
        menuBar.setOnNewProject(event -> onNewProject());
        menuBar.setOnOpenProject(event -> onOpenProject());
        menuBar.setOnSaveProject(event -> onSaveProject());
        menuBar.setOnSaveAsProject(event -> onSaveAsProject());
        menuBar.setOnExportPng(event -> onExportPng());
        menuBar.setOnExit(event -> requestClose());

        grafcet.setStatusMessageConsumer(this::setStatus);
    }

    private void setupSelectionListener() {
        grafcet.setOnSelectionChange(selectedComponent -> {
            if (simulationCoordinator.isSimulationActive()) {
                // En mode simulation, le panneau droit reste le pupitre virtuel interactif
                return;
            }
            if (selectedComponent.isPresent()) {
                switch (selectedComponent.get()) {
                    case Step step -> {
                        var sideProperty = new StepProperty(step, variableTable.getOutputVariables(),
                                variableTable.getCounterVariables(),
                                () -> {
                                    grafcet.redraw();
                                    refreshDiagnostics();
                                }, () -> {
                                    simulationCoordinator.restoreIdleRightPanel();
                                });
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
                                () -> {
                                    grafcet.redraw();
                                    refreshDiagnostics();
                                }, () -> {
                                    simulationCoordinator.restoreIdleRightPanel();
                                });
                        this.setRight(sideProperty);
                        setStatus("Transition T" + transition.getNumber() + " sélectionnée");
                    }
                }
            } else {
                simulationCoordinator.restoreIdleRightPanel();
                setStatus("Prêt");
            }
        });
    }

    private void setupKeyboardNavigation() {
        this.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.N) {
                    onNewProject();
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.O) {
                    onOpenProject();
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.S) {
                    if (event.isShiftDown()) {
                        onSaveAsProject();
                    } else {
                        onSaveProject();
                    }
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.E) {
                    onExportPng();
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.Q) {
                    requestClose();
                    event.consume();
                    return;
                } else if (event.getCode() == KeyCode.K) {
                    switchBottomView(BottomViewMode.TIMING);
                    if (isTableCollapsed) {
                        showVariableTable();
                    }
                    event.consume();
                    return;
                }
            }

            grafcet.getSelectedComponent().ifPresent(component -> {
                int step = event.isControlDown() ? 10 : 2;
                switch (event.getCode()) {
                    case UP -> component.updatePositionAndLinks(
                            grafcet.clampPositionWithoutOverlap(component, component.getX(), component.getY() - step));
                    case DOWN -> component.updatePositionAndLinks(
                            grafcet.clampPositionWithoutOverlap(component, component.getX(), component.getY() + step));
                    case LEFT -> component.updatePositionAndLinks(
                            grafcet.clampPositionWithoutOverlap(component, component.getX() - step, component.getY()));
                    case RIGHT -> component.updatePositionAndLinks(
                            grafcet.clampPositionWithoutOverlap(component, component.getX() + step, component.getY()));
                    default -> { return; }
                }
                refreshDiagnostics();
                event.consume();
            });
        });
    }

    private void onVariablesUpdated() {
        long inCount = variableTable.getInputVariables().size();
        long outCount = variableTable.getOutputVariables().size();
        long intCount = variableTable.getVariables().size() - inCount - outCount;
        tableSummaryLabel.setText(inCount + " Entrée(s) %I | " + outCount + " Sortie(s) %Q | " + intCount + " Interne(s)");
        tableSummaryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        refreshDiagnostics();

        if (simulationCoordinator.isSimulationActive()) {
            // Ne pas écraser le pupitre de simulation
            return;
        }

        // Rafraîchir le panneau latéral si un élément est actuellement sélectionné
        grafcet.getSelectedComponent().ifPresent(component -> {
            switch (component) {
                case Step step -> {
                    var sideProperty = new StepProperty(step, variableTable.getOutputVariables(),
                            variableTable.getCounterVariables(),
                            () -> {
                                grafcet.redraw();
                                refreshDiagnostics();
                            }, simulationCoordinator::restoreIdleRightPanel);
                    this.setRight(sideProperty);
                }
                case Transition transition -> {
                    List<Step> availableSteps = grafcet.getComponents().stream()
                            .filter(Step.class::isInstance)
                            .map(Step.class::cast)
                            .toList();
                    var sideProperty = new TransitionProperty(transition, variableTable.getInputVariables(), availableSteps,
                            variableTable.getCounterVariables(),
                            () -> {
                                grafcet.redraw();
                                refreshDiagnostics();
                            }, simulationCoordinator::restoreIdleRightPanel);
                    this.setRight(sideProperty);
                }
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
            tableContainer.setMaxHeight(Double.MAX_VALUE);
            SplitPane.setResizableWithParent(tableContainer, true);
            bottomContentArea.setVisible(true);
            bottomContentArea.setManaged(true);
            splitPane.setDividerPositions(lastDividerPosition > 0.1 && lastDividerPosition < 0.95 ? lastDividerPosition : 0.68);
            toggleTableBtn.setText("▼ Réduire");
            isTableCollapsed = false;
            if (currentBottomView == BottomViewMode.TIMING) {
                timingDiagramPanel.redraw();
            }
        }
    }

    private void collapseVariableTable() {
        if (!isTableCollapsed) {
            double[] pos = splitPane.getDividerPositions();
            if (pos.length > 0 && pos[0] > 0.1 && pos[0] < 0.95) {
                lastDividerPosition = pos[0];
            }
            bottomContentArea.setVisible(false);
            bottomContentArea.setManaged(false);
            tableContainer.setMaxHeight(Region.USE_PREF_SIZE);
            SplitPane.setResizableWithParent(tableContainer, false);
            splitPane.setDividerPositions(1.0);
            toggleTableBtn.setText("▲ Agrandir");
            isTableCollapsed = true;
        }
    }

    private void setStatus(String message) {
        if (message != null && !message.isBlank()) {
            tableSummaryLabel.setText(message);
            tableSummaryLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL_DARK + ";");
        }
    }

    private void setStatusError(String errorMessage) {
        tableSummaryLabel.setText("⚠ " + errorMessage);
        tableSummaryLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.RED_DANGER + ";");
    }

    // ==========================================
    // GESTION DES MODIFICATIONS & FERMETURE (SÉCURISATION DU WORKFLOW)
    // ==========================================

    /**
     * Vérifie si le diagramme ou les variables ont été modifiés depuis le dernier enregistrement.
     */
    public boolean isModified() {
        return sessionController.isModified();
    }

    public boolean confirmSaveOrDiscardBeforeClosing() {
        return sessionController.confirmSaveOrDiscardBeforeClosing();
    }

    public void requestClose() {
        sessionController.requestClose();
    }

    public void onNewProject() {
        sessionController.onNewProject();
    }

    public void onOpenProject() {
        sessionController.onOpenProject();
    }

    public boolean onSaveProject() {
        return sessionController.onSaveProject();
    }

    public boolean onSaveAsProject() {
        return sessionController.onSaveAsProject();
    }

    public boolean doSave(File targetFile) {
        return sessionController.doSave(targetFile);
    }

    public void onExportPng() {
        sessionController.onExportPng();
    }

    public void updateWindowTitle() {
        sessionController.updateWindowTitle();
    }

    public SfcProgram getLastSavedProgram() {
        return sessionController.getLastSavedProgram();
    }

    public void setLastSavedProgram(SfcProgram lastSavedProgram) {
        sessionController.setLastSavedProgram(lastSavedProgram);
        updateWindowTitle();
    }

    public Grafcet getGrafcet() {
        return grafcet;
    }

    public VariableTable getVariableTable() {
        return variableTable;
    }

    public AppMenuBar getAppMenuBar() {
        return menuBar;
    }

    public File getCurrentFile() {
        return sessionController.getCurrentFile();
    }

    public void setCurrentFile(File currentFile) {
        sessionController.setCurrentFile(currentFile);
        updateWindowTitle();
    }

    public String getCurrentProjectName() {
        return sessionController.getCurrentProjectName();
    }

    public void setCurrentProjectName(String currentProjectName) {
        sessionController.setCurrentProjectName(
                (currentProjectName != null && !currentProjectName.isBlank()) ? currentProjectName : "Nouveau_Projet");
        updateWindowTitle();
    }

    public void setCloseConfirmationHandler(CloseConfirmationHandler handler) {
        sessionController.setCloseConfirmationHandler((handler != null) ? handler : defaultCloseConfirmationHandler());
    }

    public CloseConfirmationHandler getCloseConfirmationHandler() {
        return sessionController.getCloseConfirmationHandler();
    }

    void setSaveFileChooser(java.util.function.Function<FileChooser, File> saveFileChooser) {
        sessionController.setSaveFileChooser((saveFileChooser != null) ? saveFileChooser : fc ->
                fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null));
    }

    private void toggleSimulation() {
        simulationCoordinator.toggleSimulation();
    }

    private void toggleDiagnosticsPanel() {
        simulationCoordinator.toggleDiagnosticsPanel();
    }

    public void refreshDiagnostics() {
        simulationCoordinator.refreshDiagnostics();
    }
}
