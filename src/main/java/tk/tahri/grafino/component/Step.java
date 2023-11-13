package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

public non-sealed class Step extends StepAndTransitionCommonBehavior {

    private final List<Action> actions = new ArrayList<>();

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
        this.drawNumber("S" + this.number, new Point2D(getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT / 2));

        drawActions();
    }

    protected void drawSelectedComponent() {
        drawNormalComponent();
    }

    protected void drawActions() {
        if (this.getActions().isEmpty()) {
            return;
        }
        var font = Font.font("", FontWeight.BOLD, 18);
        ctx.strokeLine(getX() + UNIT_WIDTH, getY() + UNIT_HEIGHT / 2, getX() + UNIT_WIDTH + GAP, getY() + UNIT_HEIGHT / 2);

        ctx.setFill(Color.DARKORANGE);
        ctx.setFont(font);
        ctx.setTextBaseline(VPos.CENTER);
        ctx.setTextAlign(TextAlignment.LEFT);

        var begin = new Point2D(getX() + UNIT_WIDTH + GAP, getY() + 1.5 * GAP);
        for (Action action : actions) {
            var variable = action.variable().getName().toUpperCase();
            final Text text = new Text(variable);
            text.setFont(font);
            final double width = text.getLayoutBounds().getWidth();

            ctx.strokeRect(begin.getX(), begin.getY(), width + 2 * GAP, UNIT_HEIGHT - 3 * GAP);
            ctx.fillText(variable, begin.getX() + GAP, getY() + UNIT_HEIGHT / 2);
            begin = begin.add(width + 2 * GAP, 0);
        }
    }

    public List<Action> getActions() {
        return actions;
    }
}
