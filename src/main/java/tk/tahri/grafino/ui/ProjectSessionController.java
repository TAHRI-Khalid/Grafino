package tk.tahri.grafino.ui;

import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.model.SfcProgram;
import tk.tahri.grafino.storage.SfcStorage;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Contrôleur de session projet : gestion des fichiers (.sfc / .tk), export PNG,
 * détection des modifications non enregistrées et confirmation de fermeture.
 */
public final class ProjectSessionController {

    private final MainUI host;
    private final Grafcet grafcet;
    private final VariableTable variableTable;
    private final Consumer<String> statusConsumer;
    private final Consumer<String> statusErrorConsumer;

    private File currentFile = null;
    private String currentProjectName = "Nouveau_Projet";
    private SfcProgram lastSavedProgram = null;
    private MainUI.CloseConfirmationHandler closeConfirmationHandler;
    private Function<FileChooser, File> saveFileChooser;
    private Runnable onAfterStateChange = () -> {};
    private Runnable onStopSimulationIfActive = () -> {};

    public ProjectSessionController(
            MainUI host,
            Grafcet grafcet,
            VariableTable variableTable,
            MainUI.CloseConfirmationHandler closeConfirmationHandler,
            Function<FileChooser, File> saveFileChooser,
            Consumer<String> statusConsumer,
            Consumer<String> statusErrorConsumer
    ) {
        this.host = host;
        this.grafcet = grafcet;
        this.variableTable = variableTable;
        this.closeConfirmationHandler = closeConfirmationHandler;
        this.saveFileChooser = saveFileChooser;
        this.statusConsumer = statusConsumer;
        this.statusErrorConsumer = statusErrorConsumer;
    }

    public void setHooks(Runnable onAfterStateChange, Runnable onStopSimulationIfActive) {
        this.onAfterStateChange = onAfterStateChange != null ? onAfterStateChange : () -> {};
        this.onStopSimulationIfActive = onStopSimulationIfActive != null ? onStopSimulationIfActive : () -> {};
    }

    public boolean isModified() {
        SfcProgram current = grafcet.toProgram(currentProjectName, variableTable.getVariables());
        if (lastSavedProgram == null) {
            return !grafcet.isDiagramEmpty() || !variableTable.getVariables().isEmpty();
        }
        return !current.equals(lastSavedProgram);
    }

    public boolean confirmSaveOrDiscardBeforeClosing() {
        if (!isModified()) {
            return true;
        }

        MainUI.CloseChoice choice = closeConfirmationHandler.prompt(currentProjectName);
        if (choice == null || choice == MainUI.CloseChoice.CANCEL) {
            return false;
        }

        if (choice == MainUI.CloseChoice.SAVE) {
            boolean saved = onSaveProject();
            if (saved) {
                onStopSimulationIfActive.run();
            }
            return saved;
        }

        if (choice == MainUI.CloseChoice.DISCARD) {
            onStopSimulationIfActive.run();
            return true;
        }

        return false;
    }

    public void requestClose() {
        if (host.getScene() != null && host.getScene().getWindow() instanceof Stage stage) {
            javafx.stage.WindowEvent closeEvent = new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.fireEvent(closeEvent);
            if (closeEvent.isConsumed()) {
                return;
            }
            if (stage.getOnCloseRequest() == null && !confirmSaveOrDiscardBeforeClosing()) {
                return;
            }
            stage.close();
        } else if (confirmSaveOrDiscardBeforeClosing()) {
            javafx.application.Platform.exit();
        }
    }

    public void onNewProject() {
        if (!confirmSaveOrDiscardBeforeClosing()) {
            return;
        }

        currentFile = null;
        currentProjectName = "Nouveau_Projet";
        grafcet.clearDiagram();
        variableTable.clear();
        lastSavedProgram = grafcet.toProgram(currentProjectName, variableTable.getVariables());
        updateWindowTitle();
        onAfterStateChange.run();
        statusConsumer.accept("Nouveau projet initialisé");
    }

