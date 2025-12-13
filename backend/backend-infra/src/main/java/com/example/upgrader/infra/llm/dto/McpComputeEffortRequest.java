package com.example.upgrader.infra.llm.dto;

import java.util.ArrayList;
import java.util.List;

public class McpComputeEffortRequest {
    private List<ChangeInput> changes = new ArrayList<>();
    private String llmModel;

    public McpComputeEffortRequest() {
    }

    public McpComputeEffortRequest(List<ChangeInput> changes) {
        this.changes = changes;
    }

    public List<ChangeInput> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeInput> changes) {
        this.changes = changes;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public static class ChangeInput {
        private String id;
        private String type;
        private String severity;

        public ChangeInput() {
        }

        public ChangeInput(String id, String type, String severity) {
            this.id = id;
            this.type = type;
            this.severity = severity;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }
}
