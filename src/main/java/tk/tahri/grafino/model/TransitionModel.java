package tk.tahri.grafino.model;

public class TransitionModel {
    private String id;
    private int number;
    private String receptivity; // e.g. "1", "a . b", "m", "10s/X1"
    private double x;
    private double y;

    public TransitionModel() {
        this("", 1, "1", 0, 0);
    }

    public TransitionModel(String id, int number, String receptivity, double x, double y) {
        this.id = id;
        this.number = number;
        this.receptivity = (receptivity == null || receptivity.isBlank()) ? "1" : receptivity.trim();
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

    public String getReceptivity() {
        return receptivity;
    }

    public void setReceptivity(String receptivity) {
        this.receptivity = (receptivity == null || receptivity.isBlank()) ? "1" : receptivity.trim();
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
}
