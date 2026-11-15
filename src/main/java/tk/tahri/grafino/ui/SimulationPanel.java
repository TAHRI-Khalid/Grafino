package tk.tahri.grafino.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import tk.tahri.grafino.component.Variable;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.model.SfcValidationMessage;
import tk.tahri.grafino.simulation.GrafcetSimulator;
import tk.tahri.grafino.simulation.SimulationState;
import tk.tahri.grafino.simulation.SimulationStepResult;
import tk.tahri.grafino.simulation.TimingRecorder;

import java.util.*;
import java.util.function.Supplier;

/**
 * Pupitre virtuel de simulation et banc d'essai interactif d'E/S (CEI 60848 & CEI 61131-3).
 *
 * Intègre :
 * - Section de contrôle : Démarrage / Pause, Pas-à-pas, Réinitialisation (Reset), réglage de fréquence.
 * - Section Entrées Capteurs (%I) : Boutons-poussoirs momentanés, interrupteurs à bascule et témoins LED d'état.
 * - Section Sorties Préactionneurs (%Q) : Lampes témoins industrielles avec halo lumineux (allumage en temps réel).
 * - Section Compteurs : Visualisation instantanée de l'évolution des compteurs industriels (C1, etc.).
 */
public class SimulationPanel extends VBox {

    private final GrafcetSimulator simulator;
    private final Grafcet grafcet;
    private final List<Variable> inputVariables;
    private final List<Variable> outputVariables;
    private final List<Variable> counterVariables;
    private final TimingRecorder timingRecorder;
    private final Runnable onTimingTick;
    private final Runnable onClose;

    private final Timeline simulationTimer;
    private boolean isRunning = true;
    private boolean stopped = false;

    // Éléments d'interface
    private final Label statusBadge = new Label("● EN COURS");
    private final Button playPauseBtn = new Button("⏸ Pause");
    private final Label cycleLabel = new Label("Cycle : 0");

    // Registres de composants visuels pour mise à jour rapide
    private static final DropShadow ACTIVE_LAMP_GLOW = new DropShadow(10, Color.rgb(2, 132, 199, 0.85));

    private static final String COMPACT_ROW_STYLE = "-fx-background-color: #FFFFFF; -fx-border-color: " + AppTheme.BORDER_COLOR +
            "; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 6 7 6 7;";
    private static final String COMPACT_ROW_HOVER_STYLE = "-fx-background-color: #F8FAFC; -fx-border-color: #6EE7B7;" +
            " -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 6 7 6 7;";
    private static final String COMPACT_ROW_PRESS_STYLE = "-fx-background-color: #ECFDF5; -fx-border-color: #10B981;" +
            " -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 6 7 6 7;";
    private static final String OUTPUT_TILE_IDLE_STYLE = "-fx-background-color: #FFFFFF; -fx-border-color: " + AppTheme.BORDER_COLOR +
            "; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 6 7 6 7;";
    private static final String OUTPUT_TILE_ACTIVE_STYLE = "-fx-background-color: #F0F9FF; -fx-border-color: #38BDF8;" +
            " -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 6 7 6 7;";

    private static final String PUSH_IDLE_STYLE = "-fx-background-color: #E2E8F0; -fx-text-fill: #1E293B; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 3 6 3 6; -fx-background-radius: 4px;";
    private static final String PUSH_HOVER_STYLE = "-fx-background-color: #CBD5E1; -fx-text-fill: #0F172A; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 3 6 3 6; -fx-background-radius: 4px;";
    private static final String PUSH_ACTIVE_STYLE = "-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 3 6 3 6; -fx-background-radius: 4px;";

    private static final String TOGGLE_IDLE_STYLE = "-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 11px; " +
            "-fx-cursor: hand; -fx-padding: 3 7 3 7; -fx-background-radius: 4px; -fx-border-color: #CBD5E1; -fx-border-radius: 4px;";
    private static final String TOGGLE_IDLE_HOVER_STYLE = "-fx-background-color: #E2E8F0; -fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 11px; " +
            "-fx-cursor: hand; -fx-padding: 3 7 3 7; -fx-background-radius: 4px; -fx-border-color: #94A3B8; -fx-border-radius: 4px;";
    private static final String TOGGLE_IDLE_PRESS_STYLE = "-fx-background-color: #CBD5E1; -fx-text-fill: #0F172A; -fx-font-weight: bold; -fx-font-size: 11px; " +
            "-fx-cursor: hand; -fx-padding: 3 7 3 7; -fx-background-radius: 4px; -fx-border-color: #64748B; -fx-border-radius: 4px;";
    private static final String TOGGLE_ACTIVE_STYLE = "-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 3 7 3 7; -fx-background-radius: 4px; -fx-border-color: #10B981; -fx-border-radius: 4px;";
    private static final String TOGGLE_ACTIVE_HOVER_STYLE = "-fx-background-color: #059669; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 3 7 3 7; -fx-background-radius: 4px; -fx-border-color: #059669; -fx-border-radius: 4px;";
    private static final String TOGGLE_ACTIVE_PRESS_STYLE = "-fx-background-color: #047857; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 3 7 3 7; -fx-background-radius: 4px; -fx-border-color: #047857; -fx-border-radius: 4px;";

