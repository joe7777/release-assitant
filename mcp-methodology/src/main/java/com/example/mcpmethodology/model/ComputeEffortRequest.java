package com.example.mcpmethodology.model;

import java.util.Collections;
import java.util.List;

public class ComputeEffortRequest {

    private List<ChangeInput> changes;

    public ComputeEffortRequest() {
    }

    public ComputeEffortRequest(List<ChangeInput> changes) {
        this.changes = changes;
    }

    public List<ChangeInput> getChanges() {
        return changes == null ? Collections.emptyList() : changes;
    }

    public void setChanges(List<ChangeInput> changes) {
        this.changes = changes;
    }
}
