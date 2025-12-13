package com.example.upgrader.infra.entity;

import com.example.upgrader.core.model.AnalysisStatus;
import com.example.upgrader.core.model.DependencyScope;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column
    private String springVersionCurrent;

    @Column(nullable = false)
    private String springVersionTarget;

    @Column
    private String llmModel;

    @Enumerated(EnumType.STRING)
    @Column
    private DependencyScope dependencyScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    private Integer totalWorkpoints;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "analysis", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Change> changes = new ArrayList<>();

    @OneToOne(mappedBy = "analysis", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    private EffortSummary effortSummary;

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

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public DependencyScope getDependencyScope() {
        return dependencyScope;
    }

    public void setDependencyScope(DependencyScope dependencyScope) {
        this.dependencyScope = dependencyScope;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public Integer getTotalWorkpoints() {
        return totalWorkpoints;
    }

    public void setTotalWorkpoints(Integer totalWorkpoints) {
        this.totalWorkpoints = totalWorkpoints;
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

    public List<Change> getChanges() {
        return changes;
    }

    public void setChanges(List<Change> changes) {
        this.changes = changes;
    }

    public EffortSummary getEffortSummary() {
        return effortSummary;
    }

    public void setEffortSummary(EffortSummary effortSummary) {
        this.effortSummary = effortSummary;
    }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Analysis analysis = (Analysis) o;
        return Objects.equals(id, analysis.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
