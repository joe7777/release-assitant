package com.example.upgrader.infra.mapper;

import com.example.upgrader.core.model.Effort;
import com.example.upgrader.infra.entity.Change;
import com.example.upgrader.infra.entity.Analysis;
import com.example.upgrader.infra.entity.EffortSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AnalysisEntityMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AnalysisEntityMapper() {
    }

    public static Analysis toEntity(com.example.upgrader.core.model.Analysis model, com.example.upgrader.infra.entity.Project project) {
        Analysis entity = new Analysis();
        copyToEntity(model, project, entity);
        return entity;
    }

    public static void copyToEntity(com.example.upgrader.core.model.Analysis model,
                                    com.example.upgrader.infra.entity.Project project,
                                    Analysis entity) {
        if (model == null || entity == null) {
            return;
        }
        entity.setId(model.getId());
        entity.setProject(project);
        entity.setSpringVersionCurrent(model.getSpringVersionCurrent());
        entity.setSpringVersionTarget(model.getSpringVersionTarget());
        entity.setStatus(model.getStatus());
        if (model.getCreatedAt() != null && entity.getCreatedAt() == null) {
            entity.setCreatedAt(model.getCreatedAt().toInstant(ZoneOffset.UTC));
            entity.setUpdatedAt(model.getCreatedAt().toInstant(ZoneOffset.UTC));
        }
        entity.getChanges().clear();
        entity.getChanges().addAll(mapChangesToEntity(model.getChanges(), entity));
        entity.setEffortSummary(mapEffortToEntity(model.getEffort(), entity));
    }

    public static com.example.upgrader.core.model.Analysis toModel(Analysis entity) {
        if (entity == null) {
            return null;
        }
        com.example.upgrader.core.model.Analysis model = new com.example.upgrader.core.model.Analysis();
        model.setId(entity.getId());
        model.setProject(ProjectEntityMapper.toModel(entity.getProject()));
        model.setSpringVersionCurrent(entity.getSpringVersionCurrent());
        model.setSpringVersionTarget(entity.getSpringVersionTarget());
        model.setStatus(entity.getStatus());
        if (entity.getCreatedAt() != null) {
            model.setCreatedAt(LocalDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
        }
        model.setChanges(mapChangesToModel(entity.getChanges()));
        model.setEffort(mapEffortToModel(entity.getEffortSummary()));
        return model;
    }

    public static List<Change> mapChangesToEntity(List<com.example.upgrader.core.model.Change> changes, Analysis analysis) {
        if (changes == null) {
            return List.of();
        }
        return changes.stream().filter(Objects::nonNull).map(change -> toEntityChange(change, analysis)).collect(Collectors.toList());
    }

    private static Change toEntityChange(com.example.upgrader.core.model.Change change, Analysis analysis) {
        Change entity = new Change();
        entity.setAnalysis(analysis);
        entity.setChangeId(change.getId());
        entity.setType(change.getType());
        entity.setSeverity(change.getSeverity());
        entity.setTitle(change.getTitle());
        entity.setDescription(change.getDescription());
        entity.setFilePath(change.getFilePath());
        entity.setSymbol(change.getSymbol());
        entity.setWorkpoints(change.getWorkpoints());
        return entity;
    }

    private static List<com.example.upgrader.core.model.Change> mapChangesToModel(List<Change> changeEntities) {
        if (changeEntities == null) {
            return List.of();
        }
        return changeEntities.stream()
                .filter(Objects::nonNull)
                .map(AnalysisEntityMapper::toModelChange)
                .collect(Collectors.toList());
    }

    private static com.example.upgrader.core.model.Change toModelChange(Change entity) {
        com.example.upgrader.core.model.Change change = new com.example.upgrader.core.model.Change();
        change.setId(entity.getChangeId());
        change.setType(entity.getType());
        change.setSeverity(entity.getSeverity());
        change.setTitle(entity.getTitle());
        change.setDescription(entity.getDescription());
        change.setFilePath(entity.getFilePath());
        change.setSymbol(entity.getSymbol());
        change.setWorkpoints(entity.getWorkpoints());
        return change;
    }

    public static EffortSummary mapEffortToEntity(Effort effort, Analysis analysis) {
        if (effort == null) {
            return null;
        }
        EffortSummary summary = new EffortSummary();
        summary.setAnalysis(analysis);
        summary.setTotalWorkpoints(effort.getTotalWorkpoints());
        summary.setDetails(writeDetails(effort.getWorkpointsByChange()));
        return summary;
    }

    private static Effort mapEffortToModel(EffortSummary effortSummary) {
        if (effortSummary == null) {
            return null;
        }
        Effort effort = new Effort();
        effort.setTotalWorkpoints(effortSummary.getTotalWorkpoints());
        effort.setWorkpointsByChange(readDetails(effortSummary.getDetails()));
        return effort;
    }

    private static String writeDetails(Map<String, Integer> workpoints) {
        if (workpoints == null || workpoints.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(workpoints);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize effort details", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> readDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(details, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize effort details", e);
        }
    }
}
