package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public non-sealed class Transition extends StepAndTransitionCommonBehavior {
    private static int transitionCounter = 0;
    private String receptivity = "1";

    public Transition(GraphicsContext ctx) {
        super(ctx);
        this.number = ++transitionCounter;
    }

    public static void resetCounter(int value) {
        transitionCounter = value;
    }

    public static int getCounter() {
        return transitionCounter;
    }

    public String getReceptivity() {
        return receptivity;
    }

    public void setReceptivity(String receptivity) {
        this.receptivity = (receptivity == null || receptivity.isBlank()) ? "1" : receptivity.trim();
    }

    /**
     * Retourne les étapes immédiatement amont reliées à cette transition.
     */
    public java.util.List<Step> getParentSteps() {
        return linksFromParents.stream()
                .map(Link::getParentComponent)
                .filter(Step.class::isInstance)
                .map(Step.class::cast)
                .toList();
    }

    /**
     * Retourne la première étape amont principale si disponible.
     */
    public java.util.Optional<Step> getPrimaryParentStep() {
        return getParentSteps().stream().findFirst();
    }

    @Override
    protected void drawNormalComponent() {
        double midX = getX() + UNIT_WIDTH / 2;
        double midY = getY() + UNIT_HEIGHT / 2;
        double left = getX() + UNIT_WIDTH / 4;
        double right = getX() + UNIT_WIDTH * 3 / 4;

        // Trait vertical de passage
        ctx.strokeLine(midX, getY(), midX, getY() + UNIT_HEIGHT);

        // Trait transversal normalisé de transition (toujours un trait simple CEI 60848)
        ctx.strokeLine(left, midY, right, midY);

        // Repère de transition à gauche (ex: T1)
        ctx.setTextAlign(TextAlignment.RIGHT);
        ctx.setFill(Color.DARKORANGE);
        ctx.setFont(Font.font("", FontWeight.BOLD, 14));
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText("T" + this.number, left - 6, midY);

        // Réceptivité normalisée CEI 60848 à droite (ex: 1, dcy, a . b)
        ctx.setTextAlign(TextAlignment.LEFT);
        ctx.setFill(MAIN_COLOR);
        ctx.setFont(Font.font("Monospaced", FontWeight.BOLD, 15));
        ctx.setTextBaseline(VPos.CENTER);
        String rec = (receptivity == null || receptivity.isBlank()) ? "1" : receptivity;
        ctx.fillText(rec, right + 8, midY);
    }

    @Override
    protected void drawSelectedComponent() {
        drawNormalComponent();
    }
}
