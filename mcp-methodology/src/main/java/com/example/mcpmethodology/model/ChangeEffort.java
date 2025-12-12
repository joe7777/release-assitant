package com.example.mcpmethodology.model;

public class ChangeEffort {

    private String changeId;
    private int workpoints;
    private String reason;

    public ChangeEffort() {
    }

    public ChangeEffort(String changeId, int workpoints, String reason) {
        this.changeId = changeId;
        this.workpoints = workpoints;
        this.reason = reason;
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public int getWorkpoints() {
        return workpoints;
    }

    public void setWorkpoints(int workpoints) {
        this.workpoints = workpoints;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
