package com.example.upgrader.api.dto;

import java.util.List;
import java.util.Map;

public class AnalysisDetailResponse {
    private Long id;
    private String projectName;
    private String springVersionCurrent;
    private String springVersionTarget;
    private String status;
    private Integer totalWorkpoints;
    private String createdAt;
    private List<ChangeResponse> changes;
    private Map<String, Integer> effort;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getSpringVersionCurrent() {
        return springVersionCurrent;
    }

    public void setSpringVersionCurrent(String springVersionCurrent) {
        this.springVersionCurrent = springVersionCurrent;
    }

    public String getSpringVersionTarget() {
        return springVersionTarget;
    }

    public void setSpringVersionTarget(String springVersionTarget) {
        this.springVersionTarget = springVersionTarget;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalWorkpoints() {
        return totalWorkpoints;
    }

    public void setTotalWorkpoints(Integer totalWorkpoints) {
        this.totalWorkpoints = totalWorkpoints;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<ChangeResponse> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeResponse> changes) {
        this.changes = changes;
    }

    public Map<String, Integer> getEffort() {
        return effort;
    }

    public void setEffort(Map<String, Integer> effort) {
        this.effort = effort;
    }
}
