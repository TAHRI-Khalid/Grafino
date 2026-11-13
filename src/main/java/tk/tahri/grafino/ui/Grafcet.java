package tk.tahri.grafino.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
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
    private long lastRenderNanos = 0;

    // Éditeur in-place actif
    private TextField activeInlineEditor = null;
    private Point2D dragStartPos = null;
    private boolean isDraggingComponent = false;
    private StepAndTransitionCommonBehavior hoveredComponent = null;

    // Sélection par rectangle (Lasso / Marquee)
    private Point2D marqueeStartDiag = null;
    private Point2D marqueeCurrentDiag = null;

    // Barre d'action contextuelle flottante & Aperçu fantôme (Ghost Preview)
    private final ActionBar actionBar;
    private ActionBar.GhostPreviewType activeGhostPreview = ActionBar.GhostPreviewType.NONE;
    private boolean simulationRunning = false;

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

            // Barre d'outils verticale fixe sur le côté gauche du Canvas
            this.actionBar = new ActionBar(this);
            this.actionBar.setOnHoverPreview(previewType -> {
                this.activeGhostPreview = previewType;
                redraw();
            });
            this.getChildren().add(this.actionBar);
            refreshActionBar();

            // Boutons circulaires flottants sur le Canvas : (+), (-), et Centrer le diagramme
            setupFloatingCanvasControls();

            // Boucle de rendu
            animationTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (now - lastRenderNanos >= 40_000_000) {
                        render();
                    }
                }
            };
        } else {
            this.canvas = null;
            this.ctx = null;
            this.animationTimer = null;
            this.actionBar = null;
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

            boolean multiModifier = event.isControlDown() || event.isShiftDown();

            // Détection du composant cliqué
            var clickedCompOpt = components.stream()
                    .filter(com -> com.isPointOnTheComponent(diagPt))
                    .findFirst();

            if (clickedCompOpt.isPresent()) {
                var component = clickedCompOpt.get();
                clearLinkSelections();

                if (multiModifier) {
                    // Bascule de sélection multiple (Ctrl+Clic / Shift+Clic)
                    component.setSelected(!component.isSelected());
                    dragStartPos = null;
                    refreshActionBar();
                    fireSelectionChange(getSelectedComponent());
                    redraw();
                    return;
                }

                if (!component.isSelected()) {
                    components.forEach(com -> com.setSelected(false));
                    component.setSelected(true);
                }
                fireSelectionChange(Optional.of(component));

                if (component.isBottomHandlePressed(diagPt)) {
                    operation = Operation.PRESSED_TO_BOTTOM_LINK;
                } else if (component.isTopHandlePressed(diagPt)) {
                    operation = Operation.PRESSED_TO_TOP_LINK;
                } else {
                    operation = Operation.PRESSED_TO_MOVE;
                    dragStartPos = new Point2D(component.getX(), component.getY());
                }
                pressedH = diagPt.getX() - component.getX();
                pressedV = diagPt.getY() - component.getY();
                refreshActionBar();
            } else {
                // Aucun composant cliqué : vérifier si un lien est cliqué
                components.forEach(com -> com.setSelected(false));
                clearLinkSelections();

                var clickedLinkOpt = components.stream()
                        .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                        .flatMap(List::stream)
                        .filter(link -> link.isPointOnTheComponent(diagPt))
                        .findFirst();

                if (clickedLinkOpt.isPresent()) {
                    clickedLinkOpt.get().setSelected(true);
                    dragStartPos = null;
                    operation = Operation.PRESSED_TO_MOVE;
                    fireSelectionChange(Optional.empty());
                    refreshActionBar();
                } else {
                    // Clic dans le vide -> Démarrer la sélection par rectangle (Lasso / Marquee)
                    dragStartPos = null;
                    operation = Operation.MARQUEE_SELECT;
                    marqueeStartDiag = diagPt;
                    marqueeCurrentDiag = diagPt;
                    fireSelectionChange(Optional.empty());
                    refreshActionBar();
                }
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
                updateActionBarPosition();
                return;
            }

            Point2D diagPt = screenToDiagram(event.getX(), event.getY());

            if (operation == Operation.MARQUEE_SELECT && marqueeStartDiag != null) {
                marqueeCurrentDiag = diagPt;
                double minX = Math.min(marqueeStartDiag.getX(), marqueeCurrentDiag.getX());
                double maxX = Math.max(marqueeStartDiag.getX(), marqueeCurrentDiag.getX());
                double minY = Math.min(marqueeStartDiag.getY(), marqueeCurrentDiag.getY());
                double maxY = Math.max(marqueeStartDiag.getY(), marqueeCurrentDiag.getY());

                for (var comp : components) {
                    double cx = comp.getX() + Component.UNIT_WIDTH / 2;
                    double cy = comp.getY() + Component.UNIT_HEIGHT / 2;
                    comp.setSelected(cx >= minX && cx <= maxX && cy >= minY && cy <= maxY);
                }
                return;
            }

            if (getSelectedComponent().isPresent() && event.isPrimaryButtonDown()) {
                var component = getSelectedComponent().get();
                switch (operation) {
                    case PRESSED_TO_MOVE -> {
                        isDraggingComponent = true;
                        double rawX = diagPt.getX() - pressedH;
                        double rawY = diagPt.getY() - pressedV;
                        var position = computeSmartSnap(component, rawX, rawY);
                        double dx = position.getX() - component.getX();
                        double dy = position.getY() - component.getY();
                        component.updatePositionAndLinks(position);

                        // Si plusieurs éléments sont sélectionnés, déplacer tout le groupe ensemble
                        for (var other : getSelectedComponents()) {
                            if (other != component) {
                                other.updatePositionAndLinks(new Point2D(other.getX() + dx, other.getY() + dy));
                            }
                        }
                        mouseDragPosition = null;
                    }
                    case PRESSED_TO_BOTTOM_LINK, PRESSED_TO_TOP_LINK -> {
                        mouseDragPosition = diagPt;
                        components.forEach(com -> com.setHandle(Handle.NONE));
                        activeGuideLines.clear();
                        boolean fromBottom = operation == Operation.PRESSED_TO_BOTTOM_LINK;
                        var preview = evaluateManualLinkPreview(component, diagPt, fromBottom);
                        if (preview.target() != null && preview.status() != PreviewStatus.INVALID) {
                            preview.target().setHandle(fromBottom ? Handle.TOP : Handle.BOTTOM);
                        }
                    }
                    default -> {}
                }
            }

            getSelectedLink().ifPresent(link -> link.updateSelectedLink(diagPt));
        });

        canvas.setOnMouseReleased(event -> {
            activeGuideLines.clear();
            isDraggingComponent = false;

            if (operation == Operation.PANNING) {
                this.setCursor(Cursor.DEFAULT);
                operation = Operation.PRESSED_TO_MOVE;
                dragStartPos = null;
                refreshActionBar();
                return;
            }

            if (operation == Operation.MARQUEE_SELECT) {
                marqueeStartDiag = null;
                marqueeCurrentDiag = null;
                operation = Operation.PRESSED_TO_MOVE;
                fireSelectionChange(getSelectedComponent());
                refreshActionBar();
                redraw();
                return;
            }

            if (operation == Operation.PRESSED_TO_MOVE && dragStartPos != null) {
                getSelectedComponent().ifPresent(comp -> {
                    if (comp.getX() != dragStartPos.getX() || comp.getY() != dragStartPos.getY()) {
                        notifyStructureChanged();
                    }
                });
                dragStartPos = null;
            }

            Point2D diagPt = screenToDiagram(event.getX(), event.getY());

            if (operation == Operation.PRESSED_TO_BOTTOM_LINK || operation == Operation.PRESSED_TO_TOP_LINK) {
                boolean fromBottom = operation == Operation.PRESSED_TO_BOTTOM_LINK;
                getSelectedComponent().ifPresent(anchor -> handleManualLinkRelease(anchor, diagPt, fromBottom));
            }

            mouseDragPosition = null;
            operation = Operation.PRESSED_TO_MOVE;
            refreshActionBar();
        });

        canvas.setOnMouseMoved(event -> {
            Point2D diagPt = screenToDiagram(event.getX(), event.getY());
            components.forEach(component -> component.setHandle(Handle.NONE));

            var hovered = components.stream()
                    .filter(component -> component.isPointOnTheComponent(diagPt))
                    .findFirst()
                    .orElse(null);

            this.hoveredComponent = hovered;
            if (hovered != null) {
                hovered.setHandle(Handle.BOTH);
            }
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
                updateActionBarPosition();
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
                if (operation == Operation.PRESSED_TO_BOTTOM_LINK || operation == Operation.PRESSED_TO_TOP_LINK) {
                    mouseDragPosition = null;
                    operation = Operation.PRESSED_TO_MOVE;
                    activeGuideLines.clear();
                    components.forEach(c -> c.setHandle(Handle.NONE));
                    notifyStatus("Raccordement manuel annulé.");
                    redraw();
                    event.consume();
                    return;
                }
                components.forEach(c -> c.setSelected(false));
                clearLinkSelections();
                fireSelectionChange(Optional.empty());
                refreshActionBar();
                redraw();
                event.consume();
            } else if (event.isShiftDown() && event.getCode() == KeyCode.ENTER) {
                getSelectedComponent().ifPresent(comp -> {
                    if (comp instanceof Step step) {
                        createTransitionAndStepPair(step);
                    } else if (comp instanceof Transition trans) {
                        createStepAndTransitionPair(trans);
                    }
                });
                event.consume();
            } else if (event.getCode() == KeyCode.T || event.getCode() == KeyCode.ENTER) {
                if (getSelectedLink().isPresent() && event.getCode() == KeyCode.ENTER) {
                    insertPairOnLink(getSelectedLink().get());
                } else {
                    getSelectedComponent().ifPresent(comp -> {
                        if (comp instanceof Step step) {
                            createTransition(step);
                        } else if (comp instanceof Transition trans) {
                            createStep(trans);
                        }
                    });
                }
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
            } else if (event.getCode() == KeyCode.C && !event.isControlDown()) {
                var selectedList = getSelectedComponents();
                if (selectedList.size() >= 2) {
                    if (selectedList.stream().allMatch(Step.class::isInstance)) {
                        createAndConvergence(selectedList.stream().map(Step.class::cast).toList());
                    } else if (selectedList.stream().allMatch(Transition.class::isInstance)) {
                        createOrConvergence(selectedList.stream().map(Transition.class::cast).toList());
                    }
                } else if (selectedList.size() == 1) {
                    convergeWithOpenSiblings(selectedList.getFirst());
                }
                event.consume();
            } else if (event.getCode() == KeyCode.R && !event.isControlDown()) {
                getSelectedComponents().stream()
                        .filter(Transition.class::isInstance)
                        .map(Transition.class::cast)
                        .forEach(this::loopBackToInitialStep);
                event.consume();
            } else if (event.getCode() == KeyCode.I && !event.isControlDown()) {
                getSelectedComponent().ifPresent(comp -> {
                    if (comp instanceof Step step) {
                        toggleInitialStep(step);
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
        if (draggedComp != null) {
            draggedComp.getLinksFromParents().forEach(l -> xCandidates.add(l.getParentComponent()));
            draggedComp.getLinksToChildren().forEach(l -> xCandidates.add(l.getChildComponent()));
        }
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
        notifyStructureChanged();
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
        lastRenderNanos = System.nanoTime();

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

        // Tracé de l'aperçu fantôme (Ghost Preview) au survol de la ActionBar
        if (activeGhostPreview != ActionBar.GhostPreviewType.NONE) {
            drawGhostPreview();
        }

        // Tracé du rectangle de sélection multiple (Lasso / Marquee)
        if (operation == Operation.MARQUEE_SELECT && marqueeStartDiag != null && marqueeCurrentDiag != null) {
            drawMarqueeSelectionBox();
        }

        // Tracé du lien dynamique en cours d'étirement
        if (mouseDragPosition != null && (operation == Operation.PRESSED_TO_BOTTOM_LINK || operation == Operation.PRESSED_TO_TOP_LINK)) {
            boolean fromBottom = operation == Operation.PRESSED_TO_BOTTOM_LINK;
            getSelectedComponent().ifPresent(comp -> drawDraggedLinkPreview(comp, mouseDragPosition, fromBottom));
        }

        ctx.restore();

        // Mise à jour de la position écran de la barre d'action flottante
        updateActionBarPosition();
    }

    private void drawBackgroundGrid() {
        if (canvas == null || ctx == null) return;
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double gridSize = 24.0 * zoomFactor;
        if (gridSize < 10) return;

        ctx.setFill(Color.rgb(215, 222, 228, 0.55));
        double startX = Double.isFinite(panX) ? ((panX % gridSize) + gridSize) % gridSize : 0.0;
        double startY = Double.isFinite(panY) ? ((panY % gridSize) + gridSize) % gridSize : 0.0;

        for (double x = startX; x < w; x += gridSize) {
            for (double y = startY; y < h; y += gridSize) {
                ctx.fillOval(x - 1, y - 1, 2, 2);
            }
        }
    }

    public enum PreviewStatus {
        VALID,
        VALID_LOOP,
        DUPLICATE,
        INVALID,
        FREE
    }

    public record ManualLinkPreview(
            PreviewStatus status,
            StepAndTransitionCommonBehavior target,
            String badgeText,
            boolean isAscending,
            Point2D snappedStart,
            Point2D snappedEnd
    ) {}

    public ManualLinkPreview evaluateManualLinkPreview(StepAndTransitionCommonBehavior anchor, Point2D mouse, boolean fromBottom) {
        if (anchor == null || mouse == null) {
            return new ManualLinkPreview(PreviewStatus.FREE, null, null, false, Point2D.ZERO, Point2D.ZERO);
        }

        Point2D anchorPt = fromBottom
                ? new Point2D(anchor.getX() + Component.UNIT_WIDTH / 2, anchor.getY() + Component.UNIT_HEIGHT)
                : new Point2D(anchor.getX() + Component.UNIT_WIDTH / 2, anchor.getY());

        StepAndTransitionCommonBehavior hovered = components.stream()
                .filter(c -> c != anchor && c.isPointOnTheComponent(mouse))
                .findFirst()
                .orElse(null);

        if (hovered != null) {
            StepAndTransitionCommonBehavior source = fromBottom ? anchor : hovered;
            StepAndTransitionCommonBehavior target = fromBottom ? hovered : anchor;
            Point2D startPt = new Point2D(source.getX() + Component.UNIT_WIDTH / 2, source.getY() + Component.UNIT_HEIGHT);
            Point2D endPt = new Point2D(target.getX() + Component.UNIT_WIDTH / 2, target.getY());
            boolean ascending = target.getY() < source.getY() + Component.UNIT_HEIGHT;

            boolean validAlternance = (source instanceof Step && target instanceof Transition)
                    || (source instanceof Transition && target instanceof Step);
            if (!validAlternance) {
                return new ManualLinkPreview(PreviewStatus.INVALID, hovered, "✕ Alternance CEI 60848 invalide", ascending, startPt, endPt);
            }

            boolean duplicate = source.getLinksToChildren().stream().anyMatch(l -> l.getChildComponent() == target);
            if (duplicate) {
                return new ManualLinkPreview(PreviewStatus.DUPLICATE, hovered, "Liaison déjà existante", ascending, startPt, endPt);
            }

            Optional<String> convErr = validateManualConvergenceLink(source, target);
            if (convErr.isPresent()) {
                return new ManualLinkPreview(PreviewStatus.INVALID, hovered, "✕ Convergence non conforme", ascending, startPt, endPt);
            }

            if (ascending) {
                return new ManualLinkPreview(PreviewStatus.VALID_LOOP, hovered, "▲ Boucle vers " + getElementName(target), true, startPt, endPt);
            }

            boolean createsConvergence = target.getLinksFromParents().stream().anyMatch(l -> !l.isAscending());
            String label = createsConvergence
                    ? (target instanceof Transition ? "✓ Convergence ET" : "✓ Convergence OU")
                    : "✓ Raccorder à " + getElementName(target);
            return new ManualLinkPreview(PreviewStatus.VALID, hovered, label, false, startPt, endPt);
        }

        boolean ascending = fromBottom ? (mouse.getY() < anchorPt.getY()) : (mouse.getY() > anchorPt.getY());
        return new ManualLinkPreview(PreviewStatus.FREE, null, null, ascending, anchorPt, mouse);
    }

    private void handleManualLinkRelease(StepAndTransitionCommonBehavior anchor, Point2D diagPt, boolean fromBottom) {
        var preview = evaluateManualLinkPreview(anchor, diagPt, fromBottom);
        if (preview.target() != null) {
            StepAndTransitionCommonBehavior source = fromBottom ? anchor : preview.target();
            StepAndTransitionCommonBehavior target = fromBottom ? preview.target() : anchor;
            switch (preview.status()) {
                case VALID, VALID_LOOP -> {
                    Link.between(ctx, source, target);
                    if (preview.status() == PreviewStatus.VALID_LOOP) {
                        notifyStatus("Boucle de reprise créée : " + getElementName(source) + " ▲ " + getElementName(target));
                    } else {
                        notifyStatus("Liaison créée entre " + getElementName(source) + " et " + getElementName(target));
                    }
                    notifyStructureChanged();
                }
                case DUPLICATE -> notifyStatus("Cette liaison existe déjà entre " + getElementName(source) + " et " + getElementName(target) + ".");
                case INVALID -> {
                    if (!((source instanceof Transition && target instanceof Step) || (source instanceof Step && target instanceof Transition))) {
                        notifyStatus("⚠ Violation d'alternance CEI 60848 : une étape ne peut être reliée qu'à une transition.");
                    } else {
                        validateManualConvergenceLink(source, target).ifPresent(err -> notifyStatus("⚠ " + err));
                    }
                }
                default -> {}
            }
        }
    }

    private void drawDraggedLinkPreview(StepAndTransitionCommonBehavior anchor, Point2D mouse, boolean fromBottom) {
        var preview = evaluateManualLinkPreview(anchor, mouse, fromBottom);
        Point2D begin = preview.snappedStart();
        Point2D end = preview.snappedEnd();

        ctx.save();
        switch (preview.status()) {
            case VALID, VALID_LOOP -> {
                ctx.setStroke(Color.rgb(16, 185, 129));
                ctx.setLineWidth(3.0);
            }
            case DUPLICATE -> {
                ctx.setStroke(Color.rgb(245, 158, 11));
                ctx.setLineWidth(2.5);
            }
            case INVALID -> {
                ctx.setStroke(Color.rgb(217, 56, 30));
                ctx.setLineWidth(3.0);
            }
            case FREE -> {
                ctx.setStroke(Color.rgb(0, 129, 132));
                ctx.setLineWidth(2.0);
            }
        }

        if (preview.isAscending()) {
            double gutterX = (begin.getX() > end.getX() + 20)
                    ? Math.max(begin.getX(), end.getX()) + Component.UNIT_WIDTH * 0.9
                    : Math.min(begin.getX(), end.getX()) - Component.UNIT_WIDTH * 0.9;
            double topY = end.getY() - 15;
            ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), begin.getY() + 15);
            ctx.strokeLine(begin.getX(), begin.getY() + 15, gutterX, begin.getY() + 15);
            ctx.strokeLine(gutterX, begin.getY() + 15, gutterX, topY);
            ctx.strokeLine(gutterX, topY, end.getX(), topY);
            ctx.strokeLine(end.getX(), topY, end.getX(), end.getY());

            // Flèche ascendante ▲
            double midArrowY = (begin.getY() + 15 + topY) / 2.0;
            ctx.setFill(ctx.getStroke());
            ctx.beginPath();
            ctx.moveTo(gutterX, midArrowY - 8);
            ctx.lineTo(gutterX - 5, midArrowY + 6);
            ctx.lineTo(gutterX + 5, midArrowY + 6);
            ctx.closePath();
            ctx.fill();
        } else {
            double midY = (begin.getY() + end.getY()) / 2;
            ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), midY);
            ctx.strokeLine(begin.getX(), midY, end.getX(), midY);
            ctx.strokeLine(end.getX(), midY, end.getX(), end.getY());
        }

        // Badge contextuel près du curseur
        if (preview.badgeText() != null) {
            drawPreviewCursorBadge(mouse.getX() + 14, mouse.getY() - 12, preview.badgeText(), preview.status());
        }
        ctx.restore();
    }

    private void drawPreviewCursorBadge(double x, double y, String text, PreviewStatus status) {
        Color bg = switch (status) {
            case VALID, VALID_LOOP -> Color.rgb(209, 250, 229, 0.96);
            case DUPLICATE -> Color.rgb(254, 243, 199, 0.96);
            case INVALID -> Color.rgb(254, 226, 226, 0.96);
            case FREE -> Color.rgb(229, 245, 245, 0.96);
        };
        Color border = switch (status) {
            case VALID, VALID_LOOP -> Color.rgb(16, 185, 129);
            case DUPLICATE -> Color.rgb(245, 158, 11);
            case INVALID -> Color.rgb(217, 56, 30);
            case FREE -> Color.rgb(0, 129, 132);
        };
        Color textFill = switch (status) {
            case VALID, VALID_LOOP -> Color.rgb(4, 120, 87);
            case DUPLICATE -> Color.rgb(180, 83, 9);
            case INVALID -> Color.rgb(185, 28, 28);
            case FREE -> Color.rgb(0, 102, 104);
        };

        double w = Math.max(84, text.length() * 6.8 + 16);
        double h = 22;
        ctx.setFill(bg);
        ctx.setStroke(border);
        ctx.setLineWidth(1.2);
        ctx.fillRoundRect(x, y - h / 2, w, h, 8, 8);
        ctx.strokeRoundRect(x, y - h / 2, w, h, 8, 8);

        ctx.setFill(textFill);
        ctx.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        ctx.setTextAlign(TextAlignment.LEFT);
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText(text, x + 8, y);
    }

    private void setupFloatingCanvasControls() {
        VBox controlsBox = new VBox(8);
        controlsBox.setAlignment(Pos.CENTER);
        controlsBox.setPickOnBounds(false);

        Button zoomInBtn = createCircularCanvasButton("＋", "Zoom + (Agrandir le schéma)", this::zoomIn);
        Button zoomOutBtn = createCircularCanvasButton("－", "Zoom - (Réduire le schéma)", this::zoomOut);
        Button centerBtn = createCircularCanvasButton("⌖", "Centrer le diagramme complet au centre de la page", this::centerDiagram);

        controlsBox.getChildren().addAll(zoomInBtn, zoomOutBtn, centerBtn);
        this.getChildren().add(controlsBox);

        Runnable updatePosition = () -> {
            double boxW = 42;
            double boxH = 3 * 38 + 2 * 8;
            controlsBox.setLayoutX(Math.max(12, this.getWidth() - boxW - 18));
            controlsBox.setLayoutY(Math.max(12, this.getHeight() - boxH - 18));
        };

        this.widthProperty().addListener((obs, oldV, newV) -> updatePosition.run());
        this.heightProperty().addListener((obs, oldV, newV) -> updatePosition.run());
        javafx.application.Platform.runLater(updatePosition);
    }

    private Button createCircularCanvasButton(String symbol, String tooltipText, Runnable action) {
        Button btn = new Button(symbol);
        double size = 38.0;
        btn.setMinSize(size, size);
        btn.setPrefSize(size, size);
        btn.setMaxSize(size, size);
        btn.setFocusTraversable(false);
        btn.setTooltip(new Tooltip(tooltipText));

        String normalStyle =
                "-fx-background-color: #FFFFFF;" +
                "-fx-text-fill: " + AppTheme.PRIMARY_TEAL + ";" +
                "-fx-border-color: " + AppTheme.BORDER_COLOR + ";" +
                "-fx-border-width: 1.5px;" +
                "-fx-background-radius: 19px;" +
                "-fx-border-radius: 19px;" +
                "-fx-font-size: 16px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 0;" +
                "-fx-cursor: hand;";

        String hoverStyle =
                "-fx-background-color: " + AppTheme.PRIMARY_TEAL + ";" +
                "-fx-text-fill: #FFFFFF;" +
                "-fx-border-color: " + AppTheme.PRIMARY_TEAL + ";" +
                "-fx-border-width: 1.5px;" +
                "-fx-background-radius: 19px;" +
                "-fx-border-radius: 19px;" +
                "-fx-font-size: 16px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 0;" +
                "-fx-cursor: hand;";

        btn.setStyle(normalStyle);
        btn.setEffect(new DropShadow(6, 0, 2, Color.rgb(15, 23, 42, 0.14)));

        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
        btn.setOnAction(e -> {
            action.run();
            this.requestFocus();
            e.consume();
        });
        return btn;
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
        double cx = (canvas != null && canvas.getWidth() > 0) ? canvas.getWidth() / 2 : 400;
        double cy = (canvas != null && canvas.getHeight() > 0) ? canvas.getHeight() / 2 : 300;
        zoomAround(1.18, cx, cy);
        notifyStatus(String.format(Locale.ROOT, "Zoom : %d%%", Math.round(zoomFactor * 100)));
    }

    public void zoomOut() {
        double cx = (canvas != null && canvas.getWidth() > 0) ? canvas.getWidth() / 2 : 400;
        double cy = (canvas != null && canvas.getHeight() > 0) ? canvas.getHeight() / 2 : 300;
        zoomAround(1 / 1.18, cx, cy);
        notifyStatus(String.format(Locale.ROOT, "Zoom : %d%%", Math.round(zoomFactor * 100)));
    }

    public void resetZoom() {
        centerDiagram();
    }

    /**
     * Recentre l'intégralité du diagramme SFC au milieu de la page (Canvas) et ajuste le zoom si nécessaire.
     */
    public void centerDiagram() {
        if (components.isEmpty()) {
            this.zoomFactor = 1.0;
            this.panX = 0;
            this.panY = 0;
            redraw();
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

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

        double viewW = (canvas != null && canvas.getWidth() > 0) ? canvas.getWidth() : 800;
        double viewH = (canvas != null && canvas.getHeight() > 0) ? canvas.getHeight() : 600;

        double diagW = Math.max(100, maxX - minX);
        double diagH = Math.max(100, maxY - minY);
        double padding = 80.0;

        double fitScaleX = Math.max(0.35, (viewW - padding * 2) / diagW);
        double fitScaleY = Math.max(0.35, (viewH - padding * 2) / diagH);
        this.zoomFactor = Math.clamp(Math.min(fitScaleX, fitScaleY), 0.35, 1.0);

        double diagCenterX = (minX + maxX) / 2.0;
        double diagCenterY = (minY + maxY) / 2.0;

        this.panX = (viewW / 2.0) - diagCenterX * this.zoomFactor;
        this.panY = (viewH / 2.0) - diagCenterY * this.zoomFactor;

        notifyStatus("Diagramme complet centré au milieu de la page.");
        redraw();
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    // ==========================================
    // CRÉATION & INSERTION CONTEXTUELLE MODERNE (FLOATING BAR & GHOST PREVIEW)
    // ==========================================

    private void clearLinkSelections() {
        components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .forEach(link -> link.setSelected(false));
    }

    public List<StepAndTransitionCommonBehavior> getSelectedComponents() {
        return components.stream().filter(Component::isSelected).toList();
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public void setActiveGhostPreview(ActionBar.GhostPreviewType previewType) {
        this.activeGhostPreview = (previewType != null) ? previewType : ActionBar.GhostPreviewType.NONE;
        redraw();
    }

    public ActionBar.GhostPreviewType getActiveGhostPreview() {
        return activeGhostPreview;
    }

    public void refreshActionBar() {
        if (actionBar == null) return;
        if (simulationRunning) {
            actionBar.setVisible(false);
            return;
        }
        actionBar.refreshForSelection(getSelectedComponents(), getSelectedLink().orElse(null));
        updateActionBarPosition();
    }

    /**
     * Positionne la barre d'outils verticale à un emplacement fixe sur le bord gauche du Canvas,
     * centrée verticalement.
     */
    private void updateActionBarPosition() {
        if (actionBar == null || !actionBar.isVisible()) return;

        actionBar.autosize();
        double barH = Math.max(actionBar.getHeight(), actionBar.prefHeight(-1));
        if (barH <= 10) barH = 260;

        double paneH = this.getHeight() > 0 ? this.getHeight() : 600;
        double fixedLeftX = 14.0;
        double centeredY = Math.max(14.0, (paneH - barH) / 2.0);

        actionBar.setLayoutX(fixedLeftX);
        actionBar.setLayoutY(centeredY);
    }

    private void drawMarqueeSelectionBox() {
        double minX = Math.min(marqueeStartDiag.getX(), marqueeCurrentDiag.getX());
        double minY = Math.min(marqueeStartDiag.getY(), marqueeCurrentDiag.getY());
        double w = Math.abs(marqueeCurrentDiag.getX() - marqueeStartDiag.getX());
        double h = Math.abs(marqueeCurrentDiag.getY() - marqueeStartDiag.getY());

        ctx.save();
        ctx.setFill(Color.rgb(0, 129, 132, 0.10));
        ctx.fillRect(minX, minY, w, h);
        ctx.setStroke(Color.rgb(0, 129, 132, 0.85));
        ctx.setLineWidth(1.5);
        ctx.setLineDashes(5.0, 4.0);
        ctx.strokeRect(minX, minY, w, h);
        ctx.restore();
    }

    /**
     * Retourne uniquement les liaisons descendantes (excluant les boucles de reprise ascendantes CEI 60848).
     */
    public List<Link> getDownwardLinks(StepAndTransitionCommonBehavior comp) {
        if (comp == null) return List.of();
        return comp.getLinksToChildren().stream()
                .filter(l -> !l.isAscending())
                .toList();
    }

    private void drawGhostPreview() {
        ctx.save();
        Color ghostStroke = Color.rgb(0, 129, 132, 0.65);
        Color ghostFill = Color.rgb(229, 245, 245, 0.55);
        ctx.setStroke(ghostStroke);
        ctx.setFill(ghostFill);
        ctx.setLineWidth(2.0);
        ctx.setLineDashes(5.0, 4.0);

        List<StepAndTransitionCommonBehavior> selected = getSelectedComponents();

        switch (activeGhostPreview) {
            case NEXT_SINGLE -> {
                if (selected.size() == 1) {
                    var comp = selected.getFirst();
                    double targetX = comp.getX();
                    double targetY = comp.getY() + Component.UNIT_HEIGHT + 40;
                    drawGhostLink(comp.getX() + Component.UNIT_WIDTH / 2, comp.getY() + Component.UNIT_HEIGHT,
                                  targetX + Component.UNIT_WIDTH / 2, targetY);
                    if (comp instanceof Step) {
                        drawGhostTransitionShape(targetX, targetY);
                    } else {
                        drawGhostStepShape(targetX, targetY);
                    }
                }
            }
            case BRANCH -> {
                if (selected.size() == 1) {
                    var comp = selected.getFirst();
                    List<Link> downward = getDownwardLinks(comp);
                    double targetY = comp.getY() + Component.UNIT_HEIGHT + 40;
                    double branchBarY = comp.getY() + Component.UNIT_HEIGHT + 20;
                    boolean isAndDiv = comp instanceof Transition;

                    if (downward.isEmpty()) {
                        // Aperçu d'ouverture de divergence complète à 2 branches
                        double firstX = comp.getX();
                        double secondX = comp.getX() + 2.2 * Component.UNIT_WIDTH;
                        double minCenterX = firstX + Component.UNIT_WIDTH / 2 - 15;
                        double maxCenterX = secondX + Component.UNIT_WIDTH / 2 + 15;

                        ctx.strokeLine(comp.getX() + Component.UNIT_WIDTH / 2, comp.getY() + Component.UNIT_HEIGHT,
                                       comp.getX() + Component.UNIT_WIDTH / 2, branchBarY);
                        if (isAndDiv) {
                            ctx.strokeLine(minCenterX, branchBarY - 3, maxCenterX, branchBarY - 3);
                            ctx.strokeLine(minCenterX, branchBarY + 3, maxCenterX, branchBarY + 3);
                            drawGhostStepShape(firstX, targetY);
                            drawGhostStepShape(secondX, targetY);
                        } else {
                            ctx.strokeLine(minCenterX, branchBarY, maxCenterX, branchBarY);
                            drawGhostTransitionShape(firstX, targetY);
                            drawGhostTransitionShape(secondX, targetY);
                        }
                        ctx.strokeLine(firstX + Component.UNIT_WIDTH / 2, branchBarY, firstX + Component.UNIT_WIDTH / 2, targetY);
                        ctx.strokeLine(secondX + Component.UNIT_WIDTH / 2, branchBarY, secondX + Component.UNIT_WIDTH / 2, targetY);
                    } else {
                        // Aperçu d'ajout d'une branche supplémentaire à droite
                        double maxX = downward.stream()
                                .mapToDouble(l -> l.getChildComponent().getX())
                                .max().orElse(comp.getX());
                        double minX = downward.stream()
                                .mapToDouble(l -> l.getChildComponent().getX())
                                .min().orElse(comp.getX());
                        double targetX = maxX + 2.2 * Component.UNIT_WIDTH;
                        double minCenterX = Math.min(minX, comp.getX()) + Component.UNIT_WIDTH / 2 - 15;
                        double maxCenterX = targetX + Component.UNIT_WIDTH / 2 + 15;

                        if (isAndDiv) {
                            ctx.strokeLine(minCenterX, branchBarY - 3, maxCenterX, branchBarY - 3);
                            ctx.strokeLine(minCenterX, branchBarY + 3, maxCenterX, branchBarY + 3);
                            drawGhostStepShape(targetX, targetY);
                        } else {
                            ctx.strokeLine(minCenterX, branchBarY, maxCenterX, branchBarY);
                            drawGhostTransitionShape(targetX, targetY);
                        }
                        ctx.strokeLine(targetX + Component.UNIT_WIDTH / 2, branchBarY, targetX + Component.UNIT_WIDTH / 2, targetY);
                    }
                }
            }
            case NEXT_PAIR -> {
                if (selected.size() == 1) {
                    var comp = selected.getFirst();
                    List<Link> downward = getDownwardLinks(comp);
                    double targetX = comp.getX();
                    double firstY = comp.getY() + Component.UNIT_HEIGHT + 40;
                    if (!downward.isEmpty()) {
                        double maxX = downward.stream()
                                .mapToDouble(l -> l.getChildComponent().getX())
                                .max().orElse(comp.getX());
                        targetX = maxX + 2.2 * Component.UNIT_WIDTH;
                    }
                    double secondY = firstY + Component.UNIT_HEIGHT + 40;
                    double midX = targetX + Component.UNIT_WIDTH / 2;
                    drawGhostLink(comp.getX() + Component.UNIT_WIDTH / 2, comp.getY() + Component.UNIT_HEIGHT, midX, firstY);
                    drawGhostLink(midX, firstY + Component.UNIT_HEIGHT, midX, secondY);
                    if (comp instanceof Step) {
                        drawGhostTransitionShape(targetX, firstY);
                        drawGhostStepShape(targetX, secondY);
                    } else {
                        drawGhostStepShape(targetX, firstY);
                        drawGhostTransitionShape(targetX, secondY);
                    }
                }
            }
            case CONVERGE -> {
                List<StepAndTransitionCommonBehavior> toConverge = new ArrayList<>(selected);
                if (toConverge.size() == 1) {
                    var single = toConverge.getFirst();
                    if (single instanceof Step s) {
                        toConverge.addAll(findConvergenceCandidateSteps(s));
                    } else if (single instanceof Transition t) {
                        toConverge.addAll(findConvergenceCandidateTransitions(t));
                    }
                }
                if (toConverge.size() >= 2) {
                    double avgX = toConverge.stream().mapToDouble(StepAndTransitionCommonBehavior::getX).average().orElse(400);
                    double maxY = toConverge.stream().mapToDouble(StepAndTransitionCommonBehavior::getY).max().orElse(200);
                    double targetY = maxY + Component.UNIT_HEIGHT + 45;
                    double convBarY = targetY - 20;
                    double minCenterX = toConverge.stream().mapToDouble(c -> c.getX() + Component.UNIT_WIDTH / 2).min().orElse(avgX) - 15;
                    double maxCenterX = toConverge.stream().mapToDouble(c -> c.getX() + Component.UNIT_WIDTH / 2).max().orElse(avgX) + 15;

                    boolean isAndConv = toConverge.getFirst() instanceof Step;
                    if (isAndConv) {
                        ctx.strokeLine(minCenterX, convBarY - 3, maxCenterX, convBarY - 3);
                        ctx.strokeLine(minCenterX, convBarY + 3, maxCenterX, convBarY + 3);
                        drawGhostTransitionShape(avgX, targetY);
                    } else {
                        ctx.strokeLine(minCenterX, convBarY, maxCenterX, convBarY);
                        drawGhostStepShape(avgX, targetY);
                    }
                    for (var src : toConverge) {
                        double sx = src.getX() + Component.UNIT_WIDTH / 2;
                        ctx.strokeLine(sx, src.getY() + Component.UNIT_HEIGHT, sx, convBarY);
                    }
                    ctx.strokeLine(avgX + Component.UNIT_WIDTH / 2, convBarY, avgX + Component.UNIT_WIDTH / 2, targetY);
                }
            }
            case LOOP_BACK -> {
                Step targetInit = findTargetInitialStep();
                if (targetInit != null) {
                    for (var comp : selected) {
                        if (comp instanceof Transition trans && targetInit.getY() < trans.getY()) {
                            Point2D begin = new Point2D(trans.getX() + Component.UNIT_WIDTH / 2, trans.getY() + Component.UNIT_HEIGHT);
                            Point2D end = new Point2D(targetInit.getX() + Component.UNIT_WIDTH / 2, targetInit.getY());
                            double gutterX = (begin.getX() > end.getX() + 20)
                                    ? Math.max(begin.getX(), end.getX()) + Component.UNIT_WIDTH * 0.9
                                    : Math.min(begin.getX(), end.getX()) - Component.UNIT_WIDTH * 0.9;
                            ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), begin.getY() + 15);
                            ctx.strokeLine(begin.getX(), begin.getY() + 15, gutterX, begin.getY() + 15);
                            ctx.strokeLine(gutterX, begin.getY() + 15, gutterX, end.getY() - 15);
                            ctx.strokeLine(gutterX, end.getY() - 15, end.getX(), end.getY() - 15);
                            ctx.strokeLine(end.getX(), end.getY() - 15, end.getX(), end.getY());
                        }
                    }
                }
            }
            case INSERT_ON_LINK -> {
                getSelectedLink().ifPresent(link -> {
                    var parent = link.getParentComponent();
                    var child = link.getChildComponent();
                    double midX = link.isDivergence() ? child.getX() : parent.getX();
                    double firstY = parent.getY() + Component.UNIT_HEIGHT + 35;
                    if (parent instanceof Step) {
                        drawGhostTransitionShape(midX, firstY);
                    } else {
                        drawGhostStepShape(midX, firstY);
                    }
                });
            }
            default -> {}
        }

        ctx.restore();
    }

    private void drawGhostLink(double x1, double y1, double x2, double y2) {
        double midY = (y1 + y2) / 2.0;
        ctx.strokeLine(x1, y1, x1, midY);
        ctx.strokeLine(x1, midY, x2, midY);
        ctx.strokeLine(x2, midY, x2, y2);
    }

    private void drawGhostStepShape(double x, double y) {
        double gap = Component.GAP;
        ctx.fillRect(x, y + gap, Component.UNIT_WIDTH, Component.UNIT_HEIGHT - gap * 2);
        ctx.strokeRect(x, y + gap, Component.UNIT_WIDTH, Component.UNIT_HEIGHT - gap * 2);
        ctx.strokeLine(x + Component.UNIT_WIDTH / 2, y, x + Component.UNIT_WIDTH / 2, y + Component.UNIT_HEIGHT);
    }

    private void drawGhostTransitionShape(double x, double y) {
        double midX = x + Component.UNIT_WIDTH / 2;
        double midY = y + Component.UNIT_HEIGHT / 2;
        ctx.strokeLine(midX, y, midX, y + Component.UNIT_HEIGHT);
        ctx.strokeLine(x + Component.UNIT_WIDTH / 4, midY, x + Component.UNIT_WIDTH * 3 / 4, midY);
    }

    public void createInitialStep() {
        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();
        double initX;
        if (components.isEmpty()) {
            initX = (canvas != null && canvas.getWidth() > 0) ? (canvas.getWidth() / 2 - Component.UNIT_WIDTH / 2) : 400;
        } else {
            double maxX = components.stream()
                    .mapToDouble(StepAndTransitionCommonBehavior::getX)
                    .max()
                    .orElse(400);
            initX = maxX + 3.0 * Component.UNIT_WIDTH;
        }
        double initY = 80;
        var initStep = new InitStep(ctx, new Point2D(initX, initY));
        initStep.setSelected(true);
        components.add(initStep);
        fireSelectionChange(Optional.of(initStep));
        refreshActionBar();
        notifyStatus("Étape initiale S" + initStep.getNumber() + " créée.");
        notifyStructureChanged();
        redraw();
    }

    public void createStep(Transition transition) {
        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();
        var step = new Step(ctx);

        double targetX = transition.getX();
        double targetY = transition.getY() + Component.UNIT_HEIGHT + 40;

        // Si la transition a déjà des enfants descendants (divergence ET)
        List<Link> downward = getDownwardLinks(transition);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(transition.getX());
            targetX = maxX + 2.2 * Component.UNIT_WIDTH;
        }

        step.position = new Point2D(targetX, targetY);
        Link.between(ctx, transition, step);
        components.add(step);
        fireSelectionChange(Optional.of(step));
        refreshActionBar();
        notifyStatus("Étape S" + step.getNumber() + " créée après la transition T" + transition.getNumber());
        notifyStructureChanged();
        redraw();
    }

    public void createTransition(Step step) {
        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();
        var transition = new Transition(ctx);

        double targetX = step.getX();
        double targetY = step.getY() + Component.UNIT_HEIGHT + 40;

        // Si l'étape a déjà des enfants descendants (divergence OU)
        List<Link> downward = getDownwardLinks(step);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(step.getX());
            targetX = maxX + 2.2 * Component.UNIT_WIDTH;
        }

        transition.position = new Point2D(targetX, targetY);
        Link.between(ctx, step, transition);
        components.add(transition);
        fireSelectionChange(Optional.of(transition));
        refreshActionBar();
        notifyStatus("Transition T" + transition.getNumber() + " créée après l'étape S" + step.getNumber());
        notifyStructureChanged();
        redraw();
    }

    /**
     * Crée en un seul clic un couple complet Transition + Étape après une étape existante.
     */
    public Step createTransitionAndStepPair(Step parentStep) {
        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();

        double targetX = parentStep.getX();
        double transY = parentStep.getY() + Component.UNIT_HEIGHT + 40;
        List<Link> downward = getDownwardLinks(parentStep);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(parentStep.getX());
            targetX = maxX + 2.2 * Component.UNIT_WIDTH;
        }
        double stepY = transY + Component.UNIT_HEIGHT + 40;

        var transition = new Transition(ctx);
        transition.setSelected(false);
        transition.position = new Point2D(targetX, transY);
        components.add(transition);
        Link.between(ctx, parentStep, transition);

        var nextStep = new Step(ctx);
        nextStep.setSelected(true);
        nextStep.position = new Point2D(targetX, stepY);
        components.add(nextStep);
        Link.between(ctx, transition, nextStep);

        fireSelectionChange(Optional.of(nextStep));
        refreshActionBar();
        notifyStatus("Séquence T" + transition.getNumber() + " → S" + nextStep.getNumber() + " insérée après S" + parentStep.getNumber());
        notifyStructureChanged();
        redraw();
        return nextStep;
    }

    /**
     * Crée en un seul clic un couple complet Étape + Transition après une transition existante.
     */
    public Transition createStepAndTransitionPair(Transition parentTransition) {
        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();

        double targetX = parentTransition.getX();
        double stepY = parentTransition.getY() + Component.UNIT_HEIGHT + 40;
        List<Link> downward = getDownwardLinks(parentTransition);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(parentTransition.getX());
            targetX = maxX + 2.2 * Component.UNIT_WIDTH;
        }
        double transY = stepY + Component.UNIT_HEIGHT + 40;

        var step = new Step(ctx);
        step.setSelected(false);
        step.position = new Point2D(targetX, stepY);
        components.add(step);
        Link.between(ctx, parentTransition, step);

        var nextTransition = new Transition(ctx);
        nextTransition.setSelected(true);
        nextTransition.position = new Point2D(targetX, transY);
        components.add(nextTransition);
        Link.between(ctx, step, nextTransition);

        fireSelectionChange(Optional.of(nextTransition));
        refreshActionBar();
        notifyStatus("Séquence S" + step.getNumber() + " → T" + nextTransition.getNumber() + " insérée après T" + parentTransition.getNumber());
        notifyStructureChanged();
        redraw();
        return nextTransition;
    }

    /**
     * Valide qu'un groupe d'étapes peut former une Convergence en ET conforme à la norme CEI 60848.
     */
    public Optional<String> validateAndConvergence(List<Step> steps) {
        return validateAndConvergenceCore(steps, null);
    }

    private Optional<String> validateAndConvergenceCore(List<Step> steps, Transition existingTarget) {
        if (steps == null || steps.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 étapes parallèles pour créer une convergence en ET.");
        }
        Set<Step> unique = new LinkedHashSet<>(steps);
        if (unique.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 étapes distinctes pour créer une convergence en ET.");
        }
        if (unique.stream().anyMatch(Step::isInitial)) {
            return Optional.of("Une étape initiale ne peut pas participer à une convergence en ET.");
        }
        for (Step s : unique) {
            List<Link> down = getDownwardLinks(s);
            boolean allowedExisting = existingTarget != null
                    && down.size() == 1
                    && down.getFirst().getChildComponent() == existingTarget;
            if (!down.isEmpty() && !allowedExisting) {
                return Optional.of("L'étape S" + s.getNumber() + " possède déjà une transition aval : elle doit être en fin de branche ouverte.");
            }
        }
        List<Step> stepList = List.copyOf(unique);
        if (wouldCauseOrToAndDeadlock(stepList)) {
            return Optional.of("Convergence en ET interdite (CEI 60848) : ces étapes sont issues d'un aiguillage en OU (Deadlock).");
        }
        if (!hasCommonAndDivergenceAncestor(stepList)) {
            return Optional.of("Convergence en ET non conforme (CEI 60848) : les étapes doivent provenir de branches parallèles distinctes d'une même divergence en ET.");
        }
        return Optional.empty();
    }

    /**
     * Valide qu'un groupe de transitions peut former une Convergence en OU conforme à la norme CEI 60848.
     */
    public Optional<String> validateOrConvergence(List<Transition> transitions) {
        return validateOrConvergenceCore(transitions, null);
    }

    private Optional<String> validateOrConvergenceCore(List<Transition> transitions, Step existingTarget) {
        if (transitions == null || transitions.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 transitions alternatives pour créer une convergence en OU.");
        }
        Set<Transition> unique = new LinkedHashSet<>(transitions);
        if (unique.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 transitions distinctes pour créer une convergence en OU.");
        }
        for (Transition t : unique) {
            List<Link> down = getDownwardLinks(t);
            boolean allowedExisting = existingTarget != null
                    && down.size() == 1
                    && down.getFirst().getChildComponent() == existingTarget;
            if (!down.isEmpty() && !allowedExisting) {
                return Optional.of("La transition T" + t.getNumber() + " possède déjà une étape aval : elle doit être en fin de branche ouverte.");
            }
        }
        List<Transition> transList = List.copyOf(unique);
        if (wouldCauseAndToOrUnsyncedConvergence(transList)) {
            return Optional.of("Convergence en OU interdite (CEI 60848) : ces transitions sont issues de branches parallèles en ET non synchronisées.");
        }
        if (!hasCommonOrDivergenceAncestor(transList)) {
            return Optional.of("Convergence en OU non conforme (CEI 60848) : les transitions doivent provenir de branches alternatives distinctes d'une même divergence en OU.");
        }
        return Optional.empty();
    }

    /**
     * Valide qu'un raccordement manuel (glisser-déposer) vers un élément cible ne crée pas une convergence non conforme.
     */
    public Optional<String> validateManualConvergenceLink(StepAndTransitionCommonBehavior source,
                                                          StepAndTransitionCommonBehavior target) {
        if (source == null || target == null || source == target) {
            return Optional.empty();
        }
        // Une liaison ascendante (boucle de reprise) n'est pas une convergence descendante
        boolean isAscending = target.getY() < source.getY() + Component.UNIT_HEIGHT;
        if (isAscending) {
            return Optional.empty();
        }
        List<StepAndTransitionCommonBehavior> existingDownwardParents = target.getLinksFromParents().stream()
                .filter(l -> !l.isAscending())
                .map(Link::getParentComponent)
                .toList();
        if (existingDownwardParents.isEmpty()) {
            return Optional.empty();
        }
        if (target instanceof Transition targetTrans && source instanceof Step sourceStep) {
            List<Step> allParents = new ArrayList<>();
            for (var p : existingDownwardParents) {
                if (p instanceof Step s) allParents.add(s);
            }
            allParents.add(sourceStep);
            return validateAndConvergenceCore(allParents, targetTrans);
        }
        if (target instanceof Step targetStep && source instanceof Transition sourceTrans) {
            List<Transition> allParents = new ArrayList<>();
            for (var p : existingDownwardParents) {
                if (p instanceof Transition t) allParents.add(t);
            }
            allParents.add(sourceTrans);
            return validateOrConvergenceCore(allParents, targetStep);
        }
        return Optional.empty();
    }

    /**
     * Recherche les autres étapes de fin de branche (sans liaison descendante) pouvant converger en ET
     * avec l'étape donnée selon la norme CEI 60848.
     */
    public List<Step> findConvergenceCandidateSteps(Step primary) {
        if (primary == null) return List.of();
        return components.stream()
                .filter(Step.class::isInstance)
                .map(Step.class::cast)
                .filter(s -> s != primary && validateAndConvergence(List.of(primary, s)).isEmpty())
                .toList();
    }

    /**
     * Recherche les autres transitions de fin de branche (sans liaison descendante) pouvant converger en OU
     * avec la transition donnée selon la norme CEI 60848.
     */
    public List<Transition> findConvergenceCandidateTransitions(Transition primary) {
        if (primary == null) return List.of();
        return components.stream()
                .filter(Transition.class::isInstance)
                .map(Transition.class::cast)
                .filter(t -> t != primary && validateOrConvergence(List.of(primary, t)).isEmpty())
                .toList();
    }

    private boolean hasCommonAndDivergenceAncestor(List<Step> steps) {
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Transition divTrans) {
                List<Link> branches = getDownwardLinks(divTrans);
                if (branches.size() < 2) continue;
                List<Set<Step>> branchStepSets = new ArrayList<>();
                for (Link bLink : branches) {
                    if (bLink.getChildComponent() instanceof Step startStep) {
                        branchStepSets.add(collectAllDownwardSteps(startStep));
                    } else {
                        branchStepSets.add(Set.of());
                    }
                }
                if (canAssignDistinctBranches(steps, branchStepSets)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCommonOrDivergenceAncestor(List<Transition> transitions) {
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step divStep) {
                List<Link> branches = getDownwardLinks(divStep);
                if (branches.size() < 2) continue;
                List<Set<Transition>> branchTransSets = new ArrayList<>();
                for (Link bLink : branches) {
                    if (bLink.getChildComponent() instanceof Transition startTrans) {
                        branchTransSets.add(collectAllDownwardTransitions(startTrans));
                    } else {
                        branchTransSets.add(Set.of());
                    }
                }
                if (canAssignDistinctBranches(transitions, branchTransSets)) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T> boolean canAssignDistinctBranches(List<T> elements, List<Set<T>> branchSets) {
        return assignBranchesRecursive(elements, 0, branchSets, new boolean[branchSets.size()]);
    }

    private <T> boolean assignBranchesRecursive(List<T> elements, int idx, List<Set<T>> branchSets, boolean[] usedBranch) {
        if (idx == elements.size()) {
            return true;
        }
        T elem = elements.get(idx);
        for (int b = 0; b < branchSets.size(); b++) {
            if (!usedBranch[b] && branchSets.get(b).contains(elem)) {
                usedBranch[b] = true;
                if (assignBranchesRecursive(elements, idx + 1, branchSets, usedBranch)) {
                    return true;
                }
                usedBranch[b] = false;
            }
        }
        return false;
    }

    private Set<Step> collectAllDownwardSteps(Step startStep) {
        Set<Step> visitedSteps = new LinkedHashSet<>();
        Queue<Step> queue = new ArrayDeque<>();
        visitedSteps.add(startStep);
        queue.add(startStep);
        while (!queue.isEmpty()) {
            Step s = queue.poll();
            for (Link tLink : getDownwardLinks(s)) {
                if (tLink.getChildComponent() instanceof Transition t) {
                    for (Link sLink : getDownwardLinks(t)) {
                        if (sLink.getChildComponent() instanceof Step nextS && visitedSteps.add(nextS)) {
                            queue.add(nextS);
                        }
                    }
                }
            }
        }
        return visitedSteps;
    }

    private Set<Transition> collectAllDownwardTransitions(Transition startTrans) {
        Set<Transition> visitedTrans = new LinkedHashSet<>();
        Queue<Transition> queue = new ArrayDeque<>();
        visitedTrans.add(startTrans);
        queue.add(startTrans);
        while (!queue.isEmpty()) {
            Transition t = queue.poll();
            for (Link sLink : getDownwardLinks(t)) {
                if (sLink.getChildComponent() instanceof Step s) {
                    for (Link tLink : getDownwardLinks(s)) {
                        if (tLink.getChildComponent() instanceof Transition nextT && visitedTrans.add(nextT)) {
                            queue.add(nextT);
                        }
                    }
                }
            }
        }
        return visitedTrans;
    }

    /**
     * Vérifie si la convergence en ET d'un groupe d'étapes refermerait illégalement une Divergence en OU (Deadlock CEI 60848).
     */
    public boolean wouldCauseOrToAndDeadlock(List<Step> candidateSteps) {
        if (candidateSteps == null || candidateSteps.size() < 2) return false;
        Set<Step> targetSet = new HashSet<>(candidateSteps);

        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step divStep) {
                List<Link> branchLinks = getDownwardLinks(divStep);
                if (branchLinks.size() < 2) continue;

                int matchedBranches = 0;
                for (Link bLink : branchLinks) {
                    if (bLink.getChildComponent() instanceof Transition startTrans) {
                        if (getDownwardLinks(startTrans).size() > 1) continue;
                        Set<Step> reachable = collectDownwardStepsWithoutAndDivergence(startTrans);
                        if (reachable.stream().anyMatch(targetSet::contains)) {
                            matchedBranches++;
                        }
                    }
                }
                if (matchedBranches >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Vérifie si la convergence en OU d'un groupe de transitions refermerait illégalement une Divergence en ET sans synchronisation.
     */
    public boolean wouldCauseAndToOrUnsyncedConvergence(List<Transition> candidateTransitions) {
        if (candidateTransitions == null || candidateTransitions.size() < 2) return false;
        Set<Transition> targetSet = new HashSet<>(candidateTransitions);

        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Transition divTrans) {
                List<Link> branchLinks = getDownwardLinks(divTrans);
                if (branchLinks.size() < 2) continue;

                int matchedBranches = 0;
                for (Link bLink : branchLinks) {
                    if (bLink.getChildComponent() instanceof Step startStep) {
                        Set<Transition> reachable = collectDownwardTransitionsWithoutAndConvergence(startStep);
                        if (reachable.stream().anyMatch(targetSet::contains)) {
                            matchedBranches++;
                        }
                    }
                }
                if (matchedBranches >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<Step> collectDownwardStepsWithoutAndDivergence(Transition startTrans) {
        Set<Step> visitedSteps = new LinkedHashSet<>();
        Queue<Transition> queueTrans = new ArrayDeque<>();
        Set<Transition> visitedTrans = new HashSet<>();
        queueTrans.add(startTrans);
        visitedTrans.add(startTrans);

        while (!queueTrans.isEmpty()) {
            Transition t = queueTrans.poll();
            List<Link> nextLinks = getDownwardLinks(t);
            if (nextLinks.size() > 1) continue;
            for (Link l : nextLinks) {
                if (l.getChildComponent() instanceof Step s && visitedSteps.add(s)) {
                    for (Link nextTL : getDownwardLinks(s)) {
                        if (nextTL.getChildComponent() instanceof Transition nextT && visitedTrans.add(nextT)) {
                            queueTrans.add(nextT);
                        }
                    }
                }
            }
        }
        return visitedSteps;
    }

    private Set<Transition> collectDownwardTransitionsWithoutAndConvergence(Step startStep) {
        Set<Transition> visitedTrans = new LinkedHashSet<>();
        Queue<Step> queueSteps = new ArrayDeque<>();
        Set<Step> visitedSteps = new HashSet<>();
        queueSteps.add(startStep);
        visitedSteps.add(startStep);

        while (!queueSteps.isEmpty()) {
            Step s = queueSteps.poll();
            for (Link l : getDownwardLinks(s)) {
                if (l.getChildComponent() instanceof Transition t) {
                    long downParents = t.getLinksFromParents().stream().filter(pl -> !pl.isAscending()).count();
                    if (downParents > 1) continue;
                    if (visitedTrans.add(t)) {
                        for (Link nextSL : getDownwardLinks(t)) {
                            if (nextSL.getChildComponent() instanceof Step nextS && visitedSteps.add(nextS)) {
                                queueSteps.add(nextS);
                            }
                        }
                    }
                }
            }
        }
        return visitedTrans;
    }

    /**
     * Converge automatiquement un composant sélectionné avec les autres fins de branches ouvertes compatibles CEI 60848.
     */
    public void convergeWithOpenSiblings(StepAndTransitionCommonBehavior comp) {
        if (comp instanceof Step step) {
            List<Step> group = new ArrayList<>();
            group.add(step);
            group.addAll(findConvergenceCandidateSteps(step));
            if (group.size() >= 2) {
                createAndConvergence(group);
            } else {
                notifyStatus("Sélectionnez au moins 2 étapes parallèles compatibles CEI 60848 pour créer une convergence en ET.");
            }
        } else if (comp instanceof Transition trans) {
            List<Transition> group = new ArrayList<>();
            group.add(trans);
            group.addAll(findConvergenceCandidateTransitions(trans));
            if (group.size() >= 2) {
                createOrConvergence(group);
            } else {
                notifyStatus("Sélectionnez au moins 2 transitions alternatives compatibles CEI 60848 pour créer une convergence en OU.");
            }
        }
    }

    /**
     * Crée une Convergence en ET (synchronisation à double barre CEI 60848) reliant plusieurs étapes vers une transition commune.
     */
    public Transition createAndConvergence(List<Step> steps) {
        Optional<String> validationError = validateAndConvergence(steps);
        if (validationError.isPresent()) {
            notifyStatus("⚠ " + validationError.get());
            return null;
        }

        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();

        double avgX = steps.stream().mapToDouble(Step::getX).average().orElse(400.0);
        double maxY = steps.stream().mapToDouble(Step::getY).max().orElse(200.0);
        double targetY = maxY + Component.UNIT_HEIGHT + 45.0;

        var commonTransition = new Transition(ctx);
        commonTransition.position = new Point2D(avgX, targetY);
        commonTransition.setSelected(true);
        components.add(commonTransition);

        for (Step s : steps) {
            Link.between(ctx, s, commonTransition);
        }

        fireSelectionChange(Optional.of(commonTransition));
        refreshActionBar();
        notifyStatus("Convergence en ET créée vers T" + commonTransition.getNumber() + " (" + steps.size() + " branches synchronisées)");
        notifyStructureChanged();
        redraw();
        return commonTransition;
    }

    /**
     * Crée une Convergence en OU (regroupement de séquences CEI 60848) reliant plusieurs transitions vers une étape commune.
     */
    public Step createOrConvergence(List<Transition> transitions) {
        Optional<String> validationError = validateOrConvergence(transitions);
        if (validationError.isPresent()) {
            notifyStatus("⚠ " + validationError.get());
            return null;
        }

        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();

        double avgX = transitions.stream().mapToDouble(Transition::getX).average().orElse(400.0);
        double maxY = transitions.stream().mapToDouble(Transition::getY).max().orElse(200.0);
        double targetY = maxY + Component.UNIT_HEIGHT + 45.0;

        var commonStep = new Step(ctx);
        commonStep.position = new Point2D(avgX, targetY);
        commonStep.setSelected(true);
        components.add(commonStep);

        for (Transition t : transitions) {
            Link.between(ctx, t, commonStep);
        }

        fireSelectionChange(Optional.of(commonStep));
        refreshActionBar();
        notifyStatus("Convergence en OU créée vers S" + commonStep.getNumber() + " (" + transitions.size() + " branches regroupées)");
        notifyStructureChanged();
        redraw();
        return commonStep;
    }

    /**
     * Retourne l'étape initiale principale (ou la plus haute étape du schéma) pour le rebouclage de cycle.
     */
    public Step findTargetInitialStep() {
        return components.stream()
                .filter(Step.class::isInstance)
                .map(Step.class::cast)
                .sorted(Comparator.comparing(Step::isInitial).reversed()
                        .thenComparingDouble(Step::getY))
                .findFirst()
                .orElse(null);
    }

    /**
     * Ferme le cycle en raccordant la transition donnée à l'étape initiale du Grafcet.
     */
    public Link loopBackToInitialStep(Transition transition) {
        Step target = findTargetInitialStep();
        if (target == null) return null;
        return loopBackToStep(transition, target);
    }

    public Link loopBackToStep(Transition transition, Step targetStep) {
        if (transition == null || targetStep == null) return null;
        Link link = Link.between(ctx, transition, targetStep);
        refreshActionBar();
        notifyStatus("Boucle de reprise créée : T" + transition.getNumber() + " ⮐ S" + targetStep.getNumber());
        notifyStructureChanged();
        redraw();
        return link;
    }

    /**
     * Ouvre une liaison existante, décale les éléments aval vers le bas et insère un couple Étape + Transition
     * respectant l'alternance stricte CEI 60848 et l'axe X de la branche.
     */
    public void insertPairOnLink(Link link) {
        if (link == null) return;
        var parent = link.getParentComponent();
        var child = link.getChildComponent();
        boolean wasAscending = link.isAscending();
        boolean wasDivergence = link.isDivergence();

        link.disconnect();

        if (wasAscending) {
            // Si c'était une boucle de retour, insérer en bas du parent puis reboucler vers child
            if (parent instanceof Transition trans && child instanceof Step targetStep) {
                Transition nextTrans = createStepAndTransitionPair(trans);
                Link.between(ctx, nextTrans, targetStep);
            } else if (parent instanceof Step step && child instanceof Transition targetTrans) {
                Step nextStep = createTransitionAndStepPair(step);
                Link.between(ctx, nextStep, targetTrans);
            }
            refreshActionBar();
            notifyStructureChanged();
            redraw();
            return;
        }

        // Décaler tous les descendants aval vers le bas pour faire de la place au couple inséré
        double shiftY = 2 * (Component.UNIT_HEIGHT + 40);
        shiftDownstreamComponents(child, shiftY);

        components.forEach(c -> c.setSelected(false));
        clearLinkSelections();

        double midX = wasDivergence ? child.getX() : parent.getX();
        double firstY = parent.getY() + Component.UNIT_HEIGHT + 40;
        double secondY = firstY + Component.UNIT_HEIGHT + 40;

        if (parent instanceof Step step && child instanceof Transition transChild) {
            // S_parent -> [T_new -> S_new] -> T_child
            var newTrans = new Transition(ctx);
            newTrans.setSelected(false);
            newTrans.position = new Point2D(midX, firstY);
            components.add(newTrans);

            var newStep = new Step(ctx);
            newStep.setSelected(true);
            newStep.position = new Point2D(midX, secondY);
            components.add(newStep);

            Link.between(ctx, step, newTrans);
            Link.between(ctx, newTrans, newStep);
            Link.between(ctx, newStep, transChild);

            fireSelectionChange(Optional.of(newStep));
            notifyStatus("Couple T" + newTrans.getNumber() + " → S" + newStep.getNumber() + " inséré sur la liaison.");
        } else if (parent instanceof Transition trans && child instanceof Step stepChild) {
            // T_parent -> [S_new -> T_new] -> S_child
            var newStep = new Step(ctx);
            newStep.setSelected(true);
            newStep.position = new Point2D(midX, firstY);
            components.add(newStep);

            var newTrans = new Transition(ctx);
            newTrans.setSelected(false);
            newTrans.position = new Point2D(midX, secondY);
            components.add(newTrans);

            Link.between(ctx, trans, newStep);
            Link.between(ctx, newStep, newTrans);
            Link.between(ctx, newTrans, stepChild);

            fireSelectionChange(Optional.of(newStep));
            notifyStatus("Couple S" + newStep.getNumber() + " → T" + newTrans.getNumber() + " inséré sur la liaison.");
        }

        refreshActionBar();
        notifyStructureChanged();
        redraw();
    }

    private void shiftDownstreamComponents(StepAndTransitionCommonBehavior start, double deltaY) {
        Set<StepAndTransitionCommonBehavior> visited = new HashSet<>();
        Queue<StepAndTransitionCommonBehavior> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            var current = queue.poll();
            current.updatePositionAndLinks(new Point2D(current.getX(), current.getY() + deltaY));
            for (Link l : current.getLinksToChildren()) {
                if (!l.isAscending()) {
                    var next = l.getChildComponent();
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
    }

    public void toggleInitialStep(Step step) {
        if (step == null) return;
        step.setInitial(!step.isInitial());
        fireSelectionChange(Optional.of(step));
        refreshActionBar();
        notifyStatus("Étape S" + step.getNumber() + (step.isInitial() ? " définie comme étape initiale." : " définie comme étape normale."));
        notifyStructureChanged();
        redraw();
    }

    public void editTransitionReceptivityInline(Transition transition) {
        if (transition != null) {
            openInlineReceptivityEditor(transition);
        }
    }

    public void alignSelectedHorizontally() {
        List<StepAndTransitionCommonBehavior> selected = getSelectedComponents();
        if (selected.size() < 2) return;
        double avgY = selected.stream().mapToDouble(StepAndTransitionCommonBehavior::getY).average().orElse(200.0);
        double snappedY = Math.round(avgY / 12.0) * 12.0;
        for (var comp : selected) {
            comp.updatePositionAndLinks(new Point2D(comp.getX(), snappedY));
        }
        refreshActionBar();
        notifyStatus(selected.size() + " éléments alignés horizontalement.");
        notifyStructureChanged();
        redraw();
    }

    public void createOrBranch(Step step) {
        if (step == null) return;
        if (getDownwardLinks(step).isEmpty()) {
            // Aucune transition aval : créer les 2 branches d'aiguillage en OU d'un seul coup (CEI 60848)
            createTransition(step);
            createTransition(step);
        } else {
            createTransition(step);
        }
        notifyStatus("Branche OU créée (Sélection de séquence CEI 60848)");
    }

    public void createAndBranch(Transition transition) {
        if (transition == null) return;
        if (getDownwardLinks(transition).isEmpty()) {
            // Aucune étape aval : créer les 2 branches parallèles en ET d'un seul coup (CEI 60848)
            createStep(transition);
            createStep(transition);
        } else {
            createStep(transition);
        }
        notifyStatus("Branche ET créée (Synchronisation / Parallélisme CEI 60848)");
    }

    public void deleteSelected() {
        List<StepAndTransitionCommonBehavior> selectedList = new ArrayList<>(getSelectedComponents());
        if (!selectedList.isEmpty()) {
            for (var comp : selectedList) {
                List<Link> toRemove = new ArrayList<>();
                toRemove.addAll(comp.getLinksFromParents());
                toRemove.addAll(comp.getLinksToChildren());
                toRemove.forEach(Link::disconnect);
                components.remove(comp);
            }
            fireSelectionChange(Optional.empty());
            refreshActionBar();
            notifyStatus(selectedList.size() == 1
                    ? ("Élément " + getElementName(selectedList.getFirst()) + " supprimé.")
                    : (selectedList.size() + " éléments supprimés."));
            notifyStructureChanged();
            redraw();
            return;
        }

        getSelectedLink().ifPresent(link -> {
            link.disconnect();
            refreshActionBar();
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
        refreshActionBar();
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
        this.simulationRunning = true;
        refreshActionBar();
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
        this.simulationRunning = false;
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step step) {
                step.setActive(false);
                step.setActiveOutputs(Set.of());
            } else if (comp instanceof Transition trans) {
                trans.clearSimulationState();
            }
        }
        refreshActionBar();
        redraw();
    }

    /**
     * Met à jour en direct les pastilles d'erreurs et d'avertissements sur les étapes et transitions du Canvas.
     */
    public void updateDiagnosticsVisuals(List<SfcValidationMessage> messages) {
        for (StepAndTransitionCommonBehavior comp : components) {
            comp.setDiagnosticMessages(List.of());
        }
        if (messages == null || messages.isEmpty()) {
            redraw();
            return;
        }

        Map<StepAndTransitionCommonBehavior, List<SfcValidationMessage>> perComponent = new HashMap<>();
        for (SfcValidationMessage msg : messages) {
            String ref = msg.elementId();
            if (ref == null || ref.isBlank()) {
                // Message global (ex: absence d'étape initiale) -> attacher à la première étape si existante
                components.stream()
                        .filter(Step.class::isInstance)
                        .findFirst()
                        .ifPresent(firstStep -> perComponent.computeIfAbsent(firstStep, k -> new ArrayList<>()).add(msg));
                continue;
            }

            findComponentByRef(ref).ifPresent(comp ->
                    perComponent.computeIfAbsent(comp, k -> new ArrayList<>()).add(msg)
            );
        }

        for (var entry : perComponent.entrySet()) {
            entry.getKey().setDiagnosticMessages(entry.getValue());
        }
        redraw();
    }

    private Optional<StepAndTransitionCommonBehavior> findComponentByRef(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        String clean = ref.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("l_")) {
            String[] parts = clean.split("_");
            if (parts.length >= 2) {
                clean = parts[1];
            }
        }
        String digits = clean.replaceAll("[^0-9]", "");
        int targetNum = digits.isEmpty() ? -1 : Integer.parseInt(digits);

        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step s) {
                if (clean.startsWith("s") && (s.getNumber() == targetNum
                        || ("s" + s.getNumber()).equalsIgnoreCase(clean)
                        || ("step" + s.getNumber()).equalsIgnoreCase(clean))) {
                    return Optional.of(comp);
                }
            } else if (comp instanceof Transition t) {
                if (clean.startsWith("t") && (t.getNumber() == targetNum
                        || ("t" + t.getNumber()).equalsIgnoreCase(clean))) {
                    return Optional.of(comp);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Sélectionne et met en surbrillance un composant à partir d'une référence (ex: "s1", "t2", "Step1", "l_s1_t2").
     */
    public boolean selectComponentByReference(String ref) {
        Optional<StepAndTransitionCommonBehavior> found = findComponentByRef(ref);
        if (found.isPresent()) {
            StepAndTransitionCommonBehavior comp = found.get();
            components.forEach(c -> c.setSelected(false));
            clearLinkSelections();
            comp.setSelected(true);
            fireSelectionChange(Optional.of(comp));
            refreshActionBar();
            redraw();
            return true;
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

        // 5. Conversion explicite des divergences et convergences (CEI 61131-3)
        List<SfcBranch> sfcBranches = new ArrayList<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            String compId = (comp instanceof Step s) ? ("s" + s.getNumber()) : ("t" + comp.getNumber());
            List<Link> downOut = getDownwardLinks(comp);
            if (downOut.size() >= 2) {
                SfcBranchType divType = (comp instanceof Transition) ? SfcBranchType.DIVERGENCE_AND : SfcBranchType.DIVERGENCE_OR;
                List<String> downIds = downOut.stream()
                        .map(l -> (l.getChildComponent() instanceof Step s) ? ("s" + s.getNumber()) : ("t" + l.getChildComponent().getNumber()))
                        .toList();
                sfcBranches.add(new SfcBranch("b_div_" + compId, divType.name().toLowerCase(Locale.ROOT) + "_" + compId,
                        divType, comp.getX(), comp.getY() + Component.UNIT_HEIGHT + 20, List.of(compId), downIds));
            }
            List<Link> downIn = comp.getLinksFromParents().stream().filter(l -> !l.isAscending()).toList();
            if (downIn.size() >= 2) {
                SfcBranchType convType = (comp instanceof Transition) ? SfcBranchType.CONVERGENCE_AND : SfcBranchType.CONVERGENCE_OR;
                List<String> upIds = downIn.stream()
                        .map(l -> (l.getParentComponent() instanceof Step s) ? ("s" + s.getNumber()) : ("t" + l.getParentComponent().getNumber()))
                        .toList();
                sfcBranches.add(new SfcBranch("b_conv_" + compId, convType.name().toLowerCase(Locale.ROOT) + "_" + compId,
                        convType, comp.getX(), comp.getY() - 20, upIds, List.of(compId)));
            }
        }

        return new SfcProgram(name, iecVars, sfcSteps, sfcTransitions, sfcBranches, sfcLinks);
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
        } else {
            components.forEach(c -> c.setSelected(false));
        }

        fireSelectionChange(Optional.empty());
        refreshActionBar();
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

        // Désactivation temporaire de la sélection et des badges de diagnostic
        Map<Component, Boolean> originalSelection = new HashMap<>();
        Map<StepAndTransitionCommonBehavior, List<SfcValidationMessage>> originalDiagnostics = new HashMap<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            originalSelection.put(comp, comp.isSelected());
            originalDiagnostics.put(comp, comp.getDiagnosticMessages());
            comp.setSelected(false);
            comp.setDiagnosticMessages(List.of());
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
            for (var entry : originalDiagnostics.entrySet()) {
                entry.getKey().setDiagnosticMessages(entry.getValue());
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
