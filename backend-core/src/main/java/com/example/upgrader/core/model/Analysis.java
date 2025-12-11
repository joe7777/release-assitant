package com.example.upgrader.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Analysis {
    private Long id;
    private Project project;
    private String springVersionCurrent;
    private String springVersionTarget;
    private AnalysisStatus status;
    private List<Change> changes = new ArrayList<>();
    private Effort effort;
    private Instant createdAt;
    private Instant updatedAt;

    public Analysis() {
    }

    public Analysis(Long id, Project project, String springVersionCurrent, String springVersionTarget,
                    AnalysisStatus status, List<Change> changes, Effort effort, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.project = project;
        this.springVersionCurrent = springVersionCurrent;
        this.springVersionTarget = springVersionTarget;
        this.status = status;
        if (changes != null) {
            this.changes = changes;
        }
        this.effort = effort;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
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

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public List<Change> getChanges() {
        return changes;
    }

    public void setChanges(List<Change> changes) {
        this.changes = changes;
    }

    public Effort getEffort() {
        return effort;
    }

    public void setEffort(Effort effort) {
        this.effort = effort;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
