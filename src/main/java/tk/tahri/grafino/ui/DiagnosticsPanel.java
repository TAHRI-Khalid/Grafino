package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tk.tahri.grafino.model.SfcValidationMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Panneau d'inspection et de diagnostics normatifs en temps réel (Axe 2 : CEI 60848 & CEI 61131-3).
 * Affiche la liste des règles vérifiées, alertes didactiques et erreurs de modélisation avec
 * possibilité de localisation immédiate sur le Canvas.
 */
public class DiagnosticsPanel extends VBox {

    private final VBox listContainer = new VBox(8);
    private final Label summaryBadge = new Label("0 Erreur");
    private final Consumer<String> onSelectElement;
    private final Runnable onClose;

    public DiagnosticsPanel(Consumer<String> onSelectElement, Runnable onClose) {
        this.onSelectElement = onSelectElement;
        this.onClose = onClose;

        this.setPrefWidth(380);
        this.setMinWidth(340);
        this.setSpacing(10);
        this.setPadding(new Insets(12));
        this.setStyle(AppTheme.SIDEBAR_STYLE);

        // 1. En-tête fixe
        setupHeader();

        // 2. Contenu défilant
        ScrollPane scrollPane = new ScrollPane(listContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().add(scrollPane);
    }

    private void setupHeader() {
        var header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        var titleBox = new VBox(2);
        var subTitle = new Label("INSPECTION NORMATIVE & LINTING");
        subTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");

        summaryBadge.setStyle(AppTheme.pillBadgeStyle(AppTheme.GREEN_LIGHT_BG, AppTheme.GREEN_SUCCESS) +
                "; -fx-border-color: " + AppTheme.GREEN_BORDER + "; -fx-border-radius: 12px; -fx-font-size: 12px;");

        titleBox.getChildren().addAll(subTitle, summaryBadge);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var closeBtn = new Button("✕");
        closeBtn.setTooltip(new Tooltip("Fermer le panneau de diagnostic"));
        closeBtn.setStyle(AppTheme.CLOSE_BUTTON_STYLE);
        closeBtn.setOnAction(e -> {
            if (onClose != null) onClose.run();
        });

        header.getChildren().addAll(titleBox, spacer, closeBtn);
        this.getChildren().add(header);
    }

    /**
     * Met à jour en direct la liste des messages de validation.
     */
    public void setMessages(List<SfcValidationMessage> messages) {
        listContainer.getChildren().clear();

        if (messages == null || messages.isEmpty()) {
            summaryBadge.setText("✓ 100% Conforme CEI 61131-3");
            summaryBadge.setStyle(AppTheme.pillBadgeStyle(AppTheme.GREEN_LIGHT_BG, AppTheme.GREEN_SUCCESS) +
                    "; -fx-border-color: " + AppTheme.GREEN_BORDER + "; -fx-border-radius: 12px; -fx-font-size: 12px;");

            var okCard = new VBox(8);
            okCard.setStyle(AppTheme.CARD_STYLE);
            okCard.setAlignment(Pos.CENTER_LEFT);

            var okTitle = new Label("✓ Aucune anomalie détectée");
            okTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: " + AppTheme.GREEN_SUCCESS + "; -fx-font-size: 13px;");

            var okDesc = new Label("Toutes les règles fondamentales sont respectées :\n" +
                    " • Étape initiale unique présente\n" +
                    " • Alternance stricte Étape-Transition\n" +
                    " • Temporisations t/Xi/d associées à des étapes valides\n" +
                    " • Qualificateurs de compteurs (P) conformes");
            okDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MUTED + "; -fx-line-spacing: 2px;");

            okCard.getChildren().addAll(okTitle, okDesc);
            listContainer.getChildren().add(okCard);
            return;
        }

        long errorCount = messages.stream().filter(SfcValidationMessage::isError).count();
        long warnCount = messages.stream().filter(SfcValidationMessage::isWarning).count();

        if (errorCount > 0) {
            summaryBadge.setText(errorCount + " Erreur(s) · " + warnCount + " Avertissement(s)");
            summaryBadge.setStyle(AppTheme.pillBadgeStyle(AppTheme.RED_LIGHT_BG, AppTheme.RED_DANGER) +
                    "; -fx-border-color: " + AppTheme.RED_BORDER + "; -fx-border-radius: 12px; -fx-font-size: 12px;");
        } else {
            summaryBadge.setText(warnCount + " Avertissement(s)");
            summaryBadge.setStyle(AppTheme.pillBadgeStyle(AppTheme.ORANGE_LIGHT_BG, AppTheme.ORANGE) +
                    "; -fx-border-color: " + AppTheme.ORANGE_BORDER + "; -fx-border-radius: 12px; -fx-font-size: 12px;");
        }

        for (SfcValidationMessage msg : messages) {
            listContainer.getChildren().add(createMessageCard(msg));
        }
    }

    private VBox createMessageCard(SfcValidationMessage msg) {
        var card = new VBox(6);
        card.setStyle(AppTheme.CARD_STYLE);

        var topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label levelBadge = new Label(msg.isError() ? "✕ ERREUR" : "⚠ AVERTISSEMENT");
        if (msg.isError()) {
            levelBadge.setStyle(AppTheme.pillBadgeStyle(AppTheme.RED_LIGHT_BG, AppTheme.RED_DANGER) + "; -fx-font-size: 10px;");
        } else {
            levelBadge.setStyle(AppTheme.pillBadgeStyle(AppTheme.ORANGE_LIGHT_BG, AppTheme.ORANGE) + "; -fx-font-size: 10px;");
        }

        topRow.getChildren().add(levelBadge);

        if (msg.elementId() != null && !msg.elementId().isBlank()) {
            Label elemBadge = new Label(formatElementRef(msg.elementId()));
            elemBadge.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 10px; -fx-text-fill: " + AppTheme.TEXT_MUTED + ";");
            topRow.getChildren().add(elemBadge);
        }

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topRow.getChildren().add(spacer);

        if (msg.elementId() != null && !msg.elementId().isBlank()) {
            Button inspectBtn = new Button("🔍 Voir");
            inspectBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + AppTheme.PRIMARY_TEAL + "; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 1 4 1 4;");
            inspectBtn.setTooltip(new Tooltip("Localiser et sélectionner cet élément sur le Canvas"));
            inspectBtn.setOnAction(e -> {
                if (onSelectElement != null) {
                    onSelectElement.accept(msg.elementId());
                }
            });
            topRow.getChildren().add(inspectBtn);
        }

        Label desc = new Label(msg.message());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: " + AppTheme.TEXT_MAIN + "; -fx-line-spacing: 1.5px;");

        card.getChildren().addAll(topRow, desc);
        return card;
    }

    private String formatElementRef(String elementId) {
        if (elementId == null) return "";
        String lower = elementId.toLowerCase();
        if (lower.startsWith("s")) return "Étape " + elementId;
        if (lower.startsWith("t")) return "Transition " + elementId;
        if (lower.startsWith("l")) return "Liaison " + elementId;
        return elementId;
    }
}
