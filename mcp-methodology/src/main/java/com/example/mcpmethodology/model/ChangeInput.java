package com.example.mcpmethodology.model;

import java.util.Map;

public class ChangeInput {

    private String id;
    private String type;
    private String severity;
    private Map<String, Object> metadata;

    public ChangeInput() {
    }

    public ChangeInput(String id, String type, String severity, Map<String, Object> metadata) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.metadata = metadata;
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
