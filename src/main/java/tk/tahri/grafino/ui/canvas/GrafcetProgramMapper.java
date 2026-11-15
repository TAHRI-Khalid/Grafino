package tk.tahri.grafino.ui.canvas;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import tk.tahri.grafino.component.*;
import tk.tahri.grafino.model.*;

import java.time.Duration;
import java.util.*;

/**
 * Convertisseur bidirectionnel entre le graphe visuel JavaFX ({@link Step}, {@link Transition}, {@link Link})
 * et le modèle métier immuable CEI 61131-3 ({@link SfcProgram}).
 */
public final class GrafcetProgramMapper {

    private GrafcetProgramMapper() {
    }

    /**
     * Convertit la liste des composants graphiques et des variables en un {@link SfcProgram} normalisé.
     */
    public static SfcProgram toProgram(
            String programName,
            List<StepAndTransitionCommonBehavior> components,
            List<Variable> declaredVariables
    ) {
        String name = (programName == null || programName.isBlank()) ? "Main_SFC" : programName.trim();

        List<IecVariable> iecVars = new ArrayList<>();
        if (declaredVariables != null) {
            for (Variable v : declaredVariables) {
                iecVars.add(v.toIecVariable());
            }
        }

        List<SfcStep> sfcSteps = new ArrayList<>();
        List<SfcTransition> sfcTransitions = new ArrayList<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            switch (comp) {
                case Step step -> {
                    String stepId = "s" + step.getNumber();
                    String stepName = "Step" + step.getNumber();
                    List<SfcActionBlock> actions = new ArrayList<>();
                    for (Action a : step.getActions()) {
                        SfcQualifier qual = SfcQualifier.N;
                        if (a.qualifier() != null) {
                            try {
                                qual = SfcQualifier.valueOf(a.qualifier().trim().toUpperCase(Locale.ROOT));
                            } catch (Exception _) {
                            }
                        }
                        String varName = a.getDisplayText();
                        if (varName == null || varName.isBlank()) {
                            varName = "ACTION";
                        }
                        Duration duration = null;
                        String inlineCondition = null;
                        if (qual.isTimed()) {
                            duration = IecDurationParser.parse(a.parameter()).orElse(null);
                        } else if (a.hasParameter()) {
                            inlineCondition = a.parameter();
                        }
                        actions.add(new SfcActionBlock(qual, varName, duration, inlineCondition));
                    }
                    sfcSteps.add(new SfcStep(stepId, stepName, step.getNumber(), step.isInitial(), step.getX(), step.getY(), actions));
                }
                case Transition trans -> {
                    String transId = "t" + trans.getNumber();
                    String transName = "T" + trans.getNumber();
                    sfcTransitions.add(new SfcTransition(transId, transName, trans.getNumber(), trans.getReceptivity(), trans.getX(), trans.getY()));
                }
            }
        }

