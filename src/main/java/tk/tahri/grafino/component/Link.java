package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class Link extends Component {
    private String id = UUID.randomUUID().toString();
    private final List<Point2D> path = new ArrayList<>();
    private final StepAndTransitionCommonBehavior parentComponent;
    private final StepAndTransitionCommonBehavior childComponent;

    public Link(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                StepAndTransitionCommonBehavior childComponent, List<Point2D> path) {
        super(ctx);
        this.parentComponent = parentComponent;
        this.childComponent = childComponent;
        if (!this.parentComponent.getLinksToChildren().contains(this)) {
            this.parentComponent.getLinksToChildren().add(this);
        }
        if (!this.childComponent.getLinksFromParents().contains(this)) {
            this.childComponent.getLinksFromParents().add(this);
        }
        if (path != null && !path.isEmpty()) {
            this.path.addAll(path);
        } else {
            this.update();
        }
    }

    public static Link between(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                               StepAndTransitionCommonBehavior childComponent) {

        if (childComponent.position == null) {
            double targetY = parentComponent.getY() + UNIT_HEIGHT + VERTICAL_GAP;
            if (parentComponent.getLinksToChildren().isEmpty()) {
                childComponent.position = new Point2D(parentComponent.getX(), targetY);
            } else {
                var lastChild = parentComponent.getLinksToChildren().stream()
                        .map(Link::getChildComponent)
                        .sorted(Comparator.comparing(StepAndTransitionCommonBehavior::getX))
                        .toList()
                        .getLast();
                childComponent.position = new Point2D(lastChild.getX() + 2 * UNIT_WIDTH, targetY);
            }
        }

        Link link = new Link(ctx, parentComponent, childComponent, null);
        link.update();
        return link;
    }

    public static Link between(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                               StepAndTransitionCommonBehavior childComponent, Point2D middle) {
        Link link = between(ctx, parentComponent, childComponent);
        if (middle != null && link.path.size() >= 5) {
            link.updateSelectedLink(middle);
        }
        return link;
    }

    public void remove() {
        parentComponent.getLinksToChildren().remove(this);
        childComponent.getLinksFromParents().remove(this);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Point2D> getPath() {
        return path;
    }

    public StepAndTransitionCommonBehavior getParentComponent() {
        return parentComponent;
    }

    public StepAndTransitionCommonBehavior getChildComponent() {
        return childComponent;
    }

    @Override
    protected void drawNormalComponent() {
        int space = 4;
        for (int i = 0; i < path.size() - 1; ++i) {
            var from = path.get(i);
            var to = path.get(i + 1);
            if (Math.abs(from.getY() - to.getY()) < 0.5) {
                // Branche ET synchronisée (double barre)
                if (parentComponent instanceof Transition transition && transition.getLinksToChildren().size() > 1) {
                    ctx.strokeLine(from.getX(), from.getY() - space, to.getX(), to.getY() - space);
                }
                if (childComponent instanceof Transition transition && transition.getLinksFromParents().size() > 1) {
                    ctx.strokeLine(from.getX(), from.getY() + space, to.getX(), to.getY() + space);
                }
            }
            ctx.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
        }
        drawArrow();
    }

    private void drawArrow() {
        // La flèche CEI 60848 est requise EXCLUSIVEMENT pour les liaisons ascendantes (remontées vers le haut)
        if (childComponent.getY() < parentComponent.getY() + UNIT_HEIGHT && path.size() >= 6) {
            // Le segment ascendant est entre le point index 2 et index 3
            Point2D from = path.get(2);
            Point2D to = path.get(3);
            Point2D center = from.midpoint(to);
            double length = 14;
            // Flèche orientée vers le haut
            ctx.strokeLine(center.getX(), center.getY() - length / 2, center.getX() - 6, center.getY() + length / 2);
            ctx.strokeLine(center.getX(), center.getY() - length / 2, center.getX() + 6, center.getY() + length / 2);
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

            double minX = Math.min(from.getX(), to.getX()) - SELECTION_CONTOUR_WIDTH - LINE_WIDTH;
            double maxX = Math.max(from.getX(), to.getX()) + SELECTION_CONTOUR_WIDTH + LINE_WIDTH;
            double minY = Math.min(from.getY(), to.getY()) - SELECTION_CONTOUR_WIDTH - LINE_WIDTH;
            double maxY = Math.max(from.getY(), to.getY()) + SELECTION_CONTOUR_WIDTH + LINE_WIDTH;

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

        Double preservedCorridorX = null;
        if (path.size() >= 6) {
            preservedCorridorX = path.get(2).getX();
        }

        path.clear();

        // 1. Liaison descendante normale (child est en dessous du parent)
        if (childComponent.getY() >= parentComponent.getY() + UNIT_HEIGHT) {
            if (Math.abs(begin.getX() - end.getX()) < 1.0) {
                // Segment vertical pur unique
                path.add(begin);
                path.add(end);
            } else {
                // Décrochement orthogonal pur à angle droit (mi-hauteur)
                double midY = (begin.getY() + end.getY()) / 2.0;
                midY = Math.round(midY / 20.0) * 20.0;
                Point2D p1 = new Point2D(begin.getX(), midY);
                Point2D p2 = new Point2D(end.getX(), midY);
                path.add(begin);
                path.add(p1);
                path.add(p2);
                path.add(end);
            }
        } else {
            // 2. Boucle de retour vers le haut (Manhattan routing strictement orthogonal)
            double corridorX;
            if (preservedCorridorX != null) {
                corridorX = preservedCorridorX;
            } else {
                double minX = Math.min(parentComponent.getX(), childComponent.getX());
                corridorX = minX - 60.0;
                corridorX = Math.round(corridorX / 20.0) * 20.0;
            }

            double clearanceY = 20.0;
            double bottomY = begin.getY() + clearanceY;
            double topY = end.getY() - clearanceY;

            Point2D p1 = new Point2D(begin.getX(), bottomY);  // 1. Vertical vers le bas
            Point2D p2 = new Point2D(corridorX, bottomY);     // 2. Horizontal vers la gauche
            Point2D p3 = new Point2D(corridorX, topY);        // 3. Vertical ascendant
            Point2D p4 = new Point2D(end.getX(), topY);       // 4. Horizontal vers la droite
            // 5. Vertical descendant vers le sommet du childComponent (end)

            path.add(begin);
            path.add(p1);
            path.add(p2);
            path.add(p3);
            path.add(p4);
            path.add(end);
        }
    }

    public void updateSelectedLink(Point2D point) {
        if (path.size() >= 6) {
            double newCorridorX = Math.round(point.getX() / 20.0) * 20.0;
            Point2D p2 = new Point2D(newCorridorX, path.get(1).getY());
            Point2D p3 = new Point2D(newCorridorX, path.get(4).getY());
            path.set(2, p2);
            path.set(3, p3);
        }
    }
}
