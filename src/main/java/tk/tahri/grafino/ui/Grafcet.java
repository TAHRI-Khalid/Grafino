package tk.tahri.grafino.ui;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import tk.tahri.grafino.Handle;
import tk.tahri.grafino.Operation;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Modern interactive Canvas for Grafcet with vector zoom, infinite pan, and snap-to-grid.
 */
public class Grafcet extends Pane {
    public static final double GRID_SIZE = 20.0;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 3.0;

    private final Canvas canvas = new Canvas(1200, 800);
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();
    private final List<StepAndTransitionCommonBehavior> components = new ArrayList<>();

    // Pan & Zoom transformations
    private double panX = 100.0;
    private double panY = 60.0;
    private double zoom = 1.0;
    private boolean isPanning = false;
    private double lastMouseScreenX = 0;
    private double lastMouseScreenY = 0;
    private boolean spacePressed = false;
    private boolean snapToGrid = true;

    // Drawing & Manipulation
    private Operation operation = Operation.PRESSED_TO_MOVE;
    private double pressedOffsetWorldX = 0;
    private double pressedOffsetWorldY = 0;
    private Point2D mouseDragWorldPosition;

    // Callbacks
    private Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent;
    private Consumer<Double> onZoomChanged;
    private Runnable onStateModified;

    public Grafcet() {
        this.getChildren().add(canvas);

        // Bind canvas size dynamically to this Pane container
        canvas.widthProperty().bind(this.widthProperty());
        canvas.heightProperty().bind(this.heightProperty());

        this.widthProperty().addListener((obs, o, n) -> redraw());
        this.heightProperty().addListener((obs, o, n) -> redraw());

        setupMouseEvents();
        setupScrollEvents();
    }

