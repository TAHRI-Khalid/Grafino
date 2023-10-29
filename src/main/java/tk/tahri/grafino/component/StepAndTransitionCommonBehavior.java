package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import tk.tahri.grafino.Handle;

import java.util.ArrayList;
import java.util.List;

public abstract class StepAndTransitionCommonBehavior extends Component {
    protected List<Link>  linksFromParents=new ArrayList<>();
    protected List<Link> linksToChildren=new ArrayList<>();
    protected Point2D position;
    protected Handle handle = Handle.NONE;
    protected final double radius = 12;

    protected StepAndTransitionCommonBehavior(GraphicsContext ctx) {
        super(ctx);
    }

    public double getX() {
        return position.getX();
    }

    public double getY() {
        return position.getY();
    }

    public void drawHandle() {
        ctx.setFill( SELECTION_COLOR);
        ctx.setStroke( LINE_COLOR);
        ctx.setLineWidth( LINE_WIDTH);
        switch (handle) {
            case TOP -> drawTopHandle();
            case BOTTOM -> drawBottomHandle();
            case BOTH -> {
                if (this.linksFromParents.isEmpty()) drawTopHandle();
                if (this.linksToChildren.isEmpty()) drawBottomHandle();
            }
        }
    }

    private void drawTopHandle() {
        ctx.fillOval(getX() - radius / 2 +  UNIT_WIDTH / 2, getY(), radius, radius);
        ctx.strokeOval(getX() - radius / 2 +  UNIT_WIDTH / 2, getY(), radius, radius);
    }

    private void drawBottomHandle() {
        ctx.fillOval(getX() - radius / 2 +  UNIT_WIDTH / 2, getY() +  UNIT_HEIGHT - radius, radius, radius);
        ctx.strokeOval(getX() - radius / 2 +  UNIT_WIDTH / 2, getY() +  UNIT_HEIGHT - radius, radius, radius);
    }

    @Override
    public boolean isPointOnTheComponent(Point2D point) {
        var topPoint = new Point2D(this.getX(), this.getY());
        var bottomPoint = new Point2D(this.getX() +  UNIT_WIDTH, this.getY() +  UNIT_HEIGHT);
        return point.getX()>= topPoint.getX() && point.getX() <= bottomPoint.getX() &&
                point.getY() >= topPoint.getY() && point.getY()<= bottomPoint.getY();
    }


    public boolean isBottomHandlePressed(Point2D point) {
        var topPoint = new Point2D(this.getX() +  UNIT_WIDTH / 2 - radius / 2, this.getY() +  UNIT_HEIGHT - radius);
        var bottomPoint = new Point2D(this.getX() +  UNIT_WIDTH / 2 + radius / 2, this.getY() +  UNIT_HEIGHT);
        return point.getX()>= topPoint.getX() && point.getX() <= bottomPoint.getX() &&
                point.getY() >= topPoint.getY() && point.getY()<= bottomPoint.getY();
    }

    public void updatePositionAndLinks(Point2D position) {
        this.position = position;
        this.linksFromParents.forEach(Link::update);
        this.linksToChildren.forEach(Link::update);
    }

    public void setHandle(Handle handle) {
        this.handle = handle;
    }

    public List<Link> getLinksFromParents() {
        return linksFromParents;
    }

    public List<Link> getLinksToChildren() {
        return linksToChildren;
    }
}
