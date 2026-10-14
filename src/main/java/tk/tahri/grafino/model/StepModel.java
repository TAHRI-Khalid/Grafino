package tk.tahri.grafino.model;

import java.util.ArrayList;
import java.util.List;

public class StepModel {
    private String id;
    private int number;
    private boolean initial;
    private double x;
    private double y;
    private List<GrafcetAction> actions = new ArrayList<>();

    public StepModel() {
    }

    public StepModel(String id, int number, boolean initial, double x, double y) {
        this.id = id;
        this.number = number;
        this.initial = initial;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public boolean isInitial() {
        return initial;
    }

    public void setInitial(boolean initial) {
        this.initial = initial;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public List<GrafcetAction> getActions() {
        return actions;
    }

    public void setActions(List<GrafcetAction> actions) {
        this.actions = actions != null ? actions : new ArrayList<>();
    }
}
