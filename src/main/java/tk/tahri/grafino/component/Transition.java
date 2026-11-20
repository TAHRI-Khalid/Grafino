package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import tk.tahri.grafino.ui.AppTheme;

public non-sealed class Transition extends StepAndTransitionCommonBehavior {
    private static int transitionCounter = 0;
    private String receptivity = "1";

    // États de simulation pédagogique (Axe 2 : CEI 60848 Règle 2)
    private boolean simulationActive = false;
    private boolean validated = false;        // Toutes étapes amont actives
    private boolean receptivityTrue = false;  // Condition booléenne / ST évaluée à VRAI
    private boolean fireable = false;         // Validée ET Réceptivité Vraie (franchissable)

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

    // Gestion des états de simulation
    public void setSimulationState(boolean active, boolean validated, boolean receptivityTrue, boolean fireable) {
        this.simulationActive = active;
        this.validated = validated;
        this.receptivityTrue = receptivityTrue;
        this.fireable = fireable;
    }

    public void clearSimulationState() {
        this.simulationActive = false;
        this.validated = false;
        this.receptivityTrue = false;
        this.fireable = false;
    }

    public boolean isSimulationActive() {
        return simulationActive;
    }

    public boolean isValidated() {
        return validated;
    }

    public boolean isReceptivityTrue() {
        return receptivityTrue;
    }

    public boolean isFireable() {
        return fireable;
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

        if (ctx == null) return;

        // 1. Trait vertical de passage
        ctx.strokeLine(midX, getY(), midX, getY() + UNIT_HEIGHT);

        // 2. Trait transversal & Condition selon le mode de simulation
        if (simulationActive) {
            drawSimulationComponent(midX, midY, left, right);
            return;
        }

        // Mode normal d'édition
        ctx.strokeLine(left, midY, right, midY);

        // Repère de transition à gauche (ex: T1)
        ctx.setTextAlign(TextAlignment.RIGHT);
        ctx.setFill(Color.DARKORANGE);
        ctx.setFont(AppTheme.canvasFont("", FontWeight.BOLD, 14));
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText("T" + this.number, left - 6, midY);

        // Réceptivité normalisée CEI 60848 à droite (ex: 1, dcy, a . b)
        ctx.setTextAlign(TextAlignment.LEFT);
        ctx.setFill(MAIN_COLOR);
        ctx.setFont(AppTheme.canvasFont("Monospaced", FontWeight.BOLD, 15));
        ctx.setTextBaseline(VPos.CENTER);
        String rec = (receptivity == null || receptivity.isBlank()) ? "1" : receptivity;
        ctx.fillText(rec, right + 8, midY);
    }

    /**
     * Rendu pédagogique tripartite en simulation :
     * 1. Franchissable (Vert éclatant + halo + flèche ▶) : Validée ET Réceptivité Vraie
     * 2. Validée en attente (Orange ambré + sablier ⏳) : Étape amont active mais condition fausse
     * 3. Non validée (Gris ardoise) : Étape amont inactive
     */
    private void drawSimulationComponent(double midX, double midY, double left, double right) {
        String rec = (receptivity == null || receptivity.isBlank()) ? "1" : receptivity;

        if (fireable) {
            // État 1 : FRANCHISSABLE (CEI 60848 Règle 2 respectée)
            ctx.save();
            // Halo lumineux vert
            ctx.setStroke(Color.rgb(16, 185, 129, 0.45));
            ctx.setLineWidth(8.0);
            ctx.strokeLine(left - 4, midY, right + 4, midY);

            // Trait principal vert vibrant
            ctx.setStroke(Color.rgb(16, 185, 129));
            ctx.setLineWidth(4.2);
            ctx.strokeLine(left - 2, midY, right + 2, midY);

            // Flèche indicatrice de franchissement immédiat
            ctx.setFill(Color.rgb(16, 185, 129));
            double arrowSize = 6.0;
            ctx.fillPolygon(
                    new double[]{midX - arrowSize, midX + arrowSize, midX},
                    new double[]{midY + 3, midY + 3, midY + 3 + arrowSize * 1.5},
                    3
            );
            ctx.restore();

            // Repère T<n> vert éclatant
            ctx.setTextAlign(TextAlignment.RIGHT);
            ctx.setFill(Color.rgb(16, 185, 129));
            ctx.setFont(AppTheme.canvasFont("", FontWeight.BOLD, 14));
            ctx.setTextBaseline(VPos.CENTER);
            ctx.fillText("T" + this.number, left - 6, midY);

            // Réceptivité vraie en vert éclatant avec symbole de validation
            ctx.setTextAlign(TextAlignment.LEFT);
            ctx.setFill(Color.rgb(5, 150, 105)); // Vert foncé soutenu lisible
            ctx.setFont(AppTheme.canvasFont("Monospaced", FontWeight.BOLD, 15));
            ctx.setTextBaseline(VPos.CENTER);
            ctx.fillText("✓ " + rec, right + 8, midY);

        } else if (validated) {
            // État 2 : VALIDÉE MAIS RÉCEPTIVITÉ FAUSSE (En attente d'événement ou capteur)
            ctx.save();
            ctx.setStroke(Color.rgb(245, 158, 11)); // Orange ambré
            ctx.setLineWidth(3.6);
            ctx.strokeLine(left - 1, midY, right + 1, midY);
            ctx.restore();

            // Repère T<n> ambré
            ctx.setTextAlign(TextAlignment.RIGHT);
            ctx.setFill(Color.rgb(217, 119, 6));
            ctx.setFont(AppTheme.canvasFont("", FontWeight.BOLD, 14));
            ctx.setTextBaseline(VPos.CENTER);
            ctx.fillText("T" + this.number, left - 6, midY);

            // Réceptivité fausse en rouge-orangé avec symbole d'attente
            ctx.setTextAlign(TextAlignment.LEFT);
            ctx.setFill(Color.rgb(220, 38, 38)); // Rouge explicite
            ctx.setFont(AppTheme.canvasFont("Monospaced", FontWeight.BOLD, 15));
            ctx.setTextBaseline(VPos.CENTER);
            ctx.fillText("⏳ " + rec, right + 8, midY);

        } else {
            // État 3 : NON VALIDÉE (Étape amont inactive)
            ctx.save();
            ctx.setStroke(Color.rgb(148, 163, 184)); // Gris ardoise
            ctx.setLineWidth(2.0);
            ctx.strokeLine(left, midY, right, midY);
            ctx.restore();

            // Repère T<n> grisé
            ctx.setTextAlign(TextAlignment.RIGHT);
            ctx.setFill(Color.rgb(148, 163, 184));
            ctx.setFont(AppTheme.canvasFont("", FontWeight.NORMAL, 13));
            ctx.setTextBaseline(VPos.CENTER);
            ctx.fillText("T" + this.number, left - 6, midY);

            // Réceptivité grisée
            ctx.setTextAlign(TextAlignment.LEFT);
            ctx.setFill(Color.rgb(148, 163, 184));
            ctx.setFont(AppTheme.canvasFont("Monospaced", FontWeight.NORMAL, 14));
            ctx.setTextBaseline(VPos.CENTER);
            ctx.fillText(rec, right + 8, midY);
        }
    }

    @Override
    protected void drawSelectedComponent() {
        drawNormalComponent();
    }
}
