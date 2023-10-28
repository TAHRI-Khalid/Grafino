package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

import java.util.ArrayList;
import java.util.List;

public class Link extends Component {
    private final List<Point2D> path = new ArrayList<>();
    private final StepAndTransitionCommonBehavior parentComponent;
    private final StepAndTransitionCommonBehavior childComponent;

    public Link(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                StepAndTransitionCommonBehavior childComponent) {
        super(ctx);
        this.parentComponent = parentComponent;
        this.childComponent = childComponent;
        childComponent.position = new Point2D(parentComponent.getX(), parentComponent.getY() + UNIT_HEIGHT);
    }


    public Component getParentComponent() {
        return parentComponent;
    }

    public Component getChildComponent() {
        return childComponent;
    }


    @Override
    protected void drawNormalComponent() {
        for (int i = 0; i < path.size() - 1; ++i) {
            var from = path.get(i);
            var to = path.get(i + 1);
            ctx.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
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

            var topPoint = new Point2D(0, 0);
            var bottomPoint = new Point2D(0, 0);

            if (from.getY() == to.getY()) {
                topPoint = new Point2D(from.getX(), from.getY() - SELECTION_CONTOUR_WIDTH - LINE_WIDTH / 2);
                bottomPoint = new Point2D(to.getX(), to.getY() + SELECTION_CONTOUR_WIDTH + LINE_WIDTH / 2);
            } else if (from.getX() == to.getX()) {
                topPoint = new Point2D(from.getX() - SELECTION_CONTOUR_WIDTH - LINE_WIDTH / 2, from.getY());
                bottomPoint = new Point2D(to.getX() + SELECTION_CONTOUR_WIDTH + LINE_WIDTH / 2, to.getY());
            }
            if ((point.getX() >= topPoint.getX() && point.getX() <= bottomPoint.getX() ||
                    point.getX() >= bottomPoint.getX() && point.getX() <= topPoint.getX()) &&
                    (point.getY() >= topPoint.getY() && point.getY() <= bottomPoint.getY() ||
                            point.getY() >= bottomPoint.getY() && point.getY() <= topPoint.getY())) {
                return true;
            }
        }
        return false;
    }

    public void update() {
        var begin = new Point2D(parentComponent.getX() + UNIT_WIDTH / 2, parentComponent.getY() + UNIT_HEIGHT);
        var end = new Point2D(childComponent.getX() + UNIT_WIDTH / 2, childComponent.getY());
        var middle = childComponent.getLinksFromParents().size() == 1 ?
                new Point2D(end.getX(), begin.getY()) :
                new Point2D(begin.getX(), end.getY());
        path.clear();
        path.addAll(List.of(begin, middle, end));
    }
}

