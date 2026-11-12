package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import tk.tahri.grafino.component.Link;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.StepAndTransitionCommonBehavior;
import tk.tahri.grafino.component.Transition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Barre d'outils verticale fixe ancrée sur le côté gauche du Canvas Grafcet (style Figma / Miro / TIA Portal).
 * Affiche uniquement des boutons icônes empilés verticalement, reste toujours à une position fixe à gauche
 * sans gêner le schéma, et adapte ses actions au composant sélectionné (Étape, Transition, Multi-sélection, Liaison).
 */
public class ActionBar extends VBox {

    public enum GhostPreviewType {
        NONE,
        NEXT_SINGLE,
        NEXT_PAIR,
        BRANCH,
        CONVERGE,
        LOOP_BACK,
        INSERT_ON_LINK
    }

    private static final double ICON_BTN_SIZE = 44.0;

    private static final String BAR_STYLE =
            "-fx-background-color: rgba(255, 255, 255, 0.98);" +
            "-fx-border-color: #D7DEE4;" +
            "-fx-border-width: 1.5px;" +
            "-fx-background-radius: 16px;" +
            "-fx-border-radius: 16px;" +
            "-fx-padding: 10px 8px;";

    private static final DropShadow BAR_SHADOW = new DropShadow(14, 0, 4, Color.rgb(15, 23, 42, 0.14));

    private final Grafcet grafcet;
    private Consumer<GhostPreviewType> onHoverPreview = type -> {};

    public ActionBar(Grafcet grafcet) {
        super(6);
        this.grafcet = grafcet;
        this.setAlignment(Pos.TOP_CENTER);
        this.setStyle(BAR_STYLE);
        this.setEffect(BAR_SHADOW);
        this.setManaged(false);
        this.setVisible(true);
        this.setFocusTraversable(false);
    }

    public void setOnHoverPreview(Consumer<GhostPreviewType> onHoverPreview) {
        this.onHoverPreview = (onHoverPreview != null) ? onHoverPreview : (type -> {});
    }

    /**
     * Reconstruit la colonne verticale d'icônes selon la sélection active du Grafcet.
     */
    public void refreshForSelection(List<StepAndTransitionCommonBehavior> selectedComponents, Link selectedLink) {
        onHoverPreview.accept(GhostPreviewType.NONE);
        this.getChildren().clear();

        if (selectedComponents != null && !selectedComponents.isEmpty()) {
            if (selectedComponents.size() == 1) {
                StepAndTransitionCommonBehavior comp = selectedComponents.getFirst();
                if (comp instanceof Step step) {
                    buildSingleStepActions(step);
                } else if (comp instanceof Transition transition) {
                    buildSingleTransitionActions(transition);
                }
            } else {
                buildMultiSelectionActions(selectedComponents);
            }
        } else if (selectedLink != null) {
            buildLinkActions(selectedLink);
        } else {
            buildNoSelectionActions();
        }

        this.autosize();
        this.setVisible(true);
    }

