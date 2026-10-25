package tk.tahri.grafino.model;

import java.util.List;
import java.util.Objects;

/**
 * Liaison orientée entre deux éléments d'un SFC (CEI 61131-3).
 *
 * @param id                 Identifiant technique de l'arc
 * @param sourceId           Identifiant de l'élément source (amont)
 * @param targetId           Identifiant de l'élément cible (aval)
 * @param intermediatePoints Points de passage orthogonaux pour le routage Manhattan
 */
public record SfcLink(
        String id,
        String sourceId,
        String targetId,
        List<SfcPoint> intermediatePoints
) {
    public SfcLink {
        Objects.requireNonNull(id, "L'id de liaison est obligatoire.");
        Objects.requireNonNull(sourceId, "L'id source est obligatoire.");
        Objects.requireNonNull(targetId, "L'id cible est obligatoire.");
        intermediatePoints = (intermediatePoints == null) ? List.of() : List.copyOf(intermediatePoints);
    }

    public SfcLink(String id, String sourceId, String targetId) {
        this(id, sourceId, targetId, List.of());
    }

    public boolean hasRoutingPoints() {
        return !intermediatePoints.isEmpty();
    }
}
