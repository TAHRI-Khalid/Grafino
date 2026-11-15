package tk.tahri.grafino.ui.canvas;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.SfcValidationMessage;
import tk.tahri.grafino.ui.ActionBar;
import tk.tahri.grafino.ui.Grafcet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Moteur de rendu graphique JavaFX du Canvas Grafcet (grille technique, aperçus fantômes,
 * lasso de sélection, prévisualisation de raccordement et export PNG haute résolution).
 */
public final class GrafcetRenderer {

    private GrafcetRenderer() {
    }

    public static void drawBackgroundGrid(GraphicsContext ctx, double width, double height, double panX, double panY, double zoomFactor) {
        if (ctx == null) return;
        double gridSize = 24.0 * zoomFactor;
        if (gridSize < 10) return;

        ctx.setFill(Color.rgb(215, 222, 228, 0.55));
        double startX = Double.isFinite(panX) ? ((panX % gridSize) + gridSize) % gridSize : 0.0;
        double startY = Double.isFinite(panY) ? ((panY % gridSize) + gridSize) % gridSize : 0.0;

        for (double x = startX; x < width; x += gridSize) {
            for (double y = startY; y < height; y += gridSize) {
                ctx.fillOval(x - 1, y - 1, 2, 2);
            }
        }
    }

    public static void drawAlignmentGuides(GraphicsContext ctx, List<Grafcet.AlignmentGuide> guides) {
        if (guides.isEmpty()) return;
        ctx.setStroke(Color.rgb(0, 151, 157, 0.85));
        ctx.setLineWidth(1.6);
        ctx.setLineDashes(4.0, 4.0);
        for (var guide : guides) {
            ctx.strokeLine(guide.x1(), guide.y1(), guide.x2(), guide.y2());
        }
        ctx.setLineDashes(null);
    }

    public static void drawMarqueeSelectionBox(GraphicsContext ctx, Point2D startDiag, Point2D currentDiag) {
        double minX = Math.min(startDiag.getX(), currentDiag.getX());
        double minY = Math.min(startDiag.getY(), currentDiag.getY());
        double w = Math.abs(currentDiag.getX() - startDiag.getX());
        double h = Math.abs(currentDiag.getY() - startDiag.getY());

        ctx.save();
        ctx.setFill(Color.rgb(0, 129, 132, 0.10));
        ctx.fillRect(minX, minY, w, h);
        ctx.setStroke(Color.rgb(0, 129, 132, 0.85));
        ctx.setLineWidth(1.5);
        ctx.setLineDashes(5.0, 4.0);
        ctx.strokeRect(minX, minY, w, h);
        ctx.restore();
    }

    public static void drawDraggedLinkPreview(GraphicsContext ctx, Grafcet.ManualLinkPreview preview, Point2D mouse) {
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

        if (preview.badgeText() != null) {
            drawPreviewCursorBadge(ctx, mouse.getX() + 14, mouse.getY() - 12, preview.badgeText(), preview.status());
        }
        ctx.restore();
    }

