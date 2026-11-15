package tk.tahri.grafino.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tk.tahri.grafino.Handle;
import tk.tahri.grafino.Operation;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.*;
import tk.tahri.grafino.ui.canvas.GrafcetHistoryManager;
import tk.tahri.grafino.ui.canvas.GrafcetProgramMapper;
import tk.tahri.grafino.ui.canvas.GrafcetRenderer;
import tk.tahri.grafino.ui.canvas.GrafcetTopologyService;

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

    // Espacements par défaut lors de la création et du réalignement automatique
    private static final double CREATION_V_GAP = 16.0;
    public static final double MIN_VERTICAL_GAP = CREATION_V_GAP;
    private static final double CONVERGENCE_V_GAP = 20.0;
    private static final double BRANCH_H_SPACING = 1.8 * Component.UNIT_WIDTH;
    private static final double SECONDARY_DIAGRAM_H_SPACING = 2.4 * Component.UNIT_WIDTH;

    // Barre d'action contextuelle flottante & Aperçu fantôme (Ghost Preview)
    private final ActionBar actionBar;
    private ActionBar.GhostPreviewType activeGhostPreview = ActionBar.GhostPreviewType.NONE;
    private boolean simulationRunning = false;

    private Consumer<Optional<StepAndTransitionCommonBehavior>> selectedComponent;
    private Consumer<String> statusMessageConsumer;
    private Runnable onStructureChanged;
    private java.util.function.Supplier<List<Variable>> variablesSupplier = List::of;

    // Historique Annuler / Rétablir (Undo / Redo)
    private final GrafcetHistoryManager historyManager = new GrafcetHistoryManager();
    private boolean suppressHistoryRecording = false;
    private SfcProgram pendingDragSnapshot = null;

    public void setVariablesSupplier(java.util.function.Supplier<List<Variable>> variablesSupplier) {
        this.variablesSupplier = variablesSupplier != null ? variablesSupplier : List::of;
    }

    public void setOnHistoryChanged(Runnable onHistoryChanged) {
        historyManager.setOnHistoryChanged(onHistoryChanged);
    }

    public boolean canUndo() {
        return historyManager.canUndo();
    }

    public boolean canRedo() {
        return historyManager.canRedo();
    }

    public void recordUndoSnapshot() {
        if (!suppressHistoryRecording) {
            historyManager.recordSnapshot(captureDiagramSnapshot());
        }
    }

    private SfcProgram captureDiagramSnapshot() {
        return toProgram("Snapshot", variablesSupplier.get());
    }

    public void undo() {
        if (simulationRunning) {
            notifyStatus("Annulation indisponible pendant la simulation.");
            return;
        }
        historyManager.undo(captureDiagramSnapshot()).ifPresent(previous -> {
            restoreFromSnapshot(previous);
            notifyStatus("Action annulée.");
        });
    }

    public void redo() {
        if (simulationRunning) {
            notifyStatus("Rétablissement indisponible pendant la simulation.");
            return;
        }
        historyManager.redo(captureDiagramSnapshot()).ifPresent(next -> {
            restoreFromSnapshot(next);
            notifyStatus("Action rétablie.");
        });
    }

    private void restoreFromSnapshot(SfcProgram snapshot) {
        suppressHistoryRecording = true;
        try {
            components.clear();
            activeGuideLines.clear();
            closeInlineEditor();
            GrafcetProgramMapper.populateFromProgram(snapshot, ctx, components, variablesSupplier.get());
            components.forEach(c -> c.setSelected(false));
            fireSelectionChange(Optional.empty());
            refreshActionBar();
            notifyStructureChanged();
            redraw();
        } finally {
            suppressHistoryRecording = false;
        }
    }

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
                    pendingDragSnapshot = captureDiagramSnapshot();
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
                        var snapped = computeSmartSnap(component, rawX, rawY);
                        var selectedList = getSelectedComponents();
                        var movingGroup = Set.copyOf(selectedList);

                        var clampedPrimary = clampPositionWithoutOverlap(component, snapped.getX(), snapped.getY(), movingGroup);
                        double dx = clampedPrimary.getX() - component.getX();
                        double dy = clampedPrimary.getY() - component.getY();

                        if (selectedList.size() > 1) {
                            double groupDy = GrafcetTopologyService.clampGroupDeltaY(components, selectedList, movingGroup, dx, dy);
                            for (var member : selectedList) {
                                member.updatePositionAndLinks(new Point2D(member.getX() + dx, member.getY() + groupDy));
                            }
                        } else {
                            component.updatePositionAndLinks(clampedPrimary);
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
                        if (pendingDragSnapshot != null && !suppressHistoryRecording) {
                            historyManager.recordSnapshot(pendingDragSnapshot);
                        }
                        notifyStructureChanged();
                    }
                });
                dragStartPos = null;
                pendingDragSnapshot = null;
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
        return GrafcetTopologyService.computeSmartSnap(components, activeGuideLines, draggedComp, rawX, rawY);
    }

    public Point2D clampPositionWithoutOverlap(StepAndTransitionCommonBehavior draggedComp, double targetX, double targetY) {
        return clampPositionWithoutOverlap(draggedComp, targetX, targetY, Set.of(draggedComp));
    }

    public Point2D clampPositionWithoutOverlap(
            StepAndTransitionCommonBehavior draggedComp,
            double targetX,
            double targetY,
            Set<StepAndTransitionCommonBehavior> movingGroup) {
        return GrafcetTopologyService.clampPositionWithoutOverlap(components, draggedComp, targetX, targetY, movingGroup);
    }

    private String getElementName(StepAndTransitionCommonBehavior comp) {
        return switch (comp) {
            case Step s -> "Étape S" + s.getNumber();
            case Transition t -> "Transition T" + t.getNumber();
        };
    }

    public void autoLayout() {
        if (components.isEmpty()) return;
        recordUndoSnapshot();
        double width = (canvas != null) ? canvas.getWidth() : 0;
        GrafcetTopologyService.autoLayout(components, width);
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
        GrafcetRenderer.drawBackgroundGrid(ctx, w, h, panX, panY, zoomFactor);

        // Application du Pan & Zoom
        ctx.translate(panX, panY);
        ctx.scale(zoomFactor, zoomFactor);

        // Tracé des lignes de guidage magnétique temporaires
        GrafcetRenderer.drawAlignmentGuides(ctx, activeGuideLines);

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
            GrafcetRenderer.drawGhostPreview(ctx, this, activeGhostPreview, CREATION_V_GAP, CONVERGENCE_V_GAP, BRANCH_H_SPACING);
        }

        // Tracé du rectangle de sélection multiple (Lasso / Marquee)
        if (operation == Operation.MARQUEE_SELECT && marqueeStartDiag != null && marqueeCurrentDiag != null) {
            GrafcetRenderer.drawMarqueeSelectionBox(ctx, marqueeStartDiag, marqueeCurrentDiag);
        }

        // Tracé du lien dynamique en cours d'étirement
        if (mouseDragPosition != null && (operation == Operation.PRESSED_TO_BOTTOM_LINK || operation == Operation.PRESSED_TO_TOP_LINK)) {
            boolean fromBottom = operation == Operation.PRESSED_TO_BOTTOM_LINK;
            getSelectedComponent().ifPresent(comp -> {
                var preview = evaluateManualLinkPreview(comp, mouseDragPosition, fromBottom);
                GrafcetRenderer.drawDraggedLinkPreview(ctx, preview, mouseDragPosition);
            });
        }

        ctx.restore();

        // Mise à jour de la position écran de la barre d'action flottante
        updateActionBarPosition();
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
                    recordUndoSnapshot();
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
     * Positionne la barre d'outils verticale à un emplacement fixe en haut à gauche du Canvas
     * afin d'éviter tout déplacement vertical lors des changements de sélection.
     */
    private void updateActionBarPosition() {
        if (actionBar == null || !actionBar.isVisible()) return;

        actionBar.autosize();
        double fixedLeftX = 14.0;
        double fixedTopY = 14.0;

        actionBar.setLayoutX(fixedLeftX);
        actionBar.setLayoutY(fixedTopY);
    }

    public List<Link> getDownwardLinks(StepAndTransitionCommonBehavior comp) {
        if (comp == null) return List.of();
        return GrafcetTopologyService.getDownwardLinks(comp);
    }

    public void createInitialStep() {
        recordUndoSnapshot();
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
            initX = maxX + SECONDARY_DIAGRAM_H_SPACING;
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
        recordUndoSnapshot();
        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();
        var step = new Step(ctx);

        double targetX = transition.getX();
        double targetY = transition.getY() + Component.UNIT_HEIGHT + CREATION_V_GAP;

        // Si la transition a déjà des enfants descendants (divergence ET)
        List<Link> downward = getDownwardLinks(transition);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(transition.getX());
            targetX = maxX + BRANCH_H_SPACING;
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
        recordUndoSnapshot();
        components.forEach(com -> com.setSelected(false));
        clearLinkSelections();
        var transition = new Transition(ctx);

        double targetX = step.getX();
        double targetY = step.getY() + Component.UNIT_HEIGHT + CREATION_V_GAP;

        // Si l'étape a déjà des enfants descendants (divergence OU)
        List<Link> downward = getDownwardLinks(step);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(step.getX());
            targetX = maxX + BRANCH_H_SPACING;
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
        double transY = parentStep.getY() + Component.UNIT_HEIGHT + CREATION_V_GAP;
        List<Link> downward = getDownwardLinks(parentStep);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(parentStep.getX());
            targetX = maxX + BRANCH_H_SPACING;
        }
        double stepY = transY + Component.UNIT_HEIGHT + CREATION_V_GAP;

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
        double stepY = parentTransition.getY() + Component.UNIT_HEIGHT + CREATION_V_GAP;
        List<Link> downward = getDownwardLinks(parentTransition);
        if (!downward.isEmpty()) {
            double maxX = downward.stream()
                    .mapToDouble(l -> l.getChildComponent().getX())
                    .max().orElse(parentTransition.getX());
            targetX = maxX + BRANCH_H_SPACING;
        }
        double transY = stepY + Component.UNIT_HEIGHT + CREATION_V_GAP;

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

    public Optional<String> validateAndConvergence(List<Step> steps) {
        return GrafcetTopologyService.validateAndConvergence(components, steps, null);
    }

    public Optional<String> validateOrConvergence(List<Transition> transitions) {
        return GrafcetTopologyService.validateOrConvergence(components, transitions, null);
    }

    public Optional<String> validateManualConvergenceLink(StepAndTransitionCommonBehavior source,
                                                          StepAndTransitionCommonBehavior target) {
        return GrafcetTopologyService.validateManualConvergenceLink(components, source, target);
    }

    public List<Step> findConvergenceCandidateSteps(Step primary) {
        return GrafcetTopologyService.findConvergenceCandidateSteps(components, primary);
    }

    public List<Transition> findConvergenceCandidateTransitions(Transition primary) {
        return GrafcetTopologyService.findConvergenceCandidateTransitions(components, primary);
    }

    public boolean wouldCauseOrToAndDeadlock(List<Step> candidateSteps) {
        return GrafcetTopologyService.wouldCauseOrToAndDeadlock(components, candidateSteps);
    }

    public boolean wouldCauseAndToOrUnsyncedConvergence(List<Transition> candidateTransitions) {
        return GrafcetTopologyService.wouldCauseAndToOrUnsyncedConvergence(components, candidateTransitions);
    }

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
        double targetY = maxY + Component.UNIT_HEIGHT + CONVERGENCE_V_GAP;

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
        double targetY = maxY + Component.UNIT_HEIGHT + CONVERGENCE_V_GAP;

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
        double shiftY = 2 * (Component.UNIT_HEIGHT + CREATION_V_GAP);
        shiftDownstreamComponents(child, shiftY);

        components.forEach(c -> c.setSelected(false));
        clearLinkSelections();

        double midX = wasDivergence ? child.getX() : parent.getX();
        double firstY = parent.getY() + Component.UNIT_HEIGHT + CREATION_V_GAP;
        double secondY = firstY + Component.UNIT_HEIGHT + CREATION_V_GAP;

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
        recordUndoSnapshot();
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
        recordUndoSnapshot();
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
        recordUndoSnapshot();
        boolean prevSuppress = suppressHistoryRecording;
        suppressHistoryRecording = true;
        try {
            if (getDownwardLinks(step).isEmpty()) {
                // Aucune transition aval : créer les 2 branches d'aiguillage en OU d'un seul coup (CEI 60848)
                createTransition(step);
                createTransition(step);
            } else {
                createTransition(step);
            }
            notifyStatus("Branche OU créée (Sélection de séquence CEI 60848)");
        } finally {
            suppressHistoryRecording = prevSuppress;
        }
    }

    public void createAndBranch(Transition transition) {
        if (transition == null) return;
        recordUndoSnapshot();
        boolean prevSuppress = suppressHistoryRecording;
        suppressHistoryRecording = true;
        try {
            if (getDownwardLinks(transition).isEmpty()) {
                // Aucune étape aval : créer les 2 branches parallèles en ET d'un seul coup (CEI 60848)
                createStep(transition);
                createStep(transition);
            } else {
                createStep(transition);
            }
            notifyStatus("Branche ET créée (Synchronisation / Parallélisme CEI 60848)");
        } finally {
            suppressHistoryRecording = prevSuppress;
        }
    }

    public void deleteSelected() {
        List<StepAndTransitionCommonBehavior> selectedList = new ArrayList<>(getSelectedComponents());
        if (!selectedList.isEmpty()) {
            removeComponents(selectedList);
            return;
        }

        getSelectedLink().ifPresent(link -> {
            recordUndoSnapshot();
            link.disconnect();
            refreshActionBar();
            notifyStatus("Liaison supprimée.");
            notifyStructureChanged();
            redraw();
        });
    }

    public void deleteComponent(StepAndTransitionCommonBehavior comp) {
        if (comp == null) return;
        removeComponents(List.of(comp));
    }

    private void removeComponents(List<StepAndTransitionCommonBehavior> toRemoveList) {
        if (toRemoveList.isEmpty()) return;
        recordUndoSnapshot();
        for (var comp : toRemoveList) {
            List<Link> toRemove = new ArrayList<>();
            toRemove.addAll(comp.getLinksFromParents());
            toRemove.addAll(comp.getLinksToChildren());
            toRemove.forEach(Link::disconnect);
            components.remove(comp);
        }
        fireSelectionChange(Optional.empty());
        refreshActionBar();
        notifyStatus(toRemoveList.size() == 1
                ? ("Élément " + getElementName(toRemoveList.getFirst()) + " supprimé.")
                : (toRemoveList.size() + " éléments supprimés."));
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
            String newVal = editor.getText();
            if (!Objects.equals(newVal, transition.getReceptivity())) {
                recordUndoSnapshot();
                transition.setReceptivity(newVal);
                notifyStatus("Réceptivité de T" + transition.getNumber() + " mise à jour : " + transition.getReceptivity());
                notifyStructureChanged();
            }
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
                if (num != step.getNumber()) {
                    recordUndoSnapshot();
                    step.setNumber(num);
                    notifyStatus("Numéro d'étape modifié : S" + num);
                    notifyStructureChanged();
                }
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
        suppressHistoryRecording = true;
        try {
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
        } finally {
            suppressHistoryRecording = false;
            historyManager.clear();
        }
    }

    public SfcProgram toProgram(String programName, List<Variable> declaredVariables) {
        return GrafcetProgramMapper.toProgram(programName, components, declaredVariables);
    }

    public void loadFromProgram(SfcProgram program, List<Variable> declaredVariables) {
        if (program == null) return;

        suppressHistoryRecording = true;
        try {
            components.clear();
            activeGuideLines.clear();
            closeInlineEditor();
            panX = 0;
            panY = 0;
            zoomFactor = 1.0;

            GrafcetProgramMapper.populateFromProgram(program, ctx, components, declaredVariables);

            if (components.isEmpty()) {
                createInitialStep();
            } else {
                components.forEach(c -> c.setSelected(false));
            }

            fireSelectionChange(Optional.empty());
            refreshActionBar();
            notifyStructureChanged();
            redraw();
        } finally {
            suppressHistoryRecording = false;
            historyManager.clear();
        }
    }

    public static Duration parseDuration(String param) {
        return IecDurationParser.parse(param).orElse(null);
    }

    public void exportToPng(File file, String projectTitle, double scale) throws IOException {
        GrafcetRenderer.exportToPng(components, file, projectTitle, scale);
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
