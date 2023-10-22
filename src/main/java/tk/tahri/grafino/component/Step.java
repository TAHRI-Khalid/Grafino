package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
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
        ctx.strokeLine(position.getX() + Style.UNIT_WIDTH / 2, position.getY(), position.getX() + Style.UNIT_WIDTH / 2, position.getY() + Style.UNIT_HEIGHT);
        ctx.fillRect(position.getX(), position.getY() + Style.GAP, Style.UNIT_WIDTH, Style.UNIT_HEIGHT - Style.GAP * 2);
        ctx.strokeRect(position.getX(), position.getY() + Style.GAP, Style.UNIT_WIDTH, Style.UNIT_HEIGHT - Style.GAP * 2);
    }

    @Override
    public void drawLink() {
        ctx.setLineWidth(Style.LINE_WIDTH);
        ctx.setStroke(Style.LINE_COLOR);

        for (var child :children) {
            var begin = new Point2D(getX()+Style.UNIT_WIDTH/2,getY()+Style.UNIT_HEIGHT);
            var end =  new Point2D(child.getX()+Style.UNIT_WIDTH/2,child.getY());
            if (child.getParents().size()==1){
                ctx.strokeLine(begin.getX(), begin.getY(), end.getX(), begin.getY());
                ctx.strokeLine(end.getX(), begin.getY(), end.getX(), end.getY());
            }else {
                ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), end.getY());
                ctx.strokeLine(begin.getX(), end.getY(), end.getX(), end.getY());
            }
        }
    }
}
