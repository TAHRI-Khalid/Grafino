package tk.tahri.grafino.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Objects;

public class TopToolBar extends ToolBar {
    // Menu et actions de fichiers (Axe 1 : Sécurisation du workflow)
    private final MenuButton fileMenuBtn = new MenuButton("📁 Fichier");
    private final MenuItem newProjectItem = new MenuItem("📄 Nouveau (Ctrl+N)");
    private final MenuItem openProjectItem = new MenuItem("📂 Ouvrir... (Ctrl+O)");
    private final MenuItem saveProjectItem = new MenuItem("💾 Enregistrer (Ctrl+S)");
    private final MenuItem saveAsProjectItem = new MenuItem("💾 Enregistrer sous... (Ctrl+Shift+S)");
    private final MenuItem exportPngItem = new MenuItem("📷 Exporter en image PNG... (Ctrl+E)");

    private final Button quickSaveBtn = new Button("💾");
    private final Button quickExportBtn = new Button("📷 PNG");

    private final Button stepBtn = new Button();
    private final Button transitionBtn = new Button();
    private final Button orBranchBtn = new Button();
    private final Button andBranchBtn = new Button();
    private final Button deleteBtn = new Button();
    private final Button autoLayoutBtn = new Button("📐 Aligner");
    private final Button diagnosticsBtn = new Button("✓ Conforme");
    private final Button simulationBtn = new Button("▶ Simuler");

    private final Button zoomInBtn = new Button("＋");
    private final Button zoomOutBtn = new Button("－");
    private final Button resetZoomBtn = new Button("100%");

    private final Button addVariableBtn = new Button();
    private final Button removeVariableBtn = new Button();

    public TopToolBar() {
        // Configuration du menu Fichier déroulant
        fileMenuBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.20); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9;");
        fileMenuBtn.getItems().addAll(
                newProjectItem,
                openProjectItem,
                saveProjectItem,
                saveAsProjectItem,
                new SeparatorMenuItem(),
                exportPngItem
        );

