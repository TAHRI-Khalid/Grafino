package tk.tahri.grafino.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

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
        var content = new VBox(12);

        content.getChildren().addAll(
                createSectionCard("Édition interactive sur le Canvas", new Node[]{
                        createShortcutRow("T / Entrée", "Insérer la transition suivante"),
                        createShortcutRow("S / Entrée", "Insérer l'étape suivante"),
                        createShortcutRow("Shift + Entrée", "Insérer une séquence complète (Transition + Étape)"),
                        createShortcutRow("B", "Ouvrir une branche (OU / ET)"),
                        createShortcutRow("C", "Créer une convergence"),
                        createShortcutRow("R", "Boucle de reprise ascendante"),
                        createShortcutRow("I", "Définir / basculer en étape initiale"),
                        createShortcutRow("Suppr / Retour", "Supprimer l'élément sélectionné"),
                        createShortcutRow("Double-clic", "Éditer réceptivité ou numéro d'étape in-place"),
                        createShortcutRow("Ctrl + Z / Ctrl + Y", "Annuler / Rétablir"),
                        createShortcutRow("Ctrl + L", "Aligner & Centrer tout le diagramme (Auto-Layout)")
                }),
                createSectionCard("Navigation & Affichage", new Node[]{
                        createShortcutRow("Ctrl + Molette", "Zoom fluide centré sur le curseur"),
                        createShortcutRow("Clic droit / milieu + Glisser", "Déplacement panoramique (Pan)"),
                        createShortcutRow("Ctrl + T", "Déplier / Replier la table des variables")
                }),
                createSectionCard("Gestion de Projet", new Node[]{
                        createShortcutRow("Ctrl + N", "Nouveau projet"),
                        createShortcutRow("Ctrl + O", "Ouvrir un fichier (.sfc, .tk)"),
                        createShortcutRow("Ctrl + S", "Enregistrer le projet actif"),
                        createShortcutRow("Ctrl + Shift + S", "Enregistrer sous un nouveau nom"),
                        createShortcutRow("Ctrl + E", "Exporter en image PNG haute résolution")
                })
        );

        var scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportHeight(430);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-width: 0;");

        showThemedDialog(
                "Raccourcis Clavier Grafino",
                "⌨  Raccourcis Clavier & Ergonomie",
                "Productivité et édition rapide sur le Canvas Grafcet / SFC",
                scrollPane,
                580
        );
    }

    private void showAboutDialog() {
        var content = new VBox(12);

        var descLabel = new Label(
                "Grafino est un logiciel pédagogique open-source dédié à l'enseignement et à " +
                "l'ingénierie des automatismes industriels selon les normes internationales :"
        );
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + AppTheme.TEXT_MAIN + "; -fx-line-spacing: 2px;");

        var normsCard = new VBox(10,
                descLabel,
                createNormRow("CEI 60848", "Spécification fonctionnelle Grafcet"),
                createNormRow("CEI 61131-3", "Programmation SFC, types de données & variables API")
        );
        normsCard.setStyle(AppTheme.CARD_STYLE);

        var infoCard = createSectionCard("Informations techniques", new Node[]{
                createInfoRow("Version", "1.1.0"),
                createInfoRow("Plateforme", "Java 27 / JavaFX 27"),
                createInfoRow("Public cible", "BTS CRSA, BTS CIRA, BUT GEII, Écoles d'ingénieurs"),
                createInfoRow("Licence", "Open-source pédagogique")
        });

        content.getChildren().addAll(normsCard, infoCard);

        showThemedDialog(
                "À propos de Grafino",
                "⚡  Grafino",
                "Environnement d'Automatismes Industriels — CEI 60848 & CEI 61131-3",
                content,
                520
        );
    }

    private void showThemedDialog(String windowTitle, String headerTitle, String headerSubtitle, Node bodyNode, double width) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (this.getScene() != null) {
            Window owner = this.getScene().getWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }
        }
        stage.setTitle(windowTitle);
        stage.setResizable(false);

        // Bannière supérieure Arduino Teal
        var titleLbl = new Label(headerTitle);
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");

        var subtitleLbl = new Label(headerSubtitle);
        subtitleLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppTheme.TEAL_LIGHT_BG + ";");
        subtitleLbl.setWrapText(true);

        var headerBox = new VBox(4, titleLbl, subtitleLbl);
        headerBox.setPadding(new Insets(16, 20, 16, 20));
        headerBox.setStyle("-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";");

        // Corps principal
        var bodyWrapper = new VBox(bodyNode);
        bodyWrapper.setPadding(new Insets(16, 18, 16, 18));
        bodyWrapper.setStyle("-fx-background-color: " + AppTheme.PANEL_BG + ";");
        VBox.setVgrow(bodyNode, Priority.ALWAYS);

        // Pied de page avec bouton Fermer
        var closeBtn = new Button("Fermer");
        closeBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(AppTheme.PRIMARY_BUTTON_HOVER_STYLE));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE));
        closeBtn.setOnAction(e -> stage.close());

        var footerBox = new HBox(closeBtn);
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.setPadding(new Insets(12, 18, 12, 18));
        footerBox.setStyle(
                "-fx-background-color: " + AppTheme.CARD_BG + ";" +
                "-fx-border-color: " + AppTheme.BORDER_COLOR + ";" +
                "-fx-border-width: 1px 0 0 0;"
        );

        var root = new VBox(headerBox, bodyWrapper, footerBox);
        root.setPrefWidth(width);

        Scene scene = new Scene(root);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.ENTER) {
                stage.close();
                e.consume();
            }
        });

        stage.setScene(scene);
        stage.showAndWait();
    }

    private VBox createSectionCard(String title, Node[] rows) {
        var card = new VBox(8);
        card.setStyle(AppTheme.CARD_STYLE);

        var headerLbl = new Label(title);
        headerLbl.setStyle(AppTheme.SECTION_HEADER_STYLE);
        card.getChildren().add(headerLbl);

        var separator = new Separator();
        separator.setStyle("-fx-opacity: 0.6;");
        card.getChildren().add(separator);

        for (Node row : rows) {
            card.getChildren().add(row);
        }
        return card;
    }

    private HBox createShortcutRow(String keys, String description) {
        var keyBadge = new Label(keys);
        keyBadge.setMinWidth(Region.USE_PREF_SIZE);
        keyBadge.setStyle(
                "-fx-background-color: " + AppTheme.TEAL_LIGHT_BG + ";" +
                "-fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + ";" +
                "-fx-text-fill: " + AppTheme.PRIMARY_TEAL_DARK + ";" +
                "-fx-font-family: 'JetBrains Mono', 'Consolas', 'Menlo', monospace;" +
                "-fx-font-size: 11.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 3px 8px;" +
                "-fx-background-radius: 5px;" +
                "-fx-border-radius: 5px;"
        );

        var keyContainer = new HBox(keyBadge);
        keyContainer.setPrefWidth(195);
        keyContainer.setMinWidth(195);
        keyContainer.setAlignment(Pos.CENTER_LEFT);

        var descLbl = new Label(description);
        descLbl.setWrapText(true);
        descLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        HBox.setHgrow(descLbl, Priority.ALWAYS);

        var row = new HBox(10, keyContainer, descLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox createNormRow(String normCode, String description) {
        var badge = new Label(normCode);
        badge.setMinWidth(95);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle(
                "-fx-background-color: " + AppTheme.TEAL_LIGHT_BG + ";" +
                "-fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + ";" +
                "-fx-text-fill: " + AppTheme.PRIMARY_TEAL_DARK + ";" +
                "-fx-font-size: 11.5px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 3px 8px;" +
                "-fx-background-radius: 10px;" +
                "-fx-border-radius: 10px;"
        );

        var descLbl = new Label(description);
        descLbl.setWrapText(true);
        descLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        HBox.setHgrow(descLbl, Priority.ALWAYS);

        var row = new HBox(10, badge, descLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox createInfoRow(String label, String value) {
        var keyLbl = new Label(label);
        keyLbl.setPrefWidth(115);
        keyLbl.setMinWidth(115);
        keyLbl.setStyle(AppTheme.LABEL_MUTED_STYLE);

        var valLbl = new Label(value);
        valLbl.setWrapText(true);
        valLbl.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        HBox.setHgrow(valLbl, Priority.ALWAYS);

        var row = new HBox(10, keyLbl, valLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
