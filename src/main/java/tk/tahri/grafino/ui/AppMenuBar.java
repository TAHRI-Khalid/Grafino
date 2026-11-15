package tk.tahri.grafino.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Barre de menus principale de l'application Grafino.
 * Offre une interface épurée 100% menus, sans boutons redondants.
 */
public class AppMenuBar extends MenuBar {

    // Menus principaux
    private final Menu fileMenu = new Menu("_Fichier");
    private final Menu editMenu = new Menu("_Édition");
    private final Menu viewMenu = new Menu("_Affichage");
    private final Menu simMenu = new Menu("_Simulation");
    private final Menu helpMenu = new Menu("_Aide");

    // Éléments du menu Fichier
    private final MenuItem newProjectItem = new MenuItem("📄 Nouveau projet");
    private final MenuItem openProjectItem = new MenuItem("📂 Ouvrir un projet...");
    private final MenuItem saveProjectItem = new MenuItem("💾 Enregistrer");
    private final MenuItem saveAsProjectItem = new MenuItem("💾 Enregistrer sous...");
    private final MenuItem exportPngItem = new MenuItem("📷 Exporter en image PNG...");
    private final MenuItem exitItem = new MenuItem("✕ Quitter");

    // Éléments du menu Édition
    private final MenuItem undoItem = new MenuItem("↩ Annuler");
    private final MenuItem redoItem = new MenuItem("↪ Rétablir");
    private final MenuItem autoLayoutItem = new MenuItem("📐 Aligner & Centrer (Auto-Layout)");
    private final MenuItem deleteItem = new MenuItem("🗑 Supprimer la sélection");
    private final MenuItem deselectItem = new MenuItem("Désélectionner tout");

    // Éléments du menu Affichage
    private final MenuItem toggleTableItem = new MenuItem("📋 Table des Variables d'Automatismes");
    private final MenuItem toggleTimingItem = new MenuItem("📈 Chronogramme d'Évolution Multi-voies");
    private final MenuItem toggleDiagnosticsItem = new MenuItem("🔍 Inspection & Linting normatif");
    private final MenuItem zoomInItem = new MenuItem("＋ Zoom Avant");
    private final MenuItem zoomOutItem = new MenuItem("－ Zoom Arrière");
    private final MenuItem resetZoomItem = new MenuItem("🔍 Zoom 100% (Normal)");

    // Éléments du menu Simulation
    private final MenuItem toggleSimItem = new MenuItem("▶ Démarrer la simulation");

    // Éléments du menu Aide
    private final MenuItem shortcutsItem = new MenuItem("⌨ Raccourcis clavier & Ergonomie");
    private final MenuItem aboutItem = new MenuItem("ℹ À propos de Grafino...");

    public AppMenuBar() {
        this.getStyleClass().add("top-menu-bar");
        setupMenus();
    }

    private void setupMenus() {
        // 1. Menu Fichier
        newProjectItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        openProjectItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        saveProjectItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveAsProjectItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        exportPngItem.setAccelerator(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN));
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(
                newProjectItem,
                openProjectItem,
                saveProjectItem,
                saveAsProjectItem,
                new SeparatorMenuItem(),
                exportPngItem,
                new SeparatorMenuItem(),
                exitItem
        );

