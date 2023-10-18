package tk.tahri.grafino.component;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import tk.tahri.grafino.Style;

public class Step extends Component {


    public Step(GraphicsContext gtx, double x, double y) {
        super(gtx, x, y);
    }

    @Override
    public void drawComponent() {
        ctx.setLineWidth(Style.LINE_WIDTH);
        ctx.setStroke(Style.LINE_COLOR);

        if (selected) {
            ctx.setFill(Style.SELECTION_COLOR);
        } else {
            ctx.setFill(Color.WHITE);
        }

        ctx.fillRect(position.getX(), position.getY(), Style.UNIT_WIDTH, Style.UNIT_HEIGHT);
        ctx.strokeRect(position.getX(), position.getY(), Style.UNIT_WIDTH, Style.UNIT_HEIGHT);
    }

    @Override
    public void drawLink() {
        if (this.parent != null) {
            ctx.setLineWidth(Style.LINE_WIDTH);
            ctx.setStroke(Style.LINE_COLOR);
            var begin = this.parent.position.add(Style.UNIT_WIDTH / 2, Style.UNIT_HEIGHT);
            var end = this.position.add(Style.UNIT_WIDTH / 2, 0);
            ctx.strokeLine(begin.getX(), begin.getY(), end.getX(), end.getY());
        }
    }
}
