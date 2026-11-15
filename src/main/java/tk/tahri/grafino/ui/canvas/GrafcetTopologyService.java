package tk.tahri.grafino.ui.canvas;

import javafx.geometry.Point2D;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.GraphBranchUtils;
import tk.tahri.grafino.ui.Grafcet;

import java.util.*;

/**
 * Service d'analyse topologique, d'aimantation magnétique (Smart Snap),
 * d'anti-chevauchement et de mise en page automatique (Auto-Layout) du Grafcet.
 */
public final class GrafcetTopologyService {

    public static final double MIN_VERTICAL_GAP = 16.0;
    public static final double CREATION_V_GAP = 16.0;

    private GrafcetTopologyService() {
    }

    public static List<Link> getDownwardLinks(StepAndTransitionCommonBehavior comp) {
        return comp.getLinksToChildren().stream().filter(l -> !l.isAscending()).toList();
    }

    public static Point2D computeSmartSnap(
            List<StepAndTransitionCommonBehavior> components,
            List<Grafcet.AlignmentGuide> activeGuideLines,
            StepAndTransitionCommonBehavior draggedComp,
            double rawX,
            double rawY
    ) {
        activeGuideLines.clear();

        double snappedX = rawX;
        double snappedY = rawY;
        double snapTolerance = 14.0;

        double draggedCenterX = rawX + Component.UNIT_WIDTH / 2;
        boolean snappedXDone = false;
        boolean snappedYDone = false;

        List<StepAndTransitionCommonBehavior> xCandidates = new ArrayList<>();
        if (draggedComp != null) {
            draggedComp.getLinksFromParents().forEach(l -> xCandidates.add(l.getParentComponent()));
            draggedComp.getLinksToChildren().forEach(l -> xCandidates.add(l.getChildComponent()));
        }
        components.stream().filter(c -> c != draggedComp && !xCandidates.contains(c)).forEach(xCandidates::add);

        for (var ref : xCandidates) {
            double refCenterX = ref.getX() + Component.UNIT_WIDTH / 2;
            if (Math.abs(draggedCenterX - refCenterX) <= snapTolerance) {
                snappedX = refCenterX - Component.UNIT_WIDTH / 2;
                snappedXDone = true;

                double minY = Math.min(rawY, ref.getY()) - 25;
                double maxY = Math.max(rawY + Component.UNIT_HEIGHT, ref.getY() + Component.UNIT_HEIGHT) + 25;
                activeGuideLines.add(new Grafcet.AlignmentGuide(refCenterX, minY, refCenterX, maxY, true));
                break;
            }
        }

        for (var ref : components) {
            if (ref == draggedComp) continue;

            if (Math.abs(rawY - ref.getY()) <= snapTolerance) {
                snappedY = ref.getY();
                snappedYDone = true;

                double minX = Math.min(snappedX, ref.getX()) - 25;
                double maxX = Math.max(snappedX + Component.UNIT_WIDTH, ref.getX() + Component.UNIT_WIDTH) + 25;
                double guideY = ref.getY() + Component.UNIT_HEIGHT / 2;
                activeGuideLines.add(new Grafcet.AlignmentGuide(minX, guideY, maxX, guideY, false));
                break;
            }
        }

        if (!snappedXDone) {
            double gridStep = 12.0;
            snappedX = Math.round(snappedX / gridStep) * gridStep;
        }
        if (!snappedYDone) {
            double gridStep = 12.0;
            snappedY = Math.round(snappedY / gridStep) * gridStep;
        }

        return new Point2D(snappedX, snappedY);
    }