        // 2. Menu Édition
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
        undoItem.setDisable(true);
        redoItem.setDisable(true);
        autoLayoutItem.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN));
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        deselectItem.setAccelerator(new KeyCodeCombination(KeyCode.ESCAPE));

        editMenu.getItems().addAll(
                undoItem,
                redoItem,
                new SeparatorMenuItem(),
                autoLayoutItem,
                deleteItem,
                new SeparatorMenuItem(),
                deselectItem
        );

        // 3. Menu Affichage
        toggleTableItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN));
        toggleTimingItem.setAccelerator(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN));
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.SHORTCUT_DOWN));
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN));
        resetZoomItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));

        viewMenu.getItems().addAll(
                toggleTableItem,
                toggleTimingItem,
                toggleDiagnosticsItem,
                new SeparatorMenuItem(),
                zoomInItem,
                zoomOutItem,
                resetZoomItem
        );

        // 4. Menu Simulation
        toggleSimItem.setAccelerator(new KeyCodeCombination(KeyCode.F5));
        simMenu.getItems().addAll(
                toggleSimItem
        );

        // 5. Menu Aide
        shortcutsItem.setOnAction(e -> showShortcutsDialog());
        aboutItem.setOnAction(e -> showAboutDialog());

        helpMenu.getItems().addAll(
                shortcutsItem,
                new SeparatorMenuItem(),
                aboutItem
        );

        this.getMenus().addAll(fileMenu, editMenu, viewMenu, simMenu, helpMenu);
    }

    public void setOnUndoClick(EventHandler<ActionEvent> event) {
        this.undoItem.setOnAction(event);
    }

    public void setOnRedoClick(EventHandler<ActionEvent> event) {
        this.redoItem.setOnAction(event);
    }

    public void updateUndoRedoState(boolean canUndo, boolean canRedo) {
        this.undoItem.setDisable(!canUndo);
        this.redoItem.setDisable(!canRedo);
    }

    public void setOnAutoLayoutClick(EventHandler<ActionEvent> event) {
        this.autoLayoutItem.setOnAction(event);
    }

    public void setOnDeleteClick(EventHandler<ActionEvent> event) {
        this.deleteItem.setOnAction(event);
    }

    public void setOnDeselectClick(EventHandler<ActionEvent> event) {
        this.deselectItem.setOnAction(event);
    }

    public void setOnToggleVariableTable(EventHandler<ActionEvent> event) {
        this.toggleTableItem.setOnAction(event);
    }

    public void setOnToggleTimingDiagram(EventHandler<ActionEvent> event) {
        this.toggleTimingItem.setOnAction(event);
    }

    public void setOnSimulationBtnClick(EventHandler<ActionEvent> event) {
        this.toggleSimItem.setOnAction(event);
    }

    public void setSimulationActive(boolean active) {
        if (active) {
            toggleSimItem.setText("⏹ Arrêter la simulation");
        } else {
            toggleSimItem.setText("▶ Démarrer la simulation");
        }
    }

    public void setOnZoomInClick(EventHandler<ActionEvent> event) {
        this.zoomInItem.setOnAction(event);
    }

    public void setOnZoomOutClick(EventHandler<ActionEvent> event) {
        this.zoomOutItem.setOnAction(event);
    }

    public void setOnResetZoomClick(EventHandler<ActionEvent> event) {
        this.resetZoomItem.setOnAction(event);
    }

    public void setOnNewProject(EventHandler<ActionEvent> event) {
        this.newProjectItem.setOnAction(event);
    }

    public void setOnOpenProject(EventHandler<ActionEvent> event) {
        this.openProjectItem.setOnAction(event);
    }

    public void setOnSaveProject(EventHandler<ActionEvent> event) {
        this.saveProjectItem.setOnAction(event);
    }

    public void setOnSaveAsProject(EventHandler<ActionEvent> event) {
        this.saveAsProjectItem.setOnAction(event);
    }

    public void setOnExportPng(EventHandler<ActionEvent> event) {
        this.exportPngItem.setOnAction(event);
    }

    public void setOnExit(EventHandler<ActionEvent> event) {
        this.exitItem.setOnAction(event);
    }

    public MenuItem getExitItem() {
        return exitItem;
    }

    public void setDiagnosticsStatus(long errorCount, long warningCount) {
        if (errorCount > 0) {
            toggleDiagnosticsItem.setText("🔍 Inspection normatif (✕ " + errorCount + " Erreur(s))");
        } else if (warningCount > 0) {
            toggleDiagnosticsItem.setText("🔍 Inspection normatif (⚠ " + warningCount + " Alerte(s))");
        } else {
            toggleDiagnosticsItem.setText("🔍 Inspection normatif (✓ Conforme)");
        }
    }

    public void setOnDiagnosticsClick(EventHandler<ActionEvent> event) {
        this.toggleDiagnosticsItem.setOnAction(event);
    }

    public void setGrafcetToolsVisible(boolean value) {
        // Maintenu pour rétrocompatibilité
    }

    public void setOnAddVariableBtnClick(EventHandler<ActionEvent> event) {
        // Maintenu pour rétrocompatibilité
    }

    public void setOnRemoveSelectedVariable(EventHandler<ActionEvent> event) {
        // Maintenu pour rétrocompatibilité
    }

    private void showShortcutsDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Raccourcis Clavier Grafino");
        alert.setHeaderText("Raccourcis Clavier & Ergonomie Industrielle");
        alert.setContentText(
                "Édition interactive sur le Canvas :\n" +
                " • T / Entrée : Insérer la transition suivante\n" +
                " • S / Entrée : Insérer l'étape suivante\n" +
                " • B : Ouvrir une branche (OU / ET)\n" +
                " • Suppr / Retour arrière : Supprimer l'élément sélectionné\n" +
                " • Double-clic : Éditer réceptivité ou numéro d'étape in-place\n" +
                " • Ctrl + L : Aligner & Centrer tout le diagramme (Auto-Layout)\n\n" +
                "Navigation & Affichage :\n" +
                " • Ctrl + Molette : Zoom fluide centré sur le curseur\n" +
                " • Clic droit / Clic milieu + Glisser : Déplacement panoramique (Pan)\n" +
                " • Ctrl + T : Déplier / Replier la table des variables\n\n" +
                "Gestion de Projet :\n" +
                " • Ctrl + N : Nouveau projet\n" +
                " • Ctrl + O : Ouvrir un fichier (.sfc, .tk)\n" +
                " • Ctrl + S : Enregistrer le projet actif\n" +
                " • Ctrl + Shift + S : Enregistrer sous un nouveau nom\n" +
                " • Ctrl + E : Exporter en image PNG haute résolution (pour rapports)"
        );
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("À propos de Grafino");
        alert.setHeaderText("Grafino — Environnement d'Automatismes Industriels");
        alert.setContentText(
                "Grafino est un logiciel pédagogique open-source dédié à l'enseignement et à " +
                "l'ingénierie des automatismes industriels selon les normes internationales :\n\n" +
                " • CEI 60848 (Spécification fonctionnelle Grafcet)\n" +
                " • CEI 61131-3 (Programmation SFC, types de données & variables API)\n\n" +
                "Version : 1.1.0\n" +
                "Plateforme : Java 27 / JavaFX 27"
        );
        alert.showAndWait();
    }
}
