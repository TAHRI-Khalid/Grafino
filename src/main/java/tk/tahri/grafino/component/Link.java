package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import java.util.ArrayList;
import java.util.List;

public class Link extends Component{
    private final List<Point2D> path=new ArrayList<>();
    private final Component parentComponent;
    private final Component childComponent;

    public Link(GraphicsContext ctx, StepAndTransitionCommonBehavior parentComponent,
                StepAndTransitionCommonBehavior childComponent) {
        super(ctx);
        this.parentComponent = parentComponent;
        this.childComponent = childComponent;
        childComponent.position=new Point2D(parentComponent.getX(), parentComponent.getY()+UNIT_HEIGHT);
    }


    public Component getParentComponent() {
        return parentComponent;
    }

    public Component getChildComponent() {
        return childComponent;
    }

    @Override
    protected void drawNormalStep() {

    }

    @Override
    protected void drawSelectedStep() {

    }

    @Override
    public boolean isPointIntoRegion(Point2D point) {
        return false;
    }
}
