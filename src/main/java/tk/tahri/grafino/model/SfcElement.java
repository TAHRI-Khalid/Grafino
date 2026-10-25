package tk.tahri.grafino.model;

/**
 * Racine scellée des éléments constitutifs d'un diagramme SFC selon la norme CEI 61131-3.
 * L'utilisation d'une 'sealed interface' permet au compilateur Java 25+ de garantir l'exhaustivité
 * dans les structures de contrôle avec pattern matching (switch expressions).
 */
public sealed interface SfcElement permits SfcStep, SfcTransition, SfcBranch {

    /**
     * Identifiant unique et immuable de l'élément dans le projet.
     */
    String id();

    /**
     * Nom fonctionnel ou étiquette IEC (ex: "Step0", "Trans_1").
     */
    String name();

    /**
     * Position horizontale (coordonnée X) dans le diagramme.
     */
    double x();

    /**
     * Position verticale (coordonnée Y) dans le diagramme.
     */
    double y();

    /**
     * Renvoie le point de position sous forme de SfcPoint.
     */
    default SfcPoint position() {
        return new SfcPoint(x(), y());
    }
}
