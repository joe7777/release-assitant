package com.example.upgrader.api.dto;

public class AnalysisSummaryResponse {
    private Long id;
    private String projectName;
    private String springVersionCurrent;
    private String springVersionTarget;
    private String llmModel;
    private String dependencyScope;
    private String status;
    private Integer totalWorkpoints;
    private String createdAt;

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

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getDependencyScope() {
        return dependencyScope;
    }

    public void setDependencyScope(String dependencyScope) {
        this.dependencyScope = dependencyScope;
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
}