    private void buildSingleStepActions(Step step) {
        this.getChildren().add(createContextBadge("S" + step.getNumber(), "#FEF3E2", "#D97706"));

        boolean hasDownwardChild = !grafcet.getDownwardLinks(step).isEmpty();

        // 1. + Transition en séquence linéaire (T / Entrée)
        Button addTransBtn = createIconButton(
                "＋",
                hasDownwardChild
                        ? "Une transition aval existe déjà sous S" + step.getNumber() + " — utilisez ⑂ pour créer une branche en OU"
                        : "Insérer la transition suivante sous S" + step.getNumber() + " (T ou Entrée)",
                AppTheme.PRIMARY_TEAL, "#FFFFFF", AppTheme.PRIMARY_TEAL_DARK,
                hasDownwardChild ? GhostPreviewType.NONE : GhostPreviewType.NEXT_SINGLE,
                () -> grafcet.createTransition(step)
        );
        addTransBtn.setDisable(hasDownwardChild);

        // 2. + Séquence complète (Transition + Étape)
        Button addSeqBtn = createIconButton(
                "⚡",
                hasDownwardChild
                        ? "Une transition aval existe déjà sous S" + step.getNumber() + " — utilisez ⑂ pour ajouter une branche en OU"
                        : "Insérer directement un couple Transition + Étape (Shift + Entrée)",
                AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK, "#B2DFDB",
                hasDownwardChild ? GhostPreviewType.NONE : GhostPreviewType.NEXT_PAIR,
                () -> grafcet.createTransitionAndStepPair(step)
        );
        addSeqBtn.setDisable(hasDownwardChild);

        // 3. Divergence en OU (Branche alternative CEI 60848)
        Button branchOrBtn = createIconButton(
                "⑂",
                hasDownwardChild
                        ? "Ajouter une branche alternative supplémentaire à la divergence en OU sous S" + step.getNumber() + " (B)"
                        : "Créer une divergence en OU (2 branches d'aiguillage de séquence) sous S" + step.getNumber() + " (B)",
                "#E0F2FE", "#0369A1", "#BAE6FD",
                GhostPreviewType.BRANCH,
                () -> grafcet.createOrBranch(step)
        );

        // 4. Convergence en ET (uniquement avec des étapes issues de branches parallèles compatibles CEI 60848)
        List<Step> candidateSteps = grafcet.findConvergenceCandidateSteps(step);
        Button convAndBtn = createIconButton(
                "⫘",
                candidateSteps.isEmpty()
                        ? "Convergence en ET (nécessite au moins une autre étape parallèle ouverte hors aiguillage OU)"
                        : "Converger en ET : synchroniser S" + step.getNumber() + " avec les autres étapes parallèles ouvertes (C)",
                "#EDE9FE", "#6D28D9", "#DDD6FE",
                candidateSteps.isEmpty() ? GhostPreviewType.NONE : GhostPreviewType.CONVERGE,
                () -> grafcet.convergeWithOpenSiblings(step)
        );
        convAndBtn.setDisable(candidateSteps.isEmpty());

        // 5. Statut Étape Initiale (I)
        boolean isInit = step.isInitial();
        Button initToggleBtn = createIconButton(
                isInit ? "▣" : "▢",
                "Basculer le statut d'étape initiale CEI 60848 (double carré) (I)",
                isInit ? AppTheme.GREEN_LIGHT_BG : "#F1F5F9",
                isInit ? AppTheme.GREEN_SUCCESS : AppTheme.TEXT_MUTED,
                isInit ? AppTheme.GREEN_BORDER : AppTheme.BORDER_COLOR,
                GhostPreviewType.NONE,
                () -> grafcet.toggleInitialStep(step)
        );

        // 6. Supprimer
        Button deleteBtn = createDangerIconButton(
                "🗑",
                "Supprimer l'étape S" + step.getNumber() + " (Suppr)",
                grafcet::deleteSelected
        );

        this.getChildren().addAll(
                addTransBtn, addSeqBtn, branchOrBtn, convAndBtn, initToggleBtn,
                createHorizontalSeparator(),
                deleteBtn
        );
    }

