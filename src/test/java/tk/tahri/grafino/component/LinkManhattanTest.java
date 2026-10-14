package tk.tahri.grafino.component;

import javafx.geometry.Point2D;
import java.util.List;

public class LinkManhattanTest {

    public static void main(String[] args) {
        System.out.println("=== Test du tracé orthogonal (Manhattan) des liaisons ===");

        // Test 1 : Étape au dessus d'une Transition (alignés verticalement)
        Step s0 = new Step(null, "S0", 0, true);
        s0.setPosition(new Point2D(400, 60));

        Transition t1 = new Transition(null, "T1", 1, "1");
        t1.setPosition(new Point2D(400, 160));

        Link l1 = Link.between(null, s0, t1);
        List<Point2D> p1 = l1.getPath();
        assert p1.size() == 2 : "La liaison directe doit avoir 2 points";
        assert Math.abs(p1.get(0).getX() - p1.get(1).getX()) < 0.001 : "La liaison doit être strictement verticale";
        System.out.println("✓ Test 1 validé : Liaison directe verticale pure");

        // Test 2 : Étape avec décalage horizontal vers la droite
        Transition t2 = new Transition(null, "T2", 2, "a");
        t2.setPosition(new Point2D(600, 160));

        Link l2 = Link.between(null, s0, t2);
        List<Point2D> p2 = l2.getPath();
        assert p2.size() == 4 : "La liaison avec décalage doit avoir 4 points (décrochement à 90°)";
        assertIsStrictlyOrthogonal(p2);
        System.out.println("✓ Test 2 validé : Décrochement orthogonal 90° descendant");

        // Test 3 : Boucle de remontée vers le haut (de Transition t1 vers Étape s0)
        Link loop = Link.between(null, t1, s0);
        List<Point2D> pLoop = loop.getPath();
        assert pLoop.size() == 6 : "La boucle de remontée doit comporter 6 points";
        assertIsStrictlyOrthogonal(pLoop);
        System.out.println("✓ Test 3 validé : Boucle de remontée strictement orthogonale (zéro ligne inclinée)");

        // Test 4 : Zone de clic poignée et reconnexion
        Point2D bottomHandle = new Point2D(s0.getX() + Component.UNIT_WIDTH / 2, s0.getY() + Component.UNIT_HEIGHT);
        assert s0.isBottomHandlePressed(bottomHandle) : "Le clic exact sur la poignée basse doit être détecté";
        assert s0.isBottomHandlePressed(new Point2D(bottomHandle.getX() + 15, bottomHandle.getY() + 5)) : "Le clic proche de la poignée basse doit être détecté";
        assert s0.isPointNearOrOn(new Point2D(bottomHandle.getX(), bottomHandle.getY() + 10), 25.0) : "Tolérance de reconnexion active";
        System.out.println("✓ Test 4 validé : Détection élargie de la poignée et tolérance de reconnexion");

        System.out.println("=== Tous les tests de tracé orthogonal sont validés avec succès ! ===");
    }

    private static void assertIsStrictlyOrthogonal(List<Point2D> path) {
        for (int i = 0; i < path.size() - 1; i++) {
            Point2D from = path.get(i);
            Point2D to = path.get(i + 1);
            boolean isVertical = Math.abs(from.getX() - to.getX()) < 0.001;
            boolean isHorizontal = Math.abs(from.getY() - to.getY()) < 0.001;
            assert isVertical || isHorizontal : "Chaque segment doit être soit strictement vertical, soit strictement horizontal ! Segment: " + from + " -> " + to;
        }
    }
}
