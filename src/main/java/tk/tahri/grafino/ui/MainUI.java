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
import tk.tahri.grafino.model.SfcActionBlock;
import tk.tahri.grafino.model.SfcProgram;
import tk.tahri.grafino.model.SfcQualifier;
import tk.tahri.grafino.model.SfcValidationMessage;
import tk.tahri.grafino.simulation.GrafcetSimulator;
import tk.tahri.grafino.simulation.TimingRecorder;
import tk.tahri.grafino.simulation.TimingSample;
import tk.tahri.grafino.storage.SfcStorage;

import java.io.File;
import java.util.ArrayList;
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
    private SimulationPanel activeSimulationPanel = null;
    private DiagnosticsPanel diagnosticsPanel = null;

    // Gestion du fichier actif (Axe 1 : Sécurisation du workflow)
    private File currentFile = null;
    private String currentProjectName = "Nouveau_Projet";

    public MainUI() {
        this.timingDiagramPanel = new TimingDiagramPanel(timingRecorder, this::onRestoreTimingState);

        // 1. Barre de menus principale
        this.setTop(menuBar);

        // 2. Initialisation du Grafcet
        grafcet.createInitialStep();
        grafcet.startRender();

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

        // 9. Titre dynamique de fenêtre lié au fichier chargé
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldW, newW) -> updateWindowTitle());
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
        String activeStyle = "-fx-background-color: " + AppTheme.PRIMARY_TEAL + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 9 3 9; -fx-background-radius: 4px; -fx-cursor: hand;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: " + AppTheme.TEXT_MUTED + "; -fx-font-weight: normal; -fx-font-size: 11px; -fx-padding: 3 9 3 9; -fx-background-radius: 4px; -fx-cursor: hand;";

        tabVariablesBtn.setStyle(currentBottomView == BottomViewMode.VARIABLES ? activeStyle : inactiveStyle);
        tabTimingBtn.setStyle(currentBottomView == BottomViewMode.TIMING ? activeStyle : inactiveStyle);
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

        grafcet.setStatusMessageConsumer(this::setStatus);
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
                                () -> {
                                    grafcet.redraw();
                                    refreshDiagnostics();
                                }, () -> {
                                    if (diagnosticsPanel != null) {
                                        this.setRight(diagnosticsPanel);
                                    } else {
                                        this.setRight(null);
                                    }
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
                                    if (diagnosticsPanel != null) {
                                        this.setRight(diagnosticsPanel);
                                    } else {
                                        this.setRight(null);
                                    }
                                });
                        this.setRight(sideProperty);
                        setStatus("Transition T" + transition.getNumber() + " sélectionnée");
                    }
                }
            } else {
                if (diagnosticsPanel != null) {
                    this.setRight(diagnosticsPanel);
                } else {
                    this.setRight(null);
                }
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
                    case UP -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() - step));
                    case DOWN -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() + step));
                    case LEFT -> component.updatePositionAndLinks(new Point2D(component.getX() - step, component.getY()));
                    case RIGHT -> component.updatePositionAndLinks(new Point2D(component.getX() + step, component.getY()));
                    default -> { return; }
                }
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

        if (activeSimulationPanel != null) {
            // Ne pas écraser le pupitre de simulation
            return;
        }

        // Rafraîchir le panneau latéral si un élément est actuellement sélectionné
        grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step) {
                var sideProperty = new StepProperty(step, variableTable.getOutputVariables(),
                        variableTable.getCounterVariables(),
                        () -> {
                            grafcet.redraw();
                            refreshDiagnostics();
                        }, () -> {
                            if (diagnosticsPanel != null) {
                                this.setRight(diagnosticsPanel);
                            } else {
                                this.setRight(null);
                            }
                        });
                this.setRight(sideProperty);
            } else if (component instanceof Transition transition) {
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
                            if (diagnosticsPanel != null) {
                                this.setRight(diagnosticsPanel);
                            } else {
                                this.setRight(null);
                            }
                        });
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
            tableContainer.setMaxHeight(Double.MAX_VALUE);
            SplitPane.setResizableWithParent(tableContainer, true);
            bottomContentArea.setVisible(true);
            bottomContentArea.setManaged(true);
            splitPane.setDividerPositions(lastDividerPosition > 0.1 && lastDividerPosition < 0.95 ? lastDividerPosition : 0.68);
            toggleTableBtn.setText("▼ Réduire");
            isTableCollapsed = false;
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
    // PERSISTANCE & MENU FICHIER (AXE 1)
    // ==========================================

    public void onNewProject() {
        if (!grafcet.isDiagramEmpty() || !variableTable.getVariables().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Nouveau Projet SFC");
            alert.setHeaderText("Créer un nouveau diagramme ?");
            alert.setContentText("Le diagramme actuel sera réinitialisé. Pensez à enregistrer votre travail de TP.");
            ButtonType btnYes = new ButtonType("Créer nouveau", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnCancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnYes, btnCancel);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != btnYes) {
                return;
            }
        }

        currentFile = null;
        currentProjectName = "Nouveau_Projet";
        grafcet.clearDiagram();
        variableTable.clear();
        updateWindowTitle();
        refreshDiagnostics();
        setStatus("Nouveau projet initialisé");
    }

    public void onOpenProject() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Ouvrir un programme Grafcet / SFC");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Programmes SFC / Grafcet (*.sfc, *.tk)", "*.sfc", "*.tk"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );

        if (currentFile != null && currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
            fileChooser.setInitialDirectory(currentFile.getParentFile());
        }

        File file = fileChooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            try {
                SfcProgram loaded = SfcStorage.load(file);

                // 1. Recharger la table des variables
                List<Variable> vars = loaded.variables().stream()
                        .map(Variable::fromIecVariable)
                        .toList();
                variableTable.setVariables(vars);

                // 2. Reconstruire le diagramme graphique Grafcet
                grafcet.loadFromProgram(loaded, variableTable.getVariables());

                currentFile = file;
                currentProjectName = loaded.programName();
                updateWindowTitle();
                refreshDiagnostics();
                setStatus("Programme chargé avec succès : " + file.getName());
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Erreur de chargement");
                alert.setHeaderText("Impossible d'ouvrir le fichier SFC / Grafcet");
                alert.setContentText("Détail de l'erreur : " + ex.getMessage());
                alert.showAndWait();
                setStatusError("Échec de l'ouverture du fichier : " + ex.getMessage());
            }
        }
    }

    public void onSaveProject() {
        if (currentFile == null) {
            onSaveAsProject();
        } else {
            doSave(currentFile);
        }
    }

    public void onSaveAsProject() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer le programme Grafcet / SFC");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Programme SFC CEI 61131-3 (*.sfc)", "*.sfc"),
                new FileChooser.ExtensionFilter("Fichier Grafcet rétrocompatible (*.tk)", "*.tk"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );

        String defaultName = (currentFile != null)
                ? currentFile.getName()
                : (currentProjectName + ".sfc");
        fileChooser.setInitialFileName(defaultName);

        if (currentFile != null && currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
            fileChooser.setInitialDirectory(currentFile.getParentFile());
        }

        File file = fileChooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            doSave(file);
        }
    }

    private void doSave(File targetFile) {
        try {
            String baseName = targetFile.getName();
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            }
            currentProjectName = baseName;

            SfcProgram program = grafcet.toProgram(currentProjectName, variableTable.getVariables());
            SfcStorage.save(program, targetFile);

            currentFile = targetFile;
            updateWindowTitle();
            setStatus("Programme enregistré : " + targetFile.getName());
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur d'enregistrement");
            alert.setHeaderText("Impossible d'enregistrer le fichier SFC");
            alert.setContentText("Détail de l'erreur : " + ex.getMessage());
            alert.showAndWait();
            setStatusError("Échec de l'enregistrement : " + ex.getMessage());
        }
    }

    public void onExportPng() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter le Grafcet en image PNG haute résolution");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image PNG (*.png)", "*.png")
        );
        String defaultName = currentProjectName + ".png";
        fileChooser.setInitialFileName(defaultName);

        if (currentFile != null && currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
            fileChooser.setInitialDirectory(currentFile.getParentFile());
        }

        File file = fileChooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            try {
                grafcet.exportToPng(file, currentProjectName, 2.0);
                setStatus("Export PNG réussi : " + file.getName());
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Erreur d'exportation");
                alert.setHeaderText("Impossible d'exporter l'image PNG");
                alert.setContentText("Détail de l'erreur : " + ex.getMessage());
                alert.showAndWait();
                setStatusError("Échec de l'export PNG : " + ex.getMessage());
            }
        }
    }

    private void updateWindowTitle() {
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            String display = (currentFile != null)
                    ? currentFile.getName()
                    : (currentProjectName + " (non enregistré)");
            stage.setTitle("Grafino — " + display);
        }
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
            simulator.registerInputVariable(v.getName());
        }
        for (Variable v : variableTable.getOutputVariables()) {
            simulator.getState().setBooleanVariable(v.getName(), false);
            simulator.registerOutputVariable(v.getName());
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
        timingRecorder.resetStartTime();

        // Désélectionner tout élément graphique
        grafcet.getComponents().forEach(c -> c.setSelected(false));
        grafcet.fireSelectionChange(Optional.empty());

        // Créer le pupitre de commande interactif avec liaison au chronogramme
        activeSimulationPanel = new SimulationPanel(
                simulator,
                grafcet,
                variableTable.getInputVariables(),
                variableTable.getOutputVariables(),
                variableTable.getCounterVariables(),
                timingRecorder,
                timingDiagramPanel::redraw,
                this::stopSimulation
        );

        this.setRight(activeSimulationPanel);
        menuBar.setSimulationActive(true);
        setStatus("Simulation synchrone démarrée");
    }

    private void stopSimulation() {
        if (activeSimulationPanel != null) {
            SimulationPanel panel = activeSimulationPanel;
            activeSimulationPanel = null; // Mettre à null en premier pour briser toute récursion
            panel.stop();
        }
        if (diagnosticsPanel != null) {
            this.setRight(diagnosticsPanel);
        } else {
            this.setRight(null);
        }
        menuBar.setSimulationActive(false);
        grafcet.clearSimulationVisuals();
        refreshDiagnostics();
        setStatus("Simulation arrêtée");
    }

    // ==========================================
    // DIAGNOSTIC & LINTING NORMATIF (AXE 2)
    // ==========================================

    private void toggleDiagnosticsPanel() {
        if (activeSimulationPanel != null) {
            setStatus("Veuillez arrêter la simulation pour inspecter les diagnostics");
            return;
        }

        if (diagnosticsPanel != null && this.getRight() == diagnosticsPanel) {
            this.setRight(null);
            diagnosticsPanel = null;
        } else {
            diagnosticsPanel = new DiagnosticsPanel(this::onSelectDiagnosticElement, () -> {
                this.setRight(null);
                diagnosticsPanel = null;
            });
            refreshDiagnostics();
            this.setRight(diagnosticsPanel);
        }
    }

    public void refreshDiagnostics() {
        try {
            SfcProgram program = grafcet.toProgram(currentProjectName, variableTable.getVariables());
            List<SfcValidationMessage> messages = program.validate();
            long errorCount = messages.stream().filter(SfcValidationMessage::isError).count();
            long warningCount = messages.stream().filter(SfcValidationMessage::isWarning).count();
            menuBar.setDiagnosticsStatus(errorCount, warningCount);
            if (diagnosticsPanel != null) {
                diagnosticsPanel.setMessages(messages);
            }
        } catch (Exception ignored) {
            // Diagramme en cours d'édition
        }
    }

    private void onSelectDiagnosticElement(String elementId) {
        if (elementId != null && !elementId.isBlank()) {
            grafcet.selectComponentByReference(elementId);
            setStatus("Élément inspecté : " + elementId);
        }
    }
}
