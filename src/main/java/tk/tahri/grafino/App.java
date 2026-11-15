package tk.tahri.grafino;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import tk.tahri.grafino.ui.MainUI;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        MainUI mainUI = new MainUI();
        var scene = new Scene(mainUI, 800, 600);
        scene.getStylesheets().add(String.valueOf(getClass().getResource("/css/style.css")));
        stage.setMaximized(true);
        stage.setTitle("Grafino — Nouveau Projet");
        stage.setScene(scene);

        // Interception de l'opération de fermeture : vérifier modifications non enregistrées
        stage.setOnCloseRequest(event -> {
            if (!mainUI.confirmSaveOrDiscardBeforeClosing()) {
                event.consume();
            }
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}