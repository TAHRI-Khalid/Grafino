package tk.tahri.grafino.model;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class VariableModel {
    private final SimpleStringProperty name;
    private final SimpleObjectProperty<VariableType> type;
    private final SimpleObjectProperty<VariableDirection> direction;
    private final SimpleStringProperty address;
    private final SimpleStringProperty comment;

    public VariableModel() {
        this("", VariableType.BOOL, VariableDirection.INPUT, "%I0.0", "");
    }

    public VariableModel(String name, VariableType type, VariableDirection direction, String address, String comment) {
        this.name = new SimpleStringProperty(name != null ? name : "");
        this.type = new SimpleObjectProperty<>(type != null ? type : VariableType.BOOL);
        this.direction = new SimpleObjectProperty<>(direction != null ? direction : VariableDirection.INPUT);
        this.address = new SimpleStringProperty(address != null ? address : "");
        this.comment = new SimpleStringProperty(comment != null ? comment : "");
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

    public VariableType getType() {
        return type.get();
    }

    public void setType(VariableType type) {
        this.type.set(type);
    }

    public SimpleObjectProperty<VariableType> typeProperty() {
        return type;
    }

    public VariableDirection getDirection() {
        return direction.get();
    }

    public void setDirection(VariableDirection direction) {
        this.direction.set(direction);
    }

    public SimpleObjectProperty<VariableDirection> directionProperty() {
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