    public static Point2D clampPositionWithoutOverlap(
            List<StepAndTransitionCommonBehavior> components,
            StepAndTransitionCommonBehavior draggedComp,
            double targetX,
            double targetY,
            Set<StepAndTransitionCommonBehavior> movingGroup
    ) {
        if (draggedComp == null) {
            return new Point2D(targetX, targetY);
        }

        double minY = Double.NEGATIVE_INFINITY;
        double maxY = Double.POSITIVE_INFINITY;
        double draggedCenterY = draggedComp.getY() + Component.UNIT_HEIGHT / 2.0;

        for (Link link : draggedComp.getLinksFromParents()) {
            if (!link.isAscending()) {
                var parent = link.getParentComponent();
                if (!movingGroup.contains(parent)) {
                    minY = Math.max(minY, parent.getY() + Component.UNIT_HEIGHT + MIN_VERTICAL_GAP);
                }
            }
        }
        for (Link link : draggedComp.getLinksToChildren()) {
            if (!link.isAscending()) {
                var child = link.getChildComponent();
                if (!movingGroup.contains(child)) {
                    maxY = Math.min(maxY, child.getY() - Component.UNIT_HEIGHT - MIN_VERTICAL_GAP);
                }
            }
        }

        for (var other : components) {
            if (other == draggedComp || movingGroup.contains(other)) {
                continue;
            }
            boolean horizontalOverlap = targetX < other.getX() + Component.UNIT_WIDTH - 1.0
                    && targetX + Component.UNIT_WIDTH > other.getX() + 1.0;
            if (horizontalOverlap) {
                double otherCenterY = other.getY() + Component.UNIT_HEIGHT / 2.0;
                if (draggedCenterY <= otherCenterY) {
                    maxY = Math.min(maxY, other.getY() - Component.UNIT_HEIGHT - MIN_VERTICAL_GAP);
                } else {
                    minY = Math.max(minY, other.getY() + Component.UNIT_HEIGHT + MIN_VERTICAL_GAP);
                }
            }
        }

        double clampedY = (minY <= maxY)
                ? Math.max(minY, Math.min(maxY, targetY))
                : draggedComp.getY();
        return new Point2D(targetX, clampedY);
    }

    public static double clampGroupDeltaY(
            List<StepAndTransitionCommonBehavior> components,
            List<StepAndTransitionCommonBehavior> selectedList,
            Set<StepAndTransitionCommonBehavior> movingGroup,
            double dx,
            double dy
    ) {
        double allowedDy = dy;
        for (var member : selectedList) {
            var clamped = clampPositionWithoutOverlap(components, member, member.getX() + dx, member.getY() + allowedDy, movingGroup);
            double memberDy = clamped.getY() - member.getY();
            if (dy >= 0) {
                allowedDy = Math.min(allowedDy, Math.max(0.0, memberDy));
            } else {
                allowedDy = Math.max(allowedDy, Math.min(0.0, memberDy));
            }
        }
        return allowedDy;
    }

    public static void autoLayout(List<StepAndTransitionCommonBehavior> components, double canvasWidth) {
        if (components.isEmpty()) return;

        var roots = components.stream()
                .filter(c -> c.getLinksFromParents().isEmpty() || (c instanceof Step s && s.isInitial()))
                .toList();

        if (roots.isEmpty()) {
            roots = List.of(components.getFirst());
        }

        Map<StepAndTransitionCommonBehavior, Integer> levels = new HashMap<>();
        Queue<StepAndTransitionCommonBehavior> queue = new ArrayDeque<>();

        for (var root : roots) {
            levels.put(root, 0);
            queue.add(root);
        }

        Set<StepAndTransitionCommonBehavior> visited = new HashSet<>(roots);

        while (!queue.isEmpty()) {
            var current = queue.poll();
            int currentLevel = levels.get(current);

            for (Link link : current.getLinksToChildren()) {
                var child = link.getChildComponent();
                if (!roots.contains(child)) {
                    int existingLevel = levels.getOrDefault(child, -1);
                    if (existingLevel < currentLevel + 1) {
                        levels.put(child, currentLevel + 1);
                        if (!visited.contains(child)) {
                            visited.add(child);
                            queue.add(child);
                        }
                    }
                }
            }
        }

        for (var comp : components) {
            levels.putIfAbsent(comp, 0);
        }

        Map<Integer, List<StepAndTransitionCommonBehavior>> byLevel = new TreeMap<>();
        levels.forEach((comp, lvl) -> byLevel.computeIfAbsent(lvl, _ -> new ArrayList<>()).add(comp));

        double centerX = (canvasWidth > 0) ? canvasWidth / 2 - Component.UNIT_WIDTH / 2 : 400;
        double startY = 80;
        double verticalSpacing = Component.UNIT_HEIGHT + CREATION_V_GAP;
        double horizontalSpacing = 150;

        byLevel.forEach((lvl, compsInLevel) -> {
            double y = startY + lvl * verticalSpacing;
            int count = compsInLevel.size();
            double totalWidth = (count - 1) * horizontalSpacing;
            double startX = centerX - totalWidth / 2;

            for (int i = 0; i < count; i++) {
                var comp = compsInLevel.get(i);
                double x = startX + i * horizontalSpacing;
                comp.updatePositionAndLinks(new Point2D(x, y));
            }
        });

        components.stream()
                .map(StepAndTransitionCommonBehavior::getLinksToChildren)
                .flatMap(List::stream)
                .forEach(Link::update);
    }

