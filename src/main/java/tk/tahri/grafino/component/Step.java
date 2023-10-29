package tk.tahri.grafino.component;

import javafx.scene.canvas.GraphicsContext;

public class Step extends StepAndTransitionCommonBehavior {

    public Step(GraphicsContext gtx) {
        super(gtx);
    }

    protected void drawNormalComponent() {
        ctx.strokeLine(getX() + UNIT_WIDTH / 2, getY(), getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT);
        ctx.fillRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
        ctx.strokeRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
    }

    protected void drawSelectedComponent() {
        drawNormalComponent();
    }

}
