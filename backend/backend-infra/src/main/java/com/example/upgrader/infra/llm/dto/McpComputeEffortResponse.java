package com.example.upgrader.infra.llm.dto;

import java.util.ArrayList;
import java.util.List;

public class McpComputeEffortResponse {
    private int totalWorkpoints;
    private List<Workpoint> byChange = new ArrayList<>();

    public int getTotalWorkpoints() {
        return totalWorkpoints;
    }

    public void setTotalWorkpoints(int totalWorkpoints) {
        this.totalWorkpoints = totalWorkpoints;
    }

    public List<Workpoint> getByChange() {
        return byChange;
    }

    public void setByChange(List<Workpoint> byChange) {
        this.byChange = byChange;
    }

    public static class Workpoint {
        private String changeId;
        private int workpoints;
        private String reason;

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
}
