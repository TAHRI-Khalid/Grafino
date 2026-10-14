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

public sealed abstract class StepAndTransitionCommonBehavior extends Component permits Step, Transition {
    protected List<Link> linksFromParents = new ArrayList<>();
    protected List<Link> linksToChildren = new ArrayList<>();
    protected Point2D position;
    protected Handle handle = Handle.NONE;
    protected final double radius = 12;
    protected int number;
    protected String id = java.util.UUID.randomUUID().toString();

    protected StepAndTransitionCommonBehavior(GraphicsContext ctx) {
        super(ctx);
        this.setSelected(true);
    }

    public double getX() {
        return position != null ? position.getX() : 0;
    }

    public double getY() {
        return position != null ? position.getY() : 0;
    }

    public void drawHandle() {
        if (!isSelected() && handle == Handle.NONE) {
            return;
        }
        ctx.setFill(SELECTION_COLOR);
        ctx.setStroke(LINE_COLOR);
        ctx.setLineWidth(LINE_WIDTH);

        if (handle == Handle.TOP) {
            drawTopHandle();
        } else if (handle == Handle.BOTTOM) {
            drawBottomHandle();
        } else {
            drawTopHandle();
            drawBottomHandle();
        }
    }

    private void drawTopHandle() {
        ctx.fillOval(getX() + UNIT_WIDTH / 2 - radius / 2, getY() - radius / 2, radius, radius);
        ctx.strokeOval(getX() + UNIT_WIDTH / 2 - radius / 2, getY() - radius / 2, radius, radius);
    }

    private void drawBottomHandle() {
        ctx.fillOval(getX() + UNIT_WIDTH / 2 - radius / 2, getY() + UNIT_HEIGHT - radius / 2, radius, radius);
        ctx.strokeOval(getX() + UNIT_WIDTH / 2 - radius / 2, getY() + UNIT_HEIGHT - radius / 2, radius, radius);
    }

    @Override
    public boolean isPointOnTheComponent(Point2D point) {
        return isPointNearOrOn(point, 0);
    }

    public boolean isPointNearOrOn(Point2D point, double tolerance) {
        return point.getX() >= (this.getX() - tolerance) &&
               point.getX() <= (this.getX() + UNIT_WIDTH + tolerance) &&
               point.getY() >= (this.getY() - tolerance) &&
               point.getY() <= (this.getY() + UNIT_HEIGHT + tolerance);
    }

    public boolean isBottomHandlePressed(Point2D point) {
        double centerX = this.getX() + UNIT_WIDTH / 2;
        double bottomY = this.getY() + UNIT_HEIGHT;
        // Zone de clic élargie et centrée pour faciliter la saisie à la souris
        return Math.abs(point.getX() - centerX) <= 30.0 &&
               point.getY() >= (bottomY - 25.0) && point.getY() <= (bottomY + 25.0);
    }

    public void updatePositionAndLinks(Point2D position) {
        this.position = position;
        this.linksFromParents.forEach(Link::update);
        this.linksToChildren.forEach(Link::update);
    }

    public void setHandle(Handle handle) {
        this.handle = handle;
    }

    public Handle getHandle() {
        return handle;
    }

    public List<Link> getLinksFromParents() {
        return linksFromParents;
    }

    public List<Link> getLinksToChildren() {
        return linksToChildren;
    }

    protected void drawNumber(String number, Point2D position) {
        ctx.setFill(Color.DARKORANGE);
        ctx.setFont(Font.font("", FontWeight.BOLD, 18));
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText(number, position.getX(), position.getY());
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Point2D getPosition() {
        return position;
    }

    public void setPosition(Point2D position) {
        this.position = position;
    }
}
