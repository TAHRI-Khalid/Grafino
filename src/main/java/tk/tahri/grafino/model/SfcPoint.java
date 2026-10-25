package tk.tahri.grafino.model;

/**
 * Coordonnée 2D pure et immuable dans le repère du diagramme SFC.
 * Découplée de tout framework UI (JavaFX) pour assurer la pureté du modèle métier.
 */
public record SfcPoint(double x, double y) {

    public static final SfcPoint ZERO = new SfcPoint(0.0, 0.0);

    public SfcPoint translated(double dx, double dy) {
        return new SfcPoint(x + dx, y + dy);
    }

    public double distanceTo(SfcPoint other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
