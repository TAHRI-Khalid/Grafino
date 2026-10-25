package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests des règles de tracé et de géométrie des liaisons SFC / Grafcet (CEI 60848)")
class GrafcetDrawingLogicTest {

    @Test
    @DisplayName("Liaison descendante directe (1-à-1) : non ascendante, sans divergence")
    void testDirectDescendingLink() {
        var step = new Step(null);
        step.position = new Point2D(400, 100);

        var transition = new Transition(null);
        transition.position = new Point2D(400, 200);

        var link = Link.between(null, step, transition);

        assertFalse(link.isAscending(), "Une liaison vers le bas ne doit pas être ascendante");
        assertFalse(link.isDivergence(), "Une liaison 1-à-1 ne doit pas être une divergence");
        assertFalse(link.isConvergence(), "Une liaison 1-à-1 ne doit pas être une convergence");
    }

    @Test
    @DisplayName("Détection de divergence en OU (Étape vers plusieurs transitions)")
    void testOrDivergence() {
        var step = new Step(null);
        step.position = new Point2D(400, 100);

        var t1 = new Transition(null);
        t1.position = new Point2D(300, 220);

        var t2 = new Transition(null);
        t2.position = new Point2D(500, 220);

        var link1 = Link.between(null, step, t1);
        var link2 = Link.between(null, step, t2);

        assertTrue(link1.isDivergence(), "Le premier lien d'une étape vers 2 transitions doit être marqué en divergence");
        assertTrue(link2.isDivergence(), "Le second lien d'une étape vers 2 transitions doit être marqué en divergence");
        assertEquals(2, step.getLinksToChildren().size());
    }

    @Test
    @DisplayName("Détection de convergence en OU (Plusieurs transitions vers une étape)")
    void testOrConvergence() {
        var t1 = new Transition(null);
        t1.position = new Point2D(300, 200);

        var t2 = new Transition(null);
        t2.position = new Point2D(500, 200);

        var step = new Step(null);
        step.position = new Point2D(400, 320);

        var link1 = Link.between(null, t1, step);
        var link2 = Link.between(null, t2, step);

        assertTrue(link1.isConvergence(), "Le lien doit être détecté comme convergent");
        assertTrue(link2.isConvergence(), "Le lien doit être détecté comme convergent");
        assertEquals(2, step.getLinksFromParents().size());
    }

    @Test
    @DisplayName("Boucle ascendante (Reprise de cycle de bas en haut) avec flèche montante")
    void testAscendingLoopbackLink() {
        var step0 = new Step(null);
        step0.position = new Point2D(400, 80);

        var transEnd = new Transition(null);
        transEnd.position = new Point2D(400, 400);

        // Liaison de transEnd (bas) vers step0 (haut)
        var loopLink = Link.between(null, transEnd, step0);

        assertTrue(loopLink.isAscending(), "La liaison vers un élément plus haut doit être ascendante (CEI 60848)");
        assertFalse(loopLink.isDivergence());
    }

    @Test
    @DisplayName("Déconnexion propre d'un composant et de ses liaisons")
    void testDisconnectLink() {
        var step = new Step(null);
        step.position = new Point2D(400, 100);

        var trans = new Transition(null);
        trans.position = new Point2D(400, 200);

        var link = Link.between(null, step, trans);
        assertEquals(1, step.getLinksToChildren().size());
        assertEquals(1, trans.getLinksFromParents().size());

        link.disconnect();
        assertEquals(0, step.getLinksToChildren().size());
        assertEquals(0, trans.getLinksFromParents().size());
    }

    @Test
    @DisplayName("Affordance des boutons contextuels intelligents TIA Portal")
    void testQuickActionButtonsHitTesting() {
        var step = new Step(null);
        step.position = new Point2D(400, 100);
        step.setSelected(true);

        Point2D nextBtnCenter = step.getQuickAddNextCenter();
        assertTrue(step.isQuickAddNextPressed(nextBtnCenter), "Le centre du bouton + doit être détecté");
        assertFalse(step.isQuickAddNextPressed(new Point2D(100, 100)), "Un point lointain ne doit pas être détecté");

        Point2D branchBtnCenter = step.getQuickAddBranchCenter();
        assertTrue(step.isQuickAddBranchPressed(branchBtnCenter), "Le centre du bouton branche doit être détecté");

        Point2D delBtnCenter = step.getQuickDeleteCenter();
        assertTrue(step.isQuickDeletePressed(delBtnCenter), "Le centre du bouton supprimer doit être détecté");
    }
}
