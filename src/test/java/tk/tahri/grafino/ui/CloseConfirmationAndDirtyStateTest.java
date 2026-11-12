package tk.tahri.grafino.ui;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tk.tahri.grafino.JavaFxTestHelper;
import tk.tahri.grafino.component.Action;
import tk.tahri.grafino.component.Link;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Transition;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.model.IecDataType;
import tk.tahri.grafino.model.IecVariableScope;
import tk.tahri.grafino.model.SfcProgram;
import tk.tahri.grafino.storage.SfcStorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Vérification des modifications et confirmation lors de la fermeture (Enregistrer / Abandonner / Annuler)")
class CloseConfirmationAndDirtyStateTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestHelper.initJavaFx();
    }

    @Test
    @DisplayName("Un nouveau projet initialement vierge n'est pas considéré comme modifié")
    void testInitialStateNotModified() {
        MainUI mainUI = new MainUI();
        assertFalse(mainUI.isModified(), "Un projet vierge initial ne doit pas être marqué modifié.");
    }

    @Test
    @DisplayName("L'ajout d'une transition ou d'une étape marque le projet comme modifié")
    void testModifiedAfterAddingComponent() {
        MainUI mainUI = new MainUI();
        assertFalse(mainUI.isModified());

        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);

        assertTrue(mainUI.isModified(), "L'ajout d'une transition doit marquer le projet comme modifié.");
    }

    @Test
    @DisplayName("L'ajout d'une variable d'automatisme marque le projet comme modifié")
    void testModifiedAfterAddingVariable() {
        MainUI mainUI = new MainUI();
        assertFalse(mainUI.isModified());

        Variable varDcy = new Variable("dcy", IecDataType.BOOL, IecVariableScope.VAR_INPUT, "%I0.0", "FALSE", "Bouton départ");
        mainUI.getVariableTable().getVariables().add(varDcy);

        assertTrue(mainUI.isModified(), "L'ajout d'une variable doit marquer le projet comme modifié.");
    }

    @Test
    @DisplayName("Le déplacement d'un composant graphique marque le projet comme modifié")
    void testModifiedAfterMovingComponent() {
        MainUI mainUI = new MainUI();
        assertFalse(mainUI.isModified());

        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        step0.updatePositionAndLinks(new Point2D(480.0, 120.0));

        assertTrue(mainUI.isModified(), "Le changement de coordonnées d'une étape doit être détecté comme une modification.");
    }

    @Test
    @DisplayName("L'ajout d'une action à une étape marque le projet comme modifié")
    void testModifiedAfterAddingAction() {
        MainUI mainUI = new MainUI();
        assertFalse(mainUI.isModified());

        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        step0.getActions().add(new Action("N", "LAMPE_TEMOIN"));

        assertTrue(mainUI.isModified(), "L'ajout d'une action à une étape doit marquer le projet comme modifié.");
    }

    @Test
    @DisplayName("L'enregistrement du projet réinitialise l'état de modification (isModified = false)")
    void testNotModifiedAfterSave(@TempDir Path tempDir) {
        MainUI mainUI = new MainUI();
        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);
        assertTrue(mainUI.isModified());

        File saveTarget = tempDir.resolve("mon_projet.sfc").toFile();
        boolean saved = mainUI.doSave(saveTarget);

        assertTrue(saved);
        assertFalse(mainUI.isModified(), "Après enregistrement réussi, le projet ne doit plus être marqué comme modifié.");
        assertEquals(saveTarget, mainUI.getCurrentFile());
    }

    @Test
    @DisplayName("Le chargement d'un fichier SFC existant démarre dans un état non modifié")
    void testNotModifiedAfterLoad(@TempDir Path tempDir) throws IOException {
        // Préparer un fichier SFC sur disque
        Grafcet sourceGrafcet = new Grafcet(false);
        sourceGrafcet.clearDiagram();
        Step s0 = (Step) sourceGrafcet.getComponents().get(0);
        Transition t1 = new Transition(null);
        t1.setNumber(1);
        t1.setReceptivity("dcy");
        t1.position = new Point2D(400, 180);
        sourceGrafcet.getComponents().add(t1);
        Link.between(null, s0, t1);

        File sfcFile = tempDir.resolve("projet_charge.sfc").toFile();
        SfcProgram prog = sourceGrafcet.toProgram("TestLoad", List.of());
        SfcStorage.save(prog, sfcFile);

        // Charger dans MainUI
        MainUI mainUI = new MainUI();
        SfcProgram loaded = SfcStorage.load(sfcFile);
        mainUI.getGrafcet().loadFromProgram(loaded, List.of());
        mainUI.setCurrentProjectName("TestLoad");
        mainUI.setCurrentFile(sfcFile);
        mainUI.setLastSavedProgram(mainUI.getGrafcet().toProgram("TestLoad", List.of()));

        assertFalse(mainUI.isModified(), "Un fichier venant d'être chargé ne doit pas être marqué modifié.");

        // Modifier la réceptivité
        Transition loadedTrans = (Transition) mainUI.getGrafcet().getComponents().stream()
                .filter(Transition.class::isInstance)
                .findFirst().orElseThrow();
        loadedTrans.setReceptivity("arret_urgence");

        assertTrue(mainUI.isModified(), "La modification d'une réceptivité après chargement doit marquer le projet modifié.");
    }

    @Test
    @DisplayName("Lors de la fermeture : si aucune modification n'est présente, fermer directement sans dialogue")
    void testCloseWhenNotModifiedProceedsDirectly() {
        MainUI mainUI = new MainUI();
        AtomicBoolean dialogShown = new AtomicBoolean(false);

        mainUI.setCloseConfirmationHandler(name -> {
            dialogShown.set(true);
            return MainUI.CloseChoice.CANCEL;
        });

        boolean canClose = mainUI.confirmSaveOrDiscardBeforeClosing();

        assertTrue(canClose, "La fermeture sans modification doit être autorisée immédiatement.");
        assertFalse(dialogShown.get(), "Aucun dialogue ne doit être présenté si le document n'a pas été modifié.");
    }

    @Test
    @DisplayName("Lors de la fermeture avec modifications : l'utilisateur choisit d'abandonner les changements")
    void testCloseWhenModifiedUserChoosesDiscard() {
        MainUI mainUI = new MainUI();
        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);
        assertTrue(mainUI.isModified());

        AtomicBoolean dialogShown = new AtomicBoolean(false);
        mainUI.setCloseConfirmationHandler(name -> {
            dialogShown.set(true);
            assertEquals("Nouveau_Projet", name);
            return MainUI.CloseChoice.DISCARD;
        });

        boolean canClose = mainUI.confirmSaveOrDiscardBeforeClosing();

        assertTrue(dialogShown.get(), "Le dialogue de confirmation doit être affiché.");
        assertTrue(canClose, "Si l'utilisateur choisit d'abandonner, la fermeture doit être acceptée.");
    }

    @Test
    @DisplayName("Lors de la fermeture avec modifications : l'utilisateur choisit d'annuler")
    void testCloseWhenModifiedUserChoosesCancel() {
        MainUI mainUI = new MainUI();
        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);
        assertTrue(mainUI.isModified());

        AtomicBoolean dialogShown = new AtomicBoolean(false);
        mainUI.setCloseConfirmationHandler(name -> {
            dialogShown.set(true);
            return MainUI.CloseChoice.CANCEL;
        });

        boolean canClose = mainUI.confirmSaveOrDiscardBeforeClosing();

        assertTrue(dialogShown.get());
        assertFalse(canClose, "Si l'utilisateur choisit d'annuler, la fermeture doit être interrompue.");
    }

    @Test
    @DisplayName("Lors de la fermeture avec modifications : l'utilisateur choisit d'enregistrer et l'enregistrement réussit")
    void testCloseWhenModifiedUserChoosesSaveSuccess(@TempDir Path tempDir) {
        MainUI mainUI = new MainUI();
        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);
        assertTrue(mainUI.isModified());

        File saveTarget = tempDir.resolve("sauvegarde_fermeture.sfc").toFile();
        mainUI.setSaveFileChooser(fc -> saveTarget);

        AtomicBoolean dialogShown = new AtomicBoolean(false);
        mainUI.setCloseConfirmationHandler(name -> {
            dialogShown.set(true);
            return MainUI.CloseChoice.SAVE;
        });

        boolean canClose = mainUI.confirmSaveOrDiscardBeforeClosing();

        assertTrue(dialogShown.get());
        assertTrue(canClose, "Après enregistrement réussi, la fermeture doit se poursuivre.");
        assertTrue(saveTarget.exists(), "Le fichier doit avoir été créé sur le disque.");
        assertFalse(mainUI.isModified(), "Le document doit être marqué comme enregistré.");
    }

    @Test
    @DisplayName("Lors de la fermeture avec modifications : l'utilisateur choisit d'enregistrer mais annule le dialogue de fichier")
    void testCloseWhenModifiedUserChoosesSaveCancelled() {
        MainUI mainUI = new MainUI();
        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);
        assertTrue(mainUI.isModified());

        // L'utilisateur annule le sélecteur de fichier (renvoie null)
        mainUI.setSaveFileChooser(fc -> null);

        AtomicBoolean dialogShown = new AtomicBoolean(false);
        mainUI.setCloseConfirmationHandler(name -> {
            dialogShown.set(true);
            return MainUI.CloseChoice.SAVE;
        });

        boolean canClose = mainUI.confirmSaveOrDiscardBeforeClosing();

        assertTrue(dialogShown.get());
        assertFalse(canClose, "Si la sauvegarde est annulée par l'utilisateur, la fermeture doit être annulée pour protéger le travail.");
        assertTrue(mainUI.isModified(), "Le travail reste non enregistré.");
    }

    @Test
    @DisplayName("L'opération Nouveau Projet propose d'enregistrer si des modifications existent")
    void testNewProjectPromptsIfModified() {
        MainUI mainUI = new MainUI();
        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);
        assertTrue(mainUI.isModified());

        AtomicInteger promptCount = new AtomicInteger(0);
        // L'utilisateur choisit d'annuler dans le prompt
        mainUI.setCloseConfirmationHandler(name -> {
            promptCount.incrementAndGet();
            return MainUI.CloseChoice.CANCEL;
        });

        mainUI.onNewProject();
        assertEquals(1, promptCount.get(), "onNewProject doit vérifier les modifications.");
        assertTrue(mainUI.isModified(), "Le projet ne doit pas avoir été réinitialisé suite à l'annulation.");

        // Maintenant l'utilisateur choisit d'abandonner les modifications
        mainUI.setCloseConfirmationHandler(name -> {
            promptCount.incrementAndGet();
            return MainUI.CloseChoice.DISCARD;
        });

        mainUI.onNewProject();
        assertEquals(2, promptCount.get());
        assertFalse(mainUI.isModified(), "Le nouveau projet initialisé doit être propre.");
    }

    @Test
    @DisplayName("Le menu Quitter (Ctrl+Q) déclenche la demande de fermeture")
    void testExitMenuItemTriggersCloseRequest() {
        MainUI mainUI = new MainUI();
        Step step0 = (Step) mainUI.getGrafcet().getComponents().get(0);
        mainUI.getGrafcet().createTransition(step0);
        assertTrue(mainUI.isModified());

        AtomicBoolean closePrompted = new AtomicBoolean(false);
        mainUI.setCloseConfirmationHandler(name -> {
            closePrompted.set(true);
            return MainUI.CloseChoice.CANCEL;
        });

        mainUI.getAppMenuBar().getExitItem().fire();

        assertTrue(closePrompted.get(), "L'action Quitter du menu doit déclencher la vérification des modifications.");
    }

    @Test
    @DisplayName("Le dialogue de confirmation par défaut contient les boutons Enregistrer, Abandonner et Annuler")
    void testDefaultDialogConfiguration() throws Exception {
        java.util.concurrent.CompletableFuture<List<String>> buttonLabelsFuture = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<String> titleFuture = new java.util.concurrent.CompletableFuture<>();

        javafx.application.Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                alert.setTitle("Fermeture de Grafino");
                alert.setHeaderText("Enregistrer les modifications apportées au projet 'Projet_Test' ?");
                javafx.scene.control.ButtonType btnSave = new javafx.scene.control.ButtonType("Enregistrer", javafx.scene.control.ButtonBar.ButtonData.YES);
                javafx.scene.control.ButtonType btnDiscard = new javafx.scene.control.ButtonType("Abandonner", javafx.scene.control.ButtonBar.ButtonData.NO);
                javafx.scene.control.ButtonType btnCancel = new javafx.scene.control.ButtonType("Annuler", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(btnSave, btnDiscard, btnCancel);

                titleFuture.complete(alert.getTitle());
                buttonLabelsFuture.complete(alert.getButtonTypes().stream().map(javafx.scene.control.ButtonType::getText).toList());
            } catch (Throwable t) {
                buttonLabelsFuture.completeExceptionally(t);
            }
        });

        assertEquals("Fermeture de Grafino", titleFuture.get(5, java.util.concurrent.TimeUnit.SECONDS));
        List<String> labels = buttonLabelsFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(List.of("Enregistrer", "Abandonner", "Annuler"), labels);
    }
}