    private static void drawPreviewCursorBadge(GraphicsContext ctx, double x, double y, String text, Grafcet.PreviewStatus status) {
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

    public static void drawGhostPreview(
            GraphicsContext ctx,
            Grafcet grafcet,
            ActionBar.GhostPreviewType previewType,
            double creationVGap,
            double convergenceVGap,
            double branchHSpacing
    ) {
        ctx.save();
        ctx.setStroke(Color.rgb(0, 129, 132, 0.65));
        ctx.setFill(Color.rgb(229, 245, 245, 0.55));
        ctx.setLineWidth(2.0);
        ctx.setLineDashes(5.0, 4.0);

        List<StepAndTransitionCommonBehavior> selected = grafcet.getSelectedComponents();

        switch (previewType) {
            case NEXT_SINGLE -> {
                if (selected.size() == 1) {
                    var comp = selected.getFirst();
                    double targetX = comp.getX();
                    double targetY = comp.getY() + Component.UNIT_HEIGHT + creationVGap;
                    drawGhostLink(ctx, comp.getX() + Component.UNIT_WIDTH / 2, comp.getY() + Component.UNIT_HEIGHT,
                            targetX + Component.UNIT_WIDTH / 2, targetY);
                    if (comp instanceof Step) {
                        drawGhostTransitionShape(ctx, targetX, targetY);
                    } else {
                        drawGhostStepShape(ctx, targetX, targetY);
                    }
                }
            }
            case BRANCH -> {
                if (selected.size() == 1) {
                    var comp = selected.getFirst();
                    List<Link> downward = grafcet.getDownwardLinks(comp);
                    double targetY = comp.getY() + Component.UNIT_HEIGHT + creationVGap;
                    double branchBarY = comp.getY() + Component.UNIT_HEIGHT + creationVGap / 2.0;
                    boolean isAndDiv = comp instanceof Transition;

                    if (downward.isEmpty()) {
                        double firstX = comp.getX();
                        double secondX = comp.getX() + branchHSpacing;
                        double minCenterX = firstX + Component.UNIT_WIDTH / 2 - 15;
                        double maxCenterX = secondX + Component.UNIT_WIDTH / 2 + 15;

                        ctx.strokeLine(comp.getX() + Component.UNIT_WIDTH / 2, comp.getY() + Component.UNIT_HEIGHT,
                                comp.getX() + Component.UNIT_WIDTH / 2, branchBarY);
                        if (isAndDiv) {
                            ctx.strokeLine(minCenterX, branchBarY - 3, maxCenterX, branchBarY - 3);
                            ctx.strokeLine(minCenterX, branchBarY + 3, maxCenterX, branchBarY + 3);
                            drawGhostStepShape(ctx, firstX, targetY);
                            drawGhostStepShape(ctx, secondX, targetY);
                        } else {
                            ctx.strokeLine(minCenterX, branchBarY, maxCenterX, branchBarY);
                            drawGhostTransitionShape(ctx, firstX, targetY);
                            drawGhostTransitionShape(ctx, secondX, targetY);
                        }
                        ctx.strokeLine(firstX + Component.UNIT_WIDTH / 2, branchBarY, firstX + Component.UNIT_WIDTH / 2, targetY);
                        ctx.strokeLine(secondX + Component.UNIT_WIDTH / 2, branchBarY, secondX + Component.UNIT_WIDTH / 2, targetY);
                    } else {
                        double maxX = downward.stream().mapToDouble(l -> l.getChildComponent().getX()).max().orElse(comp.getX());
                        double minX = downward.stream().mapToDouble(l -> l.getChildComponent().getX()).min().orElse(comp.getX());
                        double targetX = maxX + branchHSpacing;
                        double minCenterX = Math.min(minX, comp.getX()) + Component.UNIT_WIDTH / 2 - 15;
                        double maxCenterX = targetX + Component.UNIT_WIDTH / 2 + 15;

                        if (isAndDiv) {
                            ctx.strokeLine(minCenterX, branchBarY - 3, maxCenterX, branchBarY - 3);
                            ctx.strokeLine(minCenterX, branchBarY + 3, maxCenterX, branchBarY + 3);
                            drawGhostStepShape(ctx, targetX, targetY);
                        } else {
                            ctx.strokeLine(minCenterX, branchBarY, maxCenterX, branchBarY);
                            drawGhostTransitionShape(ctx, targetX, targetY);
                        }
                        ctx.strokeLine(targetX + Component.UNIT_WIDTH / 2, branchBarY, targetX + Component.UNIT_WIDTH / 2, targetY);
                    }
                }
            }
            case NEXT_PAIR -> {
                if (selected.size() == 1) {
                    var comp = selected.getFirst();
                    List<Link> downward = grafcet.getDownwardLinks(comp);
                    double targetX = comp.getX();
                    double firstY = comp.getY() + Component.UNIT_HEIGHT + creationVGap;
                    if (!downward.isEmpty()) {
                        double maxX = downward.stream().mapToDouble(l -> l.getChildComponent().getX()).max().orElse(comp.getX());
                        targetX = maxX + branchHSpacing;
                    }
                    double secondY = firstY + Component.UNIT_HEIGHT + creationVGap;
                    double midX = targetX + Component.UNIT_WIDTH / 2;
                    drawGhostLink(ctx, comp.getX() + Component.UNIT_WIDTH / 2, comp.getY() + Component.UNIT_HEIGHT, midX, firstY);
                    drawGhostLink(ctx, midX, firstY + Component.UNIT_HEIGHT, midX, secondY);
                    if (comp instanceof Step) {
                        drawGhostTransitionShape(ctx, targetX, firstY);
                        drawGhostStepShape(ctx, targetX, secondY);
                    } else {
                        drawGhostStepShape(ctx, targetX, firstY);
                        drawGhostTransitionShape(ctx, targetX, secondY);
                    }
                }
            }
            case CONVERGE -> {
                List<StepAndTransitionCommonBehavior> toConverge = new ArrayList<>(selected);
                if (toConverge.size() == 1) {
                    switch (toConverge.getFirst()) {
                        case Step s -> toConverge.addAll(grafcet.findConvergenceCandidateSteps(s));
                        case Transition t -> toConverge.addAll(grafcet.findConvergenceCandidateTransitions(t));
                    }
                }
                if (toConverge.size() >= 2) {
                    double avgX = toConverge.stream().mapToDouble(StepAndTransitionCommonBehavior::getX).average().orElse(400);
                    double maxY = toConverge.stream().mapToDouble(StepAndTransitionCommonBehavior::getY).max().orElse(200);
                    double targetY = maxY + Component.UNIT_HEIGHT + convergenceVGap;
                    double convBarY = targetY - convergenceVGap / 2.0;
                    double minCenterX = toConverge.stream().mapToDouble(c -> c.getX() + Component.UNIT_WIDTH / 2).min().orElse(avgX) - 15;
                    double maxCenterX = toConverge.stream().mapToDouble(c -> c.getX() + Component.UNIT_WIDTH / 2).max().orElse(avgX) + 15;

                    boolean isAndConv = toConverge.getFirst() instanceof Step;
                    if (isAndConv) {
                        ctx.strokeLine(minCenterX, convBarY - 3, maxCenterX, convBarY - 3);
                        ctx.strokeLine(minCenterX, convBarY + 3, maxCenterX, convBarY + 3);
                        drawGhostTransitionShape(ctx, avgX, targetY);
                    } else {
                        ctx.strokeLine(minCenterX, convBarY, maxCenterX, convBarY);
                        drawGhostStepShape(ctx, avgX, targetY);
                    }
                    for (var src : toConverge) {
                        double sx = src.getX() + Component.UNIT_WIDTH / 2;
                        ctx.strokeLine(sx, src.getY() + Component.UNIT_HEIGHT, sx, convBarY);
                    }
                    ctx.strokeLine(avgX + Component.UNIT_WIDTH / 2, convBarY, avgX + Component.UNIT_WIDTH / 2, targetY);
                }
            }
            case LOOP_BACK -> {
                Step targetInit = grafcet.findTargetInitialStep();
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
            case INSERT_ON_LINK -> grafcet.getSelectedLink().ifPresent(link -> {
                var parent = link.getParentComponent();
                var child = link.getChildComponent();
                double midX = link.isDivergence() ? child.getX() : parent.getX();
                double firstY = parent.getY() + Component.UNIT_HEIGHT + creationVGap;
                if (parent instanceof Step) {
                    drawGhostTransitionShape(ctx, midX, firstY);
                } else {
                    drawGhostStepShape(ctx, midX, firstY);
                }
            });
            default -> {}
        }

        ctx.restore();
    }

    private static void drawGhostLink(GraphicsContext ctx, double x1, double y1, double x2, double y2) {
        double midY = (y1 + y2) / 2.0;
        ctx.strokeLine(x1, y1, x1, midY);
        ctx.strokeLine(x1, midY, x2, midY);
        ctx.strokeLine(x2, midY, x2, y2);
    }

    private static void drawGhostStepShape(GraphicsContext ctx, double x, double y) {
        double gap = Component.GAP;
        ctx.fillRect(x, y + gap, Component.UNIT_WIDTH, Component.UNIT_HEIGHT - gap * 2);
        ctx.strokeRect(x, y + gap, Component.UNIT_WIDTH, Component.UNIT_HEIGHT - gap * 2);
        ctx.strokeLine(x + Component.UNIT_WIDTH / 2, y, x + Component.UNIT_WIDTH / 2, y + Component.UNIT_HEIGHT);
    }

    private static void drawGhostTransitionShape(GraphicsContext ctx, double x, double y) {
        double midX = x + Component.UNIT_WIDTH / 2;
        double midY = y + Component.UNIT_HEIGHT / 2;
        ctx.strokeLine(midX, y, midX, y + Component.UNIT_HEIGHT);
        ctx.strokeLine(x + Component.UNIT_WIDTH / 4, midY, x + Component.UNIT_WIDTH * 3 / 4, midY);
    }

    public static void exportToPng(
            List<StepAndTransitionCommonBehavior> components,
            File file,
            String projectTitle,
            double scale
    ) throws IOException {
        if (file == null) return;
        if (scale <= 0) scale = 2.0;

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

                switch (comp) {
                    case Step step -> {
                        double curX = step.getX() + Component.UNIT_WIDTH + Component.GAP;
                        for (Action a : step.getActions()) {
                            String text = a.getDisplayText();
                            double textWidth = (text != null) ? Math.max(30, text.length() * 9.0) : 30;
                            curX += textWidth + 40;
                        }
                        maxX = Math.max(maxX, curX);
                    }
                    case Transition trans -> {
                        minX = Math.min(minX, trans.getX() - 35);
                        String rec = trans.getReceptivity();
                        double recWidth = (rec != null) ? rec.length() * 10.0 : 20;
                        maxX = Math.max(maxX, trans.getX() + Component.UNIT_WIDTH * 3 / 4 + 15 + recWidth);
                    }
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

        g.setFill(Color.WHITE);
        g.fillRect(0, 0, diagramWidth, diagramHeight);

        if (titleHeight > 0) {
            g.setFill(Color.rgb(0, 129, 132));
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

        Map<Component, GraphicsContext> originalContexts = new HashMap<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            originalContexts.put(comp, comp.getGraphicsContext());
            comp.setGraphicsContext(g);
            for (Link link : comp.getLinksToChildren()) {
                originalContexts.put(link, link.getGraphicsContext());
                link.setGraphicsContext(g);
            }
        }

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
            components.stream()
                    .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                    .flatMap(List::stream)
                    .forEach(Link::drawComponent);

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
}
