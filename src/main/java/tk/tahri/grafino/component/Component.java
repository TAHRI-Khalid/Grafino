package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public abstract class Component {
    public static final Color MAIN_COLOR = Color.rgb(0, 129, 132); // #008184 Arduino Teal
    public static final Color LINE_COLOR = Color.rgb(0, 129, 132);
    public static final Color SELECTION_COLOR = Color.rgb(255, 204, 0); // #FFCC00 Jaune de sélection historique
    public static final double LINE_WIDTH = 2.5;
    public static final double SELECTION_CONTOUR_WIDTH = 3;
    public static final double UNIT_WIDTH = 80;
    public static final double UNIT_HEIGHT = 64;
    public static final double GAP = 10;

    protected GraphicsContext ctx;
    protected boolean selected = false;

    protected Component(GraphicsContext ctx) {
        this.ctx = ctx;
    }

    public GraphicsContext getGraphicsContext() {
        return ctx;
    }

    public void setGraphicsContext(GraphicsContext ctx) {
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

