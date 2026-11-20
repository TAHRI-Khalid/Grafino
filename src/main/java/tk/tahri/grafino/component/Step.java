package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import tk.tahri.grafino.ui.AppTheme;

import java.util.ArrayList;
import java.util.List;

public non-sealed class Step extends StepAndTransitionCommonBehavior {

    private final List<Action> actions = new ArrayList<>();
    private static int stepCounter = 0;
    private boolean initial = false;
    private boolean active = false;
    private java.util.Set<String> activeOutputNames = java.util.Set.of();

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setActiveOutputs(java.util.Set<String> activeOutputs) {
        this.activeOutputNames = (activeOutputs != null) ? activeOutputs : java.util.Set.of();
    }

    @Override
    protected void drawNormalComponent() {
        // Trait vertical de passage
        ctx.strokeLine(getX() + UNIT_WIDTH / 2, getY(), getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT);

        // Fond et contour carré
        if (active) {
            ctx.setFill(Color.rgb(220, 252, 231)); // Vert pastel émeraude actif
            ctx.fillRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
            ctx.setStroke(Color.rgb(16, 185, 129)); // Contour vert émeraude
            ctx.setLineWidth(2.2);
            ctx.strokeRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
            ctx.setLineWidth(LINE_WIDTH);
            ctx.setStroke(LINE_COLOR);
        } else {
            ctx.setFill(Color.WHITE);
            ctx.fillRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
            ctx.strokeRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
        }

        // Si étape initiale : double carré selon la norme CEI 60848
        if (initial) {
            double inner = 5.0;
            if (active) {
                ctx.setStroke(Color.rgb(16, 185, 129));
                ctx.strokeRect(getX() + inner, getY() + GAP + inner, UNIT_WIDTH - inner * 2, UNIT_HEIGHT - GAP * 2 - inner * 2);
                ctx.setStroke(LINE_COLOR);
            } else {
                ctx.strokeRect(getX() + inner, getY() + GAP + inner, UNIT_WIDTH - inner * 2, UNIT_HEIGHT - GAP * 2 - inner * 2);
            }
        }

        // Jeton de marquage CEI 60848 (point plein au centre haut de l'étape active)
        if (active) {
            double dotR = 4.5;
            ctx.setFill(Color.rgb(16, 185, 129));
            ctx.fillOval(getX() + UNIT_WIDTH / 2 - dotR, getY() + GAP + 6, dotR * 2, dotR * 2);
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

    private static final Text TEXT_MEASURER = new Text();
    private static final java.util.Map<String, Double> WIDTH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static double measureTextWidth(String str, Font font) {
        if (str == null || str.isEmpty()) return 0.0;
        String key = font.getSize() + ":" + str;
        return WIDTH_CACHE.computeIfAbsent(key, k -> {
            synchronized (TEXT_MEASURER) {
                TEXT_MEASURER.setFont(font);
                TEXT_MEASURER.setText(str);
                return TEXT_MEASURER.getLayoutBounds().getWidth();
            }
        });
    }

    protected void drawActions() {
        if (this.actions.isEmpty()) {
            return;
        }

        var font = AppTheme.canvasFont("", FontWeight.BOLD, 14);
        var qualFont = AppTheme.canvasFont("", FontWeight.BOLD, 12);

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

            final double textWidth = Math.max(30, measureTextWidth(varName, font));

            boolean showQualifier = !qual.equalsIgnoreCase("Normal") && !qual.equalsIgnoreCase("N") && !qual.isEmpty();
            double qualWidth = showQualifier ? Math.max(26, measureTextWidth(qual, qualFont) + 10) : 0;
            double totalWidth = qualWidth + textWidth + 2 * GAP + 12;

            // Déterminer si l'action est active (allumée)
            String targetKey = varName.trim().toUpperCase(java.util.Locale.ROOT);
            boolean isActionActive = (active && (qual.equalsIgnoreCase("N") || qual.equalsIgnoreCase("Normal")))
                    || activeOutputNames.contains(targetKey);

            if (isActionActive) {
                // Rectangle d'action allumé : fond cyan doux lumineux et contour cyan vibrant
                ctx.setFill(Color.rgb(224, 242, 254));
                ctx.fillRect(curX, actY, totalWidth, actH);
                ctx.setStroke(Color.rgb(2, 132, 199));
                ctx.setLineWidth(2.0);
                ctx.strokeRect(curX, actY, totalWidth, actH);
                ctx.setLineWidth(LINE_WIDTH);
                ctx.setStroke(LINE_COLOR);
            } else {
                // Rectangle d'action blanc standard
                ctx.setFill(Color.WHITE);
                ctx.fillRect(curX, actY, totalWidth, actH);
                ctx.strokeRect(curX, actY, totalWidth, actH);
            }

            if (showQualifier) {
                // Compartiment séparé du qualificateur
                ctx.strokeLine(curX + qualWidth, actY, curX + qualWidth, actY + actH);

                ctx.setFill(isActionActive ? Color.rgb(2, 132, 199) : Color.rgb(180, 40, 40));
                ctx.setFont(qualFont);
                ctx.setTextBaseline(VPos.CENTER);
                ctx.setTextAlign(TextAlignment.CENTER);
                ctx.fillText(qual, curX + qualWidth / 2, getY() + UNIT_HEIGHT / 2);
            }

            // Texte de l'action / variable
            ctx.setFill(isActionActive ? Color.rgb(3, 105, 161) : MAIN_COLOR);
            ctx.setFont(font);
            ctx.setTextBaseline(VPos.CENTER);
            ctx.setTextAlign(TextAlignment.CENTER);
            ctx.fillText(varName, curX + qualWidth + (totalWidth - qualWidth - 12) / 2, getY() + UNIT_HEIGHT / 2);

            // Témoin LED lumineux si l'action est active
            if (isActionActive) {
                ctx.setFill(Color.rgb(2, 132, 199));
                ctx.fillOval(curX + totalWidth - 12, getY() + UNIT_HEIGHT / 2 - 4, 8, 8);
            }

            curX += totalWidth;
        }
    }

    public List<Action> getActions() {
        return actions;
    }
}
