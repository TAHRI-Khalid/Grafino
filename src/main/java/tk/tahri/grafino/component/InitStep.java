package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

/**
 * Représente l'étape initiale créée au démarrage selon la norme CEI 60848.
 * Initialise explicitement le flag isInitial à true (double carré).
 */
public class InitStep extends Step {

    public InitStep(GraphicsContext gtx, Point2D position) {
        super(gtx);
        this.position = position;
        this.setInitial(true);
    }
}
