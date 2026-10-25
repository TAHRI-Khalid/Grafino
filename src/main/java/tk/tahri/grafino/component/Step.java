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
    private boolean initial = false;

    public Step(GraphicsContext gtx) {
        super(gtx);
        this.number = ++stepCounter;
    }

    public static void resetCounter(int value) {
        stepCounter = value;
    }

    public static int getCounter() {
        return stepCounter;
    }

    public boolean isInitial() {
        return initial;
    }

    public void setInitial(boolean initial) {
        this.initial = initial;
    }

    @Override
    protected void drawNormalComponent() {
        // Trait vertical de passage
        ctx.strokeLine(getX() + UNIT_WIDTH / 2, getY(), getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT);

        // Fond et contour carré
        ctx.setFill(Color.WHITE);
        ctx.fillRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
        ctx.strokeRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);

        // Si étape initiale : double carré selon la norme CEI 60848
        if (initial) {
            double inner = 5.0;
            ctx.strokeRect(getX() + inner, getY() + GAP + inner, UNIT_WIDTH - inner * 2, UNIT_HEIGHT - GAP * 2 - inner * 2);
        }

        // Numéro d'étape centré
        ctx.setTextAlign(TextAlignment.CENTER);
        this.drawNumber("S" + this.number, new Point2D(getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT / 2));

        drawActions();
    }

    @Override
    protected void drawSelectedComponent() {
        drawNormalComponent();
    }

    protected void drawActions() {
        if (this.actions.isEmpty()) {
            return;
        }

        var font = Font.font("", FontWeight.BOLD, 14);
        var qualFont = Font.font("", FontWeight.BOLD, 12);

        // Ligne de liaison horizontale vers le premier bloc action
        ctx.strokeLine(getX() + UNIT_WIDTH, getY() + UNIT_HEIGHT / 2, getX() + UNIT_WIDTH + GAP, getY() + UNIT_HEIGHT / 2);

        var curX = getX() + UNIT_WIDTH + GAP;
        double actY = getY() + 1.2 * GAP;
        double actH = UNIT_HEIGHT - 2.4 * GAP;

        for (Action action : actions) {
            String qual = action.qualifier() != null ? action.qualifier().trim() : "N";
            String varName = action.getDisplayText();
            if (action.hasParameter()) {
                qual += " " + action.parameter();
            }

            final Text text = new Text(varName);
            text.setFont(font);
            final double textWidth = Math.max(30, text.getLayoutBounds().getWidth());

            boolean showQualifier = !qual.equalsIgnoreCase("Normal") && !qual.equalsIgnoreCase("N") && !qual.isEmpty();
            double qualWidth = showQualifier ? Math.max(26, new Text(qual).getLayoutBounds().getWidth() + 10) : 0;
            double totalWidth = qualWidth + textWidth + 2 * GAP;

            // Rectangle d'action blanc
            ctx.setFill(Color.WHITE);
            ctx.fillRect(curX, actY, totalWidth, actH);
            ctx.strokeRect(curX, actY, totalWidth, actH);

            if (showQualifier) {
                // Compartiment séparé du qualificateur
                ctx.strokeLine(curX + qualWidth, actY, curX + qualWidth, actY + actH);

                ctx.setFill(Color.rgb(180, 40, 40));
                ctx.setFont(qualFont);
                ctx.setTextBaseline(VPos.CENTER);
                ctx.setTextAlign(TextAlignment.CENTER);
                ctx.fillText(qual, curX + qualWidth / 2, getY() + UNIT_HEIGHT / 2);
            }

            // Texte de l'action / variable
            ctx.setFill(MAIN_COLOR);
            ctx.setFont(font);
            ctx.setTextBaseline(VPos.CENTER);
            ctx.setTextAlign(TextAlignment.CENTER);
            ctx.fillText(varName, curX + qualWidth + (totalWidth - qualWidth) / 2, getY() + UNIT_HEIGHT / 2);

            curX += totalWidth;
        }
    }

    public List<Action> getActions() {
        return actions;
    }
}
