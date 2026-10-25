package tk.tahri.grafino.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tk.tahri.grafino.Handle;
import tk.tahri.grafino.Operation;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Surface interactive de modélisation graphique SFC / Grafcet (CEI 60848 & CEI 61131-3).
 * Intègre un auto-layout fluide, la grille technique, pan/zoom, le magnétisme intelligent
 * (Smart Snap & Guides d'alignement) et l'insertion contextuelle TIA Portal.
 */
public class Grafcet extends Pane {
    public record AlignmentGuide(double x1, double y1, double x2, double y2, boolean isVertical) {}

    private final Canvas canvas;
    private final GraphicsContext ctx;
    private final AnimationTimer animationTimer;
    private final List<StepAndTransitionCommonBehavior> components = new ArrayList<>();
    private Operation operation = Operation.PRESSED_TO_MOVE;

    // Déplacement et navigation
    private double pressedH = 0;
    private double pressedV = 0;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private Point2D mouseDragPosition;

    // Lignes de guidage magnétique actives
    private final List<AlignmentGuide> activeGuideLines = new ArrayList<>();

    // Zoom & Pan
    private double zoomFactor = 1.0;
    private double panX = 0;
    private double panY = 0;

    // Éditeur in-place actif
    private TextField activeInlineEditor = null;

    private Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent;
    private Consumer<String> statusMessageConsumer;
    private Runnable onStructureChanged;

    public void setOnStructureChanged(Runnable onStructureChanged) {
        this.onStructureChanged = onStructureChanged;
    }

    private void notifyStructureChanged() {
        if (onStructureChanged != null) {
            onStructureChanged.run();
        }
    }

    public Grafcet() {
        this(true);
    }

    public Grafcet(boolean initializeCanvas) {
        this.setStyle("-fx-background-color: #FFFFFF;");

        if (initializeCanvas) {
            this.canvas = new ResizableCanvas();
            this.ctx = canvas.getGraphicsContext2D();
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
        } else {
            this.canvas = null;
            this.ctx = null;
            this.animationTimer = null;
        }
    }

    private void setupMouseEvents() {
        canvas.setOnMousePressed(event -> {
            this.requestFocus();
            closeInlineEditor();
            activeGuideLines.clear();

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
                        double rawX = diagPt.getX() - pressedH;
                        double rawY = diagPt.getY() - pressedV;
                        var position = computeSmartSnap(component, rawX, rawY);
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
            activeGuideLines.clear();

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
                                    notifyStructureChanged();
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

            if (event.isControlDown() && event.getCode() == KeyCode.L) {
                autoLayout();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
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

    /**
     * Calcule le magnétisme intelligent (Smart Snap) d'un composant en déplacement :
     * 1. Aimantation sur l'axe central X de ses parents et enfants directs.
     * 2. Aimantation sur l'axe horizontal Y des étapes/transitions sœurs.
     * 3. Alignement sur grille fine de 12px si aucune aimantation relative n'est détectée.
     */
    public Point2D computeSmartSnap(StepAndTransitionCommonBehavior draggedComp, double rawX, double rawY) {
        activeGuideLines.clear();

        double snappedX = rawX;
        double snappedY = rawY;
        double snapTolerance = 14.0;

        double draggedCenterX = rawX + Component.UNIT_WIDTH / 2;
        boolean snappedXDone = false;
        boolean snappedYDone = false;

        // 1. Aimantation verticale sur l'axe X (Parents, enfants puis autres composants)
        List<StepAndTransitionCommonBehavior> xCandidates = new ArrayList<>();
        draggedComp.getLinksFromParents().forEach(l -> xCandidates.add(l.getParentComponent()));
        draggedComp.getLinksToChildren().forEach(l -> xCandidates.add(l.getChildComponent()));
        components.stream().filter(c -> c != draggedComp && !xCandidates.contains(c)).forEach(xCandidates::add);

        for (var ref : xCandidates) {
            double refCenterX = ref.getX() + Component.UNIT_WIDTH / 2;
            if (Math.abs(draggedCenterX - refCenterX) <= snapTolerance) {
                snappedX = refCenterX - Component.UNIT_WIDTH / 2;
                snappedXDone = true;

                double minY = Math.min(rawY, ref.getY()) - 25;
                double maxY = Math.max(rawY + Component.UNIT_HEIGHT, ref.getY() + Component.UNIT_HEIGHT) + 25;
                activeGuideLines.add(new AlignmentGuide(refCenterX, minY, refCenterX, maxY, true));
                break;
            }
        }

        // 2. Aimantation horizontale sur l'axe Y (Branches parallèles / composants au même niveau)
        for (var ref : components) {
            if (ref == draggedComp) continue;

            if (Math.abs(rawY - ref.getY()) <= snapTolerance) {
                snappedY = ref.getY();
                snappedYDone = true;

                double minX = Math.min(snappedX, ref.getX()) - 25;
                double maxX = Math.max(snappedX + Component.UNIT_WIDTH, ref.getX() + Component.UNIT_WIDTH) + 25;
                double guideY = ref.getY() + Component.UNIT_HEIGHT / 2;
                activeGuideLines.add(new AlignmentGuide(minX, guideY, maxX, guideY, false));
                break;
            }
        }

        // 3. Calage sur grille fine de 12px si aucun composant proche n'est aimanté
        if (!snappedXDone) {
            double gridStep = 12.0;
            snappedX = Math.round(snappedX / gridStep) * gridStep;
        }
        if (!snappedYDone) {
            double gridStep = 12.0;
            snappedY = Math.round(snappedY / gridStep) * gridStep;
        }

        return new Point2D(snappedX, snappedY);
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
    // AUTO-LAYOUT / ALIGNEMENT CANONIQUE CEI 60848
    // ==========================================

    /**
     * Réaligne automatiquement l'ensemble du Grafcet de manière hiérarchique et symétrique :
     * - Organisation par couches descendantes successives.
     * - Centrage horizontal des chaînes et répartition équitable des branches parallèles.
     * - Mise à jour propre de l'ensemble des liaisons.
     */
    public void autoLayout() {
        if (components.isEmpty()) return;

        // Trouver les étapes initiales ou sans parents comme racines
        var roots = components.stream()
                .filter(c -> c.getLinksFromParents().isEmpty() || (c instanceof Step s && s.isInitial()))
                .toList();

        if (roots.isEmpty()) {
            roots = List.of(components.getFirst());
        }

        // Parcours en largeur pour affecter un niveau hiérarchique à chaque composant
        Map<StepAndTransitionCommonBehavior, Integer> levels = new HashMap<>();
        Queue<StepAndTransitionCommonBehavior> queue = new ArrayDeque<>();

        for (var root : roots) {
            levels.put(root, 0);
            queue.add(root);
        }

        Set<StepAndTransitionCommonBehavior> visited = new HashSet<>(roots);

        while (!queue.isEmpty()) {
            var current = queue.poll();
            int currentLevel = levels.get(current);

            for (Link link : current.getLinksToChildren()) {
                var child = link.getChildComponent();
                if (!roots.contains(child)) {
                    int existingLevel = levels.getOrDefault(child, -1);
                    if (existingLevel < currentLevel + 1) {
                        levels.put(child, currentLevel + 1);
                        if (!visited.contains(child)) {
                            visited.add(child);
                            queue.add(child);
                        }
                    }
                }
            }
        }

        // Affecter le niveau 0 pour les éléments non raccordés
        for (var comp : components) {
            levels.putIfAbsent(comp, 0);
        }

        // Regrouper par niveau
        Map<Integer, List<StepAndTransitionCommonBehavior>> byLevel = new TreeMap<>();
        levels.forEach((comp, lvl) -> byLevel.computeIfAbsent(lvl, k -> new ArrayList<>()).add(comp));

        double centerX = (canvas != null && canvas.getWidth() > 0) ? canvas.getWidth() / 2 - Component.UNIT_WIDTH / 2 : 400;
        double startY = 80;
        double verticalSpacing = 85;
        double horizontalSpacing = 180;

        byLevel.forEach((lvl, compsInLevel) -> {
            double y = startY + lvl * verticalSpacing;
            int count = compsInLevel.size();
            double totalWidth = (count - 1) * horizontalSpacing;
            double startX = centerX - totalWidth / 2;

            for (int i = 0; i < count; i++) {
                var comp = compsInLevel.get(i);
                double x = startX + i * horizontalSpacing;
                comp.updatePositionAndLinks(new Point2D(x, y));
            }
        });

        // Mettre à jour l'ensemble des liaisons
        components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .forEach(Link::update);

        notifyStatus("Schéma réaligné automatiquement selon les normes CEI 60848.");
        redraw();
    }

    // ==========================================
    // MÉTHODES DE RENDU GRAPHIQUE ET ZOOM
    // ==========================================

    private void render() {
        if (canvas == null || ctx == null) return;
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

        // Tracé des lignes de guidage magnétique temporaires
        if (!activeGuideLines.isEmpty()) {
            ctx.setStroke(Color.rgb(0, 151, 157, 0.85)); // Vibrant Teal
            ctx.setLineWidth(1.6);
            ctx.setLineDashes(4.0, 4.0);
            for (var guide : activeGuideLines) {
                ctx.strokeLine(guide.x1(), guide.y1(), guide.x2(), guide.y2());
            }
            ctx.setLineDashes(null);
        }

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
        if (canvas == null || ctx == null) return;
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
            double gutterX = (begin.getX() > mouse.getX() + 20)
                    ? Math.max(begin.getX(), mouse.getX()) + Component.UNIT_WIDTH * 0.9
                    : Math.min(begin.getX(), mouse.getX()) - Component.UNIT_WIDTH * 0.9;
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
        double initX = (canvas != null && canvas.getWidth() > 0) ? (canvas.getWidth() / 2 - Component.UNIT_WIDTH / 2) : 400;
        double initY = 80;
        var initStep = new InitStep(ctx, new Point2D(initX, initY));
        components.add(initStep);
        fireSelectionChange(Optional.of(initStep));
        notifyStatus("Étape initiale créée.");
        notifyStructureChanged();
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
        notifyStructureChanged();
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
        notifyStructureChanged();
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
            notifyStructureChanged();
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
        notifyStructureChanged();
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
            notifyStructureChanged();
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
                notifyStructureChanged();
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
        if (this.animationTimer != null) {
            this.animationTimer.start();
        }
    }

    public void stopRender() {
        if (this.animationTimer != null) {
            this.animationTimer.stop();
        }
    }

    public void redraw() {
        render();
    }

    public void fireSelectionChange(Optional<StepAndTransitionCommonBehavior> component) {
        if (selectedComponent != null) {
            selectedComponent.accept(component);
        }
    }

    /**
     * Met à jour les états visuels d'activation des étapes, des sorties et des transitions lors de la simulation.
     */
    public void updateSimulationVisuals(Set<String> activeStepIds, Set<String> activeOutputs) {
        updateSimulationVisuals(activeStepIds, activeOutputs, Set.of(), Set.of(), Set.of());
    }

    public void updateSimulationVisuals(Set<String> activeStepIds, Set<String> activeOutputs,
                                        Set<String> validatedTransitions, Set<String> trueReceptivityTransitions,
                                        Set<String> fireableTransitions) {
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step step) {
                String id1 = "s" + step.getNumber();
                String id2 = String.valueOf(step.getNumber());
                boolean isActive = activeStepIds != null && (activeStepIds.contains(id1) || activeStepIds.contains(id2));
                step.setActive(isActive);
                step.setActiveOutputs(activeOutputs);
            } else if (comp instanceof Transition trans) {
                String id1 = "t" + trans.getNumber();
                String id2 = String.valueOf(trans.getNumber());
                boolean isValidated = validatedTransitions != null && (validatedTransitions.contains(id1) || validatedTransitions.contains(id2));
                boolean isTrue = trueReceptivityTransitions != null && (trueReceptivityTransitions.contains(id1) || trueReceptivityTransitions.contains(id2));
                boolean isFireable = fireableTransitions != null && (fireableTransitions.contains(id1) || fireableTransitions.contains(id2));
                trans.setSimulationState(true, isValidated, isTrue, isFireable);
            }
        }
        redraw();
    }

    /**
     * Réinitialise tous les indicateurs visuels de simulation sur le canvas.
     */
    public void clearSimulationVisuals() {
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step step) {
                step.setActive(false);
                step.setActiveOutputs(Set.of());
            } else if (comp instanceof Transition trans) {
                trans.clearSimulationState();
            }
        }
        redraw();
    }

    /**
     * Sélectionne et met en surbrillance un composant à partir d'une référence (ex: "s1", "t2", "Step1").
     */
    public boolean selectComponentByReference(String ref) {
        if (ref == null || ref.isBlank()) return false;
        String clean = ref.trim().toLowerCase(Locale.ROOT);
        String digits = clean.replaceAll("[^0-9]", "");
        int targetNum = digits.isEmpty() ? -1 : Integer.parseInt(digits);

        for (StepAndTransitionCommonBehavior comp : components) {
            boolean match = false;
            if (comp instanceof Step s) {
                if (clean.startsWith("s") && (s.getNumber() == targetNum || ("s" + s.getNumber()).equalsIgnoreCase(clean) || ("step" + s.getNumber()).equalsIgnoreCase(clean))) {
                    match = true;
                }
            } else if (comp instanceof Transition t) {
                if (clean.startsWith("t") && (t.getNumber() == targetNum || ("t" + t.getNumber()).equalsIgnoreCase(clean))) {
                    match = true;
                }
            }
            if (match) {
                components.forEach(c -> c.setSelected(false));
                comp.setSelected(true);
                fireSelectionChange(Optional.of(comp));
                redraw();
                return true;
            }
        }
        return false;
    }

    public void setOnSelectionChange(Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent) {
        this.selectedComponent = selectedComponent;
    }

    // ==========================================
    // PERSISTANCE & SÉCURISATION DU WORKFLOW (AXE 1)
    // ==========================================

    /**
     * Indique si le diagramme est vierge ou dans son état initial minimal.
     */
    public boolean isDiagramEmpty() {
        if (components.isEmpty()) return true;
        if (components.size() == 1 && components.get(0) instanceof Step s) {
            return s.isInitial() && s.getActions().isEmpty() && s.getLinksToChildren().isEmpty();
        }
        return false;
    }

    /**
     * Réinitialise complètement le diagramme à une étape initiale unique.
     */
    public void clearDiagram() {
        components.clear();
        activeGuideLines.clear();
        closeInlineEditor();
        Step.resetCounter(0);
        Transition.resetCounter(0);
        panX = 0;
        panY = 0;
        zoomFactor = 1.0;
        createInitialStep();
        fireSelectionChange(Optional.empty());
        notifyStructureChanged();
        redraw();
    }

    /**
     * Convertit l'état graphique actuel du Grafcet en un programme SFC immuable normalisé (CEI 61131-3).
     */
    public SfcProgram toProgram(String programName, List<Variable> declaredVariables) {
        String name = (programName == null || programName.isBlank()) ? "Main_SFC" : programName.trim();

        // 1. Conversion des variables IEC
        List<IecVariable> iecVars = new ArrayList<>();
        if (declaredVariables != null) {
            for (Variable v : declaredVariables) {
                iecVars.add(v.toIecVariable());
            }
        }

        // 2. Conversion des étapes et de leurs actions
        List<SfcStep> sfcSteps = new ArrayList<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step step) {
                String stepId = "s" + step.getNumber();
                String stepName = "Step" + step.getNumber();
                List<SfcActionBlock> actions = new ArrayList<>();
                for (Action a : step.getActions()) {
                    SfcQualifier qual = SfcQualifier.N;
                    if (a.qualifier() != null) {
                        try {
                            qual = SfcQualifier.valueOf(a.qualifier().trim().toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {}
                    }
                    String varName = a.getDisplayText();
                    if (varName == null || varName.isBlank()) {
                        varName = "ACTION";
                    }
                    Duration duration = null;
                    String inlineCondition = null;
                    if (qual.isTimed()) {
                        duration = parseDuration(a.parameter());
                    } else if (a.hasParameter()) {
                        inlineCondition = a.parameter();
                    }
                    actions.add(new SfcActionBlock(qual, varName, duration, inlineCondition));
                }
                sfcSteps.add(new SfcStep(stepId, stepName, step.getNumber(), step.isInitial(), step.getX(), step.getY(), actions));
            }
        }

        // 3. Conversion des transitions
        List<SfcTransition> sfcTransitions = new ArrayList<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Transition trans) {
                String transId = "t" + trans.getNumber();
                String transName = "T" + trans.getNumber();
                sfcTransitions.add(new SfcTransition(transId, transName, trans.getNumber(), trans.getReceptivity(), trans.getX(), trans.getY()));
            }
        }

        // 4. Conversion des liaisons
        List<SfcLink> sfcLinks = new ArrayList<>();
        Set<Link> uniqueLinks = new LinkedHashSet<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            uniqueLinks.addAll(comp.getLinksToChildren());
        }

        for (Link link : uniqueLinks) {
            String srcId = (link.getParentComponent() instanceof Step s) ? ("s" + s.getNumber()) : ("t" + link.getParentComponent().getNumber());
            String tgtId = (link.getChildComponent() instanceof Step s) ? ("s" + s.getNumber()) : ("t" + link.getChildComponent().getNumber());
            String linkId = "l_" + srcId + "_" + tgtId;
            List<SfcPoint> points = new ArrayList<>();
            if (link.isAscending()) {
                for (Point2D pt : link.getPath()) {
                    points.add(new SfcPoint(pt.getX(), pt.getY()));
                }
            }
            sfcLinks.add(new SfcLink(linkId, srcId, tgtId, points));
        }

        return new SfcProgram(name, iecVars, sfcSteps, sfcTransitions, List.of(), sfcLinks);
    }

    /**
     * Charge et reconstruit fidèlement un diagramme à partir d'un programme SFC normalisé.
     */
    public void loadFromProgram(SfcProgram program, List<Variable> declaredVariables) {
        if (program == null) return;

        components.clear();
        activeGuideLines.clear();
        closeInlineEditor();
        panX = 0;
        panY = 0;
        zoomFactor = 1.0;

        Map<String, StepAndTransitionCommonBehavior> idMap = new HashMap<>();
        Map<Integer, Step> stepByNumber = new HashMap<>();
        Map<Integer, Transition> transByNumber = new HashMap<>();

        // 1. Recréation des étapes
        for (SfcStep sfcStep : program.steps()) {
            Step step = new Step(ctx);
            step.setInitial(sfcStep.isInitial());
            step.setNumber(sfcStep.number());
            step.position = new Point2D(sfcStep.x(), sfcStep.y());

            for (SfcActionBlock ab : sfcStep.actionBlocks()) {
                String q = ab.qualifier().name();
                String varName = ab.variableName();
                Variable matchedVar = null;
                if (declaredVariables != null) {
                    matchedVar = declaredVariables.stream()
                            .filter(v -> v.getName().equalsIgnoreCase(varName))
                            .findFirst().orElse(null);
                }
                String param = (ab.duration() != null) ? ab.formattedDuration() : ab.inlineCondition();
                step.getActions().add(new Action(q, matchedVar, param, (matchedVar == null) ? varName : null));
            }

            components.add(step);
            idMap.put(sfcStep.id(), step);
            idMap.put("s" + sfcStep.number(), step);
            stepByNumber.put(sfcStep.number(), step);
        }

        // 2. Recréation des transitions
        for (SfcTransition sfcTrans : program.transitions()) {
            Transition trans = new Transition(ctx);
            trans.setNumber(sfcTrans.number());
            trans.setReceptivity(sfcTrans.conditionST());
            trans.position = new Point2D(sfcTrans.x(), sfcTrans.y());

            components.add(trans);
            idMap.put(sfcTrans.id(), trans);
            idMap.put("t" + sfcTrans.number(), trans);
            transByNumber.put(sfcTrans.number(), trans);
        }

        // 3. Recréation des liaisons
        for (SfcLink sfcLink : program.links()) {
            StepAndTransitionCommonBehavior src = idMap.get(sfcLink.sourceId());
            if (src == null) {
                src = resolveComponentByReference(sfcLink.sourceId(), stepByNumber, transByNumber);
            }
            StepAndTransitionCommonBehavior tgt = idMap.get(sfcLink.targetId());
            if (tgt == null) {
                tgt = resolveComponentByReference(sfcLink.targetId(), stepByNumber, transByNumber);
            }

            if (src != null && tgt != null) {
                Link link = Link.between(ctx, src, tgt);
                if (link.isAscending() && sfcLink.intermediatePoints() != null && sfcLink.intermediatePoints().size() >= 2) {
                    double customGutterX = sfcLink.intermediatePoints().get(1).x();
                    link.setCustomGutterX(customGutterX);
                    link.update();
                }
            }
        }

        // 4. Calibrage des compteurs automatiques pour les futures insertions
        int maxStep = program.steps().stream().mapToInt(SfcStep::number).max().orElse(0);
        Step.resetCounter(maxStep);
        int maxTrans = program.transitions().stream().mapToInt(SfcTransition::number).max().orElse(0);
        Transition.resetCounter(maxTrans);

        if (components.isEmpty()) {
            createInitialStep();
        }

        fireSelectionChange(Optional.empty());
        notifyStructureChanged();
        redraw();
    }

    private StepAndTransitionCommonBehavior resolveComponentByReference(
            String ref, Map<Integer, Step> stepByNumber, Map<Integer, Transition> transByNumber) {
        if (ref == null || ref.isBlank()) return null;
        String clean = ref.trim();
        String digits = clean.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            int num = Integer.parseInt(digits);
            char first = Character.toLowerCase(clean.charAt(0));
            if (first == 's') {
                return stepByNumber.get(num);
            } else if (first == 't') {
                return transByNumber.get(num);
            }
            if (stepByNumber.containsKey(num)) return stepByNumber.get(num);
            return transByNumber.get(num);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Duration parseDuration(String param) {
        if (param == null || param.isBlank()) return null;
        String s = param.trim();
        if (s.toUpperCase(Locale.ROOT).startsWith("T#")) {
            s = s.substring(2).trim();
        }
        try {
            if (s.toLowerCase(Locale.ROOT).endsWith("ms")) {
                long ms = Long.parseLong(s.substring(0, s.length() - 2).trim());
                return Duration.ofMillis(ms);
            } else if (s.toLowerCase(Locale.ROOT).endsWith("s")) {
                double sec = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return Duration.ofMillis((long) (sec * 1000));
            } else if (s.toLowerCase(Locale.ROOT).endsWith("m")) {
                double min = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return Duration.ofMillis((long) (min * 60000));
            }
            double val = Double.parseDouble(s);
            return Duration.ofMillis((long) (val * 1000));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Exporte le diagramme complet en image PNG haute résolution pour rapports de TP et dossiers techniques.
     */
    public void exportToPng(File file, String projectTitle, double scale) throws IOException {
        if (file == null) return;
        if (scale <= 0) scale = 2.0; // 2x haute résolution par défaut

        // 1. Boîte englobante (Bounding Box)
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        if (components.isEmpty()) {
            minX = 100; minY = 50; maxX = 500; maxY = 350;
        } else {
            for (StepAndTransitionCommonBehavior comp : components) {
                minX = Math.min(minX, comp.getX());
                minY = Math.min(minY, comp.getY());
                maxX = Math.max(maxX, comp.getX() + Component.UNIT_WIDTH);
                maxY = Math.max(maxY, comp.getY() + Component.UNIT_HEIGHT);

                if (comp instanceof Step step) {
                    double curX = step.getX() + Component.UNIT_WIDTH + Component.GAP;
                    for (Action a : step.getActions()) {
                        String text = a.getDisplayText();
                        double textWidth = (text != null) ? Math.max(30, text.length() * 9.0) : 30;
                        curX += textWidth + 40;
                    }
                    maxX = Math.max(maxX, curX);
                } else if (comp instanceof Transition trans) {
                    minX = Math.min(minX, trans.getX() - 35);
                    String rec = trans.getReceptivity();
                    double recWidth = (rec != null) ? rec.length() * 10.0 : 20;
                    maxX = Math.max(maxX, trans.getX() + Component.UNIT_WIDTH * 3 / 4 + 15 + recWidth);
                }

                for (Link link : comp.getLinksToChildren()) {
                    for (Point2D pt : link.getPath()) {
                        minX = Math.min(minX, pt.getX());
                        minY = Math.min(minY, pt.getY());
                        maxX = Math.max(maxX, pt.getX());
                        maxY = Math.max(maxY, pt.getY());
                    }
                    if (link.isAscending() && link.getCustomGutterX() != null) {
                        minX = Math.min(minX, link.getCustomGutterX() - 15);
                        maxX = Math.max(maxX, link.getCustomGutterX() + 15);
                    }
                }
            }
        }

        double padding = 45.0;
        double titleHeight = (projectTitle != null && !projectTitle.isBlank()) ? 50.0 : 0.0;
        double diagramWidth = (maxX - minX) + 2 * padding;
        double diagramHeight = (maxY - minY) + 2 * padding + titleHeight;

        Canvas offscreenCanvas = new Canvas(diagramWidth * scale, diagramHeight * scale);
        GraphicsContext g = offscreenCanvas.getGraphicsContext2D();

        g.save();
        g.scale(scale, scale);

        // Fond blanc pur de publication
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, diagramWidth, diagramHeight);

        // Cartouche de titre pour compte-rendu de TP
        if (titleHeight > 0) {
            g.setFill(Color.rgb(0, 129, 132)); // Teal Arduino
            g.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));
            g.fillText(projectTitle, padding, padding - 18);

            g.setFill(Color.rgb(100, 116, 139));
            g.setFont(Font.font("SansSerif", FontWeight.NORMAL, 11));
            g.fillText("Grafino — Conforme CEI 60848 & CEI 61131-3", diagramWidth - padding - 260, padding - 18);

            g.setStroke(Color.rgb(225, 228, 232));
            g.setLineWidth(1.0);
            g.strokeLine(padding, padding - 6, diagramWidth - padding, padding - 6);
        }

        double offsetX = padding - minX;
        double offsetY = padding + titleHeight - minY;
        g.translate(offsetX, offsetY);

        // Substitution temporaire du GraphicsContext
        Map<Component, GraphicsContext> originalContexts = new HashMap<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            originalContexts.put(comp, comp.getGraphicsContext());
            comp.setGraphicsContext(g);
            for (Link link : comp.getLinksToChildren()) {
                originalContexts.put(link, link.getGraphicsContext());
                link.setGraphicsContext(g);
            }
        }

        // Désactivation temporaire de la sélection
        Map<Component, Boolean> originalSelection = new HashMap<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            originalSelection.put(comp, comp.isSelected());
            comp.setSelected(false);
            for (Link link : comp.getLinksToChildren()) {
                originalSelection.put(link, link.isSelected());
                link.setSelected(false);
            }
        }

        try {
            // Tracé des liaisons
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .forEach(Link::drawComponent);

            // Tracé des étapes et transitions
            components.forEach(Component::drawComponent);
        } finally {
            for (var entry : originalContexts.entrySet()) {
                entry.getKey().setGraphicsContext(entry.getValue());
            }
            for (var entry : originalSelection.entrySet()) {
                entry.getKey().setSelected(entry.getValue());
            }
            g.restore();
        }

        SnapshotParameters snapParams = new SnapshotParameters();
        snapParams.setFill(Color.WHITE);
        WritableImage fxImage = offscreenCanvas.snapshot(snapParams, null);

        int w = (int) fxImage.getWidth();
        int h = (int) fxImage.getHeight();
        BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader pr = fxImage.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                bImage.setRGB(x, y, pr.getArgb(x, y));
            }
        }

        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            file = new File(file.getParentFile(), file.getName() + ".png");
        }

        ImageIO.write(bImage, "png", file);
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
