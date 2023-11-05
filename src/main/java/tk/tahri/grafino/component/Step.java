package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.TextAlignment;

public class Step extends StepAndTransitionCommonBehavior {

    private static int stepCounter = 0;

    public Step(GraphicsContext gtx) {
        super(gtx);
        this.number = ++stepCounter;
    }

    protected void drawNormalComponent() {
        ctx.strokeLine(getX() + UNIT_WIDTH / 2, getY(), getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT);
        ctx.fillRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
        ctx.strokeRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);

        ctx.setTextAlign(TextAlignment.CENTER);
        this.drawNumber("S"+this.number,new Point2D(getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT / 2));

    }

    protected void drawSelectedComponent() {
        drawNormalComponent();
    }

}