    public static Optional<String> validateAndConvergence(
            List<StepAndTransitionCommonBehavior> components,
            List<Step> steps,
            Transition existingTarget
    ) {
        if (steps == null || steps.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 étapes parallèles pour créer une convergence en ET.");
        }
        Set<Step> unique = new LinkedHashSet<>(steps);
        if (unique.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 étapes distinctes pour créer une convergence en ET.");
        }
        if (unique.stream().anyMatch(Step::isInitial)) {
            return Optional.of("Une étape initiale ne peut pas participer à une convergence en ET.");
        }
        for (Step s : unique) {
            List<Link> down = getDownwardLinks(s);
            boolean allowedExisting = existingTarget != null
                    && down.size() == 1
                    && down.getFirst().getChildComponent() == existingTarget;
            if (!down.isEmpty() && !allowedExisting) {
                return Optional.of("L'étape S" + s.getNumber() + " possède déjà une transition aval : elle doit être en fin de branche ouverte.");
            }
        }
        List<Step> stepList = List.copyOf(unique);
        if (wouldCauseOrToAndDeadlock(components, stepList)) {
            return Optional.of("Convergence en ET interdite (CEI 60848) : ces étapes sont issues d'un aiguillage en OU (Deadlock).");
        }
        if (!hasCommonAndDivergenceAncestor(components, stepList)) {
            return Optional.of("Convergence en ET non conforme (CEI 60848) : les étapes doivent provenir de branches parallèles distinctes d'une même divergence en ET.");
        }
        return Optional.empty();
    }

    public static Optional<String> validateOrConvergence(
            List<StepAndTransitionCommonBehavior> components,
            List<Transition> transitions,
            Step existingTarget
    ) {
        if (transitions == null || transitions.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 transitions alternatives pour créer une convergence en OU.");
        }
        Set<Transition> unique = new LinkedHashSet<>(transitions);
        if (unique.size() < 2) {
            return Optional.of("Sélectionnez au moins 2 transitions distinctes pour créer une convergence en OU.");
        }
        for (Transition t : unique) {
            List<Link> down = getDownwardLinks(t);
            boolean allowedExisting = existingTarget != null
                    && down.size() == 1
                    && down.getFirst().getChildComponent() == existingTarget;
            if (!down.isEmpty() && !allowedExisting) {
                return Optional.of("La transition T" + t.getNumber() + " possède déjà une étape aval : elle doit être en fin de branche ouverte.");
            }
        }
        List<Transition> transList = List.copyOf(unique);
        if (wouldCauseAndToOrUnsyncedConvergence(components, transList)) {
            return Optional.of("Convergence en OU interdite (CEI 60848) : ces transitions sont issues de branches parallèles en ET non synchronisées.");
        }
        if (!hasCommonOrDivergenceAncestor(components, transList)) {
            return Optional.of("Convergence en OU non conforme (CEI 60848) : les transitions doivent provenir de branches alternatives distinctes d'une même divergence en OU.");
        }
        return Optional.empty();
    }

