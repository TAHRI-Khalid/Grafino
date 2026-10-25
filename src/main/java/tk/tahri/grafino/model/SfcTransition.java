package tk.tahri.grafino.model;

import java.util.Objects;

/**
 * Modélisation d'une transition SFC conforme à la norme CEI 61131-3.
 *
 * @param id          Identifiant technique unique
 * @param name        Nom symbolique (ex: "Trans1", "T_Fin_Course")
 * @param number      Numéro ordinal de la transition
 * @param conditionST Expression booléenne de réceptivité (en Structured Text CEI 61131-3)
 * @param x           Position horizontale
 * @param y           Position verticale
 */
public record SfcTransition(
        String id,
        String name,
        int number,
        String conditionST,
        double x,
        double y
) implements SfcElement {

    public SfcTransition {
        Objects.requireNonNull(id, "L'id de la transition est obligatoire.");
        if (name == null || name.isBlank()) {
            name = "T" + number;
        }
        conditionST = (conditionST == null || conditionST.isBlank()) ? "TRUE" : conditionST.trim();
    }

    public SfcTransition withPosition(double newX, double newY) {
        return new SfcTransition(id, name, number, conditionST, newX, newY);
    }

    public SfcTransition withCondition(String newCondition) {
        return new SfcTransition(id, name, number, newCondition, x, y);
    }
}
