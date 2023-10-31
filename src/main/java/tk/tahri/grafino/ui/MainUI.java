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
import tk.tahri.grafino.Handle;
import tk.tahri.grafino.component.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class MainUI extends BorderPane {
    private final Canvas canvas = new Canvas(1200, 800);
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();

    private final List<StepAndTransitionCommonBehavior> components = new ArrayList<>();
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

        var initStep = new InitStep(ctx, new Point2D(600, 100));
        components.add(initStep);

        canvas.widthProperty().bind(scrollpane.widthProperty());
        canvas.heightProperty().bind(scrollpane.heightProperty());

        var toolBar = new TopToolBar();
        this.setTop(toolBar);
        toolBar.setOnStepBtnClick(event -> getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && transition.getLinksToChildren().isEmpty()) {
                components.forEach(com -> com.setSelected(false));
                var step = new Step(ctx);
                Link.between(ctx, transition, step);
                components.add(step);
            }
        }));

        toolBar.setOnTransitionBtnClick(event -> getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && step.getLinksToChildren().isEmpty()) {
                components.forEach(com -> com.setSelected(false));
                var transition = new Transition(ctx);
                Link.between(ctx, step, transition);
                components.add(transition);
            }
        }));


        toolBar.setOnOrBranchBtnClick(event -> getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && !step.getLinksToChildren().isEmpty()) {
                components.forEach(com -> com.setSelected(false));
                var transition = new Transition(ctx);
                Link.between(ctx, step, transition);
                components.add(transition);
            }
        }));

        toolBar.setOnAndBranchBtnClick(event -> getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && !transition.getLinksToChildren().isEmpty()) {
                components.forEach(com -> com.setSelected(false));
                var step = new Step(ctx);
                Link.between(ctx, transition, step);
                components.add(step);
            }
        }));

        var statusBar = new ToolBar();
        statusBar.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        statusBar.setPadding(new Insets(10));
        this.setBottom(statusBar);

        canvas.setOnMousePressed(event -> {
            // unselect all components and links
            components.forEach(com -> com.setSelected(false));
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .forEach(link -> link.setSelected(false));

            //verify if a component is pressed
            components.stream()
                    .filter(com -> com.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresent(component -> {
                        component.setSelected(true);
                        if (component instanceof StepAndTransitionCommonBehavior stepOrTransition) {
                            if (stepOrTransition.isBottomHandlePressed(new Point2D(event.getX(), event.getY()))) {
                                operation = Operation.PRESSED_TO_BOTTOM_LINK;
                            } else {
                                operation = Operation.PRESSED_TO_MOVE;
                            }
                            pressedH = event.getX() - stepOrTransition.getX();
                            pressedV = event.getY() - stepOrTransition.getY();
                        }
                    });

            //verify if a link is pressed
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .filter(com -> com.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresent(component -> component.setSelected(true));
        });

        canvas.setOnMouseDragged(event -> {
            if (getSelectedComponent().isPresent() && event.isPrimaryButtonDown()) {
                var component = getSelectedComponent().get();
                if (component instanceof StepAndTransitionCommonBehavior stepOrTransition) {
                    switch (operation) {
                        case PRESSED_TO_MOVE -> {
                            var position = new Point2D(event.getX() - pressedH, event.getY() - pressedV);
                            stepOrTransition.updatePositionAndLinks(position);
                            mouseDragPosition = null;
                        }
                        case PRESSED_TO_BOTTOM_LINK -> {
                            mouseDragPosition = new Point2D(event.getX(), event.getY());
                            Predicate<Component> predicate;
                            if (component instanceof Step) {
                                predicate = com -> com instanceof Transition;
                            } else {
                                predicate = com -> com instanceof Step;
                            }
                            components.stream()
                                    .filter(com -> com.isPointOnTheComponent(event.getX(), event.getY()))
                                    .filter(predicate)
                                    .findFirst()
                                    .ifPresent(com -> com.setHandle(Handle.TOP));
                        }
                    }
                }
            }
            getSelectedLink().ifPresent(link -> {
                link.updateLink(new Point2D(event.getX(),event.getY()));
            });
        });

        canvas.setOnMouseReleased(event -> {
            getSelectedComponent().ifPresent(source -> components.stream()
                    .filter(component -> component.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresent(target -> {
                        if (source.getY() > target.getY()) {
                            double distance = event.getX() - target.getX() <= Component.UNIT_WIDTH / 2 ? -Component.UNIT_WIDTH / 2 : Component.UNIT_WIDTH * 1.5;
                            var middle = new Point2D(target.getX() + distance, target.getY());
                            if (source instanceof Transition && target instanceof Step) {
                                Link.between(ctx, source, target, middle);
                            }
                        } else {
                            if (source instanceof Transition && target instanceof Step) {
                                Link.between(ctx, source, target);
                            } else if (source instanceof Step && target instanceof Transition) {
                                Link.between(ctx, source, target);
                            }
                        }
                    }));
            mouseDragPosition = null;
        });

        canvas.setOnMouseMoved(event -> {
            components.forEach(component -> component.setHandle(Handle.NONE));

            components.stream()
                    .filter(component -> component.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresent(component -> component.setHandle(Handle.BOTH));
        });

        this.setOnKeyPressed(event -> getSelectedComponent().ifPresent(component -> {
            int step = event.isControlDown() ? 5 : 1;
            switch (event.getCode()) {
                case UP -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() - step));
                case DOWN -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() + step));
                case LEFT -> component.updatePositionAndLinks(new Point2D(component.getX() - step, component.getY()));
                case RIGHT -> component.updatePositionAndLinks(new Point2D(component.getX() + step, component.getY()));
            }
        }));

        new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 40_000_000) {
                    ctx.setFill(Color.WHITE);
                    ctx.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                    // Draw components
                    components.forEach(Component::drawComponent);

                    // Draw links
                    components.stream()
                            .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                            .flatMap(List::stream)
                            .forEach(Link::drawComponent);

                    // Draw handles
                    components.forEach(StepAndTransitionCommonBehavior::drawHandle);

                    // Draw dragged link
                    if (mouseDragPosition != null && operation == Operation.PRESSED_TO_BOTTOM_LINK) {
                        getSelectedComponent().ifPresent(component -> drawDraggedLink(component, mouseDragPosition));
                    }

                    lastUpdate = now;
                }
            }
        }.start();
    }

    private Optional<StepAndTransitionCommonBehavior> getSelectedComponent() {
        return components.stream().filter(Component::isSelected).findFirst();
    }

    private Optional<Link> getSelectedLink() {
        return components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .filter(Link::isSelected)
                .findFirst();
    }

    private void drawDraggedLink(StepAndTransitionCommonBehavior selectedComponent, Point2D mouseDragPosition) {
        var begin = new Point2D(selectedComponent.getX() + Component.UNIT_WIDTH / 2, selectedComponent.getY() + Component.UNIT_HEIGHT);
        var mouse = new Point2D(mouseDragPosition.getX(), mouseDragPosition.getY());
        if (mouse.getY() < begin.getY()) {
            if (Math.abs(begin.getX() - mouse.getX()) > Component.UNIT_WIDTH) {
                ctx.strokeLine(begin.getX(), begin.getY(), mouse.getX(), begin.getY());
                ctx.strokeLine(mouse.getX(), begin.getY(), mouse.getX(), mouse.getY());
            } else {
                double distance = begin.getX() - mouse.getX() >= 0 ? -Component.UNIT_WIDTH : Component.UNIT_WIDTH;
                ctx.strokeLine(begin.getX(), begin.getY(), begin.getX() + distance, begin.getY());
                ctx.strokeLine(begin.getX() + distance, begin.getY(), begin.getX() + distance, mouse.getY());
                ctx.strokeLine(begin.getX() + distance, mouse.getY(), mouse.getX(), mouse.getY());
            }
        } else {
            ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), mouse.getY());
            ctx.strokeLine(begin.getX(), mouse.getY(), mouse.getX(), mouse.getY());
        }
    }

}