    public static Optional<String> validateManualConvergenceLink(
            List<StepAndTransitionCommonBehavior> components,
            StepAndTransitionCommonBehavior source,
            StepAndTransitionCommonBehavior target
    ) {
        if (source == null || target == null || source == target) {
            return Optional.empty();
        }
        boolean isAscending = target.getY() < source.getY() + Component.UNIT_HEIGHT;
        if (isAscending) {
            return Optional.empty();
        }
        List<StepAndTransitionCommonBehavior> existingDownwardParents = target.getLinksFromParents().stream()
                .filter(l -> !l.isAscending())
                .map(Link::getParentComponent)
                .toList();
        if (existingDownwardParents.isEmpty()) {
            return Optional.empty();
        }
        if (target instanceof Transition targetTrans && source instanceof Step sourceStep) {
            List<Step> allParents = new ArrayList<>();
            for (var p : existingDownwardParents) {
                if (p instanceof Step s) allParents.add(s);
            }
            allParents.add(sourceStep);
            return validateAndConvergence(components, allParents, targetTrans);
        }
        if (target instanceof Step targetStep && source instanceof Transition sourceTrans) {
            List<Transition> allParents = new ArrayList<>();
            for (var p : existingDownwardParents) {
                if (p instanceof Transition t) allParents.add(t);
            }
            allParents.add(sourceTrans);
            return validateOrConvergence(components, allParents, targetStep);
        }
        return Optional.empty();
    }

    public static List<Step> findConvergenceCandidateSteps(
            List<StepAndTransitionCommonBehavior> components,
            Step primary
    ) {
        if (primary == null) return List.of();
        return components.stream()
                .filter(Step.class::isInstance)
                .map(Step.class::cast)
                .filter(s -> s != primary && validateAndConvergence(components, List.of(primary, s), null).isEmpty())
                .toList();
    }

    public static List<Transition> findConvergenceCandidateTransitions(
            List<StepAndTransitionCommonBehavior> components,
            Transition primary
    ) {
        if (primary == null) return List.of();
        return components.stream()
                .filter(Transition.class::isInstance)
                .map(Transition.class::cast)
                .filter(t -> t != primary && validateOrConvergence(components, List.of(primary, t), null).isEmpty())
                .toList();
    }

