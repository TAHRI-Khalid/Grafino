package tk.tahri.grafino.ui;

import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tk.tahri.grafino.component.Variable;

import java.util.List;
import java.util.stream.Collectors;

public class VariableTable extends VBox {
    private final TableView<Variable> table = new TableView<>();
    private final TableColumn<Variable, String> nameCol = new TableColumn<>("Nom");
    private final TableColumn<Variable, Variable.Direction> dirCol = new TableColumn<>("Direction");
    private final TableColumn<Variable, Variable.Type> typeCol = new TableColumn<>("Type");
    private final TableColumn<Variable, String> addressCol = new TableColumn<>("Adresse API / Broche");
    private final TableColumn<Variable, String> commentCol = new TableColumn<>("Commentaire / Rôle");

    public VariableTable() {
        table.setEditable(true);

        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setMinWidth(150);
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(event -> event.getRowValue().setName(event.getNewValue()));
        nameCol.setSortable(false);
        table.getColumns().add(nameCol);

        dirCol.setCellValueFactory(cellData -> cellData.getValue().directionProperty());
        dirCol.setMinWidth(140);
        dirCol.setCellFactory(ComboBoxTableCell.forTableColumn(
                FXCollections.observableArrayList(Variable.Direction.values())
        ));
        dirCol.setOnEditCommit(event -> event.getRowValue().setDirection(event.getNewValue()));
        dirCol.setSortable(false);
        table.getColumns().add(dirCol);

        typeCol.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        typeCol.setMinWidth(100);
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(
                FXCollections.observableArrayList(Variable.Type.values())
        ));
        typeCol.setOnEditCommit(event -> event.getRowValue().setType(event.getNewValue()));
        typeCol.setSortable(false);
        table.getColumns().add(typeCol);

        addressCol.setCellValueFactory(cellData -> cellData.getValue().addressProperty());
        addressCol.setMinWidth(160);
        addressCol.setCellFactory(TextFieldTableCell.forTableColumn());
        addressCol.setOnEditCommit(event -> event.getRowValue().setAddress(event.getNewValue()));
        addressCol.setSortable(false);
        table.getColumns().add(addressCol);

        commentCol.setCellValueFactory(cellData -> cellData.getValue().commentProperty());
        commentCol.setMinWidth(350);
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
        String defaultAddr = "%I0." + (position % 8);
        table.getItems().add(position, new Variable("VAR_" + (position + 1), Variable.Type.BOOL, Variable.Direction.INPUT, defaultAddr, ""));
        table.edit(position, nameCol);
        table.getSelectionModel().clearAndSelect(position);
        table.getFocusModel().focus(position, nameCol);
    }

    public void removeSelectedVariable() {
        var selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            table.getItems().remove(selected);
        }
    }

    public List<Variable> getAllVariables() {
        return table.getItems();
    }

    public List<Variable> getInputVariables() {
        List<Variable> inputs = table.getItems().stream()
                .filter(v -> v.getDirection() == Variable.Direction.INPUT || v.getDirection() == Variable.Direction.INTERNAL)
                .collect(Collectors.toList());
        return inputs.isEmpty() ? table.getItems() : inputs;
    }

    public List<Variable> getOutputVariables() {
        List<Variable> outputs = table.getItems().stream()
                .filter(v -> v.getDirection() == Variable.Direction.OUTPUT || v.getDirection() == Variable.Direction.INTERNAL)
                .collect(Collectors.toList());
        return outputs.isEmpty() ? table.getItems() : outputs;
    }

    // Alias for backward compatibility
    public List<Variable> getOutputVaribles() {
        return getOutputVariables();
    }

    public void clear() {
        table.getItems().clear();
    }

    public void setVariables(List<Variable> variables) {
        table.getItems().clear();
        if (variables != null) {
            table.getItems().addAll(variables);
        }
    }
}
