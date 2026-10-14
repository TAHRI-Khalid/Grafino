package tk.tahri.grafino.model;

import java.util.ArrayList;
import java.util.List;

public class LinkModel {
    private String id;
    private String sourceId;
    private String targetId;
    private List<double[]> intermediatePoints = new ArrayList<>(); // [x, y] coordinates

    public LinkModel() {
    }

    public LinkModel(String id, String sourceId, String targetId) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public List<double[]> getIntermediatePoints() {
        return intermediatePoints;
    }

    public void setIntermediatePoints(List<double[]> intermediatePoints) {
        this.intermediatePoints = intermediatePoints != null ? intermediatePoints : new ArrayList<>();
    }
}