    private void buildSingleTransitionActions(Transition transition) {
        this.getChildren().add(createContextBadge("T" + transition.getNumber(), "#FEF3E2", "#D97706"));

        boolean hasDownwardChild = !grafcet.getDownwardLinks(transition).isEmpty();

        // 1. + Étape en séquence linéaire (S / Entrée)
        Button addStepBtn = createIconButton(
                "＋",
                hasDownwardChild
                        ? "Une étape aval existe déjà sous T" + transition.getNumber() + " — utilisez ⇉ pour créer une branche en ET"
                        : "Insérer l'étape suivante sous T" + transition.getNumber() + " (S ou Entrée)",
                AppTheme.PRIMARY_TEAL, "#FFFFFF", AppTheme.PRIMARY_TEAL_DARK,
                hasDownwardChild ? GhostPreviewType.NONE : GhostPreviewType.NEXT_SINGLE,
                () -> grafcet.createStep(transition)
        );
        addStepBtn.setDisable(hasDownwardChild);

        // 2. + Séquence complète (Étape + Transition)
        Button addSeqBtn = createIconButton(
                "⚡",
                hasDownwardChild
                        ? "Une étape aval existe déjà sous T" + transition.getNumber() + " — utilisez ⇉ pour ajouter une branche en ET"
                        : "Insérer directement un couple Étape + Transition (Shift + Entrée)",
                AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK, "#B2DFDB",
                hasDownwardChild ? GhostPreviewType.NONE : GhostPreviewType.NEXT_PAIR,
                () -> grafcet.createStepAndTransitionPair(transition)
        );
        addSeqBtn.setDisable(hasDownwardChild);

        // 3. Divergence en ET (Branches parallèles CEI 60848)
        Button branchAndBtn = createIconButton(
                "⇉",
                hasDownwardChild
                        ? "Ajouter une branche parallèle supplémentaire à la divergence en ET sous T" + transition.getNumber() + " (B)"
                        : "Créer une divergence en ET (2 branches parallèles simultanées) sous T" + transition.getNumber() + " (B)",
                "#E0F2FE", "#0369A1", "#BAE6FD",
                GhostPreviewType.BRANCH,
                () -> grafcet.createAndBranch(transition)
        );

        // 4. Convergence en OU (uniquement avec des transitions alternatives compatibles CEI 60848)
        List<Transition> candidateTransitions = grafcet.findConvergenceCandidateTransitions(transition);
        Button convOrBtn = createIconButton(
                "⫘",
                candidateTransitions.isEmpty()
                        ? "Convergence en OU (nécessite au moins une autre transition alternative ouverte hors parallélisme ET)"
                        : "Converger en OU : regrouper T" + transition.getNumber() + " avec les autres transitions alternatives (C)",
                "#EDE9FE", "#6D28D9", "#DDD6FE",
                candidateTransitions.isEmpty() ? GhostPreviewType.NONE : GhostPreviewType.CONVERGE,
                () -> grafcet.convergeWithOpenSiblings(transition)
        );
        convOrBtn.setDisable(candidateTransitions.isEmpty());

        // 5. Boucle ascendante vers l'étape initiale S0
        Step targetInit = grafcet.findTargetInitialStep();
        boolean canLoop = targetInit != null && targetInit.getY() + 10 < transition.getY();
        Button loopBtn = createIconButton(
                "⮐",
                canLoop
                        ? "Fermer le cycle par une boucle ascendante vers S" + targetInit.getNumber() + " (R)"
                        : "Boucle ascendante vers l'étape initiale (la transition doit être située sous l'étape initiale)",
                "#D1FAE5", "#047857", "#A7F3D0",
                canLoop ? GhostPreviewType.LOOP_BACK : GhostPreviewType.NONE,
                () -> grafcet.loopBackToInitialStep(transition)
        );
        loopBtn.setDisable(!canLoop);

        // 6. Supprimer
        Button deleteBtn = createDangerIconButton(
                "🗑",
                "Supprimer la transition T" + transition.getNumber() + " (Suppr)",
                grafcet::deleteSelected
        );

        this.getChildren().addAll(
                addStepBtn, addSeqBtn, branchAndBtn, convOrBtn, loopBtn,
                createHorizontalSeparator(),
                deleteBtn
        );
    }

