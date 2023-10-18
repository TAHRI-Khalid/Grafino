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
        var gap = 8;
        ctx.setLineWidth(Style.LINE_WIDTH);
        ctx.setStroke(Style.LINE_COLOR);

        if (selected){
            ctx.setFill(Style.SELECTION_COLOR);
        }else {
            ctx.setFill(Color.WHITE);
        }

        ctx.fillRect(position.getX(), position.getY(), Style.UNIT_WIDTH, Style.UNIT_HEIGHT);
        ctx.strokeRect(position.getX(), position.getY(), Style.UNIT_WIDTH, Style.UNIT_HEIGHT);
        ctx.strokeRect(position.getX() + gap, position.getY() + gap, Style.UNIT_WIDTH - (2 * gap), Style.UNIT_HEIGHT - (2 * gap));
    }
}