    private static final String PRIMARY_PRESS_STYLE = "-fx-background-color: #006669; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 6 12 6 12;";
    private static final String PAUSE_IDLE_STYLE = "-fx-background-color: #E67E22; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 6 12 6 12; -fx-background-radius: 4px;";
    private static final String PAUSE_HOVER_STYLE = "-fx-background-color: #D35400; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 6 12 6 12; -fx-background-radius: 4px;";
    private static final String PAUSE_PRESS_STYLE = "-fx-background-color: #B94A00; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 6 12 6 12; -fx-background-radius: 4px;";
    private static final String SECONDARY_PRESS_STYLE = "-fx-background-color: #E2E8F0; -fx-text-fill: #008184; -fx-border-color: #008184; " +
            "-fx-border-radius: 4px; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 5 11 5 11;";
    private static final String STOP_IDLE_STYLE = "-fx-background-color: #D9381E; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 6 10 6 10;";
    private static final String STOP_HOVER_STYLE = "-fx-background-color: #B91C1C; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 6 10 6 10;";
    private static final String STOP_PRESS_STYLE = "-fx-background-color: #991B1B; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
            "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 6 10 6 10;";

    private final Map<String, ToggleButton> toggleButtons = new HashMap<>();
    private final Map<String, Label> inputLedIndicators = new HashMap<>();
    private final Map<String, HBox> outputCards = new HashMap<>();
    private final Map<String, Circle> outputLamps = new HashMap<>();
    private final Map<String, Label> outputStateLabels = new HashMap<>();
    private final Map<String, Label> counterValueLabels = new HashMap<>();
    private final VBox runtimeAlertsContainer = new VBox(6);
    private int lastRuntimeAlertsHash = 0;

    // Cache d'état visuel pour éviter les invalidations CSS et redessins inutiles à chaque tick
    private final Map<String, Boolean> lastInputStates = new HashMap<>();
    private final Map<String, Boolean> lastOutputStates = new HashMap<>();
    private final Map<String, Integer> lastCounterValues = new HashMap<>();
    private long lastCycleCount = -1;
    private Set<String> lastActiveSteps = null;
    private Set<String> lastActiveOutputs = null;
    private Set<String> lastValidatedTrans = null;
    private Set<String> lastTrueRecTrans = null;
    private Set<String> lastFireableTrans = null;

    // Listes de noms précalculées pour le TimingRecorder
    private final List<String> cachedStepNames;
    private final List<String> cachedInNames;
    private final List<String> cachedOutNames;
    private final List<String> cachedCountNames;

    public SimulationPanel(GrafcetSimulator simulator,
                           Grafcet grafcet,
                           List<Variable> inputVariables,
                           List<Variable> outputVariables,
                           List<Variable> counterVariables,
                           Runnable onClose) {
        this(simulator, grafcet, inputVariables, outputVariables, counterVariables, null, null, onClose);
    }

