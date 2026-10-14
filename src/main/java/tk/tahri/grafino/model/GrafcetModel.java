package tk.tahri.grafino.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GrafcetModel {
    private String name = "Nouveau Grafcet";
    private final List<StepModel> steps = new ArrayList<>();
    private final List<TransitionModel> transitions = new ArrayList<>();
    private final List<LinkModel> links = new ArrayList<>();
    private final List<VariableModel> variables = new ArrayList<>();

    public GrafcetModel() {
    }

    public GrafcetModel(String name) {
        this.name = name != null ? name : "Nouveau Grafcet";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<StepModel> getSteps() {
        return steps;
    }

    public List<TransitionModel> getTransitions() {
        return transitions;
    }

    public List<LinkModel> getLinks() {
        return links;
    }

    public List<VariableModel> getVariables() {
        return variables;
    }

    public Optional<StepModel> findStepById(String id) {
        return steps.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public Optional<TransitionModel> findTransitionById(String id) {
        return transitions.stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public boolean isStep(String id) {
        return findStepById(id).isPresent();
    }

    public boolean isTransition(String id) {
        return findTransitionById(id).isPresent();
    }

    public void clear() {
        steps.clear();
        transitions.clear();
        links.clear();
        variables.clear();
    }
}
