package tk.tahri.grafino.model;

import java.util.List;
import java.util.Objects;

/**
 * Modélisation d'une étape SFC conforme à la norme CEI 61131-3.
 *
 * @param id           Identifiant technique unique (ex: "s_0", "s_1")
 * @param name         Nom symbolique IEC (ex: "Step0", "Attente_Piece")
 * @param number       Numéro ordinal d'étape
 * @param isInitial    Vrai s'il s'agit d'une étape initiale (représentée par un double cadre)
 * @param x            Position horizontale
 * @param y            Position verticale
 * @param actionBlocks Liste des blocs d'actions associés à l'étape
 */
public record SfcStep(
        String id,
        String name,
        int number,
        boolean isInitial,
        double x,
        double y,
        List<SfcActionBlock> actionBlocks
) implements SfcElement {

    public SfcStep {
        Objects.requireNonNull(id, "L'id de l'étape est obligatoire.");
        if (name == null || name.isBlank()) {
            name = "Step" + number;
        }
        actionBlocks = (actionBlocks == null) ? List.of() : List.copyOf(actionBlocks);
    }

    /**
     * Constructeur pratique pour une étape sans actions.
     */
    public SfcStep(String id, String name, int number, boolean isInitial, double x, double y) {
        this(id, name, number, isInitial, x, y, List.of());
    }

    public boolean hasActions() {
        return !actionBlocks.isEmpty();
    }

    /**
     * Renvoie une copie de l'étape avec une nouvelle position géographique.
     */
    public SfcStep withPosition(double newX, double newY) {
        return new SfcStep(id, name, number, isInitial, newX, newY, actionBlocks);
    }

    /**
     * Renvoie une copie de l'étape avec le statut initial modifié.
     */
    public SfcStep withInitial(boolean newInitial) {
        return new SfcStep(id, name, number, newInitial, x, y, actionBlocks);
    }

    /**
     * Renvoie une copie avec une liste d'actions mise à jour.
     */
    public SfcStep withActionBlocks(List<SfcActionBlock> newActions) {
        return new SfcStep(id, name, number, isInitial, x, y, newActions);
    }
}
