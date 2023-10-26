package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;

public class Step extends StepAndTransitionCommonBehavior {

    public Step(GraphicsContext gtx) {
        super(gtx);
    }

    protected void drawNormalStep() {
        ctx.strokeLine(getX() + UNIT_WIDTH / 2, getY(), getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT);
        ctx.fillRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
        ctx.strokeRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
    }

    protected void drawSelectedStep() {
        ctx.strokeLine(getX() + UNIT_WIDTH / 2, getY(), getX() + UNIT_WIDTH / 2, getY() + UNIT_HEIGHT);
        //ctx.fillRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
        ctx.strokeRect(getX(), getY() + GAP, UNIT_WIDTH, UNIT_HEIGHT - GAP * 2);
    }

    /*
    public void drawLink() {
        ctx.setLineWidth(LINE_WIDTH);
        ctx.setStroke(LINE_COLOR);
        var begin = new Point2D(getX()+UNIT_WIDTH/2,getY()+UNIT_HEIGHT);

        for (var child :children) {
            var end =  new Point2D(child.getX()+UNIT_WIDTH/2,child.getY());
            if (child.getParents().size()==1){
                ctx.strokeLine(begin.getX(), begin.getY(), end.getX(), begin.getY());
                ctx.strokeLine(end.getX(), begin.getY(), end.getX(), end.getY());

            }else {
                ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), end.getY());
                ctx.strokeLine(begin.getX(), end.getY(), end.getX(), end.getY());
                ctx.strokeLine(begin.getX(), end.getY()+GAP/2, end.getX(), end.getY()+GAP/2);
            }
        }
    }

     */
}
