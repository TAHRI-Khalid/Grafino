package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.TextAlignment;

public class Transition extends StepAndTransitionCommonBehavior {
    private static int transitionCounter = 0;

    public Transition(GraphicsContext ctx) {
        super(ctx);
        this.number = ++transitionCounter;
    }

    @Override
    protected void drawNormalComponent() {
        ctx.strokeLine(getX() + UNIT_WIDTH / 2, getY(), getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT);
        ctx.strokeLine(getX() + UNIT_WIDTH / 3, getY() + UNIT_HEIGHT / 2, getX() + UNIT_WIDTH * 2 / 3, getY() + UNIT_HEIGHT / 2);

        ctx.setTextAlign(TextAlignment.RIGHT);
        this.drawNumber("T"+this.number,new Point2D(getX() + UNIT_WIDTH / 3, getY() + UNIT_HEIGHT / 4));

    }

    @Override
    protected void drawSelectedComponent() {
        drawNormalComponent();
    }

}
