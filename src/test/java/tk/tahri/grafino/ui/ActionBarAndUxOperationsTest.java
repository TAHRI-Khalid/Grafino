package tk.tahri.grafino.ui;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tk.tahri.grafino.JavaFxTestHelper;
import tk.tahri.grafino.component.Link;
import tk.tahri.grafino.component.Step;
import tk.tahri.grafino.component.Transition;
import tk.tahri.grafino.model.SfcProgram;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests de la Barre d'Action Contextuelle (ActionBar) et des Opérations UX Modernes")
class ActionBarAndUxOperationsTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestHelper.initJavaFx();
    }

    @BeforeEach
    void resetCounters() {
        Step.resetCounter(0);
        Transition.resetCounter(0);
    }

    @Test
    @DisplayName("Insertion combinée Couple Transition + Étape en 1 clic (Shift+Entrée / +Séquence sur Étape)")
    void testCreateTransitionAndStepPair() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();

        Step s2 = grafcet.createTransitionAndStepPair(s1);

        assertNotNull(s2);
        assertEquals(3, grafcet.getComponents().size(), "Le schéma doit contenir S1, T1 et S2");
        assertEquals(1, s1.getLinksToChildren().size());

        var t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();
        assertEquals(1, t1.getLinksToChildren().size());
        assertSame(s2, t1.getLinksToChildren().getFirst().getChildComponent());
        assertTrue(s2.isSelected(), "La nouvelle étape finale du couple doit être sélectionnée");
    }

    @Test
    @DisplayName("Insertion combinée Couple Étape + Transition en 1 clic (Shift+Entrée / +Séquence sur Transition)")
    void testCreateStepAndTransitionPair() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();
        grafcet.createTransition(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();

        Transition t2 = grafcet.createStepAndTransitionPair(t1);

        assertNotNull(t2);
        assertEquals(4, grafcet.getComponents().size(), "Le schéma doit contenir S1, T1, S2 et T2");
        var s2 = (Step) t1.getLinksToChildren().getFirst().getChildComponent();
        assertSame(t2, s2.getLinksToChildren().getFirst().getChildComponent());
        assertTrue(t2.isSelected(), "La nouvelle transition finale du couple doit être sélectionnée");
    }

    @Test
    @DisplayName("Convergence en ET en 1 clic à partir de plusieurs étapes parallèles")
    void testCreateAndConvergence() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();
        grafcet.createTransition(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();

        // Créer 2 branches parallèles en ET sous T1 : S2 et S3
        grafcet.createStep(t1);
        Step s2 = (Step) t1.getLinksToChildren().getFirst().getChildComponent();
        grafcet.createAndBranch(t1);
        Step s3 = (Step) t1.getLinksToChildren().get(1).getChildComponent();

        // Converger S2 et S3 en ET vers une transition commune T2
        Transition tCommon = grafcet.createAndConvergence(List.of(s2, s3));

        assertNotNull(tCommon);
        assertEquals(2, tCommon.getLinksFromParents().size(), "La transition commune doit recevoir les 2 branches d'étapes");
        assertTrue(tCommon.getLinksFromParents().get(0).isConvergence(), "Le lien doit être marqué en convergence ET");
        assertTrue(tCommon.getLinksFromParents().get(1).isConvergence(), "Le second lien doit être marqué en convergence ET");
    }

    @Test
    @DisplayName("Convergence en OU en 1 clic à partir de plusieurs transitions alternatives")
    void testCreateOrConvergence() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();

        // Créer 2 branches alternatives en OU sous S1 : T1 et T2
        grafcet.createTransition(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();
        grafcet.createOrBranch(s1);
        Transition t2 = (Transition) s1.getLinksToChildren().get(1).getChildComponent();

        // Converger T1 et T2 en OU vers une étape commune S2
        Step sCommon = grafcet.createOrConvergence(List.of(t1, t2));

        assertNotNull(sCommon);
        assertEquals(2, sCommon.getLinksFromParents().size(), "L'étape commune doit recevoir les 2 transitions");
        assertTrue(sCommon.getLinksFromParents().get(0).isConvergence(), "Le lien doit être marqué en convergence OU");
        assertTrue(sCommon.getLinksFromParents().get(1).isConvergence(), "Le second lien doit être marqué en convergence OU");
    }

    @Test
    @DisplayName("Convergence automatique avec les branches sœurs ouvertes (convergeWithOpenSiblings)")
    void testConvergeWithOpenSiblings() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();
        grafcet.createTransition(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();

        grafcet.createStep(t1);
        Step s2 = (Step) t1.getLinksToChildren().getFirst().getChildComponent();
        grafcet.createAndBranch(t1);
        Step s3 = (Step) t1.getLinksToChildren().get(1).getChildComponent();

        // Depuis S3 uniquement sélectionnée, converger automatiquement avec S2
        List<Step> candidates = grafcet.findConvergenceCandidateSteps(s3);
        assertEquals(1, candidates.size());
        assertSame(s2, candidates.getFirst());

        grafcet.convergeWithOpenSiblings(s3);
        assertEquals(1, s2.getLinksToChildren().size());
        assertEquals(1, s3.getLinksToChildren().size());
        assertSame(s2.getLinksToChildren().getFirst().getChildComponent(),
                   s3.getLinksToChildren().getFirst().getChildComponent(),
                   "S2 et S3 doivent converger vers la même transition");
    }

    @Test
    @DisplayName("Boucle de reprise vers l'étape initiale S0 en 1 clic (loopBackToInitialStep)")
    void testLoopBackToInitialStep() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();
        Step s2 = grafcet.createTransitionAndStepPair(s1);
        grafcet.createTransition(s2);
        Transition t2 = (Transition) s2.getLinksToChildren().getFirst().getChildComponent();

        Link loopLink = grafcet.loopBackToInitialStep(t2);

        assertNotNull(loopLink);
        assertSame(t2, loopLink.getParentComponent());
        assertSame(s1, loopLink.getChildComponent());
        assertTrue(loopLink.isAscending(), "Le lien de rebouclage vers l'étape initiale doit être ascendant");

        // Vérifier que le Grafcet complet bouclé ne contient aucune erreur de validation CEI 60848
        SfcProgram program = grafcet.toProgram("Cycle_Complet", List.of());
        assertTrue(program.validate().stream().noneMatch(tk.tahri.grafino.model.SfcValidationMessage::isError),
                "Un cycle rebouclé vers l'étape initiale ne doit comporter aucune erreur de validation");
    }

    @Test
    @DisplayName("Insertion d'un couple Étape + Transition au milieu d'une liaison existante (insertPairOnLink)")
    void testInsertPairOnLinkPreservesAlternance() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();
        grafcet.createTransition(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();
        double originalT1Y = t1.getY();

        Link linkS1T1 = s1.getLinksToChildren().getFirst();
        grafcet.insertPairOnLink(linkS1T1);

        // S1 -> T_new -> S_new -> T1
        assertEquals(4, grafcet.getComponents().size(), "Le lien S1->T1 coupé doit insérer T_new et S_new");
        var insertedTrans = s1.getLinksToChildren().getFirst().getChildComponent();
        assertInstanceOf(Transition.class, insertedTrans, "Après S1 doit se trouver une Transition");
        var insertedStep = insertedTrans.getLinksToChildren().getFirst().getChildComponent();
        assertInstanceOf(Step.class, insertedStep, "Après la nouvelle Transition doit se trouver une Étape");
        var finalTarget = insertedStep.getLinksToChildren().getFirst().getChildComponent();
        assertSame(t1, finalTarget, "La nouvelle Étape doit être raccordée à T1");
        assertTrue(t1.getY() > originalT1Y, "T1 doit avoir été décalée vers le bas pour faire de la place");
    }

    @Test
    @DisplayName("Barre d'outils verticale fixe sur le côté gauche du Canvas (icônes seules)")
    void testActionBarContextRefresh() {
        Grafcet grafcet = new Grafcet(true);
        ActionBar bar = grafcet.getActionBar();
        assertNotNull(bar);

        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();
        assertTrue(bar.isVisible(), "La barre verticale gauche doit être visible lorsqu'une étape est sélectionnée");
        assertFalse(bar.getAllActionButtons().isEmpty(), "La barre verticale doit contenir les boutons d'action");

        // Vérifier que tous les boutons utilisent uniquement des icônes courtes
        for (var node : bar.getAllActionButtons()) {
            if (node instanceof javafx.scene.control.Button btn) {
                assertTrue(btn.getText().length() <= 2, "Chaque bouton doit afficher uniquement une icône compacte : " + btn.getText());
            }
        }

        // Vérifier que la barre est ancrée à une position fixe sur le côté gauche du Canvas (x = 14)
        assertEquals(14.0, bar.getLayoutX(), 0.01, "La barre verticale doit être fixe sur le bord gauche du Canvas");

        // Sélection d'une transition : la position X reste fixe à gauche
        grafcet.createTransition(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();
        assertTrue(bar.isVisible(), "La barre verticale doit rester visible lorsqu'une transition est sélectionnée");
        assertEquals(14.0, bar.getLayoutX(), 0.01, "La barre verticale reste fixe sur le bord gauche (x = 14)");

        // Masquage automatique pendant la simulation
        grafcet.updateSimulationVisuals(java.util.Set.of("s1"), java.util.Set.of());
        assertFalse(bar.isVisible(), "La barre d'outils d'édition doit se masquer en mode simulation");

        grafcet.clearSimulationVisuals();
        assertTrue(bar.isVisible(), "La barre d'outils doit réapparaître à l'arrêt de la simulation");

        // Ghost preview
        grafcet.setActiveGhostPreview(ActionBar.GhostPreviewType.NEXT_PAIR);
        assertEquals(ActionBar.GhostPreviewType.NEXT_PAIR, grafcet.getActiveGhostPreview());
        grafcet.setActiveGhostPreview(ActionBar.GhostPreviewType.NONE);
    }

    @Test
    @DisplayName("Différenciation ＋ vs ⑂/⇉ : ouverture directe d'une divergence à 2 branches et prévention des doublons")
    void testBranchButtonCreatesTwoBranchesWhenEmptyAndDisablesSingleAdd() {
        Grafcet grafcet = new Grafcet(true);
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();

        // Cliquer sur ⑂ (createOrBranch) sur une étape sans enfant crée directement 2 transitions en divergence OU
        grafcet.createOrBranch(s1);
        assertEquals(2, grafcet.getDownwardLinks(s1).size(),
                "createOrBranch sur une étape sans enfant doit créer directement 2 branches en OU");
        assertTrue(s1.getLinksToChildren().get(0).isDivergence());
        assertTrue(s1.getLinksToChildren().get(1).isDivergence());

        // Sélectionner S1 : le bouton '+' (indice 0) et '⚡' (indice 1) doivent être désactivés, mais '⑂' (indice 2) actif
        grafcet.getComponents().forEach(c -> c.setSelected(false));
        s1.setSelected(true);
        grafcet.refreshActionBar();
        var buttons = grafcet.getActionBar().getAllActionButtons();
        assertTrue(buttons.get(0).isDisabled(), "Le bouton + doit être désactivé quand une étape a déjà une transition aval");
        assertTrue(buttons.get(1).isDisabled(), "Le bouton ⚡ doit être désactivé quand une étape a déjà une transition aval");
        assertFalse(buttons.get(2).isDisabled(), "Le bouton ⑂ doit rester actif pour ajouter une 3e branche en OU");
    }

    @Test
    @DisplayName("Conformité CEI 60848 : le bouton Convergence ET (⫘) exclut les étapes issues d'un aiguillage en OU (Deadlock)")
    void testConvergenceCandidatesPreventOrToAndDeadlock() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();

        // Créer une divergence en OU sous S1 -> (T1 -> S2) et (T2 -> S3)
        grafcet.createOrBranch(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().get(0).getChildComponent();
        Transition t2 = (Transition) s1.getLinksToChildren().get(1).getChildComponent();
        grafcet.createStep(t1);
        Step s2 = (Step) t1.getLinksToChildren().getFirst().getChildComponent();
        grafcet.createStep(t2);
        Step s3 = (Step) t2.getLinksToChildren().getFirst().getChildComponent();

        // S2 et S3 proviennent d'une divergence en OU : elles ne doivent PAS être proposées pour une convergence en ET !
        assertTrue(grafcet.wouldCauseOrToAndDeadlock(List.of(s2, s3)),
                "La convergence en ET de deux branches issues d'un OU doit être détectée comme un Deadlock CEI 60848");
        assertTrue(grafcet.findConvergenceCandidateSteps(s2).isEmpty(),
                "Aucune étape candidate en ET ne doit être proposée pour fermer une divergence en OU");
    }

    @Test
    @DisplayName("Conformité CEI 60848 : le bouton Convergence OU (⫘) exclut les transitions issues d'une divergence en ET non synchronisée")
    void testConvergenceCandidatesPreventAndToOrUnsynced() {
        Grafcet grafcet = new Grafcet();
        grafcet.createInitialStep();
        Step s1 = (Step) grafcet.getComponents().getFirst();
        grafcet.createTransition(s1);
        Transition t1 = (Transition) s1.getLinksToChildren().getFirst().getChildComponent();

        // Créer une divergence en ET sous T1 -> (S2 -> T2) et (S3 -> T3)
        grafcet.createAndBranch(t1);
        Step s2 = (Step) t1.getLinksToChildren().get(0).getChildComponent();
        Step s3 = (Step) t1.getLinksToChildren().get(1).getChildComponent();
        grafcet.createTransition(s2);
        Transition t2 = (Transition) s2.getLinksToChildren().getFirst().getChildComponent();
        grafcet.createTransition(s3);
        Transition t3 = (Transition) s3.getLinksToChildren().getFirst().getChildComponent();

        // T2 et T3 sont issues de branches parallèles en ET : elles ne doivent pas converger en OU sans synchronisation
        assertTrue(grafcet.wouldCauseAndToOrUnsyncedConvergence(List.of(t2, t3)),
                "La convergence en OU de deux branches parallèles en ET doit être détectée comme non synchronisée");
        assertTrue(grafcet.findConvergenceCandidateTransitions(t2).isEmpty(),
                "Aucune transition candidate en OU ne doit être proposée pour fermer une divergence en ET non synchronisée");
    }
}
