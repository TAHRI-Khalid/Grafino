package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tk.tahri.grafino.Handle;
import java.util.ArrayList;
import java.util.List;

public sealed abstract class StepAndTransitionCommonBehavior extends Component  permits Step,Transition{
    protected List<Link> linksFromParents = new ArrayList<>();
    protected List<Link> linksToChildren = new ArrayList<>();
    protected Point2D position;
    protected Handle handle = Handle.NONE;
    protected final double radius = 12;
    protected int number;

    protected StepAndTransitionCommonBehavior(GraphicsContext ctx) {
        super(ctx);
        this.setSelected(true);
    }

    public double getX() {
        return position.getX();
    }

    public double getY() {
        return position.getY();
    }

    public void drawHandle() {
        ctx.setFill(SELECTION_COLOR);
        ctx.setStroke(LINE_COLOR);
        ctx.setLineWidth(LINE_WIDTH);
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
        ctx.fillOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY(), radius, radius);
        ctx.strokeOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY(), radius, radius);
    }

    private void drawBottomHandle() {
        ctx.fillOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT - radius, radius, radius);
        ctx.strokeOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT - radius, radius, radius);
    }

    @Override
    public boolean isPointOnTheComponent(Point2D point) {
        var topPoint = new Point2D(this.getX(), this.getY());
        var bottomPoint = new Point2D(this.getX() + UNIT_WIDTH, this.getY() + UNIT_HEIGHT);
        return point.getX() >= topPoint.getX() && point.getX() <= bottomPoint.getX() &&
                point.getY() >= topPoint.getY() && point.getY() <= bottomPoint.getY();
    }


    public boolean isBottomHandlePressed(Point2D point) {
        var topPoint = new Point2D(this.getX() + UNIT_WIDTH / 2 - radius / 2, this.getY() + UNIT_HEIGHT - radius);
        var bottomPoint = new Point2D(this.getX() + UNIT_WIDTH / 2 + radius / 2, this.getY() + UNIT_HEIGHT);
        return point.getX() >= topPoint.getX() && point.getX() <= bottomPoint.getX() &&
                point.getY() >= topPoint.getY() && point.getY() <= bottomPoint.getY();
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

    protected void drawNumber(String number,Point2D position) {
        ctx.setFill(Color.DARKORANGE);
        ctx.setFont(Font.font("", FontWeight.BOLD, 18));
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText(number, position.getX(), position.getY());
    }

    public int getNumber() {
        return number;
    }
}
