package tk.tahri.grafino.component;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;


public class Variable {

    public enum Type {
        BOOL, INT
    }

    private final SimpleStringProperty name;
    private final SimpleObjectProperty<Type> type;
    private final SimpleStringProperty address;
    private final SimpleStringProperty comment;

    public Variable(String name, Type type, String address, String comment) {
        this.name = new SimpleStringProperty(name);
        this.type = new SimpleObjectProperty<>(type);
        this.address = new SimpleStringProperty(address);
        this.comment = new SimpleStringProperty(comment);
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public Type getType() {
        return type.get();
    }

    public void setType(Type type) {
        this.type.set(type);
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

    public String getComment() {
        return comment.get();
    }

    public void setComment(String comment) {
        this.comment.set(comment);
    }
}
