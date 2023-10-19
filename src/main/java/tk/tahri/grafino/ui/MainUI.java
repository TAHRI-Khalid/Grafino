package tk.tahri.grafino.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import tk.tahri.grafino.Style;
import tk.tahri.grafino.component.Component;
import tk.tahri.grafino.component.InitStep;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Transition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class MainUI extends BorderPane {
    private final Canvas canvas = new Canvas(1200, 800);
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();
    private final List<Component> components = new ArrayList<>();
    private double pressedH = 0;
    private double pressedV = 0;

    public MainUI() {
        var scrollpane = new ScrollPane();
        scrollpane.setFitToHeight(true);
        scrollpane.setFitToWidth(true);
        scrollpane.setContent(canvas);
        this.setCenter(scrollpane);

        var initStep = new InitStep(ctx, 350, 50);
        components.add(initStep);

        var toolBar = new TopToolBar();
        this.setTop(toolBar);
        toolBar.setOnStepBtnClick(event -> {
            getSelectedComponent().ifPresent(component -> {
                if (component instanceof Transition transition) {
                    if (!transition.getChildren().isEmpty()) return;
                    components.forEach(com -> com.setSelected(false));
                    var step = new Step(ctx, transition.getX(), transition.getY() + Style.UNIT_HEIGHT);
                    step.setSelected(true);
                    transition.addChild(step);
                    components.add(step);
                }
            });
        });

        toolBar.setOnTransitionBtnClick(event -> {
            getSelectedComponent().ifPresent(component -> {
                if (component instanceof Step step) {
                    if (!step.getChildren().isEmpty()) return;
                    components.forEach(com -> com.setSelected(false));
                    var transition = new Transition(ctx, step.getX(), step.getY() + Style.UNIT_HEIGHT);
                    transition.setSelected(true);
                    step.addChild(transition);
                    components.add(transition);
                }
            });
        });

        toolBar.setOnOrBranchBtnClick(event -> {
            getSelectedComponent().ifPresent(component -> {
                if (component instanceof Step step && !step.getChildren().isEmpty()) {
                    components.forEach(com -> com.setSelected(false));
                    var farComponent=getThefarComponent(step.getChildren());
                    var transition = new Transition(ctx, farComponent.getX()+(Style.UNIT_WIDTH*2), step.getY() + Style.UNIT_HEIGHT);
                    transition.setSelected(true);
                    step.addChild(transition);
                    components.add(transition);
                }
            });
        });
        toolBar.setOnAndBranchBtnClick(event -> {
            getSelectedComponent().ifPresent(component -> {
                if (component instanceof Transition transition && !transition.getChildren().isEmpty()) {
                    components.forEach(com -> com.setSelected(false));
                    var farComponent=getThefarComponent(transition.getChildren());
                    var step = new Step(ctx, farComponent.getX()+(Style.UNIT_WIDTH*2), transition.getY() + Style.UNIT_HEIGHT);
                    step.setSelected(true);
                    transition.addChild(step);
                    components.add(step);
                }
            });
        });

        var statusBar = new ToolBar();
        statusBar.setBackground(new Background(new BackgroundFill(Style.MAIN_COLOR, null, null)));
        statusBar.setPadding(new Insets(10));
        this.setBottom(statusBar);

        canvas.setOnMousePressed(event -> {
            var pressedComponent = components.stream()
                    .filter(com -> com.isPressed(event))
                    .findFirst();
            components.forEach(com -> com.setSelected(false));
            if (pressedComponent.isPresent()) {
                pressedComponent.get().setSelected(true);
                pressedH = event.getX() - pressedComponent.get().getX();
                pressedV = event.getY() - pressedComponent.get().getY();
            }
        });

        canvas.setOnMouseDragged(event -> {
            if (getSelectedComponent().isPresent() && event.isPrimaryButtonDown()) {
                var component = getSelectedComponent().get();
                var position = new Point2D(event.getX() - pressedH, event.getY() - pressedV);
                component.setPosition(position);
            }
        });

        new AnimationTimer() {
            @Override
            public void handle(long l) {
                ctx.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                components.forEach(Component::drawComponent);
                components.forEach(Component::drawLink);
            }
        }.start();
    }

    private Optional<Component> getSelectedComponent() {
        return components.stream().filter(Component::isSelected).findFirst();
    }
    private Component getThefarComponent(List<Component> components){
        return components.stream().sorted(Comparator.comparing(Component::getX)).toList().getLast();
    }
}