    private void buildMultiSelectionActions(List<StepAndTransitionCommonBehavior> selected) {
        int count = selected.size();
        boolean allSteps = selected.stream().allMatch(Step.class::isInstance);
        boolean allTransitions = selected.stream().allMatch(Transition.class::isInstance);

        this.getChildren().add(createContextBadge(count + "×", AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK));

        if (allSteps) {
            List<Step> steps = selected.stream().map(Step.class::cast).toList();
            boolean validAndConv = !grafcet.wouldCauseOrToAndDeadlock(steps);
            Button convAndBtn = createIconButton(
                    "⫘",
                    validAndConv
                            ? "Converger en ET ces " + count + " étapes vers une transition commune (C)"
                            : "Convergence en ET interdite (CEI 60848) : ces étapes sont issues d'un aiguillage en OU (Deadlock)",
                    AppTheme.PRIMARY_TEAL, "#FFFFFF", AppTheme.PRIMARY_TEAL_DARK,
                    validAndConv ? GhostPreviewType.CONVERGE : GhostPreviewType.NONE,
                    () -> grafcet.createAndConvergence(steps)
            );
            convAndBtn.setDisable(!validAndConv);
            this.getChildren().add(convAndBtn);
        } else if (allTransitions) {
            List<Transition> transitions = selected.stream().map(Transition.class::cast).toList();
            boolean validOrConv = !grafcet.wouldCauseAndToOrUnsyncedConvergence(transitions);
            Button convOrBtn = createIconButton(
                    "⫘",
                    validOrConv
                            ? "Converger en OU ces " + count + " transitions vers une étape commune (C)"
                            : "Convergence en OU déconseillée (CEI 60848) : ces transitions sont issues de branches parallèles en ET non synchronisées",
                    AppTheme.PRIMARY_TEAL, "#FFFFFF", AppTheme.PRIMARY_TEAL_DARK,
                    validOrConv ? GhostPreviewType.CONVERGE : GhostPreviewType.NONE,
                    () -> grafcet.createOrConvergence(transitions)
            );
            convOrBtn.setDisable(!validOrConv);
            this.getChildren().add(convOrBtn);

            Step targetInit = grafcet.findTargetInitialStep();
            boolean canLoopAny = targetInit != null && transitions.stream().anyMatch(t -> targetInit.getY() + 10 < t.getY());
            if (targetInit != null) {
                Button loopAllBtn = createIconButton(
                        "⮐",
                        canLoopAny
                                ? "Reboucler les transitions sélectionnées vers S" + targetInit.getNumber() + " (R)"
                                : "Aucune transition sélectionnée n'est située sous S" + targetInit.getNumber(),
                        "#D1FAE5", "#047857", "#A7F3D0",
                        canLoopAny ? GhostPreviewType.LOOP_BACK : GhostPreviewType.NONE,
                        () -> transitions.stream()
                                .filter(t -> targetInit.getY() + 10 < t.getY())
                                .forEach(grafcet::loopBackToInitialStep)
                );
                loopAllBtn.setDisable(!canLoopAny);
                this.getChildren().add(loopAllBtn);
            }
        }

        Button alignYBtn = createIconButton(
                "📐",
                "Aligner horizontalement les " + count + " éléments sélectionnés sur le même axe Y",
                "#F8FAFC", AppTheme.TEXT_MAIN, AppTheme.BORDER_COLOR,
                GhostPreviewType.NONE,
                grafcet::alignSelectedHorizontally
        );

        Button deleteBtn = createDangerIconButton(
                "🗑",
                "Supprimer les " + count + " éléments sélectionnés (Suppr)",
                grafcet::deleteSelected
        );

        this.getChildren().addAll(alignYBtn, createHorizontalSeparator(), deleteBtn);
    }

    private void buildLinkActions(Link link) {
        this.getChildren().add(createContextBadge("🔗", "#F1F5F9", AppTheme.TEXT_MUTED));

        Button insertMidBtn = createIconButton(
                "➕",
                "Ouvrir la liaison et insérer un couple Étape + Transition (Entrée)",
                AppTheme.PRIMARY_TEAL, "#FFFFFF", AppTheme.PRIMARY_TEAL_DARK,
                GhostPreviewType.INSERT_ON_LINK,
                () -> grafcet.insertPairOnLink(link)
        );

        Button deleteLinkBtn = createDangerIconButton(
                "🗑",
                "Supprimer cette liaison (Suppr)",
                grafcet::deleteSelected
        );

        this.getChildren().addAll(insertMidBtn, createHorizontalSeparator(), deleteLinkBtn);
    }

    private void buildNoSelectionActions() {
        this.getChildren().add(createContextBadge("SFC", AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK));

        // 1. Ajouter l'élément suivant à la fin de la chaîne active (ou créer l'étape initiale si vide)
        Button quickAddBtn = createIconButton(
                "＋",
                "Ajouter l'élément suivant en fin de Grafcet (ou créer l'étape initiale)",
                AppTheme.PRIMARY_TEAL, "#FFFFFF", AppTheme.PRIMARY_TEAL_DARK,
                GhostPreviewType.NONE,
                this::appendNextToLastLeaf
        );

        // 2. Ajouter une séquence complète en fin de chaîne
        Button quickSeqBtn = createIconButton(
                "⚡",
                "Insérer un couple complet Étape + Transition en fin de Grafcet",
                AppTheme.TEAL_LIGHT_BG, AppTheme.PRIMARY_TEAL_DARK, "#B2DFDB",
                GhostPreviewType.NONE,
                this::appendPairToLastLeaf
        );

        this.getChildren().addAll(quickAddBtn, quickSeqBtn);
    }

    private void appendNextToLastLeaf() {
        if (grafcet.getComponents().isEmpty()) {
            grafcet.createInitialStep();
            return;
        }
        var leaf = findLowestLeafComponent();
        if (leaf instanceof Step step) {
            grafcet.createTransition(step);
        } else if (leaf instanceof Transition trans) {
            grafcet.createStep(trans);
        }
    }

