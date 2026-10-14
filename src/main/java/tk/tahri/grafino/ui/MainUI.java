package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.GrafcetModel;
import tk.tahri.grafino.model.VariableModel;
import tk.tahri.grafino.storage.GrafcetStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainUI extends BorderPane {
    private final Grafcet grafcet = new Grafcet();
    private final VariableTable variableTable = new VariableTable();
    private final Label statusLabel = new Label("Prêt - Nouveau projet Grafcet");
    private File currentFile = null;

    public MainUI() {
        // Barre de menus supérieure
        var menuBar = createMenuBar();
        var toolBar = new TopToolBar();

        var topContainer = new VBox(menuBar, toolBar);
        this.setTop(topContainer);

        grafcet.createInitialStep();
        this.setCenter(grafcet);

        // Barre d'état inférieure (Status Bar)
        var statusBar = new ToolBar(statusLabel);
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        statusBar.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        this.setBottom(statusBar);

        // Boutons de la barre d'outils
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

        toolBar.setOnAddVariableBtnClick(event -> {
            variableTable.addVariable();
            updateStatus("Variable ajoutée dans la table");
        });

        toolBar.setOnRemoveSelectedVariable(event -> {
            variableTable.removeSelectedVariable();
            updateStatus("Variable supprimée");
        });

        toolBar.setOnDeleteBtnClick(event -> {
            grafcet.deleteSelected();
            updateStatus("Élément supprimé");
        });

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

        // Mise à jour des panneaux de propriétés lors de la sélection
        grafcet.setOnSelectionChange(selectedComponent -> {
            if (selectedComponent.isPresent()) {
                switch (selectedComponent.get()) {
                    case Step step -> {
                        var sideProperty = new StepProperty(step, variableTable.getOutputVariables(), () -> grafcet.redraw());
                        this.setRight(sideProperty);
                        updateStatus("Étape " + step.getNumber() + " sélectionnée");
                    }
                    case Transition transition -> {
                        var sideProperty = new TransitionProperty(transition, variableTable.getInputVariables(), () -> grafcet.redraw());
                        this.setRight(sideProperty);
                        updateStatus("Transition T" + transition.getNumber() + " sélectionnée");
                    }
                }
            } else {
                this.setRight(null);
                updateStatus("Aucun élément sélectionné");
            }
        });

        // Gestion du clavier : Déplacement fin et Touche Suppr
        this.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                grafcet.deleteSelected();
                updateStatus("Élément supprimé");
                event.consume();
                return;
            }

            grafcet.getSelectedComponent().ifPresent(component -> {
                int delta = event.isControlDown() ? 10 : 2;
                switch (event.getCode()) {
                    case UP -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() - delta));
                    case DOWN -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() + delta));
                    case LEFT -> component.updatePositionAndLinks(new Point2D(component.getX() - delta, component.getY()));
                    case RIGHT -> component.updatePositionAndLinks(new Point2D(component.getX() + delta, component.getY()));
                    default -> {}
                }
                grafcet.redraw();
            });
        });
    }

    private MenuBar createMenuBar() {
        var menuBar = new MenuBar();

        // Menu Fichier
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

        // Menu Édition
        var editMenu = new Menu("Édition");

        var deleteItem = new MenuItem("Supprimer l'élément sélectionné");
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        deleteItem.setOnAction(e -> grafcet.deleteSelected());

        var toggleInitialItem = new MenuItem("Basculer Étape Initiale");
        toggleInitialItem.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN));
        toggleInitialItem.setOnAction(e -> grafcet.toggleSelectedStepInitial());

        editMenu.getItems().addAll(deleteItem, toggleInitialItem);

        // Menu Aide
        var helpMenu = new Menu("Aide");
        var aboutItem = new MenuItem("À propos de Grafino");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, helpMenu);
        return menuBar;
    }

    private void handleNewProject() {
        currentFile = null;
        variableTable.clear();
        grafcet.newProject();
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

                // Reconstruire les variables dans la table
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

                // Charger le graphe dans le canvas
                grafcet.loadFromModel(model, variableTable.getAllVariables());

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
            String projectName = currentFile.getName().replace(GrafcetStorage.FILE_EXTENSION, "");
            GrafcetModel model = grafcet.toModel(projectName, variableTable.getAllVariables());
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
        alert.setHeaderText("Grafino v1.0 - Outil Pédagogique Grafcet (CEI 60848)");
        alert.setContentText("""
                Grafino permet d'éditer, concevoir et enregistrer des grafcets respectant la norme CEI 60848.
                
                Raccourcis clavier :
                • Suppr / Backspace : Supprimer l'élément ou lien sélectionné
                • Ctrl + N : Nouveau projet
                • Ctrl + O : Ouvrir un fichier .tk
                • Ctrl + S : Enregistrer le projet .tk
                • Ctrl + I : Basculer étape normale / initiale
                • Flèches : Déplacement précis de l'élément sélectionné
                
                Format de fichier : .tk (JSON structuré)
                """);
        alert.showAndWait();
    }
}
