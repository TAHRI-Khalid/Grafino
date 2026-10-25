package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import tk.tahri.grafino.simulation.SimulationState;
import tk.tahri.grafino.simulation.TimingRecorder;
import tk.tahri.grafino.simulation.TimingSample;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Panneau de Chronogramme Interactif Multi-voies (Timing Diagram / Logic Analyzer).
 *
 * Offre :
 * - Visualisation temps réel des signaux digitaux (Étapes, Entrées %I, Sorties %Q) et analogiques (Compteurs).
 * - Grille temporelle dynamique avec zoom (100 ms/div à 5 s/div) et pan horizontal.
 * - Curseur rouge d'inspection instantanée (Time Scrubbing) et curseur bleu de mesure différentielle (Δt).
 * - Marqueurs verticaux d'événements de franchissement de transitions (T1, T2...).
 * - Exportation des relevés en CSV pour les comptes-rendus de TP d'automatisme.
 * - Restauration d'état sur le Grafcet (Time Travel pédagogique).
 */
public class TimingDiagramPanel extends VBox {

    public record Channel(String name, String type, Color color) {}

    private final TimingRecorder recorder;
    private final Consumer<TimingSample> onRestoreStateToGrafcet;

    // Canvas de dessin
    private final Canvas canvas = new Canvas(800, 240);
    private final GraphicsContext ctx = canvas.getGraphicsContext2D();
    private final ScrollPane scrollPane = new ScrollPane();

    // Paramètres d'échelle et d'affichage
    private double pixelsPerSecond = 100.0; // 100 pixels = 1 seconde
    private static final double MIN_PIXELS_PER_SEC = 20.0;
    private static final double MAX_PIXELS_PER_SEC = 500.0;
    private static final double CHANNEL_HEIGHT = 32.0;
    private static final double HEADER_HEIGHT = 28.0;
    private static final double LABEL_COLUMN_WIDTH = 130.0;

    // Curseurs interactifs
    private double cursorTimeSec = -1.0; // Curseur principal rouge (-1 = suit le direct)
    private double measureCursorTimeSec = -1.0; // Curseur secondaire bleu (Δt)
    private boolean isDraggingCursor = false;
    private boolean isDraggingMeasureCursor = false;
    private boolean measureModeActive = false;

    // Éléments de contrôle UI
    private final Button recordBtn = new Button("⏸ Pause Relevé");
    private final Button clearBtn = new Button("🗑 Effacer");
    private final Button zoomInBtn = new Button("🔍+");
    private final Button zoomOutBtn = new Button("🔍-");
    private final Button resetZoomBtn = new Button("1:1");
    private final ToggleButton measureBtn = new ToggleButton("📏 Mesure Δt");
    private final Button restoreBtn = new Button("⏪ Restaurer cet état");
    private final Button exportCsvBtn = new Button("📊 Exporter CSV");
    private final Label infoLabel = new Label("Relevé temporel actif — Lancez la simulation");

    // Canaux ordonnés
    private final List<Channel> activeChannels = new ArrayList<>();

    public TimingDiagramPanel(TimingRecorder recorder, Consumer<TimingSample> onRestoreStateToGrafcet) {
        this.recorder = recorder;
        this.onRestoreStateToGrafcet = onRestoreStateToGrafcet;

        this.setSpacing(6);
        this.setPadding(new Insets(6, 10, 8, 10));
        this.setStyle("-fx-background-color: " + AppTheme.PANEL_BG + ";");

        // 1. Barre d'outils supérieure
        setupToolbar();

        // 2. Zone de dessin avec Canvas défilant
        setupCanvasArea();

        // 3. Interactions souris (Scrubbing temporel, Zoom molette)
        setupMouseInteractions();

        // Rafraîchissement initial
        redraw();
    }

    private void setupToolbar() {
        var toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(2, 2, 4, 2));

        recordBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        recordBtn.setOnAction(e -> {
            boolean rec = !recorder.isRecording();
            recorder.setRecording(rec);
            recordBtn.setText(rec ? "⏸ Pause Relevé" : "▶ Reprendre");
            recordBtn.setStyle(rec ? AppTheme.SECONDARY_BUTTON_STYLE : "-fx-background-color: #E67E22; -fx-text-fill: white; -fx-font-weight: bold;");
        });

        clearBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        clearBtn.setOnAction(e -> {
            recorder.clear();
            cursorTimeSec = -1.0;
            measureCursorTimeSec = -1.0;
            redraw();
        });

        zoomInBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        zoomInBtn.setTooltip(new Tooltip("Zoomer sur l'axe du temps"));
        zoomInBtn.setOnAction(e -> zoom(1.25));

        zoomOutBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        zoomOutBtn.setTooltip(new Tooltip("Dézoomer sur l'axe du temps"));
        zoomOutBtn.setOnAction(e -> zoom(0.8));

        resetZoomBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        resetZoomBtn.setTooltip(new Tooltip("Échelle standard (100 px = 1 seconde)"));
        resetZoomBtn.setOnAction(e -> {
            pixelsPerSecond = 100.0;
            redraw();
        });

        measureBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        measureBtn.setTooltip(new Tooltip("Activer le deuxième curseur de mesure différentielle (Δt)"));
        measureBtn.setOnAction(e -> {
            measureModeActive = measureBtn.isSelected();
            if (measureModeActive && measureCursorTimeSec < 0) {
                measureCursorTimeSec = Math.max(0.0, (cursorTimeSec >= 0 ? cursorTimeSec : recorder.getTotalDurationMs() / 1000.0) - 1.0);
            }
            redraw();
        });

        restoreBtn.setStyle(AppTheme.PRIMARY_BUTTON_STYLE);
        restoreBtn.setTooltip(new Tooltip("Time Travel : appliquer l'état temporel pointé par le curseur sur le Grafcet"));
        restoreBtn.setVisible(false);
        restoreBtn.setManaged(false);
        restoreBtn.setOnAction(e -> {
            if (cursorTimeSec >= 0 && onRestoreStateToGrafcet != null) {
                recorder.findSampleAt((long) (cursorTimeSec * 1000)).ifPresent(onRestoreStateToGrafcet);
            }
        });

        exportCsvBtn.setStyle(AppTheme.SECONDARY_BUTTON_STYLE);
        exportCsvBtn.setTooltip(new Tooltip("Exporter les relevés dans un fichier CSV pour compte-rendu de TP"));
        exportCsvBtn.setOnAction(e -> onExportCsv());

        infoLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.PRIMARY_TEAL_DARK + ";");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                recordBtn, clearBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                zoomOutBtn, resetZoomBtn, zoomInBtn,
                measureBtn, restoreBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                exportCsvBtn,
                spacer,
                infoLabel
        );

        this.getChildren().add(toolbar);
    }

    private void setupCanvasArea() {
        // Envelopper le canvas dans un conteneur et un scrollPane pour défilement horizontal fluide
        StackPane canvasContainer = new StackPane(canvas);
        canvasContainer.setAlignment(Pos.TOP_LEFT);
        canvasContainer.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: " + AppTheme.BORDER_COLOR + "; -fx-border-width: 1px;");

        scrollPane.setContent(canvasContainer);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #FFFFFF; -fx-background-color: transparent; -fx-padding: 0;");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        this.getChildren().add(scrollPane);

        // Ajuster la taille du canvas lors du redimensionnement de la fenêtre
        this.widthProperty().addListener((obs, oldW, newW) -> updateCanvasDimensions());
        this.heightProperty().addListener((obs, oldH, newH) -> updateCanvasDimensions());
    }

    private void setupMouseInteractions() {
        canvas.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;

            double mouseX = e.getX();
            if (mouseX < LABEL_COLUMN_WIDTH) return;

            double timeSec = (mouseX - LABEL_COLUMN_WIDTH) / pixelsPerSecond;
            double maxTimeSec = recorder.getTotalDurationMs() / 1000.0;
            timeSec = Math.max(0.0, Math.min(timeSec, maxTimeSec));

            if (measureModeActive && measureCursorTimeSec >= 0) {
                double distCursor = Math.abs((LABEL_COLUMN_WIDTH + cursorTimeSec * pixelsPerSecond) - mouseX);
                double distMeasure = Math.abs((LABEL_COLUMN_WIDTH + measureCursorTimeSec * pixelsPerSecond) - mouseX);
                if (distMeasure < distCursor && distMeasure <= 12) {
                    isDraggingMeasureCursor = true;
                    measureCursorTimeSec = timeSec;
                    updateInfoLabel();
                    redraw();
                    return;
                }
            }

            isDraggingCursor = true;
            cursorTimeSec = timeSec;
            restoreBtn.setVisible(true);
            restoreBtn.setManaged(true);
            updateInfoLabel();
            redraw();
        });

        canvas.setOnMouseDragged(e -> {
            if (isDraggingCursor || isDraggingMeasureCursor) {
                double mouseX = e.getX();
                double timeSec = Math.max(0.0, (mouseX - LABEL_COLUMN_WIDTH) / pixelsPerSecond);
                double maxTimeSec = recorder.getTotalDurationMs() / 1000.0;
                timeSec = Math.min(timeSec, maxTimeSec);

                if (isDraggingMeasureCursor) {
                    measureCursorTimeSec = timeSec;
                } else {
                    cursorTimeSec = timeSec;
                }
                updateInfoLabel();
                redraw();
            }
        });

        canvas.setOnMouseReleased(e -> {
            isDraggingCursor = false;
            isDraggingMeasureCursor = false;
        });

        // Molette : Zoom avec Ctrl, Scroll horizontal sans Ctrl
        canvas.setOnScroll(e -> {
            if (e.isControlDown()) {
                double factor = e.getDeltaY() > 0 ? 1.15 : 0.85;
                zoom(factor);
                e.consume();
            }
        });
    }

    private void zoom(double factor) {
        pixelsPerSecond = Math.max(MIN_PIXELS_PER_SEC, Math.min(MAX_PIXELS_PER_SEC, pixelsPerSecond * factor));
        redraw();
    }

    private void updateCanvasDimensions() {
        double durationSec = Math.max(10.0, recorder.getTotalDurationMs() / 1000.0);
        double reqWidth = LABEL_COLUMN_WIDTH + (durationSec * pixelsPerSecond) + 80.0;
        double viewWidth = scrollPane.getViewportBounds() != null ? scrollPane.getViewportBounds().getWidth() : 800;
        canvas.setWidth(Math.max(reqWidth, viewWidth - 5));

        refreshChannelsList();
        double reqHeight = HEADER_HEIGHT + (activeChannels.size() * CHANNEL_HEIGHT) + 20.0;
        canvas.setHeight(Math.max(reqHeight, 200.0));
    }

    private void refreshChannelsList() {
        activeChannels.clear();
        // 1. Étapes (%X)
        for (String step : recorder.getRegisteredStepNames()) {
            activeChannels.add(new Channel(step, "STEP", Color.web(AppTheme.PRIMARY_TEAL)));
        }
        // 2. Entrées (%I)
        for (String in : recorder.getRegisteredInputNames()) {
            activeChannels.add(new Channel(in, "INPUT", Color.web("#10B981"))); // Vert Émeraude
        }
        // 3. Sorties (%Q)
        for (String out : recorder.getRegisteredOutputNames()) {
            activeChannels.add(new Channel(out, "OUTPUT", Color.web("#0284C7"))); // Cyan TIA Portal
        }
        // 4. Compteurs (INT)
        for (String c : recorder.getRegisteredCounterNames()) {
            activeChannels.add(new Channel(c, "COUNTER", Color.web("#7C3AED"))); // Violet
        }
    }

    /**
     * Rafraîchissement complet du tracé graphique vectoriel.
     */
    public void redraw() {
        updateCanvasDimensions();

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Fond blanc pur
        ctx.setFill(Color.WHITE);
        ctx.fillRect(0, 0, w, h);

        List<TimingSample> samples = recorder.getSamples();
        double totalDurationSec = recorder.getTotalDurationMs() / 1000.0;

        // 1. Grille temporelle et graduation (Règle des secondes)
        drawTimeGrid(w, h, totalDurationSec);

        // 2. Marqueurs verticaux de franchissement de transitions
        drawTransitionFiredMarkers(samples, h);

        // 3. Tracé des formes d'ondes pour chaque canal
        double currentY = HEADER_HEIGHT;
        for (int i = 0; i < activeChannels.size(); i++) {
            Channel ch = activeChannels.get(i);
            drawWaveform(ch, samples, currentY, CHANNEL_HEIGHT);
            currentY += CHANNEL_HEIGHT;
        }

        // 4. Colonne de gauche fixe avec badges des voies
        drawChannelsHeaderColumn(h);

        // 5. Curseurs interactifs
        drawCursors(h);
    }

    private void drawTimeGrid(double w, double h, double totalDurationSec) {
        ctx.setLineWidth(1.0);
        ctx.setFont(Font.font("Consolas", FontWeight.NORMAL, 10));

        // Déterminer le pas temporel en fonction du zoom
        double timeStep = 1.0; // secondes
        if (pixelsPerSecond >= 250) timeStep = 0.2;
        else if (pixelsPerSecond >= 120) timeStep = 0.5;
        else if (pixelsPerSecond <= 40) timeStep = 2.0;

        double maxTime = Math.max(totalDurationSec + 2.0, (w - LABEL_COLUMN_WIDTH) / pixelsPerSecond);

        // Bandeau d'en-tête temporel
        ctx.setFill(Color.web(AppTheme.PANEL_BG));
        ctx.fillRect(LABEL_COLUMN_WIDTH, 0, w - LABEL_COLUMN_WIDTH, HEADER_HEIGHT);
        ctx.setStroke(Color.web(AppTheme.BORDER_COLOR));
        ctx.strokeLine(LABEL_COLUMN_WIDTH, HEADER_HEIGHT, w, HEADER_HEIGHT);

        for (double t = 0.0; t <= maxTime; t += timeStep) {
            double x = LABEL_COLUMN_WIDTH + (t * pixelsPerSecond);
            if (x > w) break;

            // Ligne verticale de grille
            ctx.setStroke(Color.web("#F1F5F9")); // Gris très doux
            ctx.strokeLine(x, HEADER_HEIGHT, x, h);

            // Graduations dans l'en-tête
            ctx.setStroke(Color.web(AppTheme.BORDER_COLOR));
            ctx.strokeLine(x, HEADER_HEIGHT - 6, x, HEADER_HEIGHT);

            ctx.setFill(Color.web(AppTheme.TEXT_MUTED));
            String timeText = String.format(Locale.US, (timeStep < 1.0 ? "%.1fs" : "%.0fs"), t);
            ctx.fillText(timeText, x + 2, HEADER_HEIGHT - 8);
        }
    }

    private void drawTransitionFiredMarkers(List<TimingSample> samples, double h) {
        if (samples.isEmpty()) return;

        ctx.setLineWidth(1.0);
        for (TimingSample s : samples) {
            if (!s.firedTransitions().isEmpty()) {
                double x = LABEL_COLUMN_WIDTH + ((s.timestampMs() / 1000.0) * pixelsPerSecond);

                // Ligne ambrée pointillée
                ctx.setStroke(Color.web("#F59E0B", 0.6)); // Ambre
                ctx.strokeLine(x, HEADER_HEIGHT, x, h);

                // Badge transition dans l'en-tête
                ctx.setFill(Color.web("#FEF3C7"));
                ctx.fillRect(x - 10, 2, 20, 14);
                ctx.setStroke(Color.web("#F59E0B"));
                ctx.strokeRect(x - 10, 2, 20, 14);

                ctx.setFill(Color.web("#92400E"));
                ctx.setFont(Font.font("Consolas", FontWeight.BOLD, 9));
                String label = s.firedTransitions().getFirst();
                ctx.fillText(label, x - 8, 12);
            }
        }
    }

    private void drawWaveform(Channel ch, List<TimingSample> samples, double yBase, double rowHeight) {
        double lowY = yBase + rowHeight - 6.0;
        double highY = yBase + 8.0;

        // Ligne de référence zéro (repos)
        ctx.setStroke(Color.web("#E2E8F0"));
        ctx.setLineWidth(0.8);
        ctx.strokeLine(LABEL_COLUMN_WIDTH, lowY, canvas.getWidth(), lowY);

        if (samples.isEmpty()) return;

        ctx.setStroke(ch.color());
        ctx.setLineWidth(2.0);

        if ("COUNTER".equals(ch.type())) {
            // Signal analogique en marche d'escalier
            int minVal = 0;
            int maxVal = 10;
            for (TimingSample s : samples) {
                maxVal = Math.max(maxVal, s.getCounterValue(ch.name()));
            }
            double valSpan = Math.max(1, maxVal - minVal);

            double prevX = LABEL_COLUMN_WIDTH + ((samples.getFirst().timestampMs() / 1000.0) * pixelsPerSecond);
            int prevVal = samples.getFirst().getCounterValue(ch.name());
            double prevY = lowY - ((prevVal - minVal) / valSpan) * (rowHeight - 14.0);

            for (int i = 1; i < samples.size(); i++) {
                TimingSample cur = samples.get(i);
                double curX = LABEL_COLUMN_WIDTH + ((cur.timestampMs() / 1000.0) * pixelsPerSecond);
                int curVal = cur.getCounterValue(ch.name());
                double curY = lowY - ((curVal - minVal) / valSpan) * (rowHeight - 14.0);

                ctx.strokeLine(prevX, prevY, curX, prevY);
                if (curVal != prevVal) {
                    ctx.strokeLine(curX, prevY, curX, curY);
                    // Libellé de la valeur
                    ctx.setFill(ch.color());
                    ctx.setFont(Font.font("Consolas", FontWeight.BOLD, 9));
                    ctx.fillText(String.valueOf(curVal), curX + 2, curY - 2);
                }
                prevX = curX;
                prevY = curY;
                prevVal = curVal;
            }
            return;
        }

        // Signaux digitaux (0 ou 1) : dessin rectangulaire avec remplissage translucide
        double prevX = LABEL_COLUMN_WIDTH + ((samples.getFirst().timestampMs() / 1000.0) * pixelsPerSecond);
        boolean prevVal = getChannelValue(ch, samples.getFirst());
        double prevY = prevVal ? highY : lowY;

        for (int i = 1; i < samples.size(); i++) {
            TimingSample cur = samples.get(i);
            double curX = LABEL_COLUMN_WIDTH + ((cur.timestampMs() / 1000.0) * pixelsPerSecond);
            boolean curVal = getChannelValue(ch, cur);
            double curY = curVal ? highY : lowY;

            // Remplissage translucide sous niveau haut
            if (prevVal) {
                ctx.setFill(Color.color(ch.color().getRed(), ch.color().getGreen(), ch.color().getBlue(), 0.18));
                ctx.fillRect(prevX, highY, curX - prevX, lowY - highY);
            }

            // Segment horizontal
            ctx.strokeLine(prevX, prevY, curX, prevY);

            // Front vertical si changement d'état (montant ou descendant)
            if (curVal != prevVal) {
                ctx.strokeLine(curX, prevY, curX, curY);
            }

            prevX = curX;
            prevY = curY;
            prevVal = curVal;
        }

        // Dernier remplissage jusqu'au bord si actif
        if (prevVal && samples.size() > 0) {
            double lastX = LABEL_COLUMN_WIDTH + (recorder.getTotalDurationMs() / 1000.0 * pixelsPerSecond);
            ctx.setFill(Color.color(ch.color().getRed(), ch.color().getGreen(), ch.color().getBlue(), 0.18));
            ctx.fillRect(prevX, highY, Math.max(0, lastX - prevX), lowY - highY);
        }
    }

    private boolean getChannelValue(Channel ch, TimingSample sample) {
        return switch (ch.type()) {
            case "STEP" -> sample.getStepState(ch.name());
            case "INPUT" -> sample.getInputState(ch.name());
            case "OUTPUT" -> sample.getOutputState(ch.name());
            default -> false;
        };
    }

    private void drawChannelsHeaderColumn(double h) {
        // Fond de la colonne d'en-tête des voies
        ctx.setFill(Color.web(AppTheme.PANEL_BG));
        ctx.fillRect(0, 0, LABEL_COLUMN_WIDTH, h);
        ctx.setStroke(Color.web(AppTheme.BORDER_COLOR));
        ctx.strokeLine(LABEL_COLUMN_WIDTH, 0, LABEL_COLUMN_WIDTH, h);

        // En-tête de la colonne
        ctx.setFill(Color.web(AppTheme.TEXT_MUTED));
        ctx.setFont(Font.font("System", FontWeight.BOLD, 10));
        ctx.fillText("SIGNAUX / VOIES", 10, 18);

        // Dessin des étiquettes de chaque canal
        double currentY = HEADER_HEIGHT;
        for (Channel ch : activeChannels) {
            // Séparateur horizontal
            ctx.setStroke(Color.web(AppTheme.BORDER_COLOR));
            ctx.strokeLine(0, currentY, LABEL_COLUMN_WIDTH, currentY);

            // Puce couleur du canal
            ctx.setFill(ch.color());
            ctx.fillOval(10, currentY + 10, 8, 8);

            // Nom du canal
            ctx.setFill(Color.web(AppTheme.TEXT_MAIN));
            ctx.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
            ctx.fillText(ch.name(), 24, currentY + 18);

            // Badge de type (%I, %Q, Step, INT)
            ctx.setFill(Color.web(AppTheme.TEXT_MUTED));
            ctx.setFont(Font.font("System", FontWeight.NORMAL, 9));
            String typeBadge = switch (ch.type()) {
                case "STEP" -> "Étape";
                case "INPUT" -> "%I";
                case "OUTPUT" -> "%Q";
                case "COUNTER" -> "Compteur";
                default -> "";
            };
            ctx.fillText(typeBadge, LABEL_COLUMN_WIDTH - 42, currentY + 18);

            currentY += CHANNEL_HEIGHT;
        }
    }

    private void drawCursors(double h) {
        double maxTimeSec = recorder.getTotalDurationMs() / 1000.0;

        // 1. Curseur principal rouge (Inspection & Time Travel)
        double activeCursorSec = (cursorTimeSec >= 0) ? cursorTimeSec : maxTimeSec;
        if (activeCursorSec >= 0) {
            double cx = LABEL_COLUMN_WIDTH + (activeCursorSec * pixelsPerSecond);

            ctx.setStroke(Color.web("#EF4444")); // Rouge vif
            ctx.setLineWidth(1.5);
            ctx.strokeLine(cx, 0, cx, h);

            // Tête de lecture (drapeau triangulaire au sommet)
            ctx.setFill(Color.web("#EF4444"));
            double[] xPoints = {cx - 6, cx + 6, cx};
            double[] yPoints = {0, 0, 10};
            ctx.fillPolygon(xPoints, yPoints, 3);
        }

        // 2. Curseur bleu de mesure (Δt)
        if (measureModeActive && measureCursorTimeSec >= 0) {
            double mx = LABEL_COLUMN_WIDTH + (measureCursorTimeSec * pixelsPerSecond);

            ctx.setStroke(Color.web("#3B82F6")); // Bleu mesure
            ctx.setLineWidth(1.5);
            ctx.strokeLine(mx, 0, mx, h);

            // Drapeau bleu
            ctx.setFill(Color.web("#3B82F6"));
            double[] xPoints = {mx - 6, mx + 6, mx};
            double[] yPoints = {0, 0, 10};
            ctx.fillPolygon(xPoints, yPoints, 3);

            // Flèche horizontale de mesure entre les deux curseurs
            if (activeCursorSec >= 0) {
                double leftX = Math.min(cx(activeCursorSec), mx);
                double rightX = Math.max(cx(activeCursorSec), mx);
                double midY = HEADER_HEIGHT / 2.0;

                ctx.setStroke(Color.web("#1E40AF"));
                ctx.setLineWidth(1.0);
                ctx.strokeLine(leftX, midY, rightX, midY);

                double dt = Math.abs(activeCursorSec - measureCursorTimeSec);
                String dtText = String.format(Locale.US, "Δt = %.2fs", dt);
                ctx.setFill(Color.web("#1E3A8A"));
                ctx.setFont(Font.font("Consolas", FontWeight.BOLD, 10));
                ctx.fillText(dtText, (leftX + rightX) / 2.0 - 24, midY - 3);
            }
        }
    }

    private double cx(double timeSec) {
        return LABEL_COLUMN_WIDTH + (timeSec * pixelsPerSecond);
    }

    private void updateInfoLabel() {
        if (cursorTimeSec < 0) {
            infoLabel.setText(String.format(Locale.US, "Direct : t = %.2fs (%d échantillons)",
                    recorder.getTotalDurationMs() / 1000.0, recorder.getSampleCount()));
            return;
        }

        recorder.findSampleAt((long) (cursorTimeSec * 1000)).ifPresent(sample -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "t = %.2fs", cursorTimeSec));

            if (measureModeActive && measureCursorTimeSec >= 0) {
                double dt = Math.abs(cursorTimeSec - measureCursorTimeSec);
                sb.append(String.format(Locale.US, " | Δt = %.2fs", dt));
            }

            // Étapes actives
            List<String> activeSteps = sample.stepStates().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .toList();
            if (!activeSteps.isEmpty()) {
                sb.append(" | Étape(s) : ").append(String.join(", ", activeSteps));
            }

            // Sorties actives
            List<String> activeOuts = sample.outputStates().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .toList();
            if (!activeOuts.isEmpty()) {
                sb.append(" | Sortie(s) : ").append(String.join(", ", activeOuts));
            }

            infoLabel.setText(sb.toString());
        });
    }

    private void onExportCsv() {
        if (recorder.getSampleCount() == 0) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Chronogramme");
            alert.setHeaderText("Aucune donnée enregistrée");
            alert.setContentText("Lancez la simulation pour enregistrer des données temporelles avant d'exporter.");
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter les relevés temporels (CSV)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier CSV (*.csv)", "*.csv"));
        fileChooser.setInitialFileName("chronogramme_grafcet.csv");

        File file = fileChooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(recorder.exportToCsv());
                infoLabel.setText("Relevés exportés avec succès : " + file.getName());
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Erreur d'exportation");
                alert.setHeaderText("Échec de l'exportation CSV");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        }
    }
}
