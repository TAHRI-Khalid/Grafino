package tk.tahri.grafino.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
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

/**
 * Surface interactive de modélisation graphique SFC / Grafcet (CEI 60848 & CEI 61131-3).
 * Intègre un auto-layout fluide, la grille technique, pan/zoom et l'insertion contextuelle TIA Portal.
 */
public class Grafcet extends Pane {
    private final Canvas canvas = new ResizableCanvas();
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();
    private final AnimationTimer animationTimer;
    private final List<StepAndTransitionCommonBehavior> components = new ArrayList<>();
    private Operation operation = Operation.PRESSED_TO_MOVE;

    // Déplacement et navigation
    private double pressedH = 0;
    private double pressedV = 0;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private Point2D mouseDragPosition;

    // Zoom & Pan
    private double zoomFactor = 1.0;
    private double panX = 0;
    private double panY = 0;

    // Éditeur in-place actif
    private TextField activeInlineEditor = null;

    private Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent;
    private Consumer<String> statusMessageConsumer;

    public Grafcet() {
        this.setStyle("-fx-background-color: #FFFFFF;");
        this.getChildren().add(canvas);

        canvas.widthProperty().bind(this.widthProperty());
        canvas.heightProperty().bind(this.heightProperty());

        this.widthProperty().addListener((obs, oldV, newV) -> redraw());
        this.heightProperty().addListener((obs, oldV, newV) -> redraw());

        this.setFocusTraversable(true);

        // Gestion de la souris
        setupMouseEvents();

        // Gestion des raccourcis clavier
        setupKeyboardEvents();

        // Boucle de rendu
        animationTimer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 40_000_000) {
                    render();
                    lastUpdate = now;
                }
            }
        };
    }

    private void setupMouseEvents() {
        canvas.setOnMousePressed(event -> {
            this.requestFocus();
            closeInlineEditor();

            Point2D diagPt = screenToDiagram(event.getX(), event.getY());

            // Panoramique (Clic milieu ou clic secondaire)
            if (event.getButton() == MouseButton.SECONDARY || event.getButton() == MouseButton.MIDDLE) {
                operation = Operation.PANNING;
                lastMouseX = event.getX();
                lastMouseY = event.getY();
                this.setCursor(Cursor.CLOSED_HAND);
                return;
            }

            // Vérifier si un bouton d'action intelligente (TIA Portal) du composant sélectionné a été cliqué
            var currentSelected = getSelectedComponent();
            if (currentSelected.isPresent()) {
                var comp = currentSelected.get();
                if (comp.isQuickAddNextPressed(diagPt)) {
                    if (comp instanceof Step step) {
                        createTransition(step);
                    } else if (comp instanceof Transition trans) {
                        createStep(trans);
                    }
                    return;
                }
                if (comp.isQuickAddBranchPressed(diagPt)) {
                    if (comp instanceof Step step) {
                        createOrBranch(step);
                    } else if (comp instanceof Transition trans) {
                        createAndBranch(trans);
                    }
                    return;
                }
                if (comp.isQuickDeletePressed(diagPt)) {
                    deleteSelected();
                    return;
                }
            }

            // Désélection générale
            components.forEach(com -> com.setSelected(false));
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .forEach(link -> link.setSelected(false));

            // Détection du composant cliqué
            components.stream()
                    .filter(com -> com.isPointOnTheComponent(diagPt))
                    .findFirst()
                    .ifPresentOrElse(component -> {
                        component.setSelected(true);
                        fireSelectionChange(Optional.of(component));

                        if (component.isBottomHandlePressed(diagPt)) {
                            operation = Operation.PRESSED_TO_BOTTOM_LINK;
                        } else {
                            operation = Operation.PRESSED_TO_MOVE;
                        }
                        pressedH = diagPt.getX() - component.getX();
                        pressedV = diagPt.getY() - component.getY();
                    }, () -> fireSelectionChange(Optional.empty()));

            // Détection du lien cliqué
            if (getSelectedComponent().isEmpty()) {
                components.stream()
                        .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                        .flatMap(List::stream)
                        .filter(link -> link.isPointOnTheComponent(diagPt))
                        .findFirst()
                        .ifPresent(link -> link.setSelected(true));
            }

            // Double-clic pour édition in-place directe
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                getSelectedComponent().ifPresent(comp -> {
                    if (comp instanceof Transition trans) {
                        openInlineReceptivityEditor(trans);
                    } else if (comp instanceof Step step) {
                        openInlineStepEditor(step);
                    }
                });
            }
        });

        canvas.setOnMouseDragged(event -> {
            if (operation == Operation.PANNING) {
                double dx = event.getX() - lastMouseX;
                double dy = event.getY() - lastMouseY;
                panX += dx;
                panY += dy;
                lastMouseX = event.getX();
                lastMouseY = event.getY();
                return;
            }

            Point2D diagPt = screenToDiagram(event.getX(), event.getY());

            if (getSelectedComponent().isPresent() && event.isPrimaryButtonDown()) {
                var component = getSelectedComponent().get();
                switch (operation) {
                    case PRESSED_TO_MOVE -> {
                        var position = new Point2D(diagPt.getX() - pressedH, diagPt.getY() - pressedV);
                        component.updatePositionAndLinks(position);
                        mouseDragPosition = null;
                    }
                    case PRESSED_TO_BOTTOM_LINK -> {
                        mouseDragPosition = diagPt;
                        Predicate<Component> predicate;
                        if (component instanceof Step) {
                            predicate = com -> com instanceof Transition;
                        } else {
                            predicate = com -> com instanceof Step;
                        }
                        components.stream()
                                .filter(com -> com.isPointOnTheComponent(diagPt))
                                .filter(predicate)
                                .findFirst()
                                .ifPresent(com -> com.setHandle(Handle.TOP));
                    }
                    default -> {}
                }
            }

            getSelectedLink().ifPresent(link -> link.updateSelectedLink(diagPt));
        });

        canvas.setOnMouseReleased(event -> {
            if (operation == Operation.PANNING) {
                this.setCursor(Cursor.DEFAULT);
                operation = Operation.PRESSED_TO_MOVE;
                return;
            }

            Point2D diagPt = screenToDiagram(event.getX(), event.getY());

            if (operation == Operation.PRESSED_TO_BOTTOM_LINK) {
                getSelectedComponent().ifPresent(source -> components.stream()
                        .filter(component -> component.isPointOnTheComponent(diagPt))
                        .findFirst()
                        .ifPresent(target -> {
                            if (source != target) {
                                // Raccordement normalisé avec vérification d'alternance CEI 60848
                                if ((source instanceof Transition && target instanceof Step) ||
                                    (source instanceof Step && target instanceof Transition)) {
                                    Link.between(ctx, source, target);
                                    notifyStatus("Liaison créée entre " + getElementName(source) + " et " + getElementName(target));
                                } else {
                                    notifyStatus("⚠ Violation d'alternance CEI 60848 : une étape ne peut être reliée qu'à une transition.");
                                }
                            }
                        }));
            }

            mouseDragPosition = null;
            operation = Operation.PRESSED_TO_MOVE;
        });

        canvas.setOnMouseMoved(event -> {
            Point2D diagPt = screenToDiagram(event.getX(), event.getY());
            components.forEach(component -> component.setHandle(Handle.NONE));

            components.stream()
                    .filter(component -> component.isPointOnTheComponent(diagPt))
                    .findFirst()
                    .ifPresent(component -> component.setHandle(Handle.BOTH));
        });

        // Zoom à la molette centré sur la souris
        canvas.setOnScroll(event -> {
            if (event.isControlDown()) {
                double factor = event.getDeltaY() > 0 ? 1.12 : 0.89;
                zoomAround(factor, event.getX(), event.getY());
                event.consume();
            } else {
                panY += event.getDeltaY() * 0.8;
                panX += event.getDeltaX() * 0.8;
                event.consume();
            }
        });
    }

    private void setupKeyboardEvents() {
        this.setOnKeyPressed(event -> {
            if (activeInlineEditor != null) {
                return;
            }

            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                deleteSelected();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                components.forEach(c -> c.setSelected(false));
                fireSelectionChange(Optional.empty());
                redraw();
                event.consume();
            } else if (event.getCode() == KeyCode.T || event.getCode() == KeyCode.ENTER) {
                getSelectedComponent().ifPresent(comp -> {
                    if (comp instanceof Step step) {
                        createTransition(step);
                    } else if (comp instanceof Transition trans) {
                        createStep(trans);
                    }
                });
                event.consume();
            } else if (event.getCode() == KeyCode.S) {
                getSelectedComponent().ifPresent(comp -> {
                    if (comp instanceof Transition trans) {
                        createStep(trans);
                    }
                });
                event.consume();
            } else if (event.getCode() == KeyCode.B) {
                getSelectedComponent().ifPresent(comp -> {
                    if (comp instanceof Step step) {
                        createOrBranch(step);
                    } else if (comp instanceof Transition trans) {
                        createAndBranch(trans);
                    }
                });
                event.consume();
            }
        });
    }

    private String getElementName(StepAndTransitionCommonBehavior comp) {
        if (comp instanceof Step s) {
            return "Étape S" + s.getNumber();
        } else if (comp instanceof Transition t) {
            return "Transition T" + t.getNumber();
        }
        return "Élément";
    }

    // ==========================================
    // MÉTHODES DE RENDU GRAPHIQUE ET ZOOM
    // ==========================================

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        ctx.save();
        ctx.setFill(Color.WHITE);
        ctx.fillRect(0, 0, w, h);

        // Grille d'ingénierie TIA Portal
        drawBackgroundGrid();

        // Application du Pan & Zoom
        ctx.translate(panX, panY);
        ctx.scale(zoomFactor, zoomFactor);

        // Tracé des liaisons en premier (en dessous)
        components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .forEach(Link::drawComponent);

        // Tracé des composants
        components.forEach(Component::drawComponent);

        // Tracé des poignées et boutons contextuels TIA Portal
        components.forEach(StepAndTransitionCommonBehavior::drawHandle);

        // Tracé du lien dynamique en cours d'étirement
        if (mouseDragPosition != null && operation == Operation.PRESSED_TO_BOTTOM_LINK) {
            getSelectedComponent().ifPresent(comp -> drawDraggedLinkPreview(comp, mouseDragPosition));
        }

        ctx.restore();
    }

    private void drawBackgroundGrid() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double gridSize = 24.0 * zoomFactor;
        if (gridSize < 10) return;

        ctx.setFill(Color.rgb(215, 222, 228, 0.55));
        double startX = panX % gridSize;
        while (startX < 0) startX += gridSize;
        double startY = panY % gridSize;
        while (startY < 0) startY += gridSize;

        for (double x = startX; x < w; x += gridSize) {
            for (double y = startY; y < h; y += gridSize) {
                ctx.fillOval(x - 1, y - 1, 2, 2);
            }
        }
    }

    private void drawDraggedLinkPreview(StepAndTransitionCommonBehavior source, Point2D mouse) {
        final var begin = new Point2D(source.getX() + Component.UNIT_WIDTH / 2, source.getY() + Component.UNIT_HEIGHT);

        // Vérification de la compatibilité sous le curseur
        boolean validTargetFound = false;
        boolean invalidAlternance = false;

        for (StepAndTransitionCommonBehavior target : components) {
            if (target != source && target.isPointOnTheComponent(mouse)) {
                if ((source instanceof Step && target instanceof Transition) ||
                    (source instanceof Transition && target instanceof Step)) {
                    validTargetFound = true;
                } else {
                    invalidAlternance = true;
                }
                break;
            }
        }

        if (validTargetFound) {
            ctx.setStroke(Color.rgb(16, 185, 129)); // Vert émeraude
            ctx.setLineWidth(3.0);
        } else if (invalidAlternance) {
            ctx.setStroke(Color.rgb(217, 56, 30)); // Rouge interdiction
            ctx.setLineWidth(3.0);
        } else {
            ctx.setStroke(Color.rgb(0, 129, 132)); // Arduino Teal
            ctx.setLineWidth(2.0);
        }

        if (mouse.getY() < begin.getY()) {
            double gutterX = Math.min(begin.getX(), mouse.getX()) - Component.UNIT_WIDTH * 0.9;
            ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), begin.getY() + 15);
            ctx.strokeLine(begin.getX(), begin.getY() + 15, gutterX, begin.getY() + 15);
            ctx.strokeLine(gutterX, begin.getY() + 15, gutterX, mouse.getY());
            ctx.strokeLine(gutterX, mouse.getY(), mouse.getX(), mouse.getY());
        } else {
            double midY = (begin.getY() + mouse.getY()) / 2;
            ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), midY);
            ctx.strokeLine(begin.getX(), midY, mouse.getX(), midY);
            ctx.strokeLine(mouse.getX(), midY, mouse.getX(), mouse.getY());
        }
    }

    public Point2D screenToDiagram(double screenX, double screenY) {
        return new Point2D((screenX - panX) / zoomFactor, (screenY - panY) / zoomFactor);
    }

    public Point2D diagramToScreen(double diagX, double diagY) {
        return new Point2D(diagX * zoomFactor + panX, diagY * zoomFactor + panY);
    }

    public void zoomAround(double factor, double pivotX, double pivotY) {
        double newZoom = zoomFactor * factor;
        if (newZoom < 0.35 || newZoom > 3.0) return;

        panX = pivotX - (pivotX - panX) * (newZoom / zoomFactor);
        panY = pivotY - (pivotY - panY) * (newZoom / zoomFactor);
        zoomFactor = newZoom;
        redraw();
    }

    public void zoomIn() {
        zoomAround(1.18, getWidth() / 2, getHeight() / 2);
    }

    public void zoomOut() {
        zoomAround(1 / 1.18, getWidth() / 2, getHeight() / 2);
    }

    public void resetZoom() {
        this.zoomFactor = 1.0;
        this.panX = 0;
        this.panY = 0;
        redraw();
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    // ==========================================
    // CRÉATION & INSERTION CONTEXTUELLE TIA PORTAL
    // ==========================================

    public void createInitialStep() {
        double initX = (canvas.getWidth() > 0) ? (canvas.getWidth() / 2 - Component.UNIT_WIDTH / 2) : 400;
        double initY = 80;
        var initStep = new InitStep(ctx, new Point2D(initX, initY));
        components.add(initStep);
        fireSelectionChange(Optional.of(initStep));
        notifyStatus("Étape initiale créée.");
        redraw();
    }

    public void createStep(Transition transition) {
        components.forEach(com -> com.setSelected(false));
        var step = new Step(ctx);

        double targetX = transition.getX();
        double targetY = transition.getY() + Component.UNIT_HEIGHT + 40;

        // Si la transition a déjà des enfants (divergence ET)
        if (!transition.getLinksToChildren().isEmpty()) {
            double maxX = transition.getLinksToChildren().stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(transition.getX());
            targetX = maxX + 2.2 * Component.UNIT_WIDTH;
        }

        step.position = new Point2D(targetX, targetY);
        Link.between(ctx, transition, step);
        components.add(step);
        fireSelectionChange(Optional.of(step));
        notifyStatus("Étape S" + step.getNumber() + " créée après la transition T" + transition.getNumber());
        redraw();
    }

    public void createTransition(Step step) {
        components.forEach(com -> com.setSelected(false));
        var transition = new Transition(ctx);

        double targetX = step.getX();
        double targetY = step.getY() + Component.UNIT_HEIGHT + 40;

        // Si l'étape a déjà des enfants (divergence OU)
        if (!step.getLinksToChildren().isEmpty()) {
            double maxX = step.getLinksToChildren().stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(step.getX());
            targetX = maxX + 2.2 * Component.UNIT_WIDTH;
        }

        transition.position = new Point2D(targetX, targetY);
        Link.between(ctx, step, transition);
        components.add(transition);
        fireSelectionChange(Optional.of(transition));
        notifyStatus("Transition T" + transition.getNumber() + " créée après l'étape S" + step.getNumber());
        redraw();
    }

    public void createOrBranch(Step step) {
        createTransition(step);
        notifyStatus("Branche OU créée (Sélection de séquence)");
    }

    public void createAndBranch(Transition transition) {
        createStep(transition);
        notifyStatus("Branche ET créée (Synchronisation / Parallélisme)");
    }

    public void deleteSelected() {
        getSelectedComponent().ifPresent(this::deleteComponent);
        getSelectedLink().ifPresent(link -> {
            link.disconnect();
            notifyStatus("Liaison supprimée.");
            redraw();
        });
    }

    public void deleteComponent(StepAndTransitionCommonBehavior comp) {
        List<Link> toRemove = new ArrayList<>();
        toRemove.addAll(comp.getLinksFromParents());
        toRemove.addAll(comp.getLinksToChildren());
        toRemove.forEach(Link::disconnect);

        components.remove(comp);
        fireSelectionChange(Optional.empty());
        notifyStatus("Élément " + getElementName(comp) + " supprimé.");
        redraw();
    }

    // ==========================================
    // ÉDITION IN-PLACE DIRECTE SUR LE CANVAS
    // ==========================================

    private void openInlineReceptivityEditor(Transition transition) {
        closeInlineEditor();

        double diagX = transition.getX() + Component.UNIT_WIDTH * 3 / 4 + 8;
        double diagY = transition.getY() + Component.UNIT_HEIGHT / 2 - 13;
        Point2D screenPt = diagramToScreen(diagX, diagY);

        TextField editor = new TextField(transition.getReceptivity());
        editor.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: #008184; -fx-border-width: 1.5; -fx-background-color: #FFFFFF; -fx-padding: 2 6 2 6;");
        editor.setLayoutX(screenPt.getX());
        editor.setLayoutY(screenPt.getY());
        editor.setPrefWidth(160 * zoomFactor);

        this.activeInlineEditor = editor;

        Runnable commit = () -> {
            transition.setReceptivity(editor.getText());
            closeInlineEditor();
            notifyStatus("Réceptivité de T" + transition.getNumber() + " mise à jour : " + transition.getReceptivity());
            redraw();
        };

        editor.setOnAction(e -> commit.run());
        editor.focusedProperty().addListener((obs, oldV, focused) -> {
            if (!focused) commit.run();
        });

        this.getChildren().add(editor);
        editor.requestFocus();
        editor.selectAll();
    }

    private void openInlineStepEditor(Step step) {
        closeInlineEditor();

        double diagX = step.getX() + 8;
        double diagY = step.getY() + Component.UNIT_HEIGHT / 2 - 13;
        Point2D screenPt = diagramToScreen(diagX, diagY);

        TextField editor = new TextField(String.valueOf(step.getNumber()));
        editor.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: #008184; -fx-border-width: 1.5; -fx-background-color: #FFFFFF; -fx-alignment: center; -fx-padding: 2 4 2 4;");
        editor.setLayoutX(screenPt.getX());
        editor.setLayoutY(screenPt.getY());
        editor.setPrefWidth(Component.UNIT_WIDTH * zoomFactor - 16);

        this.activeInlineEditor = editor;

        Runnable commit = () -> {
            try {
                int num = Integer.parseInt(editor.getText().trim());
                step.setNumber(num);
                notifyStatus("Numéro d'étape modifié : S" + num);
            } catch (NumberFormatException ignored) {}
            closeInlineEditor();
            redraw();
        };

        editor.setOnAction(e -> commit.run());
        editor.focusedProperty().addListener((obs, oldV, focused) -> {
            if (!focused) commit.run();
        });

        this.getChildren().add(editor);
        editor.requestFocus();
        editor.selectAll();
    }

    private void closeInlineEditor() {
        if (activeInlineEditor != null) {
            this.getChildren().remove(activeInlineEditor);
            activeInlineEditor = null;
        }
    }

    private void notifyStatus(String msg) {
        if (statusMessageConsumer != null) {
            statusMessageConsumer.accept(msg);
        }
    }

    public void setStatusMessageConsumer(Consumer<String> consumer) {
        this.statusMessageConsumer = consumer;
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

    public List<StepAndTransitionCommonBehavior> getComponents() {
        return components;
    }

    public void startRender() {
        this.animationTimer.start();
    }

    public void stopRender() {
        this.animationTimer.stop();
    }

    public void redraw() {
        render();
    }

    public void fireSelectionChange(Optional<StepAndTransitionCommonBehavior> component) {
        if (selectedComponent != null) {
            selectedComponent.accept(component);
        }
    }

    public void setOnSelectionChange(Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent) {
        this.selectedComponent = selectedComponent;
    }

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
