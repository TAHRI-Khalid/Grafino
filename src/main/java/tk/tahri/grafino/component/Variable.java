package tk.tahri.grafino.component;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class Variable {

    public enum Type {
        BOOL, INT
    }

    public enum Direction {
        INPUT("Entrée (%I)"),
        OUTPUT("Sortie (%Q)"),
        INTERNAL("Interne (%M)");

        private final String label;

        Direction(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final SimpleStringProperty name;
    private final SimpleObjectProperty<Type> type;
    private final SimpleObjectProperty<Direction> direction;
    private final SimpleStringProperty address;
    private final SimpleStringProperty comment;

    public Variable(String name, Type type, String address, String comment) {
        this(name, type, inferDirection(address), address, comment);
    }

    public Variable(String name, Type type, Direction direction, String address, String comment) {
        this.name = new SimpleStringProperty(name != null ? name : "");
        this.type = new SimpleObjectProperty<>(type != null ? type : Type.BOOL);
        this.direction = new SimpleObjectProperty<>(direction != null ? direction : Direction.INPUT);
        this.address = new SimpleStringProperty(address != null ? address : "");
        this.comment = new SimpleStringProperty(comment != null ? comment : "");
    }

    private static Direction inferDirection(String addr) {
        if (addr == null) return Direction.INPUT;
        String u = addr.toUpperCase();
        if (u.startsWith("%Q") || u.startsWith("Q") || u.startsWith("OUT")) return Direction.OUTPUT;
        if (u.startsWith("%M") || u.startsWith("M")) return Direction.INTERNAL;
        return Direction.INPUT;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public Type getType() {
        return type.get();
    }

    public void setType(Type type) {
        this.type.set(type);
    }

    public SimpleObjectProperty<Type> typeProperty() {
        return type;
    }

    public Direction getDirection() {
        return direction.get();
    }

    public void setDirection(Direction direction) {
        this.direction.set(direction);
    }

    public SimpleObjectProperty<Direction> directionProperty() {
        return direction;
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

    public SimpleStringProperty addressProperty() {
        return address;
    }

    public String getComment() {
        return comment.get();
    }

    public void setComment(String comment) {
        this.comment.set(comment);
    }

    public SimpleStringProperty commentProperty() {
        return comment;
    }
}
