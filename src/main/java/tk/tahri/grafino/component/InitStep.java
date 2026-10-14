package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

public class InitStep extends Step {
    public InitStep(GraphicsContext gtx, Point2D position) {
        super(gtx);
        this.position = position;
        this.setInitial(true);
    }

    public InitStep(GraphicsContext gtx, String id, int number, Point2D position) {
        super(gtx, id, number, true);
        this.position = position;
    }
}