        // Bouton Enregistrer rapide
        quickSaveBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.16); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 8 4 8;");
        quickSaveBtn.setTooltip(new Tooltip("Enregistrer le projet (Ctrl + S)"));

        // Bouton Export PNG rapide
        quickExportBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.16); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9;");
        quickExportBtn.setTooltip(new Tooltip("Exporter le diagramme en image PNG haute résolution pour compte-rendu de TP (Ctrl + E)"));

        stepBtn.getStyleClass().add("toolBarButton");
        var stepImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Step.png"))));
        stepImg.setFitWidth(28);
        stepImg.setFitHeight(28);
        stepBtn.setGraphic(stepImg);
        stepBtn.setTooltip(new Tooltip("Créer une Étape (S)"));

        transitionBtn.getStyleClass().add("toolBarButton");
        var transitionImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Transition.png"))));
        transitionImg.setFitWidth(28);
        transitionImg.setFitHeight(28);
        transitionBtn.setGraphic(transitionImg);
        transitionBtn.setTooltip(new Tooltip("Créer une Transition (T)"));

        orBranchBtn.getStyleClass().add("toolBarButton");
        var orImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Or_Branch.png"))));
        orImg.setFitWidth(28);
        orImg.setFitHeight(28);
        orBranchBtn.setGraphic(orImg);
        orBranchBtn.setTooltip(new Tooltip("Divergence en OU (Sélection de séquence - B)"));

        andBranchBtn.getStyleClass().add("toolBarButton");
        var andImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/And_Branch.png"))));
        andImg.setFitWidth(28);
        andImg.setFitHeight(28);
        andBranchBtn.setGraphic(andImg);
        andBranchBtn.setTooltip(new Tooltip("Divergence en ET (Parallélisme / Synchronisation)"));

        // Bouton Supprimer
        deleteBtn.setText("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 3 8 3 8;");
        deleteBtn.setTooltip(new Tooltip("Supprimer l'élément sélectionné (Suppr)"));

        // Bouton Auto-Layout
        autoLayoutBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.18); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9;");
        autoLayoutBtn.setTooltip(new Tooltip("Aligner et centrer automatiquement tout le Grafcet (Ctrl+L)"));

        // Bouton Diagnostic & Linting normatif (Axe 2)
        diagnosticsBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.16); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9; -fx-border-color: rgba(16, 185, 129, 0.6); -fx-border-radius: 4px;");
        diagnosticsBtn.setTooltip(new Tooltip("Inspection normative en direct CEI 60848 / CEI 61131-3 (cliquez pour afficher les diagnostics)"));

        // Bouton Simulation
        simulationBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 5 12 5 12;");
        simulationBtn.setTooltip(new Tooltip("Démarrer la simulation synchrone et ouvrir le pupitre E/S"));

        // Boutons de Zoom
        String zoomBtnStyle = "-fx-background-color: rgba(255, 255, 255, 0.15); -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 9 4 9;";
        zoomInBtn.setStyle(zoomBtnStyle);
        zoomInBtn.setTooltip(new Tooltip("Zoom Avant (Ctrl + Molette Haut)"));

        zoomOutBtn.setStyle(zoomBtnStyle);
        zoomOutBtn.setTooltip(new Tooltip("Zoom Arrière (Ctrl + Molette Bas)"));

        resetZoomBtn.setStyle(zoomBtnStyle);
        resetZoomBtn.setTooltip(new Tooltip("Réinitialiser le zoom à 100%"));

        var sepFile = new Separator(Orientation.VERTICAL);
        sepFile.setStyle("-fx-opacity: 0.35;");
        var sep1 = new Separator(Orientation.VERTICAL);
        sep1.setStyle("-fx-opacity: 0.35;");
        var sep2 = new Separator(Orientation.VERTICAL);
        sep2.setStyle("-fx-opacity: 0.35;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.setStyle("-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";");
        this.setPadding(new Insets(6, 12, 6, 12));

        this.getItems().addAll(
                fileMenuBtn, quickSaveBtn, quickExportBtn,
                sepFile,
                stepBtn, transitionBtn, orBranchBtn, andBranchBtn,
                sep1,
                deleteBtn,
                autoLayoutBtn,
                diagnosticsBtn,
                simulationBtn,
                spacer,
                sep2,
                zoomOutBtn, resetZoomBtn, zoomInBtn
        );
    }

    public void setOnStepBtnClick(EventHandler<ActionEvent> event) {
        this.stepBtn.setOnAction(event);
    }

    public void setOnTransitionBtnClick(EventHandler<ActionEvent> event) {
        this.transitionBtn.setOnAction(event);
    }

    public void setOnOrBranchBtnClick(EventHandler<ActionEvent> event) {
        this.orBranchBtn.setOnAction(event);
    }

    public void setOnAndBranchBtnClick(EventHandler<ActionEvent> event) {
        this.andBranchBtn.setOnAction(event);
    }

    public void setOnDeleteBtnClick(EventHandler<ActionEvent> event) {
        this.deleteBtn.setOnAction(event);
    }

    public void setOnAutoLayoutClick(EventHandler<ActionEvent> event) {
        this.autoLayoutBtn.setOnAction(event);
    }

    public void setOnSimulationBtnClick(EventHandler<ActionEvent> event) {
        this.simulationBtn.setOnAction(event);
    }

    public void setSimulationActive(boolean active) {
        if (active) {
            simulationBtn.setText("⏹ Arrêter");
            simulationBtn.setStyle("-fx-background-color: #D9381E; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 5 12 5 12;");
            simulationBtn.setTooltip(new Tooltip("Arrêter la simulation et revenir en mode édition"));
        } else {
            simulationBtn.setText("▶ Simuler");
            simulationBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 5 12 5 12;");
            simulationBtn.setTooltip(new Tooltip("Démarrer la simulation synchrone et ouvrir le pupitre E/S"));
        }
    }

    public void setOnZoomInClick(EventHandler<ActionEvent> event) {
        this.zoomInBtn.setOnAction(event);
    }

    public void setOnZoomOutClick(EventHandler<ActionEvent> event) {
        this.zoomOutBtn.setOnAction(event);
    }

    public void setOnResetZoomClick(EventHandler<ActionEvent> event) {
        this.resetZoomBtn.setOnAction(event);
    }

    public void setOnAddVariableBtnClick(EventHandler<ActionEvent> event) {
        this.addVariableBtn.setOnAction(event);
    }

    public void setOnRemoveSelectedVariable(EventHandler<ActionEvent> event) {
        this.removeVariableBtn.setOnAction(event);
    }

    public void setOnNewProject(EventHandler<ActionEvent> event) {
        this.newProjectItem.setOnAction(event);
    }

    public void setOnOpenProject(EventHandler<ActionEvent> event) {
        this.openProjectItem.setOnAction(event);
    }

    public void setOnSaveProject(EventHandler<ActionEvent> event) {
        this.saveProjectItem.setOnAction(event);
        this.quickSaveBtn.setOnAction(event);
    }

    public void setOnSaveAsProject(EventHandler<ActionEvent> event) {
        this.saveAsProjectItem.setOnAction(event);
    }

    public void setOnExportPng(EventHandler<ActionEvent> event) {
        this.exportPngItem.setOnAction(event);
        this.quickExportBtn.setOnAction(event);
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
    }

    public void setGrafcetToolsVisible(boolean value) {
        // Maintenu pour compatibilité
    }
}
