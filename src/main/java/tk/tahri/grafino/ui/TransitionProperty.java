package tk.tahri.grafino.ui;

import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import tk.tahri.grafino.component.Transition;

public class TransitionProperty extends VBox {
    public TransitionProperty(Transition transition) {
        this.setPrefWidth(400);
        var title = new Label("Transition : T" + transition.getNumber());
        title.getStyleClass().add("title");
        var separator = new Separator();
        this.getChildren().addAll(title, separator);
    }
}
