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
    protected Component parent;
    protected List<Component> children = new ArrayList<>();

    protected Component(GraphicsContext ctx, double x, double y) {
        this.position = new Point2D(x, y);
        this.ctx = ctx;
    }

    public abstract void drawComponent();
    public abstract void drawLink();

    public boolean isPressed(MouseEvent event) {
        var mouseX = event.getX();
        var mouseY = event.getY();

        return mouseX >= position.getX() && mouseX <= position.getX() + Style.UNIT_WIDTH &&
                mouseY >= position.getY() && mouseY <= position.getY() + Style.UNIT_HEIGHT;
    }

    public void addChild(Component component){
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
}