    public SimulationPanel(GrafcetSimulator simulator,
                           Grafcet grafcet,
                           List<Variable> inputVariables,
                           List<Variable> outputVariables,
                           List<Variable> counterVariables,
                           TimingRecorder timingRecorder,
                           Runnable onTimingTick,
                           Runnable onClose) {
        this.simulator = simulator;
        this.grafcet = grafcet;
        this.inputVariables = (inputVariables != null) ? inputVariables : List.of();
        this.outputVariables = (outputVariables != null) ? outputVariables : List.of();
        this.counterVariables = (counterVariables != null) ? counterVariables : List.of();
        this.timingRecorder = timingRecorder;
        this.onTimingTick = onTimingTick;
        this.onClose = onClose;

        List<String> sNames = new ArrayList<>();
        for (var comp : grafcet.getComponents()) {
            if (comp instanceof Step step) {
                sNames.add("X" + step.getNumber());
            }
        }
        this.cachedStepNames = List.copyOf(sNames);
        this.cachedInNames = this.inputVariables.stream().map(Variable::getName).toList();
        this.cachedOutNames = this.outputVariables.stream().map(Variable::getName).toList();
        this.cachedCountNames = this.counterVariables.stream().map(Variable::getName).toList();

        this.setPrefWidth(415);
        this.setMinWidth(370);
        this.setSpacing(10);
        this.setPadding(new Insets(12));
        this.setStyle(AppTheme.SIDEBAR_STYLE);

        // 1. En-tête fixe avec titre, badge d'état et bouton quitter
        setupHeader();

        // 2. Conteneur principal défilant pour tous les écrans
        VBox scrollContent = new VBox(10);
        scrollContent.setPadding(new Insets(2, 6, 10, 2));

        // 3. Section Contrôles de simulation
        scrollContent.getChildren().add(createControlSection());

        // 4. Section Entrées Capteurs (%I) avec boutons poussoirs et switches
        scrollContent.getChildren().add(createInputsSection());

        // 5. Section Sorties Préactionneurs (%Q) avec lampes témoins industrielles
        scrollContent.getChildren().add(createOutputsSection());

        // 6. Section Compteurs & Internes (si existants)
        if (!this.counterVariables.isEmpty() || !simulator.getState().getCounterVariables().isEmpty()) {
            scrollContent.getChildren().add(createCountersSection());
        }

        // 7. Section Diagnostics & Comportement Temps Réel (conflits OU, Set/Reset, synchronisations ET)
        scrollContent.getChildren().add(new CollapsibleSection("🩺", "Diagnostics d'Exécution", null, runtimeAlertsContainer, true));

        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().add(scrollPane);

        // 7. Horloge synchrone de simulation
        this.simulationTimer = new Timeline(new KeyFrame(Duration.millis(100), e -> runSimulationTick()));
        this.simulationTimer.setCycleCount(Timeline.INDEFINITE);
        this.simulationTimer.play();

        // Rafraîchissement visuel initial
        refreshVisuals();
    }

    private void setupHeader() {
        var header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        var titleBox = new VBox(2);
        var subTitle = new Label("PUPITRE VIRTUEL & BANC D'ESSAI");
        subTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        var titleLbl = new Label("🎮 Simulation Synchrone");
        titleLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        titleBox.getChildren().addAll(subTitle, titleLbl);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBadge.setStyle(AppTheme.pillBadgeStyle("#D1FAE5", "#065F46") + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        var closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + AppTheme.TEXT_MUTED +
                "; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 2 6 2 6;");
        closeBtn.setTooltip(new Tooltip("Arrêter et fermer le pupitre de simulation"));
        closeBtn.setOnAction(e -> stopAndClose());

        header.getChildren().addAll(titleBox, spacer, statusBadge, closeBtn);
        this.getChildren().add(header);
    }

    private CollapsibleSection createControlSection() {
        VBox box = new VBox(10);

        GridPane btnGrid = createColumnGrid(3);

        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        playPauseBtn.setOnAction(e -> togglePlayPause());
        applyInteractiveStyles(playPauseBtn,
                () -> isRunning ? AppTheme.PRIMARY_BUTTON_STYLE : PAUSE_IDLE_STYLE,
                () -> isRunning ? AppTheme.PRIMARY_BUTTON_HOVER_STYLE : PAUSE_HOVER_STYLE,
                () -> isRunning ? PRIMARY_PRESS_STYLE : PAUSE_PRESS_STYLE);

        Button stepBtn = new Button("⏭ Pas-à-pas");
        stepBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setOnAction(e -> executeStepAndRecord());
        applyInteractiveStyles(stepBtn,
                () -> AppTheme.SECONDARY_BUTTON_STYLE,
                () -> AppTheme.SECONDARY_BUTTON_HOVER_STYLE,
                () -> SECONDARY_PRESS_STYLE);

        Button stopSimBtn = new Button("⏹ Arrêter");
        stopSimBtn.setMaxWidth(Double.MAX_VALUE);
        stopSimBtn.setTooltip(new Tooltip("Arrêter la simulation et revenir en mode édition"));
        stopSimBtn.setOnAction(e -> stopAndClose());
        applyInteractiveStyles(stopSimBtn,
                () -> STOP_IDLE_STYLE,
                () -> STOP_HOVER_STYLE,
                () -> STOP_PRESS_STYLE);

        btnGrid.add(playPauseBtn, 0, 0);
        btnGrid.add(stepBtn, 1, 0);
        btnGrid.add(stopSimBtn, 2, 0);

        HBox metaBar = new HBox(8);
        metaBar.setAlignment(Pos.CENTER_LEFT);

        Button resetBtn = new Button("🔄 Reset");
        resetBtn.setMinWidth(Region.USE_PREF_SIZE);
        applyInteractiveStyles(resetBtn,
                () -> AppTheme.SECONDARY_BUTTON_STYLE,
                () -> AppTheme.SECONDARY_BUTTON_HOVER_STYLE,
                () -> SECONDARY_PRESS_STYLE);
        resetBtn.setOnAction(e -> {
            simulator.reset();
            if (timingRecorder != null) {
                timingRecorder.resetStartTime();
            }
            // Réinitialiser les boutons d'entrée à 0
            for (var tb : toggleButtons.values()) {
                tb.setSelected(false);
                tb.setStyle(TOGGLE_IDLE_STYLE);
            }
            invalidateVisualCache();
            refreshVisuals();
            if (onTimingTick != null) {
                onTimingTick.run();
            }
        });

        cycleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        cycleLabel.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label speedLbl = new Label("Vitesse :");
        speedLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");
        speedLbl.setMinWidth(Region.USE_PREF_SIZE);

        ComboBox<String> speedCombo = new ComboBox<>();
        speedCombo.getItems().addAll("Rapide (50ms)", "Normal (100ms)", "Pédagogique (500ms)", "Ralenti (1s)");
        speedCombo.setValue("Normal (100ms)");
        speedCombo.setStyle("-fx-font-size: 11px;");
        speedCombo.setOnAction(e -> {
            int ms = switch (speedCombo.getSelectionModel().getSelectedIndex()) {
                case 0 -> 50;
                case 2 -> 500;
                case 3 -> 1000;
                default -> 100;
            };
            simulationTimer.stop();
            simulationTimer.getKeyFrames().setAll(new KeyFrame(Duration.millis(ms), ev -> runSimulationTick()));
            if (isRunning) {
                simulationTimer.play();
            }
        });

        metaBar.getChildren().addAll(resetBtn, cycleLabel, spacer, speedLbl, speedCombo);

        box.getChildren().addAll(btnGrid, metaBar);
        return new CollapsibleSection("⚙", "Contrôles Automate", null, box, true);
    }