    private void setupMouseEvents() {
        canvas.setOnMousePressed(event -> {
            Point2D world = screenToWorld(event.getX(), event.getY());

            // Pan start (Middle mouse button, Right click, or Left click with Space held)
            if (event.getButton() == MouseButton.MIDDLE || event.getButton() == MouseButton.SECONDARY || (event.getButton() == MouseButton.PRIMARY && spacePressed)) {
                isPanning = true;
                lastMouseScreenX = event.getX();
                lastMouseScreenY = event.getY();
                canvas.setCursor(Cursor.CLOSED_HAND);
                event.consume();
                return;
            }

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            // Unselect all components and links
            components.forEach(com -> com.setSelected(false));
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .forEach(link -> link.setSelected(false));

            // Check if a component is clicked
            var clickedComponent = components.stream()
                    .filter(com -> com.isPointOnTheComponent(world.getX(), world.getY()))
                    .findFirst();

            if (clickedComponent.isPresent()) {
                var comp = clickedComponent.get();
                comp.setSelected(true);
                fireSelectionChange(Optional.of(comp));

                if (comp.isBottomHandlePressed(world)) {
                    operation = Operation.PRESSED_TO_BOTTOM_LINK;
                } else {
                    operation = Operation.PRESSED_TO_MOVE;
                }
                pressedOffsetWorldX = world.getX() - comp.getX();
                pressedOffsetWorldY = world.getY() - comp.getY();
            } else {
                fireSelectionChange(Optional.empty());

                // Check if a link is clicked
                components.stream()
                        .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                        .flatMap(List::stream)
                        .filter(com -> com.isPointOnTheComponent(world.getX(), world.getY()))
                        .findFirst()
                        .ifPresent(link -> link.setSelected(true));
            }

            redraw();
        });

        canvas.setOnMouseDragged(event -> {
            if (isPanning) {
                double dx = event.getX() - lastMouseScreenX;
                double dy = event.getY() - lastMouseScreenY;
                panX += dx;
                panY += dy;
                lastMouseScreenX = event.getX();
                lastMouseScreenY = event.getY();
                redraw();
                event.consume();
                return;
            }

            Point2D world = screenToWorld(event.getX(), event.getY());

            if (getSelectedComponent().isPresent() && event.isPrimaryButtonDown()) {
                var component = getSelectedComponent().get();
                switch (operation) {
                    case PRESSED_TO_MOVE -> {
                        double rawX = world.getX() - pressedOffsetWorldX;
                        double rawY = world.getY() - pressedOffsetWorldY;
                        Point2D newPos = snapToGrid ? snap(rawX, rawY) : new Point2D(rawX, rawY);
                        component.updatePositionAndLinks(newPos);
                        mouseDragWorldPosition = null;
                    }
                    case PRESSED_TO_BOTTOM_LINK -> {
                        mouseDragWorldPosition = world;
                        Predicate<Component> predicate = (component instanceof Step)
                                ? com -> com instanceof Transition
                                : com -> com instanceof Step;

                        components.forEach(com -> com.setHandle(Handle.NONE));
                        components.stream()
                                .filter(com -> com.isPointOnTheComponent(world.getX(), world.getY()))
                                .filter(predicate)
                                .findFirst()
                                .ifPresent(com -> com.setHandle(Handle.TOP));
                    }
                }
            }

            getSelectedLink().ifPresent(link -> link.updateSelectedLink(world));
            redraw();
        });

        canvas.setOnMouseReleased(event -> {
            if (isPanning) {
                isPanning = false;
                canvas.setCursor(spacePressed ? Cursor.OPEN_HAND : Cursor.DEFAULT);
                event.consume();
                return;
            }

            Point2D world = screenToWorld(event.getX(), event.getY());
            boolean stateChanged = false;

            if (operation == Operation.PRESSED_TO_MOVE && getSelectedComponent().isPresent()) {
                var comp = getSelectedComponent().get();
                if (snapToGrid) {
                    comp.updatePositionAndLinks(snap(comp.getX(), comp.getY()));
                }
                stateChanged = true;
            }

            if (operation == Operation.PRESSED_TO_BOTTOM_LINK && getSelectedComponent().isPresent()) {
                var source = getSelectedComponent().get();
                var targetOpt = components.stream()
                        .filter(component -> component.isPointOnTheComponent(world.getX(), world.getY()))
                        .findFirst();

                if (targetOpt.isPresent()) {
                    var target = targetOpt.get();
                    if (target != source) {
                        boolean validAlternation = (source instanceof Step && target instanceof Transition)
                                || (source instanceof Transition && target instanceof Step);

                        boolean exists = source.getLinksToChildren().stream()
                                .anyMatch(l -> l.getChildComponent() == target);

                        if (validAlternation && !exists) {
                            if (target.getY() < source.getY()) {
                                var begin = new Point2D(source.getX() + Component.UNIT_WIDTH / 2, source.getY() + Component.UNIT_HEIGHT);
                                var middle = begin.midpoint(world);

                                if (Math.abs(begin.getX() - middle.getX()) < Component.UNIT_WIDTH) {
                                    double distance = begin.getX() - world.getX() >= 0 ? -Component.UNIT_WIDTH : Component.UNIT_WIDTH;
                                    middle = new Point2D(begin.getX() + distance, middle.getY());
                                }
                                Link.between(ctx, source, target, middle);
                            } else {
                                Link.between(ctx, source, target);
                            }
                            stateChanged = true;
                        }
                    }
                }
            }

            mouseDragWorldPosition = null;
            components.forEach(c -> c.setHandle(Handle.NONE));
            redraw();

            if (stateChanged) {
                notifyStateModified();
            }
        });

        canvas.setOnMouseMoved(event -> {
            if (isPanning) return;

            Point2D world = screenToWorld(event.getX(), event.getY());
            boolean changed = false;
            for (var component : components) {
                if (component.isPointOnTheComponent(world.getX(), world.getY())) {
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
    }

    private void setupScrollEvents() {
        canvas.setOnScroll(event -> {
            // Zoom centered on mouse pointer
            double factor = event.getDeltaY() > 0 ? 1.12 : 0.89;
            double mouseScreenX = event.getX();
            double mouseScreenY = event.getY();

            double newZoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom * factor));
            if (newZoom != zoom) {
                // Keep the world point under the mouse invariant
                panX = mouseScreenX - (mouseScreenX - panX) * (newZoom / zoom);
                panY = mouseScreenY - (mouseScreenY - panY) * (newZoom / zoom);
                zoom = newZoom;

                if (onZoomChanged != null) {
                    onZoomChanged.accept(zoom);
                }
                redraw();
            }
            event.consume();
        });
    }

    public Point2D screenToWorld(double screenX, double screenY) {
        double wx = (screenX - panX) / zoom;
        double wy = (screenY - panY) / zoom;
        return new Point2D(wx, wy);
    }

    public Point2D worldToScreen(double worldX, double worldY) {
        double sx = worldX * zoom + panX;
        double sy = worldY * zoom + panY;
        return new Point2D(sx, sy);
    }

    public Point2D snap(double x, double y) {
        double sx = Math.round(x / GRID_SIZE) * GRID_SIZE;
        double sy = Math.round(y / GRID_SIZE) * GRID_SIZE;
        return new Point2D(sx, sy);
    }

    public void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;

        ctx.setFill(Color.rgb(250, 252, 254));
        ctx.fillRect(0, 0, width, height);

        // Dessiner la grille de fond alignée sur le pan et le zoom
        drawInfiniteGrid(width, height);

        // Appliquer la transformation vectorielle (Pan & Zoom)
        ctx.save();
        ctx.translate(panX, panY);
        ctx.scale(zoom, zoom);

        // 1. Dessiner les liens
        components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .distinct()
                .forEach(Link::drawComponent);

        // 2. Dessiner les composants (Étapes et Transitions)
        components.forEach(Component::drawComponent);

        // 3. Dessiner les poignées d'interaction
        components.forEach(StepAndTransitionCommonBehavior::drawHandle);

        // 4. Dessiner le lien en cours d'étirement à la souris
        if (mouseDragWorldPosition != null && operation == Operation.PRESSED_TO_BOTTOM_LINK) {
            getSelectedComponent().ifPresent(component -> drawDraggedLink(component, mouseDragWorldPosition));
        }

        ctx.restore();
    }

    private void drawInfiniteGrid(double width, double height) {
        double scaledGrid = GRID_SIZE * zoom;
        // Si le niveau de zoom est très faible, on espace les lignes de grille
        while (scaledGrid < 15.0) {
            scaledGrid *= 2.0;
        }

        double startX = (panX % scaledGrid);
        double startY = (panY % scaledGrid);

        ctx.setStroke(Color.rgb(225, 232, 238));
        ctx.setLineWidth(0.8);

        for (double x = startX; x < width; x += scaledGrid) {
            ctx.strokeLine(x, 0, x, height);
        }
        for (double y = startY; y < height; y += scaledGrid) {
            ctx.strokeLine(0, y, width, y);
        }
    }

    private void drawDraggedLink(StepAndTransitionCommonBehavior selectedComponent, Point2D mouseDragWorld) {
        final var begin = new Point2D(selectedComponent.getX() + Component.UNIT_WIDTH / 2, selectedComponent.getY() + Component.UNIT_HEIGHT);
        final var mouse = mouseDragWorld;
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

    // ---------------- Zoom Controls ----------------

    public void zoomIn() {
        setZoomCentered(zoom * 1.25);
    }

    public void zoomOut() {
        setZoomCentered(zoom / 1.25);
    }

    public void resetZoom() {
        setZoomCentered(1.0);
    }

    public void setZoomCentered(double targetZoom) {
        double newZoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, targetZoom));
        double centerX = canvas.getWidth() / 2.0;
        double centerY = canvas.getHeight() / 2.0;

        panX = centerX - (centerX - panX) * (newZoom / zoom);
        panY = centerY - (centerY - panY) * (newZoom / zoom);
        zoom = newZoom;

        if (onZoomChanged != null) {
            onZoomChanged.accept(zoom);
        }
        redraw();
    }

