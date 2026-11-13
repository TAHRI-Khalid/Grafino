package tk.tahri.grafino.ui;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import tk.tahri.grafino.component.Component;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.model.IecDataType;
import tk.tahri.grafino.model.IecVariableScope;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Table des variables d'automatismes (E/S et mémoires) conforme à la norme CEI 61131-3.
 * Comprend le filtrage par onglets (%I, %Q, %M), l'auto-incrémentation des adresses,
 * la recherche en direct et la validation des types IEC.
 */
public class VariableTable extends VBox {

    public enum FilterCategory {
        ALL("Toutes"),
        INPUTS("🟢 Entrées (%I)"),
        OUTPUTS("🔵 Sorties (%Q)"),
        INTERNAL("🟣 Internes (%M)");

        private final String label;
        FilterCategory(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final ObservableList<Variable> masterData = FXCollections.observableArrayList();
    private final FilteredList<Variable> filteredData = new FilteredList<>(masterData, p -> true);
    private final TableView<Variable> table = new TableView<>();

    private final TableColumn<Variable, IecVariableScope> scopeCol = new TableColumn<>("Portée");
    private final TableColumn<Variable, String> nameCol = new TableColumn<>("Identificateur");
    private final TableColumn<Variable, IecDataType> typeCol = new TableColumn<>("Type IEC");
    private final TableColumn<Variable, String> addressCol = new TableColumn<>("Adresse API");
    private final TableColumn<Variable, String> initValCol = new TableColumn<>("Val. Initiale");
    private final TableColumn<Variable, String> commentCol = new TableColumn<>("Commentaire / Rôle");

    private FilterCategory currentCategory = FilterCategory.ALL;
    private final TextField searchField = new TextField();
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final ToggleButton allBtn = new ToggleButton("Toutes (0)");
    private final ToggleButton inputsBtn = new ToggleButton("Entrées %I (0)");
    private final ToggleButton outputsBtn = new ToggleButton("Sorties %Q (0)");
    private final ToggleButton internalBtn = new ToggleButton("Mémoires %M (0)");

    private Runnable onVariablesChanged;
    private java.util.function.Consumer<String> onValidationError;

    public VariableTable() {
        this.setSpacing(6);
        this.setPadding(new Insets(8));
        this.setStyle("-fx-background-color: " + AppTheme.PANEL_BG + "; -fx-border-color: " + AppTheme.BORDER_COLOR + "; -fx-border-width: 1 0 0 0;");

        // 1. Barre d'outils supérieure (Filtres, Recherche, Actions rapides)
        var toolBar = createHeaderToolbar();
        this.getChildren().add(toolBar);

        // 2. Configuration de la TableView
        setupTableView();
        VBox.setVgrow(table, Priority.ALWAYS);
        this.getChildren().add(table);

        // 3. Mise à jour des compteurs d'onglets lors des modifications
        masterData.addListener((ListChangeListener<Variable>) c -> {
            updateTabBadges();
            notifyChanged();
        });
        updateTabBadges();
    }

    private HBox createHeaderToolbar() {
        var bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 4, 6, 4));

        // Groupement des boutons de filtres (Onglets visuels Arduino)
        allBtn.setToggleGroup(filterGroup);
        inputsBtn.setToggleGroup(filterGroup);
        outputsBtn.setToggleGroup(filterGroup);
        internalBtn.setToggleGroup(filterGroup);
        allBtn.setSelected(true);

        updateAllTabsStyle();
        filterGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> updateAllTabsStyle());

        allBtn.setOnAction(e -> { currentCategory = FilterCategory.ALL; applyFilter(); });
        inputsBtn.setOnAction(e -> { currentCategory = FilterCategory.INPUTS; applyFilter(); });
        outputsBtn.setOnAction(e -> { currentCategory = FilterCategory.OUTPUTS; applyFilter(); });
        internalBtn.setOnAction(e -> { currentCategory = FilterCategory.INTERNAL; applyFilter(); });

        var tabsBox = new HBox(4, allBtn, inputsBtn, outputsBtn, internalBtn);
        tabsBox.setAlignment(Pos.CENTER_LEFT);

