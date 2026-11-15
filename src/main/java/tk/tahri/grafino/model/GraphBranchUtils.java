package tk.tahri.grafino.model;

import java.util.List;
import java.util.Set;

/**
 * Utilitaires d'analyse topologique de graphes SFC / Grafcet.
 */
public final class GraphBranchUtils {

    private GraphBranchUtils() {
    }

    /**
     * Vérifie s'il existe une affectation injective associant chaque élément de {@code elements}
     * à un ensemble distinct de {@code branchSets}.
     */
    public static <T> boolean canAssignDistinctBranches(List<T> elements, List<Set<T>> branchSets) {
        if (elements == null || branchSets == null || elements.size() > branchSets.size()) {
            return false;
        }
        return assignBranchesRecursive(elements, 0, branchSets, new boolean[branchSets.size()]);
    }

    private static <T> boolean assignBranchesRecursive(
            List<T> elements,
            int idx,
            List<Set<T>> branchSets,
            boolean[] usedBranch
    ) {
        if (idx == elements.size()) {
            return true;
        }
        T elem = elements.get(idx);
        for (int b = 0; b < branchSets.size(); b++) {
            if (!usedBranch[b] && branchSets.get(b).contains(elem)) {
                usedBranch[b] = true;
                if (assignBranchesRecursive(elements, idx + 1, branchSets, usedBranch)) {
                    return true;
                }
                usedBranch[b] = false;
            }
        }
        return false;
    }
}
