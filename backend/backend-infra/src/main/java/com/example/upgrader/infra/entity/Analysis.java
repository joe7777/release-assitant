package com.example.upgrader.infra.entity;

import com.example.upgrader.core.model.AnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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

    @Column(nullable = false)
    private String springVersionCurrent;

    @Column(nullable = false)
    private String springVersionTarget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    private Integer totalWorkpoints;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

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
