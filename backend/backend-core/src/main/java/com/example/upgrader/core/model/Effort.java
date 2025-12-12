package com.example.upgrader.core.model;

import java.util.Map;

public class Effort {
    private int totalWorkpoints;
    private Map<String, Integer> workpointsByChange;

    public int getTotalWorkpoints() {
        return totalWorkpoints;
    }

    public void setTotalWorkpoints(int totalWorkpoints) {
        this.totalWorkpoints = totalWorkpoints;
    }

    public Map<String, Integer> getWorkpointsByChange() {
        return workpointsByChange;
    }

    public void setWorkpointsByChange(Map<String, Integer> workpointsByChange) {
        this.workpointsByChange = workpointsByChange;
    }
}
