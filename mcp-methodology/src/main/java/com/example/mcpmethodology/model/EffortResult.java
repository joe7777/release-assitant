package com.example.mcpmethodology.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EffortResult {

    private int totalWorkpoints;
    private List<ChangeEffort> byChange = new ArrayList<>();

    public EffortResult() {
    }

    public EffortResult(int totalWorkpoints, List<ChangeEffort> byChange) {
        this.totalWorkpoints = totalWorkpoints;
        if (byChange != null) {
            this.byChange = new ArrayList<>(byChange);
        }
    }

    public int getTotalWorkpoints() {
        return totalWorkpoints;
    }

    public void setTotalWorkpoints(int totalWorkpoints) {
        this.totalWorkpoints = totalWorkpoints;
    }

    public List<ChangeEffort> getByChange() {
        return Collections.unmodifiableList(byChange);
    }

    public void setByChange(List<ChangeEffort> byChange) {
        if (byChange == null) {
            this.byChange = new ArrayList<>();
        } else {
            this.byChange = new ArrayList<>(byChange);
        }
    }
}
