package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.GrafcetModel;
import tk.tahri.grafino.model.UndoRedoManager;
import tk.tahri.grafino.model.VariableModel;
import tk.tahri.grafino.storage.GrafcetStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainUI extends BorderPane {
    private final Grafcet grafcet = new Grafcet();
    private final VariableTable variableTable = new VariableTable();
    private final UndoRedoManager undoManager = new UndoRedoManager();

    private final Label statusLabel = new Label("Prêt - Nouveau projet Grafcet");
    private final Label zoomLabel = new Label("Zoom : 100%");
    private final Label snapLabel = new Label("🧲 Grille : Active");

    private final MenuItem undoItem = new MenuItem("Annuler");
    private final MenuItem redoItem = new MenuItem("Rétablir");
    private TopToolBar toolBar;
    private File currentFile = null;

    public MainUI() {
        // Barre de menus supérieure
        var menuBar = createMenuBar();
        toolBar = new TopToolBar();

        var topContainer = new VBox(menuBar, toolBar);
        this.setTop(topContainer);

        grafcet.createInitialStep();
        this.setCenter(grafcet);

        // Barre d'état inférieure (Status Bar moderne)
        var statusBar = createStatusBar();
        this.setBottom(statusBar);

        // Enregistrer l'état initial pour Undo
        recordSnapshot();

        // Écouter les modifications du Canvas pour l'historique Undo
        grafcet.setOnStateModified(this::recordSnapshot);

        // Mise à jour de l'affichage du zoom
        grafcet.setOnZoomChanged(zoom -> {
            int pct = (int) Math.round(zoom * 100);
            zoomLabel.setText("Zoom : " + pct + "%");
        });

        setupToolBarHandlers();
        setupSelectionHandlers();
        setupKeyHandlers();
    }

    private ToolBar createStatusBar() {
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        snapLabel.setStyle("-fx-text-fill: #E2E8F0; -fx-font-size: 12px;");
        zoomLabel.setStyle("-fx-text-fill: #FFCC00; -fx-font-size: 12px; -fx-font-weight: bold;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var statusBar = new ToolBar(statusLabel, spacer, snapLabel, new Separator(), zoomLabel);
        statusBar.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        statusBar.setPadding(new Insets(6, 14, 6, 14));
        return statusBar;
    }

    private void setupToolBarHandlers() {
        toolBar.setOnStepBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && transition.getLinksToChildren().isEmpty()) {
                grafcet.createStep(transition);
                updateStatus("Étape créée après la transition T" + transition.getNumber());
            }
        }));

        toolBar.setOnTransitionBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && step.getLinksToChildren().isEmpty()) {
                grafcet.createTransition(step);
                updateStatus("Transition créée après l'étape " + step.getNumber());
            }
        }));

        toolBar.setOnOrBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && !step.getLinksToChildren().isEmpty()) {
                grafcet.createTransition(step);
                updateStatus("Branche OU créée (Sélection de séquence)");
            }
        }));

        toolBar.setOnAndBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && !transition.getLinksToChildren().isEmpty()) {
                grafcet.createStep(transition);
                updateStatus("Branche ET créée (Synchronisation / Parallélisme)");
            }
        }));

        toolBar.setOnDeleteBtnClick(event -> {
            grafcet.deleteSelected();
            updateStatus("Élément supprimé");
        });

        // Undo / Redo
        toolBar.setOnUndoClick(event -> handleUndo());
        toolBar.setOnRedoClick(event -> handleRedo());

        // Zoom & Pan
        toolBar.setOnZoomInClick(event -> grafcet.zoomIn());
        toolBar.setOnZoomOutClick(event -> grafcet.zoomOut());
        toolBar.setOnZoomFitClick(event -> grafcet.zoomFit());

        // Snap-to-Grid
        toolBar.setOnToggleSnapClick(event -> {
            boolean active = !grafcet.isSnapToGrid();
            grafcet.setSnapToGrid(active);
            snapLabel.setText("🧲 Grille : " + (active ? "Active" : "Libre"));
            updateStatus("Accrochage magnétique grille : " + (active ? "Activé" : "Désactivé"));
        });

        // Variables
        toolBar.setOnAddVariableBtnClick(event -> {
            variableTable.addVariable();
            recordSnapshot();
            updateStatus("Variable ajoutée dans la table");
        });

        toolBar.setOnRemoveSelectedVariable(event -> {
            variableTable.removeSelectedVariable();
            recordSnapshot();
            updateStatus("Variable supprimée");
        });

        toolBar.setOnGrafcetBtnClick(event -> {
            this.setCenter(grafcet);
            toolBar.setGrafcetToolsVisible(true);
            grafcet.redraw();
            updateStatus("Vue Éditeur Grafcet active");
        });

        toolBar.setOnVariableTableBtnClick(event -> {
            this.setCenter(variableTable);
            toolBar.setGrafcetToolsVisible(false);
            updateStatus("Vue Table des Variables active");
        });
    }

    private void setupSelectionHandlers() {
        grafcet.setOnSelectionChange(selectedComponent -> {
            if (selectedComponent.isPresent()) {
                switch (selectedComponent.get()) {
                    case Step step -> {
                        var sideProperty = new StepProperty(step, variableTable.getOutputVariables(), () -> {
                            grafcet.redraw();
                            recordSnapshot();
                        });
                        this.setRight(sideProperty);
                        updateStatus("Étape " + step.getNumber() + " sélectionnée");
                    }
                    case Transition transition -> {
                        var sideProperty = new TransitionProperty(transition, variableTable.getInputVariables(), () -> {
                            grafcet.redraw();
                            recordSnapshot();
                        });
                        this.setRight(sideProperty);
                        updateStatus("Transition T" + transition.getNumber() + " sélectionnée");
                    }
                }
            } else {
                this.setRight(null);
                updateStatus("Aucun élément sélectionné");
            }
        });
    }

    private void setupKeyHandlers() {
        this.setOnKeyPressed(event -> {
            // Espace pour panoramique
            if (event.getCode() == KeyCode.SPACE) {
                grafcet.setSpacePressed(true);
            }

            // Suppr / Backspace
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                grafcet.deleteSelected();
                updateStatus("Élément supprimé");
                event.consume();
                return;
            }

            // Déplacement fin au clavier
            grafcet.getSelectedComponent().ifPresent(component -> {
                int delta = event.isControlDown() ? 20 : (int) Grafcet.GRID_SIZE;
                switch (event.getCode()) {
                    case UP -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() - delta));
                    case DOWN -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() + delta));
                    case LEFT -> component.updatePositionAndLinks(new Point2D(component.getX() - delta, component.getY()));
                    case RIGHT -> component.updatePositionAndLinks(new Point2D(component.getX() + delta, component.getY()));
                    default -> { return; }
                }
                grafcet.redraw();
                recordSnapshot();
                event.consume();
            });
        });

        this.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                grafcet.setSpacePressed(false);
            }
        });
    }

    private MenuBar createMenuBar() {
        var menuBar = new MenuBar();

        // 1. Menu Fichier
        var fileMenu = new Menu("Fichier");

        var newProjectItem = new MenuItem("Nouveau projet");
        newProjectItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newProjectItem.setOnAction(e -> handleNewProject());

        var openItem = new MenuItem("Ouvrir (.tk)...");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openItem.setOnAction(e -> handleOpenFile());

        var saveItem = new MenuItem("Enregistrer (.tk)");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        saveItem.setOnAction(e -> handleSaveFile(false));

        var saveAsItem = new MenuItem("Enregistrer sous...");
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        saveAsItem.setOnAction(e -> handleSaveFile(true));

        var exitItem = new MenuItem("Quitter");
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        exitItem.setOnAction(e -> {
            Stage stage = (Stage) this.getScene().getWindow();
            if (stage != null) stage.close();
        });

        fileMenu.getItems().addAll(newProjectItem, new SeparatorMenuItem(), openItem, saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);

        // 2. Menu Édition (Undo, Redo, Delete, Initial step)
        var editMenu = new Menu("Édition");

        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoItem.setOnAction(e -> handleUndo());
        undoItem.setDisable(true);

        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redoItem.setOnAction(e -> handleRedo());
        redoItem.setDisable(true);

        var deleteItem = new MenuItem("Supprimer l'élément sélectionné");
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        deleteItem.setOnAction(e -> grafcet.deleteSelected());

        var toggleInitialItem = new MenuItem("Basculer Étape Initiale");
        toggleInitialItem.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN));
        toggleInitialItem.setOnAction(e -> grafcet.toggleSelectedStepInitial());

        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), deleteItem, toggleInitialItem);

        // 3. Menu Affichage (Zoom & Grille)
        var viewMenu = new Menu("Affichage");

        var zoomInItem = new MenuItem("Zoom avant");
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN));
        zoomInItem.setOnAction(e -> grafcet.zoomIn());

        var zoomOutItem = new MenuItem("Zoom arrière");
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOutItem.setOnAction(e -> grafcet.zoomOut());

        var zoomFitItem = new MenuItem("Ajuster à la vue");
        zoomFitItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));
        zoomFitItem.setOnAction(e -> grafcet.zoomFit());

        var resetZoomItem = new MenuItem("Taille réelle (100%)");
        resetZoomItem.setOnAction(e -> grafcet.resetZoom());

        var toggleSnapItem = new CheckMenuItem("Accrochage magnétique à la grille");
        toggleSnapItem.setSelected(true);
        toggleSnapItem.setOnAction(e -> {
            grafcet.setSnapToGrid(toggleSnapItem.isSelected());
            snapLabel.setText("🧲 Grille : " + (toggleSnapItem.isSelected() ? "Active" : "Libre"));
        });

        viewMenu.getItems().addAll(zoomInItem, zoomOutItem, zoomFitItem, resetZoomItem, new SeparatorMenuItem(), toggleSnapItem);

        // 4. Menu Aide
        var helpMenu = new Menu("Aide");
        var aboutItem = new MenuItem("À propos de Grafino");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, helpMenu);
        return menuBar;
    }

    // ---------------- Undo / Redo Management ----------------

    private void recordSnapshot() {
        undoManager.pushSnapshot(grafcet.toModel(getProjectName(), variableTable.getAllVariables()));
        updateUndoRedoUI();
    }

    private void handleUndo() {
        if (undoManager.canUndo()) {
            var currentModel = grafcet.toModel(getProjectName(), variableTable.getAllVariables());
            var previousModel = undoManager.undo(currentModel);
            if (previousModel != null) {
                restoreModel(previousModel);
                updateStatus("Action annulée (Ctrl + Z)");
            }
        }
        updateUndoRedoUI();
    }

    private void handleRedo() {
        if (undoManager.canRedo()) {
            var nextModel = undoManager.redo();
            if (nextModel != null) {
                restoreModel(nextModel);
                updateStatus("Action rétablie (Ctrl + Y)");
            }
        }
        updateUndoRedoUI();
    }

    private void updateUndoRedoUI() {
        boolean canUndo = undoManager.canUndo();
        boolean canRedo = undoManager.canRedo();
        undoItem.setDisable(!canUndo);
        redoItem.setDisable(!canRedo);
        toolBar.setUndoEnabled(canUndo);
        toolBar.setRedoEnabled(canRedo);
    }

    private void restoreModel(GrafcetModel model) {
        // Restaurer les variables
        List<Variable> vars = new ArrayList<>();
        for (VariableModel vm : model.getVariables()) {
            Variable.Type vt = (vm.getType() == tk.tahri.grafino.model.VariableType.INT) ? Variable.Type.INT : Variable.Type.BOOL;
            Variable.Direction vd;
            try {
                vd = Variable.Direction.valueOf(vm.getDirection().name());
            } catch (Exception ex) {
                vd = Variable.Direction.INPUT;
            }
            vars.add(new Variable(vm.getName(), vt, vd, vm.getAddress(), vm.getComment()));
        }
        variableTable.setVariables(vars);

        // Restaurer le graphe
        grafcet.loadFromModel(model, variableTable.getAllVariables());
    }

    private String getProjectName() {
        return currentFile != null ? currentFile.getName().replace(GrafcetStorage.FILE_EXTENSION, "") : "Nouveau projet";
    }

    // ---------------- File Actions ----------------

    private void handleNewProject() {
        currentFile = null;
        variableTable.clear();
        undoManager.clear();
        grafcet.newProject();
        recordSnapshot();
        updateTitle("Nouveau projet");
        updateStatus("Nouveau projet Grafcet vierge créé");
    }

    private void handleOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Ouvrir un fichier Grafcet (.tk)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(GrafcetStorage.FILE_DESCRIPTION, "*" + GrafcetStorage.FILE_EXTENSION)
        );

        Stage stage = (Stage) this.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            try {
                GrafcetModel model = GrafcetStorage.load(file);
                undoManager.clear();
                restoreModel(model);
                recordSnapshot();
                currentFile = file;
                updateTitle(file.getName());
                updateStatus("Projet chargé avec succès : " + file.getName());
            } catch (IOException ex) {
                showError("Erreur d'ouverture", "Impossible de lire le fichier .tk : " + ex.getMessage());
            }
        }
    }

    private void handleSaveFile(boolean saveAs) {
        if (currentFile == null || saveAs) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Enregistrer le projet Grafcet (.tk)");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(GrafcetStorage.FILE_DESCRIPTION, "*" + GrafcetStorage.FILE_EXTENSION)
            );
            if (currentFile != null) {
                chooser.setInitialFileName(currentFile.getName());
                chooser.setInitialDirectory(currentFile.getParentFile());
            } else {
                chooser.setInitialFileName("mon_grafcet.tk");
            }

            Stage stage = (Stage) this.getScene().getWindow();
            File file = chooser.showSaveDialog(stage);
            if (file == null) {
                return;
            }
            if (!file.getName().toLowerCase().endsWith(GrafcetStorage.FILE_EXTENSION)) {
                file = new File(file.getAbsolutePath() + GrafcetStorage.FILE_EXTENSION);
            }
            currentFile = file;
        }

        try {
            GrafcetModel model = grafcet.toModel(getProjectName(), variableTable.getAllVariables());
            GrafcetStorage.save(model, currentFile);
            updateTitle(currentFile.getName());
            updateStatus("Projet enregistré dans : " + currentFile.getName());
        } catch (IOException ex) {
            showError("Erreur de sauvegarde", "Impossible d'enregistrer le fichier .tk : " + ex.getMessage());
        }
    }

    private void updateTitle(String fileName) {
        Stage stage = (Stage) this.getScene().getWindow();
        if (stage != null) {
            stage.setTitle("Grafino - " + fileName);
        }
    }

    private void updateStatus(String msg) {
        statusLabel.setText(msg);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("À propos de Grafino");
        alert.setHeaderText("Grafino v1.0 - Environnement Grafcet CEI 60848");
        alert.setContentText("""
                Grafino permet d'éditer, concevoir et enregistrer des grafcets conformes à la norme CEI 60848.
                
                Navigation & Confort :
                • Molette souris : Zoom centré fluide (25% à 300%)
                • Clic droit / Clic molette / Espace + Glisser : Panoramique infini
                • Magnétisme Grille : Accrochage automatique pour des liaisons droites
                • Ctrl + Z / Ctrl + Y : Annuler / Rétablir illimité
                • Ctrl + 0 : Ajuster tout le Grafcet à la vue
                • Suppr : Supprimer l'élément sélectionné
                • Ctrl + I : Basculer étape normale / initiale
                
                Format de persistance : .tk (JSON structuré)
                """);
        alert.showAndWait();
    }
}
