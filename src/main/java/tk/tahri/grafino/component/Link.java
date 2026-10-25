package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Link extends Component {
    private final List<Point2D> path = new ArrayList<>();
    private final StepAndTransitionCommonBehavior parentComponent;
    private final StepAndTransitionCommonBehavior childComponent;
    private Double customGutterX = null;

    private Link(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                 StepAndTransitionCommonBehavior childComponent, List<Point2D> path) {
        super(ctx);
        this.parentComponent = parentComponent;
        this.childComponent = childComponent;
        this.parentComponent.getLinksToChildren().add(this);
        this.childComponent.getLinksFromParents().add(this);
        this.path.addAll(path);
    }

    public static Link between(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                               StepAndTransitionCommonBehavior childComponent) {
        // Empêcher l'auto-liaison ou liaison en doublon
        for (Link existing : parentComponent.getLinksToChildren()) {
            if (existing.getChildComponent() == childComponent) {
                return existing;
            }
        }

        var begin = new Point2D(parentComponent.getX() + UNIT_WIDTH / 2, parentComponent.getY() + UNIT_HEIGHT);

        if (childComponent.position != null) {
            var end = new Point2D(childComponent.getX() + UNIT_WIDTH / 2, childComponent.getY());
            Link link;
            if (end.getY() < begin.getY()) {
                // Boucle de reprise ascendante
                double gutterX = Math.min(begin.getX(), end.getX()) - UNIT_WIDTH * 0.9;
                var p1 = begin;
                var p2 = new Point2D(gutterX, begin.getY() + 15);
                var p3 = new Point2D(gutterX, end.getY() - 15);
                var p4 = new Point2D(end.getX(), end.getY() - 15);
                var p5 = end;
                link = new Link(ctx, parentComponent, childComponent, List.of(p1, p2, p3, p4, p5));
                link.customGutterX = gutterX;
            } else {
                var middle = new Point2D(end.getX(), (begin.getY() + end.getY()) / 2);
                link = new Link(ctx, parentComponent, childComponent, List.of(begin, middle, end));
            }
            link.update();
            return link;
        }

        // Si position de l'enfant pas encore définie : placement automatique
        double targetY = parentComponent.getY() + UNIT_HEIGHT + 40;
        double targetX;
        if (parentComponent.getLinksToChildren().isEmpty()) {
            targetX = parentComponent.getX();
        } else {
            var lastChild = parentComponent.getLinksToChildren().stream()
                    .map(Link::getChildComponent)
                    .max(Comparator.comparingDouble(StepAndTransitionCommonBehavior::getX))
                    .orElse(parentComponent);
            targetX = lastChild.getX() + 2.5 * UNIT_WIDTH;
        }
        childComponent.position = new Point2D(targetX, targetY);

        var end = new Point2D(childComponent.getX() + UNIT_WIDTH / 2, childComponent.getY());
        var middle = new Point2D(end.getX(), (begin.getY() + end.getY()) / 2);
        var link = new Link(ctx, parentComponent, childComponent, List.of(begin, middle, end));
        link.update();
        return link;
    }

    public static Link between(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                               StepAndTransitionCommonBehavior childComponent, Point2D middle) {
        var link = between(ctx, parentComponent, childComponent);
        if (link.isAscending()) {
            link.customGutterX = middle.getX();
            link.update();
        }
        return link;
    }

    public StepAndTransitionCommonBehavior getParentComponent() {
        return parentComponent;
    }

    public StepAndTransitionCommonBehavior getChildComponent() {
        return childComponent;
    }

    public boolean isAscending() {
        return childComponent.getY() < parentComponent.getY() + UNIT_HEIGHT;
    }

    public boolean isDivergence() {
        return parentComponent.getLinksToChildren().size() > 1 && !isAscending();
    }

    public boolean isConvergence() {
        return childComponent.getLinksFromParents().size() > 1 && !isAscending();
    }

    @Override
    protected void drawNormalComponent() {
        // 1. Tracé du chemin principal (en omettant les segments horizontaux pris en charge par les barres de divergence/convergence)
        for (int i = 0; i < path.size() - 1; ++i) {
            var from = path.get(i);
            var to = path.get(i + 1);

            boolean isHorizontal = Math.abs(from.getY() - to.getY()) < 0.5;
            if (isHorizontal && (isDivergence() || isConvergence())) {
                continue;
            }

            ctx.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
        }

        // 2. Tracé de la barre de divergence normalisée
        if (isDivergence()) {
            drawDivergenceBar();
        }

        // 3. Tracé de la barre de convergence normalisée
        if (isConvergence()) {
            drawConvergenceBar();
        }

        // 4. Flèche de retour ascendante (obligatoire selon CEI 60848)
        if (isAscending()) {
            drawUpwardArrow();
        }
    }

    private void drawDivergenceBar() {
        // Dessiné uniquement par le premier lien pour éviter les redessins multiples
        if (parentComponent.getLinksToChildren().indexOf(this) != 0) {
            return;
        }

        double branchY = parentComponent.getY() + UNIT_HEIGHT + 20;
        double minX = parentComponent.getLinksToChildren().stream()
                .mapToDouble(l -> l.getChildComponent().getX() + UNIT_WIDTH / 2)
                .min().orElse(parentComponent.getX() + UNIT_WIDTH / 2);
        double maxX = parentComponent.getLinksToChildren().stream()
                .mapToDouble(l -> l.getChildComponent().getX() + UNIT_WIDTH / 2)
                .max().orElse(parentComponent.getX() + UNIT_WIDTH / 2);

        minX = Math.min(minX, parentComponent.getX() + UNIT_WIDTH / 2);
        maxX = Math.max(maxX, parentComponent.getX() + UNIT_WIDTH / 2);

        // Étendre légèrement la barre des deux côtés pour conformité graphique
        minX -= 15;
        maxX += 15;

        boolean isParallel = parentComponent instanceof Transition;
        if (isParallel) {
            // Divergence en ET : Double barre horizontale (exactement 2 lignes)
            double gap = 6.0;
            ctx.strokeLine(minX, branchY - gap / 2, maxX, branchY - gap / 2);
            ctx.strokeLine(minX, branchY + gap / 2, maxX, branchY + gap / 2);
        } else {
            // Divergence en OU : Simple barre horizontale (exactement 1 ligne)
            ctx.strokeLine(minX, branchY, maxX, branchY);
        }
    }

    private void drawConvergenceBar() {
        // Dessiné uniquement par le premier lien convergent
        if (childComponent.getLinksFromParents().indexOf(this) != 0) {
            return;
        }

        double convY = childComponent.getY() - 20;
        double minX = childComponent.getLinksFromParents().stream()
                .mapToDouble(l -> l.getParentComponent().getX() + UNIT_WIDTH / 2)
                .min().orElse(childComponent.getX() + UNIT_WIDTH / 2);
        double maxX = childComponent.getLinksFromParents().stream()
                .mapToDouble(l -> l.getParentComponent().getX() + UNIT_WIDTH / 2)
                .max().orElse(childComponent.getX() + UNIT_WIDTH / 2);

        minX = Math.min(minX, childComponent.getX() + UNIT_WIDTH / 2);
        maxX = Math.max(maxX, childComponent.getX() + UNIT_WIDTH / 2);

        minX -= 15;
        maxX += 15;

        boolean isParallel = childComponent instanceof Transition;
        if (isParallel) {
            // Convergence en ET : Double barre horizontale (exactement 2 lignes)
            double gap = 6.0;
            ctx.strokeLine(minX, convY - gap / 2, maxX, convY - gap / 2);
            ctx.strokeLine(minX, convY + gap / 2, maxX, convY + gap / 2);
        } else {
            // Convergence en OU : Simple barre horizontale (exactement 1 ligne)
            ctx.strokeLine(minX, convY, maxX, convY);
        }
    }

    private void drawUpwardArrow() {
        // Identifier le segment vertical ascendant (vers le haut)
        for (int i = 0; i < path.size() - 1; ++i) {
            var from = path.get(i);
            var to = path.get(i + 1);
            if (Math.abs(from.getX() - to.getX()) < 1.0 && from.getY() > to.getY()) {
                double midY = (from.getY() + to.getY()) / 2;
                double arrowX = from.getX();
                double size = 9;

                ctx.beginPath();
                ctx.moveTo(arrowX, midY - size);
                ctx.lineTo(arrowX - 5, midY + size);
                ctx.lineTo(arrowX + 5, midY + size);
                ctx.closePath();
                ctx.fill();
                ctx.stroke();
                break;
            }
        }
    }

    @Override
    protected void drawSelectedComponent() {
        drawNormalComponent();
    }

    @Override
    public boolean isPointOnTheComponent(Point2D point) {
        for (int i = 0; i < path.size() - 1; ++i) {
            var from = path.get(i);
            var to = path.get(i + 1);

            double minX = Math.min(from.getX(), to.getX()) - 6;
            double maxX = Math.max(from.getX(), to.getX()) + 6;
            double minY = Math.min(from.getY(), to.getY()) - 6;
            double maxY = Math.max(from.getY(), to.getY()) + 6;

            if (point.getX() >= minX && point.getX() <= maxX &&
                    point.getY() >= minY && point.getY() <= maxY) {
                return true;
            }
        }
        return false;
    }

    public void update() {
        var begin = new Point2D(parentComponent.getX() + UNIT_WIDTH / 2, parentComponent.getY() + UNIT_HEIGHT);
        var end = new Point2D(childComponent.getX() + UNIT_WIDTH / 2, childComponent.getY());

        path.clear();

        if (isAscending()) {
            double gutterX = (customGutterX != null) ? customGutterX :
                    Math.min(begin.getX(), end.getX()) - UNIT_WIDTH * 0.9;
            var p1 = begin;
            var p2 = new Point2D(begin.getX(), begin.getY() + 15);
            var p3 = new Point2D(gutterX, begin.getY() + 15);
            var p4 = new Point2D(gutterX, end.getY() - 15);
            var p5 = new Point2D(end.getX(), end.getY() - 15);
            var p6 = end;
            path.addAll(List.of(p1, p2, p3, p4, p5, p6));
            return;
        }

        boolean hasDivergence = parentComponent.getLinksToChildren().size() > 1;
        boolean hasConvergence = childComponent.getLinksFromParents().size() > 1;

        if (hasDivergence && !hasConvergence) {
            double branchY = parentComponent.getY() + UNIT_HEIGHT + 20;
            var p1 = begin;
            var p2 = new Point2D(begin.getX(), branchY);
            var p3 = new Point2D(end.getX(), branchY);
            var p4 = end;
            path.addAll(List.of(p1, p2, p3, p4));
        } else if (hasConvergence && !hasDivergence) {
            double convY = childComponent.getY() - 20;
            var p1 = begin;
            var p2 = new Point2D(begin.getX(), convY);
            var p3 = new Point2D(end.getX(), convY);
            var p4 = end;
            path.addAll(List.of(p1, p2, p3, p4));
        } else if (hasDivergence && hasConvergence) {
            double branchY = parentComponent.getY() + UNIT_HEIGHT + 20;
            double convY = childComponent.getY() - 20;
            var p1 = begin;
            var p2 = new Point2D(begin.getX(), branchY);
            var p3 = new Point2D(end.getX(), branchY);
            var p4 = new Point2D(end.getX(), convY);
            var p5 = end;
            path.addAll(List.of(p1, p2, p3, p4, p5));
        } else {
            // Liaison simple 1-à-1
            if (Math.abs(begin.getX() - end.getX()) < 1.0) {
                path.addAll(List.of(begin, end));
            } else {
                double midY = (begin.getY() + end.getY()) / 2;
                var p1 = begin;
                var p2 = new Point2D(begin.getX(), midY);
                var p3 = new Point2D(end.getX(), midY);
                var p4 = end;
                path.addAll(List.of(p1, p2, p3, p4));
            }
        }
    }

    public void updateSelectedLink(Point2D point) {
        if (isAscending()) {
            this.customGutterX = point.getX();
            update();
        }
    }

    public void disconnect() {
        parentComponent.getLinksToChildren().remove(this);
        childComponent.getLinksFromParents().remove(this);
    }
}
