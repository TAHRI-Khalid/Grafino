package tk.tahri.grafino.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Barre de menus principale de l'application Grafino.
 * Remplace la barre d'outils supérieure par une structure de menus complète (style IDE industriel).
 */
public class AppMenuBar extends HBox {

    // Menus principaux
    private final MenuBar menuBar = new MenuBar();
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
    private final MenuItem autoLayoutItem = new MenuItem("📐 Aligner & Centrer (Auto-Layout)");
    private final MenuItem deleteItem = new MenuItem("🗑 Supprimer l'élément sélectionné");
    private final MenuItem deselectItem = new MenuItem("Désélectionner tout");

    // Éléments du menu Affichage
    private final MenuItem toggleTableItem = new MenuItem("📋 Table des Variables d'Automatismes");
    private final MenuItem toggleDiagnosticsItem = new MenuItem("🔍 Inspection & Linting normatif");
    private final MenuItem zoomInItem = new MenuItem("＋ Zoom Avant");
    private final MenuItem zoomOutItem = new MenuItem("－ Zoom Arrière");
    private final MenuItem resetZoomItem = new MenuItem("🔍 Zoom 100% (Normal)");

    // Éléments du menu Simulation
    private final MenuItem toggleSimItem = new MenuItem("▶ Démarrer la simulation");

    // Éléments du menu Aide
    private final MenuItem shortcutsItem = new MenuItem("⌨ Raccourcis clavier & Ergonomie");
    private final MenuItem aboutItem = new MenuItem("ℹ À propos de Grafino...");

    // Composants visuels rapides (à droite de la barre de menus)
    private final Button diagnosticsBtn = new Button("✓ Conforme");
    private final Button simulationBtn = new Button("▶ Simuler");
    private final Button zoomOutBtn = new Button("－");
    private final Button resetZoomBtn = new Button("100%");
    private final Button zoomInBtn = new Button("＋");

    public AppMenuBar() {
        this.getStyleClass().add("top-menu-bar");
        this.setAlignment(Pos.CENTER_LEFT);
        this.setStyle("-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";");
        this.setPadding(new Insets(3, 10, 3, 6));

        setupMenus();

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        setupQuickControls();

        var sep = new Separator(Orientation.VERTICAL);
        sep.setStyle("-fx-opacity: 0.35;");

        this.getChildren().addAll(
                menuBar,
                spacer,
                diagnosticsBtn,
                simulationBtn,
                sep,
                zoomOutBtn, resetZoomBtn, zoomInBtn
        );
    }

    private void setupMenus() {
        menuBar.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

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
        autoLayoutItem.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN));
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        deselectItem.setAccelerator(new KeyCodeCombination(KeyCode.ESCAPE));

        editMenu.getItems().addAll(
                autoLayoutItem,
                deleteItem,
                new SeparatorMenuItem(),
                deselectItem
        );

        // 3. Menu Affichage
        toggleTableItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN));
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.SHORTCUT_DOWN));
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN));
        resetZoomItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));

        viewMenu.getItems().addAll(
                toggleTableItem,
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

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, simMenu, helpMenu);
    }

    private void setupQuickControls() {
        // Badge de diagnostic normatif
        diagnosticsBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.16); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9; -fx-border-color: rgba(16, 185, 129, 0.6); -fx-border-radius: 4px;");
        diagnosticsBtn.setTooltip(new Tooltip("Inspection normative en direct CEI 60848 / CEI 61131-3 (cliquez pour inspecter)"));

        // Bouton Démarrer / Arrêter la simulation
        simulationBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 5 12 5 12;");
        simulationBtn.setTooltip(new Tooltip("Démarrer la simulation synchrone et ouvrir le pupitre E/S (F5)"));

        // Boutons de Zoom
        String zoomBtnStyle = "-fx-background-color: rgba(255, 255, 255, 0.15); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9;";
        zoomInBtn.setStyle(zoomBtnStyle);
        zoomInBtn.setTooltip(new Tooltip("Zoom Avant (Ctrl + Molette Haut)"));

        zoomOutBtn.setStyle(zoomBtnStyle);
        zoomOutBtn.setTooltip(new Tooltip("Zoom Arrière (Ctrl + Molette Bas)"));

        resetZoomBtn.setStyle(zoomBtnStyle);
        resetZoomBtn.setTooltip(new Tooltip("Réinitialiser le zoom à 100% (Ctrl + 0)"));
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

    public void setOnSimulationBtnClick(EventHandler<ActionEvent> event) {
        this.simulationBtn.setOnAction(event);
        this.toggleSimItem.setOnAction(event);
    }

    public void setSimulationActive(boolean active) {
        if (active) {
            simulationBtn.setText("⏹ Arrêter");
            simulationBtn.setStyle("-fx-background-color: #D9381E; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 5 12 5 12;");
            simulationBtn.setTooltip(new Tooltip("Arrêter la simulation et revenir en mode édition (F5)"));
            toggleSimItem.setText("⏹ Arrêter la simulation");
        } else {
            simulationBtn.setText("▶ Simuler");
            simulationBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 5 12 5 12;");
            simulationBtn.setTooltip(new Tooltip("Démarrer la simulation synchrone et ouvrir le pupitre E/S (F5)"));
            toggleSimItem.setText("▶ Démarrer la simulation");
        }
    }

    public void setOnZoomInClick(EventHandler<ActionEvent> event) {
        this.zoomInBtn.setOnAction(event);
        this.zoomInItem.setOnAction(event);
    }

    public void setOnZoomOutClick(EventHandler<ActionEvent> event) {
        this.zoomOutBtn.setOnAction(event);
        this.zoomOutItem.setOnAction(event);
    }

    public void setOnResetZoomClick(EventHandler<ActionEvent> event) {
        this.resetZoomBtn.setOnAction(event);
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

    public void setDiagnosticsStatus(long errorCount, long warningCount) {
        if (errorCount > 0) {
            diagnosticsBtn.setText("✕ " + errorCount + " Erreur(s)");
            diagnosticsBtn.setStyle("-fx-background-color: rgba(220, 38, 38, 0.45); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9; -fx-border-color: #EF4444; -fx-border-radius: 4px;");
            diagnosticsBtn.setTooltip(new Tooltip("Des non-conformités normatives bloquantes ont été détectées (cliquez pour inspecter)"));
        } else if (warningCount > 0) {
            diagnosticsBtn.setText("⚠ " + warningCount + " Alerte(s)");
            diagnosticsBtn.setStyle("-fx-background-color: rgba(245, 158, 11, 0.35); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9; -fx-border-color: #F59E0B; -fx-border-radius: 4px;");
            diagnosticsBtn.setTooltip(new Tooltip("Des avertissements pédagogiques ont été détectés (cliquez pour inspecter)"));
        } else {
            diagnosticsBtn.setText("✓ Conforme");
            diagnosticsBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.16); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9; -fx-border-color: rgba(16, 185, 129, 0.6); -fx-border-radius: 4px;");
            diagnosticsBtn.setTooltip(new Tooltip("Schéma 100% conforme aux règles CEI 60848 & CEI 61131-3 (cliquez pour inspecter)"));
        }
    }

    public void setOnDiagnosticsClick(EventHandler<ActionEvent> event) {
        this.diagnosticsBtn.setOnAction(event);
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
                "Version : 1.0-SNAPSHOT\n" +
                "Plateforme : Java 27 / JavaFX 27"
        );
        alert.showAndWait();
    }
}
