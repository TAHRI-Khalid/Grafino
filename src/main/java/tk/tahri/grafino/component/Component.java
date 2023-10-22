package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import tk.tahri.grafino.Style;
import tk.tahri.grafino.Vertex;

import java.util.ArrayList;
import java.util.List;

public abstract class Component {
    protected Point2D position;
    protected final GraphicsContext ctx;
    protected boolean selected = false;
    protected Vertex vertex = Vertex.NONE;
    protected double radius = 12;
    protected List<Component> parents=new ArrayList<>();
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
        component.parents.add(this);
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

    public List<Component> getParents() {
        return parents;
    }

    public void setParents(List<Component> parents) {
        this.parents = parents;
    }

    public void setVertex(Vertex vertex) {
        this.vertex = vertex;
    }

    public void drawVertex() {
        ctx.setFill(Style.SELECTION_COLOR);
        ctx.setStroke(Style.LINE_COLOR);
        ctx.setLineWidth(Style.LINE_WIDTH);
        switch (vertex) {
            case TOP -> drawTopVertex();
            case BOTTOM -> drawBottomVertex();
            case BOTH -> {
                if (parents.isEmpty()) drawTopVertex();
                if (children.isEmpty()) drawBottomVertex();
            }
        }
    }

    private void drawTopVertex() {
        ctx.fillOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY(), radius, radius);
        ctx.strokeOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY(), radius, radius);
    }

    private void drawBottomVertex() {
        ctx.fillOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY() + Style.UNIT_HEIGHT - radius, radius, radius);
        ctx.strokeOval(getX() - radius / 2 + Style.UNIT_WIDTH / 2, getY() + Style.UNIT_HEIGHT - radius, radius, radius);
    }

    private boolean isMouseIntoRegion(MouseEvent event, Point2D top, Point2D bottom) {
        var mouseX = event.getX();
        var mouseY = event.getY();
        return mouseX >= top.getX() && mouseX <= bottom.getX() &&
                mouseY >= top.getY() && mouseY <= bottom.getY();
    }

    public void drawDraggedLink(Component selectedComponent, Point2D mouseDragPosition) {
        var begin = new Point2D(selectedComponent.getX() + Style.UNIT_WIDTH / 2, selectedComponent.getY() + Style.UNIT_HEIGHT);
        var end = new Point2D(mouseDragPosition.getX(), mouseDragPosition.getY());

        ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), end.getY());
        ctx.strokeLine(begin.getX(), end.getY(), end.getX(), end.getY());
    }
}