    public void onOpenProject() {
        if (!confirmSaveOrDiscardBeforeClosing()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Ouvrir un programme Grafcet / SFC");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Programmes SFC / Grafcet (*.sfc, *.tk)", "*.sfc", "*.tk"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );

        if (currentFile != null && currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
            fileChooser.setInitialDirectory(currentFile.getParentFile());
        }

        File file = fileChooser.showOpenDialog(host.getScene() != null ? host.getScene().getWindow() : null);
        if (file != null) {
            try {
                SfcProgram loaded = SfcStorage.load(file);

                List<Variable> vars = loaded.variables().stream()
                        .map(Variable::fromIecVariable)
                        .toList();
                variableTable.setVariables(vars);
                grafcet.loadFromProgram(loaded, variableTable.getVariables());

                currentFile = file;
                currentProjectName = loaded.programName();
                lastSavedProgram = grafcet.toProgram(currentProjectName, variableTable.getVariables());
                updateWindowTitle();
                onAfterStateChange.run();
                statusConsumer.accept("Programme chargé avec succès : " + file.getName());
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Erreur de chargement");
                alert.setHeaderText("Impossible d'ouvrir le fichier SFC / Grafcet");
                alert.setContentText("Détail de l'erreur : " + ex.getMessage());
                alert.showAndWait();
                statusErrorConsumer.accept("Échec de l'ouverture du fichier : " + ex.getMessage());
            }
        }
    }

    public boolean onSaveProject() {
        return (currentFile == null) ? onSaveAsProject() : doSave(currentFile);
    }

    public boolean onSaveAsProject() {
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

        File file = saveFileChooser.apply(fileChooser);
        return (file != null) && doSave(file);
    }

    public boolean doSave(File targetFile) {
        try {
            String baseName = targetFile.getName();
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            }
            currentProjectName = baseName;

            SfcProgram program = grafcet.toProgram(currentProjectName, variableTable.getVariables());
            SfcStorage.save(program, targetFile);

            currentFile = targetFile;
            lastSavedProgram = program;
            updateWindowTitle();
            statusConsumer.accept("Programme enregistré : " + targetFile.getName());
            return true;
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur d'enregistrement");
            alert.setHeaderText("Impossible d'enregistrer le fichier SFC");
            alert.setContentText("Détail de l'erreur : " + ex.getMessage());
            alert.showAndWait();
            statusErrorConsumer.accept("Échec de l'enregistrement : " + ex.getMessage());
            return false;
        }
    }

    public void onExportPng() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter le Grafcet en image PNG haute résolution");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image PNG (*.png)", "*.png")
        );
        fileChooser.setInitialFileName(currentProjectName + ".png");

        if (currentFile != null && currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
            fileChooser.setInitialDirectory(currentFile.getParentFile());
        }

        File file = fileChooser.showSaveDialog(host.getScene() != null ? host.getScene().getWindow() : null);
        if (file != null) {
            try {
                grafcet.exportToPng(file, currentProjectName, 2.0);
                statusConsumer.accept("Export PNG réussi : " + file.getName());
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Erreur d'exportation");
                alert.setHeaderText("Impossible d'exporter l'image PNG");
                alert.setContentText("Détail de l'erreur : " + ex.getMessage());
                alert.showAndWait();
                statusErrorConsumer.accept("Échec de l'export PNG : " + ex.getMessage());
            }
        }
    }

    public void updateWindowTitle() {
        if (host.getScene() != null && host.getScene().getWindow() instanceof Stage stage) {
            String dirtyMarker = isModified() ? " *" : "";
            String display = (currentFile != null)
                    ? (currentFile.getName() + dirtyMarker)
                    : (currentProjectName + dirtyMarker + " (non enregistré)");
            stage.setTitle("Grafino — " + display);
        }
    }

    public SfcProgram getLastSavedProgram() { return lastSavedProgram; }
    public void setLastSavedProgram(SfcProgram lastSavedProgram) { this.lastSavedProgram = lastSavedProgram; }
    public File getCurrentFile() { return currentFile; }
    public void setCurrentFile(File currentFile) { this.currentFile = currentFile; }
    public String getCurrentProjectName() { return currentProjectName; }
    public void setCurrentProjectName(String currentProjectName) { this.currentProjectName = currentProjectName; }
    public void setCloseConfirmationHandler(MainUI.CloseConfirmationHandler handler) {
        this.closeConfirmationHandler = (handler != null) ? handler : _ -> MainUI.CloseChoice.CANCEL;
    }
    public MainUI.CloseConfirmationHandler getCloseConfirmationHandler() { return closeConfirmationHandler; }
    public void setSaveFileChooser(Function<FileChooser, File> saveFileChooser) { this.saveFileChooser = saveFileChooser; }
}
