package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import tk.tahri.grafino.Handle;
import tk.tahri.grafino.model.SfcValidationMessage;

import java.util.ArrayList;
import java.util.List;

public sealed abstract class StepAndTransitionCommonBehavior extends Component permits Step, Transition {
    public static final double ACTION_BTN_RADIUS = 10.0;

    protected List<Link> linksFromParents = new ArrayList<>();
    protected List<Link> linksToChildren = new ArrayList<>();
    public Point2D position;
    protected Handle handle = Handle.NONE;
    protected final double radius = 12;
    protected int number;
    private List<SfcValidationMessage> diagnosticMessages = List.of();

    protected StepAndTransitionCommonBehavior(GraphicsContext ctx) {
        super(ctx);
        this.setSelected(true);
    }

    public List<SfcValidationMessage> getDiagnosticMessages() {
        return diagnosticMessages;
    }

    public void setDiagnosticMessages(List<SfcValidationMessage> messages) {
        this.diagnosticMessages = (messages == null) ? List.of() : List.copyOf(messages);
    }

    public boolean hasDiagnosticError() {
        return diagnosticMessages.stream().anyMatch(SfcValidationMessage::isError);
    }

    public boolean hasDiagnosticWarning() {
        return !hasDiagnosticError() && diagnosticMessages.stream().anyMatch(SfcValidationMessage::isWarning);
    }

    @Override
    public void drawComponent() {
        super.drawComponent();
        if (ctx != null && !diagnosticMessages.isEmpty()) {
            drawDiagnosticBadge();
        }
    }

    protected void drawDiagnosticBadge() {
        boolean isErr = hasDiagnosticError();
        Color badgeColor = isErr ? Color.rgb(217, 56, 30) : Color.rgb(245, 158, 11); // Rouge #D9381E ou Ambre #F59E0B

        double badgeX;
        double badgeY;
        if (this instanceof Step) {
            badgeX = getX() + 2;
            badgeY = getY() + GAP + 2;
        } else {
            badgeX = getX() + UNIT_WIDTH / 4 - 28;
            badgeY = getY() + UNIT_HEIGHT / 2 - 14;
        }

        double r = 8.5;
        ctx.save();
        ctx.setFill(badgeColor);
        ctx.fillOval(badgeX - r, badgeY - r, r * 2, r * 2);
        ctx.setStroke(Color.WHITE);
        ctx.setLineWidth(1.6);
        ctx.strokeOval(badgeX - r, badgeY - r, r * 2, r * 2);

        ctx.setFill(Color.WHITE);
        ctx.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        ctx.setTextAlign(TextAlignment.CENTER);
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText(isErr ? "!" : "⚠", badgeX, badgeY + 0.5);
        ctx.restore();
    }

    public double getX() {
        return position != null ? position.getX() : 0.0;
    }

    public double getY() {
        return position != null ? position.getY() : 0.0;
    }

    public void setPosition(Point2D position) {
        updatePositionAndLinks(position);
    }

    public void drawHandle() {
        ctx.setFill(SELECTION_COLOR);
        ctx.setStroke(LINE_COLOR);
        ctx.setLineWidth(LINE_WIDTH);
        switch (handle) {
            case TOP -> drawTopHandle();
            case BOTTOM -> drawBottomHandle();
            case BOTH -> {
                if (this.linksFromParents.isEmpty()) drawTopHandle();
                if (this.linksToChildren.isEmpty()) drawBottomHandle();
            }
        }
    }

