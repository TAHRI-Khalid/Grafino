package tk.tahri.grafino.component;

import javafx.scene.canvas.GraphicsContext;

public class Transition extends StepAndTransitionCommonBehavior {
    public Transition(GraphicsContext ctx) {
        super(ctx);
    }


    @Override
    protected void drawNormalComponent() {
        ctx.strokeLine( getX() +  UNIT_WIDTH / 2,  getY(),  getX() +  UNIT_WIDTH / 2,  getY() +  UNIT_HEIGHT);
        ctx.strokeLine( getX() +  UNIT_WIDTH / 3,  getY() +  UNIT_HEIGHT / 2,  getX() +  UNIT_WIDTH * 2 / 3,  getY() +  UNIT_HEIGHT / 2);
    }

    @Override
    protected void drawSelectedComponent() {
        drawNormalComponent();
    }

}