    private void applyInteractiveStyles(Region node,
                                        Supplier<String> normalStyle,
                                        Supplier<String> hoverStyle,
                                        Supplier<String> pressedStyle) {
        node.setStyle(normalStyle.get());
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (!node.isPressed()) {
                node.setStyle(hoverStyle.get());
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (!node.isPressed()) {
                node.setStyle(normalStyle.get());
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> node.setStyle(pressedStyle.get()));
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e ->
                node.setStyle(node.isHover() ? hoverStyle.get() : normalStyle.get()));
    }

    private GridPane createColumnGrid(int columns) {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int i = 0; i < columns; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / columns);
            grid.getColumnConstraints().add(col);
        }
        return grid;
    }

    private GridPane createThreeColumnGrid() {
        return createColumnGrid(3);
    }

    private CollapsibleSection createInputsSection() {
        VBox box = new VBox(6);

        // Déterminer la liste unique des entrées : déclarées dans la table ou répertoriées par le simulateur
        // Utilisation d'un dictionnaire indexé par la clé normalisée pour éviter toute duplication
        Map<String, String> uniqueInputs = new LinkedHashMap<>(); // normalizedKey -> originalDisplayName
        Map<String, Variable> varMap = new HashMap<>();

        for (Variable v : inputVariables) {
            String name = v.getName();
            String norm = SimulationState.normalizeKey(name);
            uniqueInputs.putIfAbsent(norm, name);
            varMap.putIfAbsent(norm, v);
        }
        for (String simIn : simulator.getKnownInputVariables()) {
            String norm = SimulationState.normalizeKey(simIn);
            uniqueInputs.putIfAbsent(norm, simIn);
        }

        if (uniqueInputs.isEmpty()) {
            Label emptyLbl = new Label("Aucune entrée %I déclarée. Les transitions réceptives peuvent également être testées.");
            emptyLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + "; -fx-wrap-text: true;");
            box.getChildren().add(emptyLbl);
        } else {
            GridPane grid = createThreeColumnGrid();
            int idx = 0;
            for (Map.Entry<String, String> entry : uniqueInputs.entrySet()) {
                String norm = entry.getKey();
                String displayName = entry.getValue();
                Variable v = varMap.get(norm);
                String address = (v != null && v.getAddress() != null && !v.getAddress().isBlank())
                        ? v.getAddress() : "%I?";
                grid.add(createInputCard(norm, displayName, address), idx % 3, idx / 3);
                idx++;
            }
            box.getChildren().add(grid);
        }

        return new CollapsibleSection("🟢", "Entrées Capteurs (%I)", uniqueInputs.size() + " entrée(s)", box, true);
    }

    private Node createInputCard(String normKey, String displayName, String address) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        applyInteractiveStyles(card,
                () -> COMPACT_ROW_STYLE,
                () -> COMPACT_ROW_HOVER_STYLE,
                () -> COMPACT_ROW_PRESS_STYLE);

        // Ligne 1 : Nom + LED d'état (0 / 1) centrés
        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER);

        Label nameLbl = new Label(displayName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        nameLbl.setMinWidth(0);
        nameLbl.setTooltip(new Tooltip(displayName + " (" + address + ")"));

        Label ledIndicator = new Label("0");
        ledIndicator.setStyle(getLedStyle(false));
        ledIndicator.setMinWidth(Region.USE_PREF_SIZE);
        inputLedIndicators.put(normKey, ledIndicator);

        top.getChildren().addAll(nameLbl, ledIndicator);

        // Adresse en sous-titre centré
        Label addrBadge = new Label(address);
        addrBadge.setStyle(AppTheme.pillBadgeStyle("#DCFCE7", "#15803D") + "; -fx-font-size: 10px; -fx-padding: 1 6 1 6;");

        // Boutons empilés l'un sous l'autre : Poussoir et Interrupteur
        VBox actions = new VBox(4);
        actions.setAlignment(Pos.CENTER);
        actions.setMaxWidth(Double.MAX_VALUE);

        // 1. Bouton Poussoir (Appui = 1, Relâche = 0)
        Button pushBtn = new Button("Poussoir");
        pushBtn.setMaxWidth(Double.MAX_VALUE);
        pushBtn.setTooltip(new Tooltip("Bouton poussoir momentané (appui = 1, relâche = 0)"));
        applyInteractiveStyles(pushBtn,
                () -> PUSH_IDLE_STYLE,
                () -> PUSH_HOVER_STYLE,
                () -> PUSH_ACTIVE_STYLE);
        pushBtn.setOnMousePressed(e -> {
            simulator.setInput(normKey, true);
            simulator.step();
            refreshVisuals();
        });
        pushBtn.setOnMouseReleased(e -> {
            // Ne repasser à false que si l'interrupteur n'est pas actif
            ToggleButton tb = toggleButtons.get(normKey);
            if (tb == null || !tb.isSelected()) {
                simulator.setInput(normKey, false);
            }
            simulator.step();
            refreshVisuals();
        });

        // 2. Bouton Interrupteur (Maintien permanent 0 ⇄ 1)
        ToggleButton toggleBtn = new ToggleButton("Interrupteur");
        toggleBtn.setMaxWidth(Double.MAX_VALUE);
        toggleBtn.setTooltip(new Tooltip("Interrupteur à bascule (maintien 0 ⇄ 1)"));
        applyInteractiveStyles(toggleBtn,
                () -> toggleBtn.isSelected() ? TOGGLE_ACTIVE_STYLE : TOGGLE_IDLE_STYLE,
                () -> toggleBtn.isSelected() ? TOGGLE_ACTIVE_HOVER_STYLE : TOGGLE_IDLE_HOVER_STYLE,
                () -> toggleBtn.isSelected() ? TOGGLE_ACTIVE_PRESS_STYLE : TOGGLE_IDLE_PRESS_STYLE);
        toggleBtn.setOnAction(e -> {
            boolean active = toggleBtn.isSelected();
            simulator.setInput(normKey, active);
            toggleBtn.setStyle(toggleBtn.isHover()
                    ? (active ? TOGGLE_ACTIVE_HOVER_STYLE : TOGGLE_IDLE_HOVER_STYLE)
                    : (active ? TOGGLE_ACTIVE_STYLE : TOGGLE_IDLE_STYLE));
            simulator.step();
            refreshVisuals();
        });
        toggleButtons.put(normKey, toggleBtn);

        actions.getChildren().addAll(pushBtn, toggleBtn);
        card.getChildren().addAll(top, addrBadge, actions);
        return card;
    }

    private CollapsibleSection createOutputsSection() {
        VBox box = new VBox(6);

        // Déterminer la liste unique des sorties : déclarées dans la table ou répertoriées par le simulateur
        // Utilisation d'un dictionnaire indexé par la clé normalisée pour éviter toute duplication
        Map<String, String> uniqueOutputs = new LinkedHashMap<>(); // normalizedKey -> originalDisplayName
        Map<String, Variable> varMap = new HashMap<>();

        for (Variable v : outputVariables) {
            String name = v.getName();
            String norm = SimulationState.normalizeKey(name);
            uniqueOutputs.putIfAbsent(norm, name);
            varMap.putIfAbsent(norm, v);
        }
        for (String simOut : simulator.getKnownOutputVariables()) {
            String norm = SimulationState.normalizeKey(simOut);
            uniqueOutputs.putIfAbsent(norm, simOut);
        }

        if (uniqueOutputs.isEmpty()) {
            Label emptyLbl = new Label("Aucune sortie %Q déclarée.");
            emptyLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");
            box.getChildren().add(emptyLbl);
        } else {
            GridPane grid = createThreeColumnGrid();
            int idx = 0;
            for (Map.Entry<String, String> entry : uniqueOutputs.entrySet()) {
                String norm = entry.getKey();
                String displayName = entry.getValue();
                Variable v = varMap.get(norm);
                String address = (v != null && v.getAddress() != null && !v.getAddress().isBlank())
                        ? v.getAddress() : "%Q?";
                grid.add(createOutputCard(norm, displayName, address), idx % 3, idx / 3);
                idx++;
            }
            box.getChildren().add(grid);
        }

        return new CollapsibleSection("💡", "Sorties Actionneurs (%Q)", uniqueOutputs.size() + " lampe(s)", box, true);
    }

    private Node createOutputCard(String normKey, String displayName, String address) {
        HBox card = new HBox(7);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(OUTPUT_TILE_IDLE_STYLE);
        outputCards.put(normKey, card);

        // Lampe témoin industrielle circulaire agrandie
        Circle lamp = new Circle(10);
        lamp.setFill(Color.rgb(148, 163, 184));
        lamp.setStroke(Color.rgb(100, 116, 139));
        lamp.setStrokeWidth(1.5);
        outputLamps.put(normKey, lamp);

        VBox info = new VBox(2);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLbl = new Label(displayName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        nameLbl.setTooltip(new Tooltip(displayName + " (" + address + ")"));

        HBox meta = new HBox(4);
        meta.setAlignment(Pos.CENTER_LEFT);

        Label addrBadge = new Label(address);
        addrBadge.setStyle(AppTheme.pillBadgeStyle("#E0F2FE", "#0369A1") + "; -fx-font-size: 9.5px; -fx-padding: 1 4 1 4;");
        addrBadge.setMinWidth(Region.USE_PREF_SIZE);

        Label stateLbl = new Label("OFF");
        stateLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 10.5px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");
        stateLbl.setMinWidth(Region.USE_PREF_SIZE);
        outputStateLabels.put(normKey, stateLbl);

        meta.getChildren().addAll(addrBadge, stateLbl);
        info.getChildren().addAll(nameLbl, meta);

        card.getChildren().addAll(lamp, info);
        return card;
    }

    private CollapsibleSection createCountersSection() {
        GridPane grid = createThreeColumnGrid();

        Map<String, String> uniqueCounters = new LinkedHashMap<>();
        Map<String, Variable> varMap = new HashMap<>();

        for (Variable v : counterVariables) {
            String name = v.getName();
            String norm = SimulationState.normalizeKey(name);
            uniqueCounters.putIfAbsent(norm, name);
            varMap.putIfAbsent(norm, v);
        }
        for (String cName : simulator.getState().getCounterVariables().keySet()) {
            String norm = SimulationState.normalizeKey(cName);
            uniqueCounters.putIfAbsent(norm, cName);
        }

        int idx = 0;
        for (Map.Entry<String, String> entry : uniqueCounters.entrySet()) {
            String norm = entry.getKey();
            String displayName = entry.getValue();
            Variable v = varMap.get(norm);
            String address = (v != null && v.getAddress() != null && !v.getAddress().isBlank())
                    ? v.getAddress() : "INT";
            grid.add(createCounterCard(norm, displayName, address), idx % 3, idx / 3);
            idx++;
        }

        return new CollapsibleSection("🔢", "Compteurs Industriels", uniqueCounters.size() + " actif(s)", grid, true);
    }

    private Node createCounterCard(String normKey, String displayName, String address) {
        HBox card = new HBox(7);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(OUTPUT_TILE_IDLE_STYLE);

        // Indicateur circulaire violet assorti aux sorties
        Circle badgeCircle = new Circle(10);
        badgeCircle.setFill(Color.rgb(245, 243, 255)); // #F5F3FF
        badgeCircle.setStroke(Color.rgb(124, 58, 237)); // #7C3AED
        badgeCircle.setStrokeWidth(1.5);

        Label iconLbl = new Label("C");
        iconLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #7C3AED;");
        StackPane indicator = new StackPane(badgeCircle, iconLbl);
        indicator.setMinWidth(Region.USE_PREF_SIZE);

        VBox info = new VBox(2);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLbl = new Label(displayName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");
        nameLbl.setTooltip(new Tooltip(displayName + " (" + address + ")"));

        HBox meta = new HBox(4);
        meta.setAlignment(Pos.CENTER_LEFT);

        Label addrBadge = new Label(address);
        addrBadge.setStyle(AppTheme.pillBadgeStyle("#EDE9FE", "#6D28D9") + "; -fx-font-size: 9.5px; -fx-padding: 1 4 1 4;");
        addrBadge.setMinWidth(Region.USE_PREF_SIZE);

        Label valLbl = new Label("0");
        valLbl.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7C3AED;");
        valLbl.setMinWidth(Region.USE_PREF_SIZE);
        counterValueLabels.put(normKey, valLbl);

        meta.getChildren().addAll(addrBadge, valLbl);
        info.getChildren().addAll(nameLbl, meta);

        card.getChildren().addAll(indicator, info);
        return card;
    }

    private void runSimulationTick() {
        if (!isRunning) return;
        executeStepAndRecord();
    }

    private void executeStepAndRecord() {
        SimulationStepResult result = simulator.step();
        refreshVisuals();

        if (timingRecorder != null && timingRecorder.isRecording()) {
            timingRecorder.recordTick(
                    System.currentTimeMillis(),
                    simulator.getState(),
                    result,
                    cachedStepNames,
                    cachedInNames,
                    cachedOutNames,
                    cachedCountNames
            );

            if (onTimingTick != null) {
                onTimingTick.run();
            }
        }
    }

    private void invalidateVisualCache() {
        lastInputStates.clear();
        lastOutputStates.clear();
        lastCounterValues.clear();
        lastCycleCount = -1;
        lastActiveSteps = null;
        lastActiveOutputs = null;
        lastValidatedTrans = null;
        lastTrueRecTrans = null;
        lastFireableTrans = null;
        lastRuntimeAlertsHash = 0;
    }

    public void refreshVisuals() {
        // 1. Rafraîchissement du canvas Grafcet uniquement si l'un des états logiques a évolué
        var activeSteps = simulator.getState().getActiveStepIds();
        var activeOutputs = simulator.getState().getActiveOutputs();
        var validatedTrans = simulator.getValidatedTransitions();
        var trueRecTrans = simulator.getTrueReceptivityTransitions();
        var fireableTrans = simulator.getFireableTransitions();

        if (!Objects.equals(activeSteps, lastActiveSteps)
                || !Objects.equals(activeOutputs, lastActiveOutputs)
                || !Objects.equals(validatedTrans, lastValidatedTrans)
                || !Objects.equals(trueRecTrans, lastTrueRecTrans)
                || !Objects.equals(fireableTrans, lastFireableTrans)) {
            lastActiveSteps = Set.copyOf(activeSteps);
            lastActiveOutputs = Set.copyOf(activeOutputs);
            lastValidatedTrans = Set.copyOf(validatedTrans);
            lastTrueRecTrans = Set.copyOf(trueRecTrans);
            lastFireableTrans = Set.copyOf(fireableTrans);
            grafcet.updateSimulationVisuals(activeSteps, activeOutputs, validatedTrans, trueRecTrans, fireableTrans);
        }

        // 2. Mise à jour du compteur de cycles uniquement sur changement
        long currentCycle = simulator.getState().getCycleCount();
        if (currentCycle != lastCycleCount) {
            lastCycleCount = currentCycle;
            cycleLabel.setText("Cycle : " + currentCycle);
        }

        // 3. Mise à jour des voyants LED d'entrées uniquement sur changement d'état
        for (var entry : inputLedIndicators.entrySet()) {
            String key = entry.getKey();
            boolean val = simulator.getInput(key);
            Boolean prev = lastInputStates.put(key, val);
            if (prev == null || prev != val) {
                Label led = entry.getValue();
                led.setText(val ? "1" : "0");
                led.setStyle(getLedStyle(val));
            }
        }

        // 4. Mise à jour des lampes témoins de sorties uniquement sur changement d'état
        for (var entry : outputLamps.entrySet()) {
            String key = entry.getKey();
            boolean active = simulator.isOutputActive(key);
            Boolean prev = lastOutputStates.put(key, active);
            if (prev != null && prev == active) {
                continue;
            }
            Circle lamp = entry.getValue();
            Label stateLbl = outputStateLabels.get(key);
            HBox card = outputCards.get(key);

            if (active) {
                // Lampe ALLUMÉE : Cyan éclatant avec effet de halo lumineux partagé
                lamp.setFill(Color.rgb(2, 132, 199)); // #0284C7
                lamp.setStroke(Color.rgb(56, 189, 248)); // #38BDF8
                lamp.setEffect(ACTIVE_LAMP_GLOW);
                if (card != null) {
                    card.setStyle(OUTPUT_TILE_ACTIVE_STYLE);
                }
                if (stateLbl != null) {
                    stateLbl.setText("ON ●");
                    stateLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 10.5px; -fx-text-fill: #0284C7;");
                }
            } else {
                // Lampe ÉTEINTE : gris neutre doux sans halo
                lamp.setFill(Color.rgb(148, 163, 184)); // #94A3B8
                lamp.setStroke(Color.rgb(100, 116, 139));
                lamp.setEffect(null);
                if (card != null) {
                    card.setStyle(OUTPUT_TILE_IDLE_STYLE);
                }
                if (stateLbl != null) {
                    stateLbl.setText("OFF");
                    stateLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 10.5px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");
                }
            }
        }

        // 5. Mise à jour des compteurs uniquement sur changement de valeur
        for (var entry : counterValueLabels.entrySet()) {
            String key = entry.getKey();
            int val = simulator.getState().getCounterValue(key);
            Integer prev = lastCounterValues.put(key, val);
            if (prev == null || prev != val) {
                entry.getValue().setText(String.valueOf(val));
            }
        }

        // 6. Mise à jour des diagnostics dynamiques de simulation
        refreshRuntimeDiagnostics();
    }

    private void refreshRuntimeDiagnostics() {
        List<SfcValidationMessage> runtimeMsgs = simulator.getRuntimeDiagnostics();
        int hash = runtimeMsgs.hashCode();
        if (hash == lastRuntimeAlertsHash && !runtimeAlertsContainer.getChildren().isEmpty()) {
            return;
        }
        lastRuntimeAlertsHash = hash;
        runtimeAlertsContainer.getChildren().clear();

        if (runtimeMsgs.isEmpty()) {
            Label okLbl = new Label("✓ Aucun conflit d'exécution détecté (aiguillages OU, Set/Reset et synchronisations ET conformes).");
            okLbl.setWrapText(true);
            okLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: " + AppTheme.GREEN_SUCCESS + ";");
            runtimeAlertsContainer.getChildren().add(okLbl);
            return;
        }

        for (SfcValidationMessage msg : runtimeMsgs) {
            VBox card = new VBox(5);
            String border = msg.isError() ? AppTheme.RED_BORDER : (msg.isWarning() ? AppTheme.ORANGE_BORDER : "#BAE6FD");
            String bg = msg.isError() ? AppTheme.RED_LIGHT_BG : (msg.isWarning() ? AppTheme.ORANGE_LIGHT_BG : "#F0F9FF");
            card.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border +
                    "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 9px;");

            Label ruleLbl = new Label((msg.isError() ? "✕ " : (msg.isWarning() ? "⚠ " : "ℹ ")) + msg.ruleReference());
            ruleLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                    (msg.isError() ? AppTheme.RED_DANGER : (msg.isWarning() ? AppTheme.ORANGE : "#0369A1")) + ";");

            Label mLbl = new Label(msg.message());
            mLbl.setWrapText(true);
            mLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");

            card.getChildren().addAll(ruleLbl, mLbl);

            if (msg.hasPedagogicalHint()) {
                Label hintLbl = new Label("💡 " + msg.pedagogicalHint());
                hintLbl.setWrapText(true);
                hintLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
                card.getChildren().add(hintLbl);
            }
            runtimeAlertsContainer.getChildren().add(card);
        }
    }

    private void togglePlayPause() {
        isRunning = !isRunning;
        if (isRunning) {
            playPauseBtn.setText("⏸ Pause");
            playPauseBtn.setStyle(playPauseBtn.isHover() ? AppTheme.PRIMARY_BUTTON_HOVER_STYLE : AppTheme.PRIMARY_BUTTON_STYLE);
            statusBadge.setText("● EN COURS");
            statusBadge.setStyle(AppTheme.pillBadgeStyle("#D1FAE5", "#065F46") + "; -fx-font-weight: bold; -fx-font-size: 11px;");
            simulationTimer.play();
        } else {
            playPauseBtn.setText("▶ Reprendre");
            playPauseBtn.setStyle(playPauseBtn.isHover() ? PAUSE_HOVER_STYLE : PAUSE_IDLE_STYLE);
            statusBadge.setText("⏸ PAUSE");
            statusBadge.setStyle(AppTheme.pillBadgeStyle("#FEF3C7", "#92400E") + "; -fx-font-weight: bold; -fx-font-size: 11px;");
            simulationTimer.stop();
        }
    }

    private String getLedStyle(boolean active) {
        if (active) {
            return "-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
                    "-fx-font-size: 11px; -fx-padding: 2 7 2 7; -fx-background-radius: 10px;";
        } else {
            return "-fx-background-color: #E2E8F0; -fx-text-fill: #64748B; -fx-font-weight: bold; " +
                    "-fx-font-size: 11px; -fx-padding: 2 7 2 7; -fx-background-radius: 10px;";
        }
    }

    public void stop() {
        if (stopped) return;
        stopped = true;
        isRunning = false;
        if (simulationTimer != null) {
            simulationTimer.stop();
        }
        grafcet.clearSimulationVisuals();
    }

    public void stopAndClose() {
        stop();
        if (onClose != null) {
            onClose.run();
        }
    }
}