    public static boolean wouldCauseOrToAndDeadlock(
            List<StepAndTransitionCommonBehavior> components,
            List<Step> candidateSteps
    ) {
        if (candidateSteps == null || candidateSteps.size() < 2) return false;
        Set<Step> targetSet = new HashSet<>(candidateSteps);

        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step divStep) {
                List<Link> branchLinks = getDownwardLinks(divStep);
                if (branchLinks.size() < 2) continue;

                int matchedBranches = 0;
                for (Link bLink : branchLinks) {
                    if (bLink.getChildComponent() instanceof Transition startTrans) {
                        if (getDownwardLinks(startTrans).size() > 1) continue;
                        Set<Step> reachable = collectDownwardStepsWithoutAndDivergence(startTrans);
                        if (reachable.stream().anyMatch(targetSet::contains)) {
                            matchedBranches++;
                        }
                    }
                }
                if (matchedBranches >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean wouldCauseAndToOrUnsyncedConvergence(
            List<StepAndTransitionCommonBehavior> components,
            List<Transition> candidateTransitions
    ) {
        if (candidateTransitions == null || candidateTransitions.size() < 2) return false;
        Set<Transition> targetSet = new HashSet<>(candidateTransitions);

        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Transition divTrans) {
                List<Link> branchLinks = getDownwardLinks(divTrans);
                if (branchLinks.size() < 2) continue;

                int matchedBranches = 0;
                for (Link bLink : branchLinks) {
                    if (bLink.getChildComponent() instanceof Step startStep) {
                        Set<Transition> reachable = collectDownwardTransitionsWithoutAndConvergence(startStep);
                        if (reachable.stream().anyMatch(targetSet::contains)) {
                            matchedBranches++;
                        }
                    }
                }
                if (matchedBranches >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCommonAndDivergenceAncestor(
            List<StepAndTransitionCommonBehavior> components,
            List<Step> steps
    ) {
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Transition divTrans) {
                List<Link> branches = getDownwardLinks(divTrans);
                if (branches.size() < 2) continue;
                List<Set<Step>> branchStepSets = new ArrayList<>();
                for (Link bLink : branches) {
                    if (bLink.getChildComponent() instanceof Step startStep) {
                        branchStepSets.add(collectAllDownwardSteps(startStep));
                    } else {
                        branchStepSets.add(Set.of());
                    }
                }
                if (GraphBranchUtils.canAssignDistinctBranches(steps, branchStepSets)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCommonOrDivergenceAncestor(
            List<StepAndTransitionCommonBehavior> components,
            List<Transition> transitions
    ) {
        for (StepAndTransitionCommonBehavior comp : components) {
            if (comp instanceof Step divStep) {
                List<Link> branches = getDownwardLinks(divStep);
                if (branches.size() < 2) continue;
                List<Set<Transition>> branchTransSets = new ArrayList<>();
                for (Link bLink : branches) {
                    if (bLink.getChildComponent() instanceof Transition startTrans) {
                        branchTransSets.add(collectAllDownwardTransitions(startTrans));
                    } else {
                        branchTransSets.add(Set.of());
                    }
                }
                if (GraphBranchUtils.canAssignDistinctBranches(transitions, branchTransSets)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<Step> collectAllDownwardSteps(Step startStep) {
        Set<Step> visitedSteps = new LinkedHashSet<>();
        Queue<Step> queue = new ArrayDeque<>();
        visitedSteps.add(startStep);
        queue.add(startStep);
        while (!queue.isEmpty()) {
            Step s = queue.poll();
            for (Link tLink : getDownwardLinks(s)) {
                if (tLink.getChildComponent() instanceof Transition t) {
                    for (Link sLink : getDownwardLinks(t)) {
                        if (sLink.getChildComponent() instanceof Step nextS && visitedSteps.add(nextS)) {
                            queue.add(nextS);
                        }
                    }
                }
            }
        }
        return visitedSteps;
    }

    private static Set<Transition> collectAllDownwardTransitions(Transition startTrans) {
        Set<Transition> visitedTrans = new LinkedHashSet<>();
        Queue<Transition> queue = new ArrayDeque<>();
        visitedTrans.add(startTrans);
        queue.add(startTrans);
        while (!queue.isEmpty()) {
            Transition t = queue.poll();
            for (Link sLink : getDownwardLinks(t)) {
                if (sLink.getChildComponent() instanceof Step s) {
                    for (Link tLink : getDownwardLinks(s)) {
                        if (tLink.getChildComponent() instanceof Transition nextT && visitedTrans.add(nextT)) {
                            queue.add(nextT);
                        }
                    }
                }
            }
        }
        return visitedTrans;
    }

    private static Set<Step> collectDownwardStepsWithoutAndDivergence(Transition startTrans) {
        Set<Step> visitedSteps = new LinkedHashSet<>();
        Queue<Transition> queueTrans = new ArrayDeque<>();
        Set<Transition> visitedTrans = new HashSet<>();
        queueTrans.add(startTrans);
        visitedTrans.add(startTrans);

        while (!queueTrans.isEmpty()) {
            Transition t = queueTrans.poll();
            List<Link> nextLinks = getDownwardLinks(t);
            if (nextLinks.size() > 1) continue;
            for (Link l : nextLinks) {
                if (l.getChildComponent() instanceof Step s && visitedSteps.add(s)) {
                    for (Link nextTL : getDownwardLinks(s)) {
                        if (nextTL.getChildComponent() instanceof Transition nextT && visitedTrans.add(nextT)) {
                            queueTrans.add(nextT);
                        }
                    }
                }
            }
        }
        return visitedSteps;
    }

    private static Set<Transition> collectDownwardTransitionsWithoutAndConvergence(Step startStep) {
        Set<Transition> visitedTrans = new LinkedHashSet<>();
        Queue<Step> queueSteps = new ArrayDeque<>();
        Set<Step> visitedSteps = new HashSet<>();
        queueSteps.add(startStep);
        visitedSteps.add(startStep);

        while (!queueSteps.isEmpty()) {
            Step s = queueSteps.poll();
            for (Link l : getDownwardLinks(s)) {
                if (l.getChildComponent() instanceof Transition t) {
                    long downParents = t.getLinksFromParents().stream().filter(pl -> !pl.isAscending()).count();
                    if (downParents > 1) continue;
                    if (visitedTrans.add(t)) {
                        for (Link nextSL : getDownwardLinks(t)) {
                            if (nextSL.getChildComponent() instanceof Step nextS && visitedSteps.add(nextS)) {
                                queueSteps.add(nextS);
                            }
                        }
                    }
                }
            }
        }
        return visitedTrans;
    }
}
