package tk.tahri.grafino.ui;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tk.tahri.grafino.component.Variable;

import java.util.List;

public class VariableTable extends VBox {
    private final TableView<Variable> table = new TableView<>();
    private final TableColumn<Variable, String> nameCol = new TableColumn<>("Name");
    private final TableColumn<Variable, Variable.Type> typeCol = new TableColumn<>("Type");
    private final TableColumn<Variable, String> addressCol = new TableColumn<>("Address");
    private final TableColumn<Variable, String> commentCol = new TableColumn<>("Comment");

    public VariableTable() {
        table.setEditable(true);

        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setMinWidth(150);
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(event -> event.getRowValue().setName(event.getNewValue()));
        nameCol.setSortable(false);
        table.getColumns().add(nameCol);

        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setMinWidth(120);
        typeCol.setSortable(false);
        table.getColumns().add(typeCol);

        addressCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressCol.setMinWidth(150);
        addressCol.setCellFactory(TextFieldTableCell.forTableColumn());
        addressCol.setOnEditCommit(event -> event.getRowValue().setAddress(event.getNewValue()));
        addressCol.setSortable(false);
        table.getColumns().add(addressCol);

        commentCol.setCellValueFactory(new PropertyValueFactory<>("comment"));
        commentCol.setMinWidth(400);
        commentCol.setCellFactory(TextFieldTableCell.forTableColumn());
        commentCol.setOnEditCommit(event -> event.getRowValue().setComment(event.getNewValue()));
        commentCol.setSortable(false);
        table.getColumns().add(commentCol);

        VBox.setVgrow(table, Priority.ALWAYS);
        this.getChildren().add(table);
    }

    public void addVariable() {
        table.requestFocus();
        var position = table.getItems().size();
        table.getItems().add(position, new Variable("", Variable.Type.BOOL, "%I0.0", ""));
        table.edit(position, nameCol);
        table.getSelectionModel().clearAndSelect(position);
        table.getFocusModel().focus(position,nameCol);
    }

    public void removeSelectedVariable() {
        var selected = table.getSelectionModel().getSelectedItem();
        table.getItems().remove(selected);
    }
    public List<Variable> getOutputVaribles(){
        return table.getItems();
    }
}