    public void zoomFit() {
        if (components.isEmpty()) {
            panX = 100;
            panY = 60;
            zoom = 1.0;
            redraw();
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (var c : components) {
            minX = Math.min(minX, c.getX());
            minY = Math.min(minY, c.getY());
            maxX = Math.max(maxX, c.getX() + Component.UNIT_WIDTH + 80);
            maxY = Math.max(maxY, c.getY() + Component.UNIT_HEIGHT);
        }

        double pad = 60;
        double graphW = (maxX - minX) + 2 * pad;
        double graphH = (maxY - minY) + 2 * pad;

        double scaleX = canvas.getWidth() / graphW;
        double scaleY = canvas.getHeight() / graphH;
        zoom = Math.min(1.5, Math.max(MIN_ZOOM, Math.min(scaleX, scaleY)));

        panX = (canvas.getWidth() - (maxX + minX) * zoom) / 2.0;
        panY = (canvas.getHeight() - (maxY + minY) * zoom) / 2.0;

        if (onZoomChanged != null) {
            onZoomChanged.accept(zoom);
        }
        redraw();
    }

    public double getZoom() {
        return zoom;
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
        redraw();
    }

    public void setSpacePressed(boolean pressed) {
        this.spacePressed = pressed;
        canvas.setCursor(pressed ? Cursor.OPEN_HAND : Cursor.DEFAULT);
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
        boolean removed = false;
        var selected = getSelectedComponent();
        if (selected.isPresent()) {
            var comp = selected.get();
            new ArrayList<>(comp.getLinksFromParents()).forEach(Link::remove);
            new ArrayList<>(comp.getLinksToChildren()).forEach(Link::remove);
            components.remove(comp);
            fireSelectionChange(Optional.empty());
            removed = true;
        }

        var linkOpt = getSelectedLink();
        if (linkOpt.isPresent()) {
            linkOpt.get().remove();
            removed = true;
        }

        redraw();
        if (removed) {
            notifyStateModified();
        }
    }

    public void toggleSelectedStepInitial() {
        getSelectedComponent().ifPresent(c -> {
            if (c instanceof Step step) {
                step.setInitial(!step.isInitial());
                redraw();
                notifyStateModified();
            }
        });
    }

    public List<StepAndTransitionCommonBehavior> getComponents() {
        return components;
    }

    public void newProject() {
        components.clear();
        Step.resetCounter(0);
        Transition.resetCounter(0);
        panX = 200;
        panY = 80;
        zoom = 1.0;
        createInitialStep();
        fireSelectionChange(Optional.empty());
        redraw();
        notifyStateModified();
    }

    public void createInitialStep() {
        var initStep = new Step(ctx, UUID.randomUUID().toString(), 0, true);
        initStep.setPosition(snap(400, 60));
        components.add(initStep);
        redraw();
    }

    public void createStep(Transition transition) {
        components.forEach(com -> com.setSelected(false));
        var step = new Step(ctx);
        Link.between(ctx, transition, step);
        if (snapToGrid && step.getPosition() != null) {
            step.setPosition(snap(step.getX(), step.getY()));
            transition.getLinksToChildren().forEach(Link::update);
        }
        components.add(step);
        fireSelectionChange(Optional.of(step));
        redraw();
        notifyStateModified();
    }

    public void createTransition(Step step) {
        components.forEach(com -> com.setSelected(false));
        var transition = new Transition(ctx);
        Link.between(ctx, step, transition);
        if (snapToGrid && transition.getPosition() != null) {
            transition.setPosition(snap(transition.getX(), transition.getY()));
            step.getLinksToChildren().forEach(Link::update);
        }
        components.add(transition);
        fireSelectionChange(Optional.of(transition));
        redraw();
        notifyStateModified();
    }

    public void startRender() {
        redraw();
    }

    public void stopRender() {
    }

    public void fireSelectionChange(Optional<StepAndTransitionCommonBehavior> component) {
        if (selectedComponent != null) {
            selectedComponent.accept(component);
        }
    }

    public void setOnSelectionChange(Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent) {
        this.selectedComponent = selectedComponent;
    }

    public void setOnZoomChanged(Consumer<Double> onZoomChanged) {
        this.onZoomChanged = onZoomChanged;
    }

    public void setOnStateModified(Runnable onStateModified) {
        this.onStateModified = onStateModified;
    }

    public void notifyStateModified() {
        if (onStateModified != null) {
            onStateModified.run();
        }
    }

    // ---------------- Serialization Bridge ----------------

    public GrafcetModel toModel(String name, List<Variable> variablesList) {
        GrafcetModel model = new GrafcetModel(name);

        for (Variable v : variablesList) {
            VariableType vt = (v.getType() == Variable.Type.INT) ? VariableType.INT : VariableType.BOOL;
            VariableDirection vd = v.getAddress().toUpperCase().startsWith("%Q") ? VariableDirection.OUTPUT :
                    (v.getAddress().toUpperCase().startsWith("%M") ? VariableDirection.INTERNAL : VariableDirection.INPUT);
            model.getVariables().add(new VariableModel(v.getName(), vt, vd, v.getAddress(), v.getComment()));
        }

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

        int maxStep = 0;
        for (StepModel sm : model.getSteps()) {
            Step s = new Step(ctx, sm.getId(), sm.getNumber(), sm.isInitial());
            s.setPosition(new Point2D(sm.getX(), sm.getY()));
            maxStep = Math.max(maxStep, sm.getNumber());

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

        int maxTrans = 0;
        for (TransitionModel tm : model.getTransitions()) {
            Transition t = new Transition(ctx, tm.getId(), tm.getNumber(), tm.getReceptivity());
            t.setPosition(new Point2D(tm.getX(), tm.getY()));
            maxTrans = Math.max(maxTrans, tm.getNumber());
            components.add(t);
            lookup.put(t.getId(), t);
        }
        Transition.resetCounter(maxTrans);

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

        zoomFit();
        redraw();
    }
}
