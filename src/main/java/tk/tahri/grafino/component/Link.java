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
        this.path.addAll(path);
    }

    public static Link between(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                               StepAndTransitionCommonBehavior childComponent) {

        var begin = new Point2D(parentComponent.getX() + UNIT_WIDTH / 2, parentComponent.getY() + UNIT_HEIGHT);
        if (childComponent.position != null) {
            var end = new Point2D(childComponent.getX() + UNIT_WIDTH / 2, childComponent.getY());
            var middle = new Point2D(begin.getX(), end.getY());
            var path = List.of(begin, middle, end);
            return new Link(ctx, parentComponent, childComponent, path);
        }

        if (parentComponent.getLinksToChildren().isEmpty()) {
            childComponent.position = new Point2D(parentComponent.getX(), parentComponent.getY() + UNIT_HEIGHT);
        } else {
            var component = parentComponent.getLinksToChildren().stream()
                    .map(Link::getChildComponent)
                    .sorted(Comparator.comparing(StepAndTransitionCommonBehavior::getX))
                    .toList()
                    .getLast();
            childComponent.position = new Point2D(component.getX() + 3 * UNIT_WIDTH, parentComponent.getY() + UNIT_HEIGHT);
        }
        var end = new Point2D(childComponent.getX() + UNIT_WIDTH / 2, childComponent.getY());
        var middle = new Point2D(end.getX(), begin.getY());
        var path = List.of(begin, middle, end);
        return new Link(ctx, parentComponent, childComponent, path);
    }

    public static Link between(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                               StepAndTransitionCommonBehavior childComponent, Point2D middle) {

        var begin = new Point2D(parentComponent.getX() + UNIT_WIDTH / 2, parentComponent.getY() + UNIT_HEIGHT);
        var end = new Point2D(childComponent.getX() + UNIT_WIDTH / 2, childComponent.getY());
        var middleBottom = new Point2D(middle.getX(), begin.getY());
        var middleTop = new Point2D(middle.getX(), end.getY());
        var path = List.of(begin, middleBottom, middleTop, end);
        return new Link(ctx, parentComponent, childComponent, path);
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
            if (from.getY() == to.getY()) {
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
        if (path.size() >= 4) {
            double length = 14;
            var center = path.get(1).midpoint(path.get(2)).subtract(0, length / 2);
            ctx.strokeLine(center.getX(), center.getY(), center.getX() - 6, center.getY() + length);
            ctx.strokeLine(center.getX(), center.getY(), center.getX() + 6, center.getY() + length);
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

        if (parentComponent.getY() + UNIT_HEIGHT <= childComponent.getY()) {
            var middle = childComponent.getLinksFromParents().size() == 1 ?
                    new Point2D(end.getX(), begin.getY()) :
                    new Point2D(begin.getX(), end.getY());
            path.clear();
            path.addAll(List.of(begin, middle, end));
        } else {
            double midX = (path.size() >= 4) ? path.get(1).getX() : (parentComponent.getX() - 60);
            var middleBottom = new Point2D(midX, begin.getY());
            var middleTop = new Point2D(midX, end.getY());
            path.clear();
            path.addAll(List.of(begin, middleBottom, middleTop, end));
        }
    }

    public void updateSelectedLink(Point2D point) {
        if (path.size() == 4) {
            var middleBottom = new Point2D(point.getX(), path.get(1).getY());
            var middleTop = new Point2D(point.getX(), path.get(2).getY());
            path.set(1, middleBottom);
            path.set(2, middleTop);
        }
    }
}
