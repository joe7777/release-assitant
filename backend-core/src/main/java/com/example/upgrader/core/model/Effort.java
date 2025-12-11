package com.example.upgrader.core.model;

import java.util.Map;

public class Effort {
    private Integer totalWorkpoints;
    private Map<String, Integer> workpointsByChangeId;

    public Effort() {
    }

    public Effort(Integer totalWorkpoints, Map<String, Integer> workpointsByChangeId) {
        this.totalWorkpoints = totalWorkpoints;
        this.workpointsByChangeId = workpointsByChangeId;
    }

    public Integer getTotalWorkpoints() {
        return totalWorkpoints;
    }

    public void setTotalWorkpoints(Integer totalWorkpoints) {
        this.totalWorkpoints = totalWorkpoints;
    }

    public Map<String, Integer> getWorkpointsByChangeId() {
        return workpointsByChangeId;
    }

    public void setWorkpointsByChangeId(Map<String, Integer> workpointsByChangeId) {
        this.workpointsByChangeId = workpointsByChangeId;
    }
}
