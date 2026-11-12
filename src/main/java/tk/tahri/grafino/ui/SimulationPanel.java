package tk.tahri.grafino.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
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
    private static final DropShadow ACTIVE_LAMP_GLOW = new DropShadow(12, Color.rgb(2, 132, 199, 0.85));

    private final Map<String, ToggleButton> toggleButtons = new HashMap<>();
    private final Map<String, Label> inputLedIndicators = new HashMap<>();
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

        this.setPrefWidth(380);
        this.setMinWidth(340);
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

        HBox btnBar = new HBox(8);
        btnBar.setAlignment(Pos.CENTER_LEFT);

        playPauseBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
        playPauseBtn.setOnAction(e -> togglePlayPause());

        Button stepBtn = new Button("⏭ Pas-à-pas");
        stepBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        stepBtn.setOnAction(e -> executeStepAndRecord());

        Button resetBtn = new Button("🔄 Reset");
        resetBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        resetBtn.setOnAction(e -> {
            simulator.reset();
            if (timingRecorder != null) {
                timingRecorder.resetStartTime();
            }
            // Réinitialiser les boutons d'entrée à 0
            for (var tb : toggleButtons.values()) {
                tb.setSelected(false);
                tb.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 11px; " +
                        "-fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px; -fx-border-color: #CBD5E1; -fx-border-radius: 4px;");
            }
            invalidateVisualCache();
            refreshVisuals();
            if (onTimingTick != null) {
                onTimingTick.run();
            }
        });

        Button stopSimBtn = new Button("⏹ Arrêter");
        stopSimBtn.setStyle("-fx-background-color: #D9381E; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
                "-fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-padding: 4 10 4 10;");
        stopSimBtn.setTooltip(new Tooltip("Arrêter la simulation et revenir en mode édition"));
        stopSimBtn.setOnAction(e -> stopAndClose());

        btnBar.getChildren().addAll(playPauseBtn, stepBtn, resetBtn, stopSimBtn);

        HBox metaBar = new HBox(12);
        metaBar.setAlignment(Pos.CENTER_LEFT);

        cycleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");

        Label speedLbl = new Label("Vitesse :");
        speedLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

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

        metaBar.getChildren().addAll(cycleLabel, new Region(), speedLbl, speedCombo);

        box.getChildren().addAll(btnBar, metaBar);
        return new CollapsibleSection("⚙", "Contrôles Automate", null, box, true);
    }

    private CollapsibleSection createInputsSection() {
        VBox box = new VBox(8);

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
            for (Map.Entry<String, String> entry : uniqueInputs.entrySet()) {
                String norm = entry.getKey();
                String displayName = entry.getValue();
                Variable v = varMap.get(norm);
                String address = (v != null && v.getAddress() != null && !v.getAddress().isBlank())
                        ? v.getAddress() : "%I?";
                box.getChildren().add(createInputCard(norm, displayName, address));
            }
        }

        return new CollapsibleSection("🟢", "Entrées Capteurs (%I)", uniqueInputs.size() + " entrée(s)", box, true);
    }

    private Node createInputCard(String normKey, String displayName, String address) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: " + AppTheme.BORDER_COLOR +
                "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 8px;");

        // En-tête de la carte : Nom + Adresse
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label(displayName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");

        Label addrBadge = new Label(address);
        addrBadge.setStyle(AppTheme.pillBadgeStyle("#DCFCE7", "#15803D") + "; -fx-font-size: 10px;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Témoin LED d'état de l'entrée
        Label ledIndicator = new Label("0");
        ledIndicator.setStyle(getLedStyle(false));
        inputLedIndicators.put(normKey, ledIndicator);

        top.getChildren().addAll(nameLbl, addrBadge, spacer, ledIndicator);

        // Boutons de commande : Poussoir (Momentané) et Bascule (Toggle)
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        // 1. Bouton Poussoir (Appui = 1, Relâche = 0)
        Button pushBtn = new Button("Poussoir");
        pushBtn.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #1E293B; -fx-font-weight: bold; " +
                "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px;");
        pushBtn.setOnMousePressed(e -> {
            simulator.setInput(normKey, true);
            pushBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
                    "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px;");
            simulator.step();
            refreshVisuals();
        });
        pushBtn.setOnMouseReleased(e -> {
            // Ne repasser à false que si le bouton bascule n'est pas actif
            ToggleButton tb = toggleButtons.get(normKey);
            if (tb == null || !tb.isSelected()) {
                simulator.setInput(normKey, false);
            }
            pushBtn.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #1E293B; -fx-font-weight: bold; " +
                    "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px;");
            simulator.step();
            refreshVisuals();
        });

        // 2. Bouton Bascule (Maintien permanent 0 ⇄ 1)
        ToggleButton toggleBtn = new ToggleButton("Bascule 0/1");
        toggleBtn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 11px; " +
                "-fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px; -fx-border-color: #CBD5E1; -fx-border-radius: 4px;");
        toggleBtn.setOnAction(e -> {
            boolean active = toggleBtn.isSelected();
            simulator.setInput(normKey, active);
            if (active) {
                toggleBtn.setStyle("-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
                        "-fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px;");
            } else {
                toggleBtn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 11px; " +
                        "-fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px; -fx-border-color: #CBD5E1; -fx-border-radius: 4px;");
            }
            simulator.step();
            refreshVisuals();
        });
        toggleButtons.put(normKey, toggleBtn);

        actions.getChildren().addAll(pushBtn, toggleBtn);
        card.getChildren().addAll(top, actions);
        return card;
    }

    private CollapsibleSection createOutputsSection() {
        VBox box = new VBox(8);

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
            for (Map.Entry<String, String> entry : uniqueOutputs.entrySet()) {
                String norm = entry.getKey();
                String displayName = entry.getValue();
                Variable v = varMap.get(norm);
                String address = (v != null && v.getAddress() != null && !v.getAddress().isBlank())
                        ? v.getAddress() : "%Q?";
                box.getChildren().add(createOutputCard(norm, displayName, address));
            }
        }

        return new CollapsibleSection("💡", "Sorties Actionneurs (%Q)", uniqueOutputs.size() + " lampe(s)", box, true);
    }

    private Node createOutputCard(String normKey, String displayName, String address) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: " + AppTheme.BORDER_COLOR +
                "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10px;");

        // Lampe témoin industrielle circulaire
        Circle lamp = new Circle(12);
        lamp.setFill(Color.rgb(100, 116, 139)); // Éteinte par défaut (gris ardoise)
        lamp.setStroke(Color.rgb(71, 85, 105));
        lamp.setStrokeWidth(1.5);
        outputLamps.put(normKey, lamp);

        VBox info = new VBox(2);
        Label nameLbl = new Label(displayName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");

        Label addrBadge = new Label(address);
        addrBadge.setStyle(AppTheme.pillBadgeStyle("#E0F2FE", "#0369A1") + "; -fx-font-size: 10px;");
        info.getChildren().addAll(nameLbl, addrBadge);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // État de la lampe : ALLUMÉE ou ÉTEINTE
        Label stateLbl = new Label("ÉTEINTE");
        stateLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");
        outputStateLabels.put(normKey, stateLbl);

        card.getChildren().addAll(lamp, info, spacer, stateLbl);
        return card;
    }

    private CollapsibleSection createCountersSection() {
        VBox box = new VBox(6);

        Map<String, String> uniqueCounters = new LinkedHashMap<>();

        for (Variable v : counterVariables) {
            String name = v.getName();
            String norm = SimulationState.normalizeKey(name);
            uniqueCounters.putIfAbsent(norm, name);
        }
        for (String cName : simulator.getState().getCounterVariables().keySet()) {
            String norm = SimulationState.normalizeKey(cName);
            uniqueCounters.putIfAbsent(norm, cName);
        }

        for (Map.Entry<String, String> entry : uniqueCounters.entrySet()) {
            String norm = entry.getKey();
            String displayName = entry.getValue();

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: " + AppTheme.BORDER_COLOR +
                    "; -fx-border-radius: 4px; -fx-padding: 6px 10px;");

            Label name = new Label(displayName);
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label valLbl = new Label("Valeur : 0");
            valLbl.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #7C3AED;");
            counterValueLabels.put(norm, valLbl);

            row.getChildren().addAll(name, spacer, valLbl);
            box.getChildren().add(row);
        }

        return new CollapsibleSection("🔢", "Compteurs Industriels", uniqueCounters.size() + " actif(s)", box, true);
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

            if (active) {
                // Lampe ALLUMÉE : Cyan éclatant avec effet de halo lumineux partagé
                lamp.setFill(Color.rgb(2, 132, 199)); // #0284C7
                lamp.setStroke(Color.rgb(56, 189, 248)); // #38BDF8
                lamp.setEffect(ACTIVE_LAMP_GLOW);
                if (stateLbl != null) {
                    stateLbl.setText("ALLUMÉE ●");
                    stateLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #0284C7;");
                }
            } else {
                // Lampe ÉTEINTE : gris neutre doux sans halo
                lamp.setFill(Color.rgb(148, 163, 184)); // #94A3B8
                lamp.setStroke(Color.rgb(100, 116, 139));
                lamp.setEffect(null);
                if (stateLbl != null) {
                    stateLbl.setText("ÉTEINTE");
                    stateLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");
                }
            }
        }

        // 5. Mise à jour des compteurs uniquement sur changement de valeur
        for (var entry : counterValueLabels.entrySet()) {
            String key = entry.getKey();
            int val = simulator.getState().getCounterValue(key);
            Integer prev = lastCounterValues.put(key, val);
            if (prev == null || prev != val) {
                entry.getValue().setText("Valeur : " + val);
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
            okLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.GREEN_SUCCESS + ";");
            runtimeAlertsContainer.getChildren().add(okLbl);
            return;
        }

        for (SfcValidationMessage msg : runtimeMsgs) {
            VBox card = new VBox(4);
            String border = msg.isError() ? AppTheme.RED_BORDER : (msg.isWarning() ? AppTheme.ORANGE_BORDER : "#BAE6FD");
            String bg = msg.isError() ? AppTheme.RED_LIGHT_BG : (msg.isWarning() ? AppTheme.ORANGE_LIGHT_BG : "#F0F9FF");
            card.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border +
                    "; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 7px;");

            Label ruleLbl = new Label((msg.isError() ? "✕ " : (msg.isWarning() ? "⚠ " : "ℹ ")) + msg.ruleReference());
            ruleLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " +
                    (msg.isError() ? AppTheme.RED_DANGER : (msg.isWarning() ? AppTheme.ORANGE : "#0369A1")) + ";");

            Label mLbl = new Label(msg.message());
            mLbl.setWrapText(true);
            mLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MAIN + ";");

            card.getChildren().addAll(ruleLbl, mLbl);

            if (msg.hasPedagogicalHint()) {
                Label hintLbl = new Label("💡 " + msg.pedagogicalHint());
                hintLbl.setWrapText(true);
                hintLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");
                card.getChildren().add(hintLbl);
            }
            runtimeAlertsContainer.getChildren().add(card);
        }
    }

    private void togglePlayPause() {
        isRunning = !isRunning;
        if (isRunning) {
            playPauseBtn.setText("⏸ Pause");
            playPauseBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
            statusBadge.setText("● EN COURS");
            statusBadge.setStyle(AppTheme.pillBadgeStyle("#D1FAE5", "#065F46") + "; -fx-font-weight: bold; -fx-font-size: 11px;");
            simulationTimer.play();
        } else {
            playPauseBtn.setText("▶ Reprendre");
            playPauseBtn.setStyle("-fx-background-color: #E67E22; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 10 4 10; -fx-background-radius: 4px;");
            statusBadge.setText("⏸ PAUSE");
            statusBadge.setStyle(AppTheme.pillBadgeStyle("#FEF3C7", "#92400E") + "; -fx-font-weight: bold; -fx-font-size: 11px;");
            simulationTimer.stop();
        }
    }

    private String getLedStyle(boolean active) {
        if (active) {
            return "-fx-background-color: #10B981; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; " +
                    "-fx-font-size: 10px; -fx-padding: 2 7 2 7; -fx-background-radius: 10px;";
        } else {
            return "-fx-background-color: #E2E8F0; -fx-text-fill: #64748B; -fx-font-weight: bold; " +
                    "-fx-font-size: 10px; -fx-padding: 2 7 2 7; -fx-background-radius: 10px;";
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
