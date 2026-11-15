package tk.tahri.grafino.ui;

import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.SfcProgram;
import tk.tahri.grafino.model.SfcValidationMessage;
import tk.tahri.grafino.simulation.GrafcetSimulator;
import tk.tahri.grafino.simulation.TimingRecorder;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coordonnateur du mode simulation synchrone et du panneau d'inspection normative (diagnostics).
 */
public final class SimulationCoordinator {

    private final MainUI host;
    private final Grafcet grafcet;
    private final VariableTable variableTable;
    private final AppMenuBar menuBar;
    private final TimingRecorder timingRecorder;
    private final Supplier<String> projectNameSupplier;
    private final Runnable onTitleRefresh;
    private final Runnable onTimingRedrawIfVisible;
    private final Consumer<String> statusConsumer;

    private SimulationPanel activeSimulationPanel;
    private DiagnosticsPanel diagnosticsPanel;

    public SimulationCoordinator(
            MainUI host,
            Grafcet grafcet,
            VariableTable variableTable,
            AppMenuBar menuBar,
            TimingRecorder timingRecorder,
            Supplier<String> projectNameSupplier,
            Runnable onTitleRefresh,
            Runnable onTimingRedrawIfVisible,
            Consumer<String> statusConsumer
    ) {
        this.host = host;
        this.grafcet = grafcet;
        this.variableTable = variableTable;
        this.menuBar = menuBar;
        this.timingRecorder = timingRecorder;
        this.projectNameSupplier = projectNameSupplier;
        this.onTitleRefresh = onTitleRefresh;
        this.onTimingRedrawIfVisible = onTimingRedrawIfVisible;
        this.statusConsumer = statusConsumer;
    }

    public boolean isSimulationActive() {
        return activeSimulationPanel != null;
    }

    public void restoreIdleRightPanel() {
        host.setRight(diagnosticsPanel);
    }

    public void toggleSimulation() {
        if (activeSimulationPanel != null) {
            stopSimulation();
        } else {
            startSimulation();
        }
    }

    public void startSimulation() {
        if (activeSimulationPanel != null) {
            stopSimulation();
            return;
        }

        SfcProgram program = grafcet.toProgram(projectNameSupplier.get(), variableTable.getVariables());
        GrafcetSimulator simulator = new GrafcetSimulator(program);
        timingRecorder.resetStartTime();

        grafcet.getComponents().forEach(c -> c.setSelected(false));
        grafcet.fireSelectionChange(Optional.empty());

        activeSimulationPanel = new SimulationPanel(
                simulator,
                grafcet,
                variableTable.getInputVariables(),
                variableTable.getOutputVariables(),
                variableTable.getCounterVariables(),
                timingRecorder,
                onTimingRedrawIfVisible,
                this::stopSimulation
        );

        host.setRight(activeSimulationPanel);
        menuBar.setSimulationActive(true);
        statusConsumer.accept("Simulation synchrone démarrée");
    }

    public void stopSimulation() {
        if (activeSimulationPanel != null) {
            SimulationPanel panel = activeSimulationPanel;
            activeSimulationPanel = null;
            panel.stop();
        }
        host.setRight(diagnosticsPanel);
        menuBar.setSimulationActive(false);
        grafcet.clearSimulationVisuals();
        refreshDiagnostics();
        statusConsumer.accept("Simulation arrêtée");
    }

    public void toggleDiagnosticsPanel() {
        if (activeSimulationPanel != null) {
            statusConsumer.accept("Veuillez arrêter la simulation pour inspecter les diagnostics");
            return;
        }

        if (diagnosticsPanel != null && host.getRight() == diagnosticsPanel) {
            host.setRight(null);
            diagnosticsPanel = null;
        } else {
            diagnosticsPanel = new DiagnosticsPanel(this::onSelectDiagnosticElement, () -> {
                host.setRight(null);
                diagnosticsPanel = null;
            });
            refreshDiagnostics();
            host.setRight(diagnosticsPanel);
        }
    }

    public void refreshDiagnostics() {
        onTitleRefresh.run();
        try {
            SfcProgram program = grafcet.toProgram(projectNameSupplier.get(), variableTable.getVariables());
            List<SfcValidationMessage> messages = program.validate();
            long errorCount = messages.stream().filter(SfcValidationMessage::isError).count();
            long warningCount = messages.stream().filter(SfcValidationMessage::isWarning).count();
            menuBar.setDiagnosticsStatus(errorCount, warningCount);
            grafcet.updateDiagnosticsVisuals(messages);
            if (diagnosticsPanel != null) {
                diagnosticsPanel.setMessages(messages);
            }
        } catch (Exception _) {
            // Diagramme en cours d'édition
        }
    }

    private void onSelectDiagnosticElement(String elementId) {
        if (elementId != null && !elementId.isBlank()) {
            grafcet.selectComponentByReference(elementId);
            statusConsumer.accept("Élément inspecté : " + elementId);
        }
    }
}
