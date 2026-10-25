package tk.tahri.grafino.model;

import java.util.List;
import java.util.Objects;

/**
 * Modélisation d'une branche ou barre de synchronisation SFC (CEI 61131-3).
 *
 * @param id            Identifiant unique de la branche
 * @param name          Nom symbolique
 * @param type          Type d'aiguillage (ET / OU, Divergence / Convergence)
 * @param x             Position horizontale
 * @param y             Position verticale
 * @param upstreamIds   Identifiants des éléments amonts raccordés
 * @param downstreamIds Identifiants des éléments avals raccordés
 */
public record SfcBranch(
        String id,
        String name,
        SfcBranchType type,
        double x,
        double y,
        List<String> upstreamIds,
        List<String> downstreamIds
) implements SfcElement {

    public SfcBranch {
        Objects.requireNonNull(id, "L'id de branche est obligatoire.");
        Objects.requireNonNull(type, "Le type de branche est obligatoire.");
        if (name == null || name.isBlank()) {
            name = type.name().toLowerCase();
        }
        upstreamIds = (upstreamIds == null) ? List.of() : List.copyOf(upstreamIds);
        downstreamIds = (downstreamIds == null) ? List.of() : List.copyOf(downstreamIds);
    }

    public boolean isParallel() {
        return type.isParallel();
    }

    public boolean isAlternative() {
        return type.isAlternative();
    }
}
