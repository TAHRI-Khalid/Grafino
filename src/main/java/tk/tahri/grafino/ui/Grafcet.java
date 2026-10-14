package tk.tahri.grafino.ui;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import tk.tahri.grafino.Handle;
import tk.tahri.grafino.Operation;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Grafcet extends ScrollPane {
    private final Canvas canvas = new Canvas(2400, 1800);
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();
    private final List<StepAndTransitionCommonBehavior> components = new ArrayList<>();
    private Operation operation = Operation.PRESSED_TO_MOVE;
    private double pressedH = 0;
    private double pressedV = 0;
    private Point2D mouseDragPosition;
    private Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent;

    public Grafcet() {
        this.setFitToHeight(false);
        this.setFitToWidth(false);
        this.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        this.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        this.setContent(canvas);

        canvas.setOnMousePressed(event -> {
            // Unselect all components and links
            components.forEach(com -> com.setSelected(false));
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .forEach(link -> link.setSelected(false));

            // Verify if a component is pressed
            components.stream()
                    .filter(com -> com.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresentOrElse(component -> {
                                component.setSelected(true);
                                fireSelectionChange(Optional.of(component));
                                if (component.isBottomHandlePressed(new Point2D(event.getX(), event.getY()))) {
                                    operation = Operation.PRESSED_TO_BOTTOM_LINK;
                                } else {
                                    operation = Operation.PRESSED_TO_MOVE;
                                }
                                pressedH = event.getX() - component.getX();
                                pressedV = event.getY() - component.getY();
                            },
                            () -> fireSelectionChange(Optional.empty()));

            // Verify if a link is pressed
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .filter(com -> com.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresent(component -> component.setSelected(true));

            redraw();
        });

        canvas.setOnMouseDragged(event -> {
            if (getSelectedComponent().isPresent() && event.isPrimaryButtonDown()) {
                var component = getSelectedComponent().get();
                switch (operation) {
                    case PRESSED_TO_MOVE -> {
                        var position = new Point2D(event.getX() - pressedH, event.getY() - pressedV);
                        component.updatePositionAndLinks(position);
                        mouseDragPosition = null;
                    }
                    case PRESSED_TO_BOTTOM_LINK -> {
                        mouseDragPosition = new Point2D(event.getX(), event.getY());
                        Predicate<Component> predicate = (component instanceof Step)
                                ? com -> com instanceof Transition
                                : com -> com instanceof Step;

                        components.forEach(com -> com.setHandle(Handle.NONE));
                        components.stream()
                                .filter(com -> com.isPointOnTheComponent(event.getX(), event.getY()))
                                .filter(predicate)
                                .findFirst()
                                .ifPresent(com -> com.setHandle(Handle.TOP));
                    }
                }
            }
            getSelectedLink().ifPresent(link -> link.updateSelectedLink(new Point2D(event.getX(), event.getY())));
            redraw();
        });

        canvas.setOnMouseReleased(event -> {
            getSelectedComponent().ifPresent(source -> components.stream()
                    .filter(component -> component.isPointOnTheComponent(event.getX(), event.getY()))
                    .findFirst()
                    .ifPresent(target -> {
                        if (target == source) return;

                        // Check alternation: Step <-> Transition
                        boolean validAlternation = (source instanceof Step && target instanceof Transition)
                                || (source instanceof Transition && target instanceof Step);
                        if (!validAlternation) return;

                        // Check if link already exists
                        boolean exists = source.getLinksToChildren().stream()
                                .anyMatch(l -> l.getChildComponent() == target);
                        if (exists) return;

                        if (target.getY() < source.getY()) {
                            var begin = new Point2D(source.getX() + Component.UNIT_WIDTH / 2, source.getY() + Component.UNIT_HEIGHT);
                            var middle = begin.midpoint(new Point2D(event.getX(), event.getY()));

                            if (Math.abs(begin.getX() - middle.getX()) < Component.UNIT_WIDTH) {
                                double distance = begin.getX() - event.getX() >= 0 ? -Component.UNIT_WIDTH : Component.UNIT_WIDTH;
                                middle = new Point2D(begin.getX() + distance, middle.getY());
                            }
                            Link.between(ctx, source, target, middle);
                        } else {
                            Link.between(ctx, source, target);
                        }
                    }));
            mouseDragPosition = null;
            components.forEach(c -> c.setHandle(Handle.NONE));
            redraw();
        });

        canvas.setOnMouseMoved(event -> {
            boolean changed = false;
            for (var component : components) {
                if (component.isPointOnTheComponent(event.getX(), event.getY())) {
                    if (component.getHandle() != Handle.BOTH) {
                        component.setHandle(Handle.BOTH);
                        changed = true;
                    }
                } else if (component.getHandle() != Handle.NONE) {
                    component.setHandle(Handle.NONE);
                    changed = true;
                }
            }
            if (changed) {
                redraw();
            }
        });

        redraw();
    }

    public void redraw() {
        ctx.setFill(Color.WHITE);
        ctx.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Grille de fond discrète pour faciliter l'alignement
        ctx.setStroke(Color.rgb(240, 243, 245));
        ctx.setLineWidth(1.0);
        for (double x = 0; x < canvas.getWidth(); x += 40) {
            ctx.strokeLine(x, 0, x, canvas.getHeight());
        }
        for (double y = 0; y < canvas.getHeight(); y += 40) {
            ctx.strokeLine(0, y, canvas.getWidth(), y);
        }

        // 1. Draw links
        components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .distinct()
                .forEach(Link::drawComponent);

        // 2. Draw components
        components.forEach(Component::drawComponent);

        // 3. Draw handles
        components.forEach(StepAndTransitionCommonBehavior::drawHandle);

        // 4. Draw dragged link
        if (mouseDragPosition != null && operation == Operation.PRESSED_TO_BOTTOM_LINK) {
            getSelectedComponent().ifPresent(component -> drawDraggedLink(component, mouseDragPosition));
        }
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

    public void deleteSelected() {
        getSelectedComponent().ifPresent(comp -> {
            // Remove connected links
            new ArrayList<>(comp.getLinksFromParents()).forEach(Link::remove);
            new ArrayList<>(comp.getLinksToChildren()).forEach(Link::remove);
            components.remove(comp);
            fireSelectionChange(Optional.empty());
        });

        getSelectedLink().ifPresent(Link::remove);

        redraw();
    }

    public void toggleSelectedStepInitial() {
        getSelectedComponent().ifPresent(c -> {
            if (c instanceof Step step) {
                step.setInitial(!step.isInitial());
                redraw();
            }
        });
    }

    private void drawDraggedLink(StepAndTransitionCommonBehavior selectedComponent, Point2D mouseDragPosition) {
        final var begin = new Point2D(selectedComponent.getX() + Component.UNIT_WIDTH / 2, selectedComponent.getY() + Component.UNIT_HEIGHT);
        final var mouse = new Point2D(mouseDragPosition.getX(), mouseDragPosition.getY());
        var middle = begin.midpoint(mouse);

        ctx.setStroke(Component.LINE_COLOR);
        ctx.setLineWidth(Component.LINE_WIDTH);

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

    public void newProject() {
        components.clear();
        Step.resetCounter(0);
        Transition.resetCounter(0);
        createInitialStep();
        fireSelectionChange(Optional.empty());
        redraw();
    }

    public void createInitialStep() {
        var initStep = new Step(ctx, UUID.randomUUID().toString(), 0, true);
        initStep.setPosition(new Point2D(500, 80));
        components.add(initStep);
        redraw();
    }

    public void createStep(Transition transition) {
        components.forEach(com -> com.setSelected(false));
        var step = new Step(ctx);
        Link.between(ctx, transition, step);
        components.add(step);
        fireSelectionChange(Optional.of(step));
        redraw();
    }

    public void createTransition(Step step) {
        components.forEach(com -> com.setSelected(false));
        var transition = new Transition(ctx);
        Link.between(ctx, step, transition);
        components.add(transition);
        fireSelectionChange(Optional.of(transition));
        redraw();
    }

    public void startRender() {
        redraw();
    }

    public void stopRender() {
        // Redraw on demand replaces background loop
    }

    public void fireSelectionChange(Optional<StepAndTransitionCommonBehavior> component) {
        if (selectedComponent != null) {
            selectedComponent.accept(component);
        }
    }

    public void setOnSelectionChange(Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent) {
        this.selectedComponent = selectedComponent;
    }

    // ---------------- Serialization Bridge ----------------

    public GrafcetModel toModel(String name, List<Variable> variablesList) {
        GrafcetModel model = new GrafcetModel(name);

        // Variables
        for (Variable v : variablesList) {
            VariableType vt = (v.getType() == Variable.Type.INT) ? VariableType.INT : VariableType.BOOL;
            VariableDirection vd = v.getAddress().toUpperCase().startsWith("%Q") ? VariableDirection.OUTPUT :
                    (v.getAddress().toUpperCase().startsWith("%M") ? VariableDirection.INTERNAL : VariableDirection.INPUT);
            model.getVariables().add(new VariableModel(v.getName(), vt, vd, v.getAddress(), v.getComment()));
        }

        // Steps & Transitions
        for (StepAndTransitionCommonBehavior c : components) {
            if (c instanceof Step s) {
                StepModel sm = new StepModel(s.getId(), s.getNumber(), s.isInitial(), s.getX(), s.getY());
                for (Action a : s.getActions()) {
                    String vName = a.variable() != null ? a.variable().getName() : "";
                    sm.getActions().add(new GrafcetAction(a.qualifier(), vName));
                }
                model.getSteps().add(sm);
            } else if (c instanceof Transition t) {
                model.getTransitions().add(new TransitionModel(t.getId(), t.getNumber(), t.getReceptivity(), t.getX(), t.getY()));
            }
        }

        // Links
        Set<Link> allLinks = new HashSet<>();
        components.forEach(c -> allLinks.addAll(c.getLinksToChildren()));
        for (Link l : allLinks) {
            LinkModel lm = new LinkModel(l.getId(), l.getParentComponent().getId(), l.getChildComponent().getId());
            for (Point2D p : l.getPath()) {
                lm.getIntermediatePoints().add(new double[]{p.getX(), p.getY()});
            }
            model.getLinks().add(lm);
        }

        return model;
    }

    public void loadFromModel(GrafcetModel model, List<Variable> variablesList) {
        components.clear();
        fireSelectionChange(Optional.empty());

        Map<String, StepAndTransitionCommonBehavior> lookup = new HashMap<>();

        // Recreate Steps
        int maxStep = 0;
        for (StepModel sm : model.getSteps()) {
            Step s = new Step(ctx, sm.getId(), sm.getNumber(), sm.isInitial());
            s.setPosition(new Point2D(sm.getX(), sm.getY()));
            maxStep = Math.max(maxStep, sm.getNumber());

            // Reconstruct actions
            for (GrafcetAction ga : sm.getActions()) {
                variablesList.stream()
                        .filter(v -> v.getName().equalsIgnoreCase(ga.getVariableName()))
                        .findFirst()
                        .ifPresent(v -> s.getActions().add(new Action(ga.getQualifier(), v)));
            }

            components.add(s);
            lookup.put(s.getId(), s);
        }
        Step.resetCounter(maxStep);

        // Recreate Transitions
        int maxTrans = 0;
        for (TransitionModel tm : model.getTransitions()) {
            Transition t = new Transition(ctx, tm.getId(), tm.getNumber(), tm.getReceptivity());
            t.setPosition(new Point2D(tm.getX(), tm.getY()));
            maxTrans = Math.max(maxTrans, tm.getNumber());
            components.add(t);
            lookup.put(t.getId(), t);
        }
        Transition.resetCounter(maxTrans);

        // Recreate Links
        for (LinkModel lm : model.getLinks()) {
            var parent = lookup.get(lm.getSourceId());
            var child = lookup.get(lm.getTargetId());
            if (parent != null && child != null) {
                if (lm.getIntermediatePoints().size() == 4) {
                    Point2D middle = new Point2D(lm.getIntermediatePoints().get(1)[0], lm.getIntermediatePoints().get(1)[1]);
                    Link.between(ctx, parent, child, middle);
                } else {
                    Link.between(ctx, parent, child);
                }
            }
        }

        redraw();
    }
}
