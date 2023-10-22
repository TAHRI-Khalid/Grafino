package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import tk.tahri.grafino.Style;

import java.util.ArrayList;
import java.util.List;

public abstract class Component {
    protected Point2D position;
    protected final GraphicsContext ctx;
    protected boolean selected = false;
    protected boolean shouldDrawVertex = false;
    protected double radius = 12;
    protected Component parent;
    protected List<Component> children = new ArrayList<>();

    protected Component(GraphicsContext ctx, double x, double y) {
        this.position = new Point2D(x, y);
        this.ctx = ctx;
    }

    public abstract void drawComponent();

    public abstract void drawLink();

    public boolean isPressed(MouseEvent event) {
        var topPoint = new Point2D(this.getX(), this.getY());
        var bottomPoint = new Point2D(this.getX() + Style.UNIT_WIDTH, this.getY() + Style.UNIT_HEIGHT);
        return isMouseIntoRegion(event, topPoint, bottomPoint);
    }

    public boolean isTopVertexPressed(MouseEvent event) {
        var topPoint = new Point2D(this.getX() + Style.UNIT_WIDTH / 2 - radius / 2, this.getY());
        var bottomPoint = new Point2D(this.getX() + Style.UNIT_WIDTH / 2 + radius / 2, this.getY() + radius);
        return isMouseIntoRegion(event, topPoint, bottomPoint);
    }

    public boolean isBottomVertexPressed(MouseEvent event) {
        var topPoint = new Point2D(this.getX() + Style.UNIT_WIDTH / 2 - radius / 2, this.getY() + Style.UNIT_HEIGHT - radius);
        var bottomPoint = new Point2D(this.getX() + Style.UNIT_WIDTH / 2 + radius / 2, this.getY() + Style.UNIT_HEIGHT);
        return isMouseIntoRegion(event, topPoint, bottomPoint);
    }

    public void addChild(Component component) {
        this.children.add(component);
        component.setParent(this);
    }

    public double getX() {
        return position.getX();
    }

    public double getY() {
        return position.getY();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public List<Component> getChildren() {
        return children;
    }

    public void setPosition(Point2D position) {
        this.position = position;
    }

    public Component getParent() {
        return parent;
    }

    public void setParent(Component parent) {
        this.parent = parent;
    }

    public void setShouldDrawVertex(boolean shouldDrawVertex) {
        this.shouldDrawVertex = shouldDrawVertex;
    }

    public void drawVertex() {
        if (shouldDrawVertex) {
            ctx.setFill(Style.SELECTION_COLOR);
            ctx.setStroke(Style.LINE_COLOR);
            ctx.setLineWidth(Style.LINE_WIDTH);

            if (parent == null) {
                drawTopVertex();
            }
            if (children.isEmpty()) {
                drawBottomVertex();
            }
        }
    }

    public void drawTopVertex(){
        ctx.fillOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY(), radius, radius);
        ctx.strokeOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY(), radius, radius);
    }
    public void drawBottomVertex(){
        ctx.fillOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY() + Style.UNIT_HEIGHT - radius, radius, radius);
        ctx.strokeOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY() + Style.UNIT_HEIGHT - radius, radius, radius);
    }

    private boolean isMouseIntoRegion(MouseEvent event, Point2D top, Point2D bottom) {
        var mouseX = event.getX();
        var mouseY = event.getY();
        return mouseX >= top.getX() && mouseX <= bottom.getX() &&
                mouseY >= top.getY() && mouseY <= bottom.getY();
    }

}