    private void drawTopHandle() {
        ctx.fillOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY(), radius, radius);
        ctx.strokeOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY(), radius, radius);
    }

    private void drawBottomHandle() {
        ctx.fillOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT - radius, radius, radius);
        ctx.strokeOval(getX() - radius / 2 + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT - radius, radius, radius);
    }

    public Point2D getQuickAddNextCenter() {
        return new Point2D(getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT + 14);
    }

    public Point2D getQuickAddBranchCenter() {
        return new Point2D(getX() + UNIT_WIDTH + 14, getY() + UNIT_HEIGHT / 2);
    }

    public Point2D getQuickDeleteCenter() {
        return new Point2D(getX() + UNIT_WIDTH + 8, getY() - 4);
    }

    public boolean isQuickAddNextPressed(Point2D p) {
        return getQuickAddNextCenter().distance(p) <= ACTION_BTN_RADIUS + 3;
    }

    public boolean isQuickAddBranchPressed(Point2D p) {
        return getQuickAddBranchCenter().distance(p) <= ACTION_BTN_RADIUS + 3;
    }

    public boolean isQuickDeletePressed(Point2D p) {
        return getQuickDeleteCenter().distance(p) <= ACTION_BTN_RADIUS + 3;
    }

    public void drawQuickActionHandles() {
        // 1. Bouton "Suivant (+)" en bas
        Point2D nextCenter = getQuickAddNextCenter();
        ctx.setFill(Color.rgb(0, 129, 132)); // Arduino Teal
        ctx.fillOval(nextCenter.getX() - ACTION_BTN_RADIUS, nextCenter.getY() - ACTION_BTN_RADIUS, ACTION_BTN_RADIUS * 2, ACTION_BTN_RADIUS * 2);
        ctx.setStroke(Color.WHITE);
        ctx.setLineWidth(1.6);
        ctx.strokeOval(nextCenter.getX() - ACTION_BTN_RADIUS, nextCenter.getY() - ACTION_BTN_RADIUS, ACTION_BTN_RADIUS * 2, ACTION_BTN_RADIUS * 2);

        // Symbole '+'
        double s = 5.0;
        ctx.strokeLine(nextCenter.getX() - s, nextCenter.getY(), nextCenter.getX() + s, nextCenter.getY());
        ctx.strokeLine(nextCenter.getX(), nextCenter.getY() - s, nextCenter.getX(), nextCenter.getY() + s);

        // 2. Bouton "Branche (⑂ / ⇉)" à droite
        Point2D branchCenter = getQuickAddBranchCenter();
        ctx.setFill(Color.rgb(2, 132, 199)); // Cyan #0284C7
        ctx.fillOval(branchCenter.getX() - ACTION_BTN_RADIUS, branchCenter.getY() - ACTION_BTN_RADIUS, ACTION_BTN_RADIUS * 2, ACTION_BTN_RADIUS * 2);
        ctx.setStroke(Color.WHITE);
        ctx.setLineWidth(1.6);
        ctx.strokeOval(branchCenter.getX() - ACTION_BTN_RADIUS, branchCenter.getY() - ACTION_BTN_RADIUS, ACTION_BTN_RADIUS * 2, ACTION_BTN_RADIUS * 2);

        ctx.setFill(Color.WHITE);
        ctx.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        ctx.setTextAlign(TextAlignment.CENTER);
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText(this instanceof Step ? "⑂" : "⇉", branchCenter.getX(), branchCenter.getY());

        // 3. Bouton "Supprimer (✕)" en haut à droite
        Point2D delCenter = getQuickDeleteCenter();
        double delR = 8.0;
        ctx.setFill(Color.rgb(217, 56, 30)); // Rouge #D9381E
        ctx.fillOval(delCenter.getX() - delR, delCenter.getY() - delR, delR * 2, delR * 2);
        ctx.setStroke(Color.WHITE);
        ctx.setLineWidth(1.4);
        ctx.strokeOval(delCenter.getX() - delR, delCenter.getY() - delR, delR * 2, delR * 2);
        double ds = 3.5;
        ctx.strokeLine(delCenter.getX() - ds, delCenter.getY() - ds, delCenter.getX() + ds, delCenter.getY() + ds);
        ctx.strokeLine(delCenter.getX() + ds, delCenter.getY() - ds, delCenter.getX() - ds, delCenter.getY() + ds);
    }

    @Override
    public boolean isPointOnTheComponent(Point2D point) {
        if (position == null) return false;
        var topPoint = new Point2D(this.getX(), this.getY());
        var bottomPoint = new Point2D(this.getX() + UNIT_WIDTH, this.getY() + UNIT_HEIGHT);
        return point.getX() >= topPoint.getX() && point.getX() <= bottomPoint.getX() &&
                point.getY() >= topPoint.getY() && point.getY() <= bottomPoint.getY();
    }

    public boolean isBottomHandlePressed(Point2D point) {
        if (position == null) return false;
        var topPoint = new Point2D(this.getX() + UNIT_WIDTH / 2 - radius / 2, this.getY() + UNIT_HEIGHT - radius);
        var bottomPoint = new Point2D(this.getX() + UNIT_WIDTH / 2 + radius / 2, this.getY() + UNIT_HEIGHT);
        return point.getX() >= topPoint.getX() && point.getX() <= bottomPoint.getX() &&
                point.getY() >= topPoint.getY() && point.getY() <= bottomPoint.getY();
    }

    public void updatePositionAndLinks(Point2D position) {
        this.position = position;
        this.linksFromParents.forEach(Link::update);
        this.linksToChildren.forEach(Link::update);
    }

    public void setHandle(Handle handle) {
        this.handle = handle;
    }

    public List<Link> getLinksFromParents() {
        return linksFromParents;
    }

    public List<Link> getLinksToChildren() {
        return linksToChildren;
    }

    protected void drawNumber(String number, Point2D position) {
        ctx.setFill(Color.DARKORANGE);
        ctx.setFont(Font.font("", FontWeight.BOLD, 18));
        ctx.setTextBaseline(VPos.CENTER);
        ctx.fillText(number, position.getX(), position.getY());
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}
