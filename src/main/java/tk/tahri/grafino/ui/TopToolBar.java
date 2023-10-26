package tk.tahri.grafino.ui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import tk.tahri.grafino.component.Component;

import java.util.Objects;
import java.util.function.Supplier;

public class TopToolBar extends ToolBar {
    private final Button stepBtn = new Button();
    private final Button transitionBtn = new Button();
    private final Button endBtn = new Button();
    private final Button jumpBtn = new Button();
    private final Button orBranchBtn = new Button();
    private final Button andBranchBtn = new Button();

    public final Supplier<ActionEvent> actionEventSupplier = () -> null;

    public TopToolBar() {
        stepBtn.getStyleClass().add("toolBarButton");
        var stepImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Step_v3.png"))));
        stepImg.setFitWidth(30);
        stepImg.setFitHeight(30);
        stepBtn.setGraphic(stepImg);

        transitionBtn.getStyleClass().add("toolBarButton");
        var transitionImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Transition_v3.png"))));
        transitionImg.setFitWidth(30);
        transitionImg.setFitHeight(30);
        transitionBtn.setGraphic(transitionImg);


        endBtn.getStyleClass().add("toolBarButton");
        var endImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/End_v3.png"))));
        endImg.setFitWidth(30);
        endImg.setFitHeight(30);
        endBtn.setGraphic(endImg);


        jumpBtn.getStyleClass().add("toolBarButton");
        var jumpImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Jump_v3.png"))));
        jumpImg.setFitWidth(30);
        jumpImg.setFitHeight(30);
        jumpBtn.setGraphic(jumpImg);


        orBranchBtn.getStyleClass().add("toolBarButton");
        var orImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/Or_Branch_v3.png"))));
        orImg.setFitWidth(30);
        orImg.setFitHeight(30);
        orBranchBtn.setGraphic(orImg);


        andBranchBtn.getStyleClass().add("toolBarButton");
        var andImg = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/And_Branch_v3.png"))));
        andImg.setFitWidth(30);
        andImg.setFitHeight(30);
        andBranchBtn.setGraphic(andImg);

        this.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        this.getItems().addAll(stepBtn, transitionBtn, endBtn, jumpBtn, orBranchBtn, andBranchBtn, new Separator());
        this.setPadding(new Insets(10));

    }

    public void setOnStepBtnClick(EventHandler<ActionEvent> event) {
        this.stepBtn.setOnAction(event);
    }

    public void setOnTransitionBtnClick(EventHandler<ActionEvent> event) {
        this.transitionBtn.setOnAction(event);
    }

    public void setOnEndBtnClick(EventHandler<ActionEvent> event) {
        this.endBtn.setOnAction(event);
    }

    public void setOnJumpBtnClick(EventHandler<ActionEvent> event) {
        this.jumpBtn.setOnAction(event);
    }

    public void setOnOrBranchBtnClick(EventHandler<ActionEvent> event) {
        this.orBranchBtn.setOnAction(event);
    }

    public void setOnAndBranchBtnClick(EventHandler<ActionEvent> event) {
        this.andBranchBtn.setOnAction(event);
    }
}
