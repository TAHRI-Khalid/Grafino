package tk.tahri.grafino;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import tk.tahri.grafino.ui.MainUI;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        var scene = new Scene(new MainUI(), 800, 600);
        scene.getStylesheets().add(String.valueOf(getClass().getResource("/css/style.css")));
        stage.setMaximized(true);
        stage.setTitle("Grafino");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}