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
import javafx.scene.paint.Color;
import tk.tahri.grafino.Operation;
import tk.tahri.grafino.Style;
import tk.tahri.grafino.Vertex;
import tk.tahri.grafino.component.Component;
import tk.tahri.grafino.component.InitStep;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Transition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class MainUI extends BorderPane {
    private final Canvas canvas = new Canvas(1200, 800);
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();
    private final List<Component> components = new ArrayList<>();
    private Operation operation = Operation.PRESSED_TO_MOVE;
    private double pressedH = 0;
    private double pressedV = 0;
    private Point2D mouseDragPosition;

    public MainUI() {
        var scrollpane = new ScrollPane();
        scrollpane.setFitToHeight(true);
        scrollpane.setFitToWidth(true);
        scrollpane.setContent(canvas);
        this.setCenter(scrollpane);

        var initStep = new InitStep(ctx, 350, 50);
        components.add(initStep);

        canvas.widthProperty().bind(scrollpane.widthProperty());
        canvas.heightProperty().bind(scrollpane.heightProperty());

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
                    var farComponent = getTheExtremeRightComponent(step.getChildren());
                    var transition = new Transition(ctx, farComponent.getX() + (Style.UNIT_WIDTH * 3), step.getY() + Style.UNIT_HEIGHT);
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
                    var farComponent = getTheExtremeRightComponent(transition.getChildren());
                    var step = new Step(ctx, farComponent.getX() + (Style.UNIT_WIDTH * 3), transition.getY() + Style.UNIT_HEIGHT);
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
            pressedComponent.ifPresent(component -> {
                component.setSelected(true);

                if (component.isTopVertexPressed(event)) {
                    operation = Operation.PRESSED_TO_TOP_LINK;
                } else if (component.isBottomVertexPressed(event)) {
                    operation = Operation.PRESSED_TO_BOTTOM_LINK;
                } else {
                    operation = Operation.PRESSED_TO_MOVE;
                }

                pressedH = event.getX() - component.getX();
                pressedV = event.getY() - component.getY();
            });
        });

        canvas.setOnMouseDragged(event -> {
            if (getSelectedComponent().isPresent() && event.isPrimaryButtonDown()) {
                var component = getSelectedComponent().get();

                switch (operation) {
                    case PRESSED_TO_MOVE -> {
                        var position = new Point2D(event.getX() - pressedH, event.getY() - pressedV);
                        component.setPosition(position);
                        mouseDragPosition = null;
                    }
                    case PRESSED_TO_BOTTOM_LINK -> {
                        mouseDragPosition = new Point2D(event.getX(), event.getY());
                        Predicate<Component> predicate ;
                        if (component instanceof Step){
                             predicate = com -> com instanceof Transition;
                        }else {
                            predicate = com -> com instanceof Step;
                        }
                        components.stream()
                                .filter(com -> com.isPressed(event))
                                .filter(predicate)
                                .findFirst()
                                .ifPresent(com -> com.setVertex(Vertex.TOP));
                    }
                }
            }
        });

        canvas.setOnMouseReleased(event -> {
            getSelectedComponent().ifPresent(source -> {
                components.stream()
                    .filter(component -> component.isPressed(event))
                    .findFirst()
                    .ifPresent(target -> {
                        if (source instanceof Transition && target instanceof Step) {
                            source.addChild(target);
                        }else if(source instanceof Step && target instanceof Transition){
                            source.addChild(target);
                        }
                    });
            });
            mouseDragPosition = null;
        });

        canvas.setOnMouseMoved(event -> {
            components.forEach(component -> component.setVertex(Vertex.NONE));
            components.stream()
                    .filter(component -> component.isPressed(event))
                    .findFirst()
                    .ifPresent(component -> component.setVertex(Vertex.BOTH));
        });

        new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 40_000_000) {
                    ctx.setFill(Color.WHITE);
                    ctx.fillRect(0,0,canvas.getWidth(),canvas.getHeight());
                    //ctx.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    components.forEach(Component::drawComponent);
                    components.forEach(Component::drawLink);
                    components.forEach(Component::drawVertex);

                    if (mouseDragPosition != null && operation == Operation.PRESSED_TO_BOTTOM_LINK) {
                        getSelectedComponent().ifPresent(selectedComponent -> {
                            components.forEach(component -> component.drawDraggedLink(selectedComponent, mouseDragPosition));
                        });
                    }

                    lastUpdate = now;
                }
            }
        }.start();
    }

    private Optional<Component> getSelectedComponent() {
        return components.stream().filter(Component::isSelected).findFirst();
    }

    private Component getTheExtremeRightComponent(List<Component> components) {
        return components.stream()
                .sorted(Comparator.comparing(Component::getX))
                .toList()
                .getLast();
    }

    private Component getTheExtremeLeftComponent(List<Component> components) {
        return components.stream()
                .sorted(Comparator.comparing(Component::getX).reversed())
                .toList()
                .getLast();
    }
}
