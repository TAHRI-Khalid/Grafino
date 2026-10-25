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
    @DisplayName("Boucle de reprise après divergence : ne doit pas créer de fausse convergence ni de ligne parasite")
    void testLoopbackAfterDivergenceDoesNotCreateFalseConvergence() {
        // S1 -> T1 -> S2 -> (T2, T3)
        // T2 boucle vers S1
        // T3 boucle vers S2
        var s1 = new Step(null);
        s1.position = new Point2D(400, 50);

        var t1 = new Transition(null);
        t1.position = new Point2D(400, 130);

        var s2 = new Step(null);
        s2.position = new Point2D(400, 220);

        var t2 = new Transition(null);
        t2.position = new Point2D(300, 320);

        var t3 = new Transition(null);
        t3.position = new Point2D(500, 320);

        var linkS1T1 = Link.between(null, s1, t1);
        var linkT1S2 = Link.between(null, t1, s2);
        var linkS2T2 = Link.between(null, s2, t2);
        var linkS2T3 = Link.between(null, s2, t3);

        // Avant les retours : divergence normale sous S2
        assertTrue(linkS2T2.isDivergence());
        assertTrue(linkS2T3.isDivergence());
        assertFalse(linkT1S2.isConvergence());

        // Liaison boucle T2 -> S1
        var loopT2S1 = Link.between(null, t2, s1);
        assertTrue(loopT2S1.isAscending());
        assertFalse(loopT2S1.isConvergence());

        // Liaison boucle T3 -> S2 (scénario rapporté par l'utilisateur)
        var loopT3S2 = Link.between(null, t3, s2);
        assertTrue(loopT3S2.isAscending(), "T3 -> S2 doit être détectée comme une liaison ascendante");
        assertFalse(loopT3S2.isConvergence(), "Une boucle de reprise ascendante ne doit pas être une convergence");
        assertFalse(loopT3S2.isDivergence(), "Une boucle de reprise ascendante ne doit pas être une divergence");

        // CRUCIAL : T1 -> S2 ne doit PAS être marqué comme convergence
        // (S2 a 2 parents, mais un seul parent descendant T1 et une boucle de reprise T3)
        assertFalse(linkT1S2.isConvergence(), "T1 -> S2 ne doit pas devenir une convergence à cause d'une boucle de retour");

        // Vérifier que la gouttière de T3 (situé à droite de S2) est bien à droite
        double expectedGutterX = (500 + Component.UNIT_WIDTH / 2) + Component.UNIT_WIDTH * 0.9;
        assertTrue(loopT3S2.isPointOnTheComponent(new Point2D(expectedGutterX, 250)),
                "La gouttière de la boucle de droite doit se trouver à droite");
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

    @Test
    @DisplayName("Magnétisme intelligent (Smart Snap) sur l'axe vertical du parent")
    void testSmartSnapVerticalAlignment() {
        var grafcet = new tk.tahri.grafino.ui.Grafcet();

        var parentStep = new Step(null);
        parentStep.position = new Point2D(400, 100);
        grafcet.getComponents().add(parentStep);

        var childTrans = new Transition(null);
        childTrans.position = new Point2D(400, 220);
        grafcet.getComponents().add(childTrans);
        Link.between(null, parentStep, childTrans);

        // Déplacement de childTrans avec un léger décalage de 6px en X
        double rawX = 406;
        double rawY = 250;
        Point2D snapped = grafcet.computeSmartSnap(childTrans, rawX, rawY);

        assertEquals(400.0, snapped.getX(), "L'élément doit s'aimanter exactement sur l'axe X de son parent");
    }

    @Test
    @DisplayName("Magnétisme intelligent (Smart Snap) sur l'axe horizontal d'une branche sœur")
    void testSmartSnapHorizontalAlignment() {
        var grafcet = new tk.tahri.grafino.ui.Grafcet();

        var branch1 = new Step(null);
        branch1.position = new Point2D(300, 200);
        grafcet.getComponents().add(branch1);

        var branch2 = new Step(null);
        branch2.position = new Point2D(500, 200);
        grafcet.getComponents().add(branch2);

        // Déplacement de branch2 avec un léger décalage de 5px en Y
        double rawX = 520;
        double rawY = 205;
        Point2D snapped = grafcet.computeSmartSnap(branch2, rawX, rawY);

        assertEquals(200.0, snapped.getY(), "L'élément doit s'aimanter sur la hauteur Y de la branche parallèle");
    }

    @Test
    @DisplayName("Auto-layout hiérarchique régulier du Grafcet")
    void testAutoLayoutHierarchy() {
        var grafcet = new tk.tahri.grafino.ui.Grafcet();

        var s0 = new Step(null);
        s0.setInitial(true);
        s0.position = new Point2D(100, 50);
        grafcet.getComponents().add(s0);

        var t1 = new Transition(null);
        t1.position = new Point2D(250, 80);
        grafcet.getComponents().add(t1);
        Link.between(null, s0, t1);

        var s1 = new Step(null);
        s1.position = new Point2D(60, 300);
        grafcet.getComponents().add(s1);
        Link.between(null, t1, s1);

        grafcet.autoLayout();

        assertTrue(s0.getY() < t1.getY(), "L'étape initiale doit être au-dessus de la transition");
        assertTrue(t1.getY() < s1.getY(), "La transition doit être au-dessus de l'étape suivante");
        assertEquals(s0.getX(), t1.getX(), "Une chaîne simple doit être alignée sur le même axe X");
        assertEquals(t1.getX(), s1.getX(), "L'étape suivante doit être alignée sur le même axe X");
    }
}