        List<SfcLink> sfcLinks = new ArrayList<>();
        Set<Link> uniqueLinks = new LinkedHashSet<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            uniqueLinks.addAll(comp.getLinksToChildren());
        }

        for (Link link : uniqueLinks) {
            String srcId = componentRefId(link.getParentComponent());
            String tgtId = componentRefId(link.getChildComponent());
            String linkId = "l_" + srcId + "_" + tgtId;
            List<SfcPoint> points = new ArrayList<>();
            if (link.isAscending()) {
                for (Point2D pt : link.getPath()) {
                    points.add(new SfcPoint(pt.getX(), pt.getY()));
                }
            }
            sfcLinks.add(new SfcLink(linkId, srcId, tgtId, points));
        }

        List<SfcBranch> sfcBranches = new ArrayList<>();
        for (StepAndTransitionCommonBehavior comp : components) {
            String compId = componentRefId(comp);
            List<Link> downOut = comp.getLinksToChildren().stream().filter(l -> !l.isAscending()).toList();
            if (downOut.size() >= 2) {
                SfcBranchType divType = (comp instanceof Transition) ? SfcBranchType.DIVERGENCE_AND : SfcBranchType.DIVERGENCE_OR;
                List<String> downIds = downOut.stream()
                        .map(l -> componentRefId(l.getChildComponent()))
                        .toList();
                sfcBranches.add(new SfcBranch("b_div_" + compId, divType.name().toLowerCase(Locale.ROOT) + "_" + compId,
                        divType, comp.getX(), comp.getY() + Component.UNIT_HEIGHT + 20, List.of(compId), downIds));
            }
            List<Link> downIn = comp.getLinksFromParents().stream().filter(l -> !l.isAscending()).toList();
            if (downIn.size() >= 2) {
                SfcBranchType convType = (comp instanceof Transition) ? SfcBranchType.CONVERGENCE_AND : SfcBranchType.CONVERGENCE_OR;
                List<String> upIds = downIn.stream()
                        .map(l -> componentRefId(l.getParentComponent()))
                        .toList();
                sfcBranches.add(new SfcBranch("b_conv_" + compId, convType.name().toLowerCase(Locale.ROOT) + "_" + compId,
                        convType, comp.getX(), comp.getY() - 20, upIds, List.of(compId)));
            }
        }

        return new SfcProgram(name, iecVars, sfcSteps, sfcTransitions, sfcBranches, sfcLinks);
    }

    /**
     * Reconstruit la liste de composants graphiques à partir d'un {@link SfcProgram}.
     */
    public static void populateFromProgram(
            SfcProgram program,
            GraphicsContext ctx,
            List<StepAndTransitionCommonBehavior> components,
            List<Variable> declaredVariables
    ) {
        Map<String, StepAndTransitionCommonBehavior> idMap = new HashMap<>();
        Map<Integer, Step> stepByNumber = new HashMap<>();
        Map<Integer, Transition> transByNumber = new HashMap<>();

        for (SfcStep sfcStep : program.steps()) {
            Step step = new Step(ctx);
            step.setInitial(sfcStep.isInitial());
            step.setNumber(sfcStep.number());
            step.position = new Point2D(sfcStep.x(), sfcStep.y());

            for (SfcActionBlock ab : sfcStep.actionBlocks()) {
                String q = ab.qualifier().name();
                String varName = ab.variableName();
                Variable matchedVar = null;
                if (declaredVariables != null) {
                    matchedVar = declaredVariables.stream()
                            .filter(v -> v.getName().equalsIgnoreCase(varName))
                            .findFirst().orElse(null);
                }
                String param = (ab.duration() != null) ? ab.formattedDuration() : ab.inlineCondition();
                step.getActions().add(new Action(q, matchedVar, param, (matchedVar == null) ? varName : null));
            }

            components.add(step);
            idMap.put(sfcStep.id(), step);
            idMap.put("s" + sfcStep.number(), step);
            stepByNumber.put(sfcStep.number(), step);
        }

        for (SfcTransition sfcTrans : program.transitions()) {
            Transition trans = new Transition(ctx);
            trans.setNumber(sfcTrans.number());
            trans.setReceptivity(sfcTrans.conditionST());
            trans.position = new Point2D(sfcTrans.x(), sfcTrans.y());

            components.add(trans);
            idMap.put(sfcTrans.id(), trans);
            idMap.put("t" + sfcTrans.number(), trans);
            transByNumber.put(sfcTrans.number(), trans);
        }

        for (SfcLink sfcLink : program.links()) {
            StepAndTransitionCommonBehavior src = idMap.get(sfcLink.sourceId());
            if (src == null) {
                src = resolveComponentByReference(sfcLink.sourceId(), stepByNumber, transByNumber);
            }
            StepAndTransitionCommonBehavior tgt = idMap.get(sfcLink.targetId());
            if (tgt == null) {
                tgt = resolveComponentByReference(sfcLink.targetId(), stepByNumber, transByNumber);
            }

            if (src != null && tgt != null) {
                Link link = Link.between(ctx, src, tgt);
                if (link.isAscending()) {
                    extractAscendingGutterX(sfcLink.intermediatePoints()).ifPresent(customGutterX -> {
                        link.setCustomGutterX(customGutterX);
                        link.update();
                    });
                }
            }
        }

        int maxStep = program.steps().stream().mapToInt(SfcStep::number).max().orElse(0);
        Step.resetCounter(maxStep);
        int maxTrans = program.transitions().stream().mapToInt(SfcTransition::number).max().orElse(0);
        Transition.resetCounter(maxTrans);
    }

    private static String componentRefId(StepAndTransitionCommonBehavior comp) {
        return switch (comp) {
            case Step s -> "s" + s.getNumber();
            case Transition t -> "t" + t.getNumber();
        };
    }

    private static OptionalDouble extractAscendingGutterX(List<SfcPoint> points) {
        if (points == null || points.size() < 2) {
            return OptionalDouble.empty();
        }
        for (int i = 0; i < points.size() - 1; i++) {
            SfcPoint from = points.get(i);
            SfcPoint to = points.get(i + 1);
            if (Math.abs(from.x() - to.x()) < 1.0 && from.y() > to.y()) {
                return OptionalDouble.of(from.x());
            }
        }
        if (points.size() >= 6) {
            return OptionalDouble.of(points.get(2).x());
        }
        return OptionalDouble.of(points.get(1).x());
    }

    private static StepAndTransitionCommonBehavior resolveComponentByReference(
            String ref, Map<Integer, Step> stepByNumber, Map<Integer, Transition> transByNumber) {
        if (ref == null || ref.isBlank()) return null;
        String clean = ref.trim();
        String digits = clean.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            int num = Integer.parseInt(digits);
            char first = Character.toLowerCase(clean.charAt(0));
            if (first == 's') {
                return stepByNumber.get(num);
            } else if (first == 't') {
                return transByNumber.get(num);
            }
            if (stepByNumber.containsKey(num)) return stepByNumber.get(num);
            return transByNumber.get(num);
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
