package tk.tahri.grafino.component;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import tk.tahri.grafino.Style;

public class InitStep extends Step {
    public InitStep(GraphicsContext gtx, double x, double y) {
        super(gtx, x, y);
    }

    @Override
    public void drawComponent() {
        super.drawComponent();
        var gap = 8;
        ctx.strokeRect(position.getX() + gap, position.getY() + Style.GAP + gap, Style.UNIT_WIDTH - gap * 2, Style.UNIT_HEIGHT - Style.GAP * 2 - gap * 2);
    }
}