        // Champ de recherche instantané
        searchField.setPromptText("🔍 Rechercher une variable...");
        searchField.setPrefWidth(220);
        searchField.setStyle(AppTheme.TEXT_FIELD_STYLE + "; -fx-background-radius: 14px; -fx-border-radius: 14px; -fx-padding: 4 10 4 10;");
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter());

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Boutons d'ajout rapide avec couleurs sémantiques distinctes
        var addInputBtn = new Button("＋ Entrée (%I)");
        String inputStyle = "-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        String inputHover = "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        addInputBtn.setStyle(inputStyle);
        addInputBtn.setOnMouseEntered(e -> addInputBtn.setStyle(inputHover));
        addInputBtn.setOnMouseExited(e -> addInputBtn.setStyle(inputStyle));
        addInputBtn.setOnAction(e -> addQuickInput());

        var addOutputBtn = new Button("＋ Sortie (%Q)");
        String outputStyle = "-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        String outputHover = "-fx-background-color: #0369A1; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        addOutputBtn.setStyle(outputStyle);
        addOutputBtn.setOnMouseEntered(e -> addOutputBtn.setStyle(outputHover));
        addOutputBtn.setOnMouseExited(e -> addOutputBtn.setStyle(outputStyle));
        addOutputBtn.setOnAction(e -> addQuickOutput());

        var addInternalBtn = new Button("＋ Mémoire (%M)");
        String internalStyle = "-fx-background-color: #7C3AED; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        String internalHover = "-fx-background-color: #6D28D9; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        addInternalBtn.setStyle(internalStyle);
        addInternalBtn.setOnMouseEntered(e -> addInternalBtn.setStyle(internalHover));
        addInternalBtn.setOnMouseExited(e -> addInternalBtn.setStyle(internalStyle));
        addInternalBtn.setOnAction(e -> addQuickInternal());

        var addCounterBtn = new Button("＋ Compteur (INT)");
        String counterStyle = "-fx-background-color: #E67E22; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        String counterHover = "-fx-background-color: #D35400; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 6; -fx-cursor: hand;";
        addCounterBtn.setStyle(counterStyle);
        addCounterBtn.setOnMouseEntered(e -> addCounterBtn.setStyle(counterHover));
        addCounterBtn.setOnMouseExited(e -> addCounterBtn.setStyle(counterStyle));
        addCounterBtn.setOnAction(e -> addQuickCounter());

        var deleteBtn = new Button("🗑");
        deleteBtn.setTooltip(new Tooltip("Supprimer la variable sélectionnée"));
        deleteBtn.setStyle("-fx-background-color: " + AppTheme.RED_LIGHT_BG + "; -fx-border-color: " + AppTheme.RED_BORDER + "; -fx-text-fill: " + AppTheme.RED_DANGER + "; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8 4 8; -fx-background-radius: 6; -fx-cursor: hand;");
        deleteBtn.setOnAction(e -> removeSelectedVariable());

        bar.getChildren().addAll(tabsBox, searchField, spacer, addInputBtn, addOutputBtn, addInternalBtn, addCounterBtn, deleteBtn);
        return bar;
    }

    private void applyTabStyle(ToggleButton btn) {
        boolean selected = btn.isSelected();
        if (selected) {
            String primaryColor = AppTheme.PRIMARY_TEAL;
            String lightBg = AppTheme.TEAL_LIGHT_BG;
            String textDark = AppTheme.PRIMARY_TEAL_DARK;

            if (btn == inputsBtn) {
                primaryColor = "#10B981";
                lightBg = "#ECFDF5";
                textDark = "#065F46";
            } else if (btn == outputsBtn) {
                primaryColor = "#0284C7";
                lightBg = "#F0F9FF";
                textDark = "#0369A1";
            } else if (btn == internalBtn) {
                primaryColor = "#7C3AED";
                lightBg = "#F5F3FF";
                textDark = "#5B21B6";
            }

            btn.setStyle("-fx-background-color: " + lightBg + ";" +
                    "-fx-border-color: " + primaryColor + ";" +
                    "-fx-border-width: 0 0 2px 0;" +
                    "-fx-text-fill: " + textDark + ";" +
                    "-fx-font-weight: bold;" +
                    "-fx-padding: 5 12 5 12;" +
                    "-fx-font-size: 11px;" +
                    "-fx-background-radius: 4 4 0 0;" +
                    "-fx-cursor: hand;");
        } else {
            btn.setStyle("-fx-background-color: transparent;" +
                    "-fx-border-color: transparent;" +
                    "-fx-text-fill: " + AppTheme.TEXT_MUTED + ";" +
                    "-fx-font-weight: bold;" +
                    "-fx-padding: 5 12 5 12;" +
                    "-fx-font-size: 11px;" +
                    "-fx-cursor: hand;");
        }
    }

    private void updateAllTabsStyle() {
        applyTabStyle(allBtn);
        applyTabStyle(inputsBtn);
        applyTabStyle(outputsBtn);
        applyTabStyle(internalBtn);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private void setupTableView() {
        table.setEditable(true);
        SortedList<Variable> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 1. Colonne Portée (Scope) avec ComboBox
        scopeCol.setCellValueFactory(cellData -> cellData.getValue().scopeProperty());
        scopeCol.setMinWidth(130);
        scopeCol.setPrefWidth(140);
        scopeCol.setCellFactory(col -> new ComboBoxTableCell<>(new StringConverter<>() {
            @Override
            public String toString(IecVariableScope object) {
                return (object != null) ? object.name() + " (" + object.iecPrefix() + ")" : "";
            }
            @Override
            public IecVariableScope fromString(String string) {
                return null;
            }
        }, IecVariableScope.VAR_INPUT, IecVariableScope.VAR_OUTPUT, IecVariableScope.VAR));
        scopeCol.setOnEditCommit(event -> {
            Variable row = event.getRowValue();
            IecVariableScope newScope = event.getNewValue();
            if (newScope != null) {
                row.setScope(newScope);
                adjustAddressForScope(row, newScope);
                table.refresh();
                notifyChanged();
            }
        });

        // 2. Colonne Nom / Identificateur
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setMinWidth(140);
        nameCol.setPrefWidth(160);
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(event -> {
            String val = event.getNewValue();
            if (val != null && !val.isBlank()) {
                event.getRowValue().setName(val.trim());
                notifyChanged();
            }
        });

        // 3. Colonne Type IEC avec ComboBox
        typeCol.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        typeCol.setMinWidth(100);
        typeCol.setPrefWidth(110);
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(
                IecDataType.BOOL, IecDataType.INT, IecDataType.DINT,
                IecDataType.REAL, IecDataType.TIME, IecDataType.STRING
        ));
        typeCol.setOnEditCommit(event -> {
            event.getRowValue().setType(event.getNewValue());
            notifyChanged();
        });

        // 4. Colonne Adresse Matérielle avec validation en temps réel
        addressCol.setCellValueFactory(cellData -> cellData.getValue().addressProperty());
        addressCol.setMinWidth(120);
        addressCol.setPrefWidth(130);
        addressCol.setCellFactory(col -> new TableCell<Variable, String>() {
            private final TextField textField = new TextField();
            private final Tooltip invalidTooltip = new Tooltip("Format CEI 61131-3 attendu : %I0.0, %Q0.1, %M0.0, %IW2...");

            {
                textField.setStyle("-fx-padding: 2 6 2 6; -fx-font-size: 13px;");
                textField.setOnAction(e -> applyAndCommit());
                textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused && isEditing()) {
                        applyAndCommit();
                    }
                });

                textField.textProperty().addListener((obs, oldText, newText) -> {
                    if (Variable.isValidAddress(newText)) {
                        textField.setStyle("-fx-border-color: #10B981; -fx-border-width: 1.5; -fx-background-color: #ECFDF5;");
                        Tooltip.uninstall(textField, invalidTooltip);
                    } else {
                        textField.setStyle("-fx-border-color: #EF4444; -fx-border-width: 2; -fx-background-color: #FEF2F2;");
                        Tooltip.install(textField, invalidTooltip);
                    }
                });
            }

            private void applyAndCommit() {
                String input = textField.getText();
                String normalized = Variable.normalizeAddress(input);
                if (Variable.isValidAddress(normalized)) {
                    commitEdit(normalized);
                } else {
                    cancelEdit();
                    notifyValidationError("❌ Adresse API invalide : '" + input + "'. Format attendu : %I0.0, %Q0.1, %M0.0, %IW2 (CEI 61131-3).");
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                textField.setText(getItem() != null ? getItem() : "");
                setGraphic(textField);
                setText(null);
                textField.requestFocus();
                textField.selectAll();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (isEditing()) {
                        textField.setText(item);
                        setGraphic(textField);
                        setText(null);
                    } else {
                        setText(item);
                        setGraphic(null);
                        if (!Variable.isValidAddress(item)) {
                            setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            }
        });

        addressCol.setOnEditCommit(event -> {
            Variable row = event.getRowValue();
            String newAddress = event.getNewValue();
            if (newAddress != null && Variable.isValidAddress(newAddress)) {
                row.setAddress(newAddress);
                table.refresh();
                notifyChanged();
            }
        });

        // 5. Colonne Valeur Initiale
        initValCol.setCellValueFactory(cellData -> cellData.getValue().initialValueProperty());
        initValCol.setMinWidth(100);
        initValCol.setPrefWidth(110);
        initValCol.setCellFactory(TextFieldTableCell.forTableColumn());
        initValCol.setOnEditCommit(event -> {
            event.getRowValue().setInitialValue(event.getNewValue());
            notifyChanged();
        });

        // 6. Colonne Commentaire / Description
        commentCol.setCellValueFactory(cellData -> cellData.getValue().commentProperty());
        commentCol.setMinWidth(180);
        commentCol.setCellFactory(TextFieldTableCell.forTableColumn());
        commentCol.setOnEditCommit(event -> {
            event.getRowValue().setComment(event.getNewValue());
            notifyChanged();
        });

        table.getColumns().addAll(scopeCol, nameCol, typeCol, addressCol, initValCol, commentCol);
    }

    // ---------------- Actions Rapides & Auto-Incrémentation ----------------

    public void addQuickInput() {
        int nextNum = getNextAddressOffset("%I");
        String varName = "e_" + nextNum;
        String address = "%I0." + nextNum;
        Variable v = new Variable(varName, IecDataType.BOOL, IecVariableScope.VAR_INPUT, address, "FALSE", "Entrée capteur");
        addAndFocus(v);
    }

    public void addQuickOutput() {
        int nextNum = getNextAddressOffset("%Q");
        String varName = "s_" + nextNum;
        String address = "%Q0." + nextNum;
        Variable v = new Variable(varName, IecDataType.BOOL, IecVariableScope.VAR_OUTPUT, address, "FALSE", "Sortie actionneur");
        addAndFocus(v);
    }

    public void addQuickInternal() {
        int nextNum = getNextAddressOffset("%M");
        String varName = "m_" + nextNum;
        String address = "%M0." + nextNum;
        Variable v = new Variable(varName, IecDataType.BOOL, IecVariableScope.VAR, address, "FALSE", "Mémoire interne");
        addAndFocus(v);
    }

    public void addQuickCounter() {
        int nextNum = getNextCounterNumber();
        String varName = "C" + nextNum;
        int wordOffset = (nextNum - 1) * 2;
        String address = "%MW" + wordOffset;
        Variable v = new Variable(varName, IecDataType.INT, IecVariableScope.VAR, address, "0", "Compteur cycle");
        addAndFocus(v);
    }

    private int getNextCounterNumber() {
        int max = 0;
        Pattern pattern = Pattern.compile("(?i)^C(\\d+)$");
        for (Variable v : masterData) {
            Matcher m = pattern.matcher(v.getName());
            if (m.find()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {}
            }
        }
        return max + 1;
    }

    public void addVariable() {
        addQuickInput();
    }

    private void addAndFocus(Variable v) {
        masterData.add(v);
        table.getSelectionModel().select(v);
        table.scrollTo(v);
        notifyChanged();
    }

    public void removeSelectedVariable() {
        Variable selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            masterData.remove(selected);
            notifyChanged();
        }
    }

    private int getNextAddressOffset(String prefix) {
        int max = -1;
        Pattern pattern = Pattern.compile(".*\\.(\\d+)$");
        for (Variable v : masterData) {
            if (v.getAddress().toUpperCase().startsWith(prefix)) {
                Matcher matcher = pattern.matcher(v.getAddress());
                if (matcher.find()) {
                    try {
                        int num = Integer.parseInt(matcher.group(1));
                        max = Math.max(max, num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max + 1;
    }

    public void adjustAddressForScope(Variable targetVar, IecVariableScope newScope) {
        String prefix = newScope.iecPrefix();
        if (prefix == null || prefix.isBlank()) return;

        String currentAddr = targetVar.getAddress();
        String candidate;
        if (currentAddr != null && (currentAddr.startsWith("%I") || currentAddr.startsWith("%Q") ||
                currentAddr.startsWith("%M"))) {
            candidate = prefix + currentAddr.substring(2);
        } else {
            candidate = prefix + "0.0";
        }

        // Vérifier si cette adresse candidate est déjà utilisée par une AUTRE variable de la table
        boolean isDuplicate = masterData.stream()
                .anyMatch(other -> other != targetVar && other.getAddress().equalsIgnoreCase(candidate));

        if (isDuplicate) {
            int nextOffset = getNextAddressOffset(prefix);
            targetVar.setAddress(prefix + "0." + nextOffset);
        } else {
            targetVar.setAddress(candidate);
        }
    }

    // ---------------- Filtrage Dynamique ----------------

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();

        filteredData.setPredicate(v -> {
            // Filtre par catégorie (Onglet actif)
            boolean matchCat = switch (currentCategory) {
                case ALL -> true;
                case INPUTS -> v.isInput();
                case OUTPUTS -> v.isOutput();
                case INTERNAL -> v.isInternal();
            };
            if (!matchCat) return false;

            // Filtre par mot-clé de recherche
            if (query.isEmpty()) return true;
            return v.getName().toLowerCase().contains(query)
                    || v.getAddress().toLowerCase().contains(query)
                    || v.getComment().toLowerCase().contains(query)
                    || v.getType().name().toLowerCase().contains(query);
        });
    }

    private void updateTabBadges() {
        long inCount = masterData.stream().filter(Variable::isInput).count();
        long outCount = masterData.stream().filter(Variable::isOutput).count();
        long intCount = masterData.stream().filter(Variable::isInternal).count();

        allBtn.setText("Toutes (" + masterData.size() + ")");
        inputsBtn.setText("Entrées %I (" + inCount + ")");
        outputsBtn.setText("Sorties %Q (" + outCount + ")");
        internalBtn.setText("Mémoires %M (" + intCount + ")");
        updateAllTabsStyle();
    }

    // ---------------- Accesseurs & Écouteurs ----------------

    public ObservableList<Variable> getVariables() {
        return masterData;
    }

    public List<Variable> getAllVariables() {
        return List.copyOf(masterData);
    }

    public List<Variable> getInputVariables() {
        return masterData.stream().filter(Variable::isInput).toList();
    }

    public List<Variable> getOutputVariables() {
        return masterData.stream().filter(Variable::isOutput).toList();
    }

    public List<Variable> getCounterVariables() {
        return masterData.stream()
                .filter(v -> v.getType() == IecDataType.INT || v.getType() == IecDataType.DINT
                        || v.getType() == IecDataType.BYTE || v.getType() == IecDataType.WORD
                        || v.getName().matches("(?i)^C\\d*$"))
                .toList();
    }

    // Alias rétrocompatible avec l'ancienne signature
    public List<Variable> getOutputVaribles() {
        return getOutputVariables();
    }

    public void setVariables(List<Variable> variables) {
        masterData.clear();
        if (variables != null) {
            masterData.addAll(variables);
        }
        updateTabBadges();
        notifyChanged();
    }

    public void clear() {
        masterData.clear();
        updateTabBadges();
        notifyChanged();
    }

    public void setOnVariablesChanged(Runnable listener) {
        this.onVariablesChanged = listener;
    }

    public void setOnValidationError(java.util.function.Consumer<String> onValidationError) {
        this.onValidationError = onValidationError;
    }

    private void notifyValidationError(String errorMsg) {
        if (onValidationError != null) {
            onValidationError.accept(errorMsg);
        }
    }

    private void notifyChanged() {
        if (onVariablesChanged != null) {
            onVariablesChanged.run();
        }
    }
}
