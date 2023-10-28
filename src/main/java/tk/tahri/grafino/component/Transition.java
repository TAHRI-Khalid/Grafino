package tk.tahri.grafino.component;

import javafx.scene.canvas.GraphicsContext;

public class Transition extends StepAndTransitionCommonBehavior {
    public Transition(GraphicsContext ctx) {
        super(ctx);
    }


    @Override
    protected void drawNormalComponent() {
       // ctx.fillRect( getX(),  getY() +  GAP,  UNIT_WIDTH,  UNIT_HEIGHT -  GAP * 2);
        ctx.strokeLine( getX() +  UNIT_WIDTH / 2,  getY(),  getX() +  UNIT_WIDTH / 2,  getY() +  UNIT_HEIGHT);
        ctx.strokeLine( getX() +  UNIT_WIDTH / 3,  getY() +  UNIT_HEIGHT / 2,  getX() +  UNIT_WIDTH * 2 / 3,  getY() +  UNIT_HEIGHT / 2);
    }

    @Override
    protected void drawSelectedComponent() {
        //ctx.fillRect( getX(),  getY() +  GAP,  UNIT_WIDTH,  UNIT_HEIGHT -  GAP * 2);
        drawNormalComponent();
    }



    /*
    public void drawLink() {
        ctx.setLineWidth( LINE_WIDTH);
        ctx.setStroke( LINE_COLOR);
        var begin = new Point2D(getX()+ UNIT_WIDTH/2,getY()+ UNIT_HEIGHT);

        for (var child :children) {
            var end =  new Point2D(child.getX()+ UNIT_WIDTH/2,child.getY());
            if (child.getParents().size()==1){
                ctx.strokeLine(begin.getX(), begin.getY(), end.getX(), begin.getY());
                ctx.strokeLine(end.getX(), begin.getY(), end.getX(), end.getY());
                if (this.getChildren().size() > 1){
                    ctx.strokeLine(begin.getX(), begin.getY()- GAP/2, end.getX(), begin.getY()- GAP/2);
                }
            }else {
                ctx.strokeLine(begin.getX(), begin.getY(), begin.getX(), end.getY());
                ctx.strokeLine(begin.getX(), end.getY(), end.getX(), end.getY());
            }
        }
    }

     */
}