    private void appendPairToLastLeaf() {
        if (grafcet.getComponents().isEmpty()) {
            grafcet.createInitialStep();
        }
        var leaf = findLowestLeafComponent();
        if (leaf instanceof Step step) {
            grafcet.createTransitionAndStepPair(step);
        } else if (leaf instanceof Transition trans) {
            grafcet.createStepAndTransitionPair(trans);
        }
    }

    private StepAndTransitionCommonBehavior findLowestLeafComponent() {
        return grafcet.getComponents().stream()
                .filter(c -> grafcet.getDownwardLinks(c).isEmpty())
                .max(Comparator.comparingDouble(StepAndTransitionCommonBehavior::getY))
                .orElseGet(() -> grafcet.getComponents().getLast());
    }

    /**
     * Retourne la liste à plat de tous les boutons d'action présents dans la barre verticale.
     */
    public List<Node> getAllActionButtons() {
        List<Node> all = new ArrayList<>();
        for (Node child : this.getChildren()) {
            if (child instanceof Button) {
                all.add(child);
            }
        }
        return all;
    }

    private Label createContextBadge(String text, String bgHex, String textHex) {
        Label badge = new Label(text);
        badge.setAlignment(Pos.CENTER);
        badge.setMinWidth(ICON_BTN_SIZE);
        badge.setMaxWidth(ICON_BTN_SIZE);
        badge.setStyle(
                "-fx-background-color: " + bgHex + ";" +
                "-fx-text-fill: " + textHex + ";" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13.5px;" +
                "-fx-padding: 4px 2px;" +
                "-fx-background-radius: 9px;"
        );
        return badge;
    }

    private Region createHorizontalSeparator() {
        Separator sep = new Separator(Orientation.HORIZONTAL);
        sep.setMaxWidth(ICON_BTN_SIZE - 6);
        sep.setPadding(new Insets(2, 0, 2, 0));
        return sep;
    }

    private Button createIconButton(String iconSymbol, String tooltipText,
                                    String bgHex, String textHex, String borderHex,
                                    GhostPreviewType previewType, Runnable action) {
        Button btn = new Button(iconSymbol);
        btn.setFocusTraversable(false);
        btn.setMinSize(ICON_BTN_SIZE, ICON_BTN_SIZE);
        btn.setPrefSize(ICON_BTN_SIZE, ICON_BTN_SIZE);
        btn.setMaxSize(ICON_BTN_SIZE, ICON_BTN_SIZE);

        String normalStyle =
                "-fx-background-color: " + bgHex + ";" +
                "-fx-text-fill: " + textHex + ";" +
                "-fx-border-color: " + borderHex + ";" +
                "-fx-border-width: 1.2px;" +
                "-fx-background-radius: 11px;" +
                "-fx-border-radius: 11px;" +
                "-fx-font-size: 21px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 0;" +
                "-fx-cursor: hand;";

        String hoverStyle =
                "-fx-background-color: " + borderHex + ";" +
                "-fx-text-fill: " + (bgHex.equals(AppTheme.PRIMARY_TEAL) ? "#FFFFFF" : textHex) + ";" +
                "-fx-border-color: " + textHex + ";" +
                "-fx-border-width: 1.4px;" +
                "-fx-background-radius: 11px;" +
                "-fx-border-radius: 11px;" +
                "-fx-font-size: 21px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 0;" +
                "-fx-cursor: hand;";

        btn.setStyle(normalStyle);
        installFastTooltip(btn, tooltipText);

        btn.setOnMouseEntered(e -> {
            btn.setStyle(hoverStyle);
            onHoverPreview.accept(previewType);
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle(normalStyle);
            onHoverPreview.accept(GhostPreviewType.NONE);
        });
        btn.setOnAction(e -> {
            onHoverPreview.accept(GhostPreviewType.NONE);
            action.run();
            grafcet.requestFocus();
            e.consume();
        });
        return btn;
    }

    private Button createDangerIconButton(String iconSymbol, String tooltipText, Runnable action) {
        return createIconButton(
                iconSymbol,
                tooltipText,
                AppTheme.RED_LIGHT_BG, AppTheme.RED_DANGER, AppTheme.RED_BORDER,
                GhostPreviewType.NONE,
                action
        );
    }

    private void installFastTooltip(Button btn, String text) {
        Tooltip tt = new Tooltip(text);
        tt.setShowDelay(Duration.millis(120));
        btn.setTooltip(tt);
    }
}
