package tk.tahri.grafino.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Section repliable en accordéon pour les panneaux latéraux d'édition (TransitionProperty et StepProperty).
 * Offre un en-tête cliquable avec icône, titre, badge contextuel et chevron d'état (▼ / ▶).
 */
public class CollapsibleSection extends VBox {

    private final HBox header;
    private final Label titleLabel;
    private final Label badgeLabel;
    private final Label chevron;
    private final VBox contentBox;
    private boolean expanded;

    public CollapsibleSection(String icon, String title, Node contentNode, boolean initiallyExpanded) {
        this(icon, title, null, contentNode, initiallyExpanded);
    }

    public CollapsibleSection(String icon, String title, String badgeText, Node contentNode, boolean initiallyExpanded) {
        this.expanded = initiallyExpanded;
        this.setStyle(AppTheme.CARD_STYLE);
        this.setSpacing(8);

        // En-tête interactif
        header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("collapsible-header");

        if (icon != null && !icon.isBlank()) {
            var iconLbl = new Label(icon);
            iconLbl.getStyleClass().add("collapsible-icon");
            header.getChildren().add(iconLbl);
        }

        titleLabel = new Label(title);
        titleLabel.setStyle(AppTheme.SECTION_HEADER_STYLE);
        header.getChildren().add(titleLabel);

        badgeLabel = new Label();
        if (badgeText != null && !badgeText.isBlank()) {
            badgeLabel.setText(badgeText);
            badgeLabel.setStyle(AppTheme.pillBadgeStyle(AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK) +
                    "; -fx-font-size: 11.5px; -fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + "; -fx-border-radius: 10px;");
            header.getChildren().add(badgeLabel);
        }

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().add(spacer);

        chevron = new Label(initiallyExpanded ? "▼" : "▶");
        chevron.getStyleClass().add("collapsible-chevron");
        header.getChildren().add(chevron);

        // Clic sur l'en-tête pour déplier / replier
        header.setOnMouseClicked(_ -> setExpanded(!expanded));

        // Boîte de contenu
        contentBox = new VBox(8);
        if (contentNode != null) {
            contentBox.getChildren().add(contentNode);
        }
        contentBox.setVisible(initiallyExpanded);
        contentBox.setManaged(initiallyExpanded);

        this.getChildren().addAll(header, contentBox);
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        contentBox.setVisible(expanded);
        contentBox.setManaged(expanded);
        chevron.setText(expanded ? "▼" : "▶");
    }

    public boolean isExpanded() {
        return expanded;
    }

    public VBox getContentBox() {
        return contentBox;
    }

    public void setBadge(String text) {
        if (text != null && !text.isBlank()) {
            badgeLabel.setText(text);
            badgeLabel.setStyle(AppTheme.pillBadgeStyle(AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK) +
                    "; -fx-font-size: 11.5px; -fx-border-color: " + AppTheme.TEAL_LIGHT_BORDER + "; -fx-border-radius: 10px;");
            if (!header.getChildren().contains(badgeLabel)) {
                // Insérer juste après titleLabel
                int titleIndex = header.getChildren().indexOf(titleLabel);
                header.getChildren().add(titleIndex + 1, badgeLabel);
            }
            badgeLabel.setVisible(true);
            badgeLabel.setManaged(true);
        } else {
            badgeLabel.setText("");
            badgeLabel.setVisible(false);
            badgeLabel.setManaged(false);
        }
    }
}
