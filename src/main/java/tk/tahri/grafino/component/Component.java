package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public abstract class Component {
    public static final Color MAIN_COLOR = Color.rgb(0, 109, 112);
    public static final Color LINE_COLOR = Color.rgb(23, 161, 165);
    public static final Color SELECTION_COLOR = Color.rgb(255, 204, 0);
    public static final double LINE_WIDTH = 2.5;
    public static final double SELECTION_CONTOUR_WIDTH = 3;
    public static final double UNIT_WIDTH = 80;
    public static final double UNIT_HEIGHT = 68;
    public static final double GAP = 10;

    protected final GraphicsContext ctx;
    protected boolean selected = false;

    protected Component(GraphicsContext ctx) {
        this.ctx = ctx;
    }

    public void drawComponent() {
        if (selected) {
            ctx.setLineWidth(LINE_WIDTH + 2 * SELECTION_CONTOUR_WIDTH);
            ctx.setStroke(SELECTION_COLOR);
            drawSelectedComponent();
        }
        ctx.setLineWidth(LINE_WIDTH);
        ctx.setStroke(LINE_COLOR);
        ctx.setFill(Color.WHITE);
        drawNormalComponent();
    }

    protected abstract void drawNormalComponent();

    protected abstract void drawSelectedComponent();

    public abstract boolean isPointOnTheComponent(Point2D point);
    public boolean isPointOnTheComponent(double x, double y){
        var point=new Point2D(x,y);
        return isPointOnTheComponent(point);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}

