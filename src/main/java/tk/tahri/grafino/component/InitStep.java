package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

public class InitStep extends Step {
    public InitStep(GraphicsContext gtx, Point2D position) {
        super(gtx);
        this.position=position;
    }

    @Override
    public void drawComponent() {
        super.drawComponent();
        var gap = 8;
        ctx.strokeRect(getX() + gap, getY() +  GAP + gap,  UNIT_WIDTH - gap * 2,  UNIT_HEIGHT -  GAP * 2 - gap * 2);
    }
}
