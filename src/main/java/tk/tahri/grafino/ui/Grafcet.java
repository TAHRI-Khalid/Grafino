package tk.tahri.grafino.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import tk.tahri.grafino.Handle;
import tk.tahri.grafino.Operation;
import tk.tahri.grafino.component.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Grafcet extends Pane {
    private final Canvas canvas = new ResizableCanvas();
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();
    private final AnimationTimer animationTimer;
    private final List<StepAndTransitionCommonBehavior> components = new ArrayList<>();
    private Operation operation = Operation.PRESSED_TO_MOVE;
    private double pressedH = 0;
    private double pressedV = 0;
    private Point2D mouseDragPosition;
    private Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent;

    public Grafcet() {
        this.setStyle("-fx-background-color: #FFFFFF;");
        this.getChildren().add(canvas);

        canvas.widthProperty().bind(this.widthProperty());
        canvas.heightProperty().bind(this.heightProperty());

        this.widthProperty().addListener((obs, oldV, newV) -> redraw());
        this.heightProperty().addListener((obs, oldV, newV) -> redraw());

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
                    .ifPresentOrElse(component -> {
                                component.setSelected(true);
                                fireSelectionChange(Optional.of(component));
                                if (component instanceof StepAndTransitionCommonBehavior stepOrTransition) {
                                    if (stepOrTransition.isBottomHandlePressed(new Point2D(event.getX(), event.getY()))) {
                                        operation = Operation.PRESSED_TO_BOTTOM_LINK;
                                    } else {
                                        operation = Operation.PRESSED_TO_MOVE;
                                    }
                                    pressedH = event.getX() - stepOrTransition.getX();
                                    pressedV = event.getY() - stepOrTransition.getY();
                                }
                            },
                            () -> fireSelectionChange(Optional.empty()));

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
            getSelectedLink().ifPresent(link -> link.updateSelectedLink(new Point2D(event.getX(), event.getY())));
        });

        canvas.setOnMouseReleased(event -> {
            getSelectedComponent().ifPresent(source -> components.stream()
                    .filter(component -> component.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresent(target -> {
                        if (target.getY() < source.getY()) {
                            var begin = new Point2D(source.getX() + Component.UNIT_WIDTH / 2, source.getY() + Component.UNIT_HEIGHT);
                            var middle = begin.midpoint(new Point2D(event.getX(), event.getY()));

                            if (Math.abs(begin.getX() - middle.getX()) < Component.UNIT_WIDTH) {
                                double distance = begin.getX() - event.getX() >= 0 ? -Component.UNIT_WIDTH : Component.UNIT_WIDTH;
                                middle = new Point2D(begin.getX() + distance, middle.getY());
                            }

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

        animationTimer = new AnimationTimer() {
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
        };
    }

    public Optional<StepAndTransitionCommonBehavior> getSelectedComponent() {
        return components.stream().filter(Component::isSelected).findFirst();
    }

    public Optional<Link> getSelectedLink() {
        return components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .filter(Link::isSelected)
                .findFirst();
    }

    private void drawDraggedLink(StepAndTransitionCommonBehavior selectedComponent, Point2D mouseDragPosition) {
        final var begin = new Point2D(selectedComponent.getX() + Component.UNIT_WIDTH / 2, selectedComponent.getY() + Component.UNIT_HEIGHT);
        final var mouse = new Point2D(mouseDragPosition.getX(), mouseDragPosition.getY());
        var middle = begin.midpoint(mouse);

        if (mouse.getY() < begin.getY()) {
            if (Math.abs(begin.getX() - middle.getX()) < Component.UNIT_WIDTH) {
                double distance = begin.getX() - mouse.getX() >= 0 ? -Component.UNIT_WIDTH : Component.UNIT_WIDTH;
                middle = new Point2D(begin.getX() + distance, middle.getY());
            }
            ctx.strokeLine(begin.getX(), begin.getY(), middle.getX(), begin.getY());
            ctx.strokeLine(middle.getX(), begin.getY(), middle.getX(), mouse.getY());
            ctx.strokeLine(middle.getX(), mouse.getY(), mouse.getX(), mouse.getY());
        } else {
            ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), mouse.getY());
            ctx.strokeLine(begin.getX(), mouse.getY(), mouse.getX(), mouse.getY());
        }
    }

    public List<StepAndTransitionCommonBehavior> getComponents() {
        return components;
    }

    public void createInitialStep() {
        var initStep = new InitStep(ctx, new Point2D(600, 100));
        components.add(initStep);
    }

    public void createStep(Transition transition) {
        components.forEach(com -> com.setSelected(false));
        var step = new Step(ctx);
        Link.between(ctx, transition, step);
        components.add(step);
        fireSelectionChange(Optional.of(step));
    }

    public void createTransition(Step step) {
        components.forEach(com -> com.setSelected(false));
        var transition = new Transition(ctx);
        Link.between(ctx, step, transition);
        components.add(transition);
        fireSelectionChange(Optional.of(transition));
    }

    public void startRender() {
        this.animationTimer.start();
    }

    public void stopRender() {
        this.animationTimer.stop();
    }

    /**
     * Force un redessin immédiat de l'ensemble des composants et liaisons SFC sur le Canvas.
     */
    public void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        ctx.setFill(Color.WHITE);
        ctx.fillRect(0, 0, w, h);

        components.forEach(Component::drawComponent);

        components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .forEach(Link::drawComponent);

        components.forEach(StepAndTransitionCommonBehavior::drawHandle);
    }

    public void fireSelectionChange(Optional<StepAndTransitionCommonBehavior> component) {
        selectedComponent.accept(component);
    }

    public void setOnSelectionChange(Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent) {
        this.selectedComponent = selectedComponent;
    }

    /**
     * Canvas personnalisable étendant Canvas pour être redimensionné dynamiquement
     * par son conteneur parent (Pane / StackPane) afin de couvrir tout l'espace disponible.
     */
    private static class ResizableCanvas extends Canvas {
        @Override
        public boolean isResizable() {
            return true;
        }

        @Override
        public double prefWidth(double height) {
            return getWidth();
        }

        @Override
        public double prefHeight(double width) {
            return getHeight();
        }

        @Override
        public double minWidth(double height) {
            return 1.0;
        }

        @Override
        public double minHeight(double width) {
            return 1.0;
        }

        @Override
        public double maxWidth(double height) {
            return Double.MAX_VALUE;
        }

        @Override
        public double maxHeight(double width) {
            return Double.MAX_VALUE;
        }
    }
}
