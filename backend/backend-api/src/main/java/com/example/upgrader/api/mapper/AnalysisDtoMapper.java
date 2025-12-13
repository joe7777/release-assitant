package com.example.upgrader.api.mapper;

import com.example.upgrader.api.dto.AnalysisDetailResponse;
import com.example.upgrader.api.dto.AnalysisSummaryResponse;
import com.example.upgrader.api.dto.ChangeResponse;
import com.example.upgrader.api.dto.CreateAnalysisRequest;
import com.example.upgrader.core.command.CreateAnalysisCommand;
import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.model.Change;
import com.example.upgrader.core.model.DependencyScope;
import com.example.upgrader.core.model.Effort;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnalysisDtoMapper {

    private AnalysisDtoMapper() {
    }

    public static CreateAnalysisCommand toCommand(CreateAnalysisRequest request) {
        CreateAnalysisCommand command = new CreateAnalysisCommand();
        command.setProjectGitUrl(request.getProjectGitUrl());
        command.setProjectName(request.getProjectName());
        command.setBranch(request.getBranch());
        command.setSpringVersionTarget(request.getSpringVersionTarget());
        command.setLlmModel(request.getLlmModel());
        command.setGitTokenId(request.getGitTokenId());
        command.setDependencyScope(resolveDependencyScope(request.getDependencyScope()));
        return command;
    }

    public static AnalysisSummaryResponse toSummaryResponse(Analysis analysis) {
        AnalysisSummaryResponse response = new AnalysisSummaryResponse();
        response.setId(analysis.getId());
        response.setProjectName(analysis.getProject() != null ? analysis.getProject().getName() : null);
        response.setSpringVersionCurrent(analysis.getSpringVersionCurrent());
        response.setSpringVersionTarget(analysis.getSpringVersionTarget());
        response.setLlmModel(analysis.getLlmModel());
        response.setDependencyScope(analysis.getDependencyScope() != null ? analysis.getDependencyScope().name() : null);
        response.setStatus(analysis.getStatus() != null ? analysis.getStatus().name() : null);
        response.setTotalWorkpoints(extractTotalWorkpoints(analysis.getEffort()));
        response.setCreatedAt(analysis.getCreatedAt() != null ? analysis.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
        return response;
    }

    public static List<AnalysisSummaryResponse> toSummaryList(List<Analysis> analyses) {
        return analyses.stream().map(AnalysisDtoMapper::toSummaryResponse).collect(Collectors.toList());
    }

    public static AnalysisDetailResponse toDetailResponse(Analysis analysis) {
        AnalysisDetailResponse response = new AnalysisDetailResponse();
        response.setId(analysis.getId());
        response.setProjectName(analysis.getProject() != null ? analysis.getProject().getName() : null);
        response.setSpringVersionCurrent(analysis.getSpringVersionCurrent());
        response.setSpringVersionTarget(analysis.getSpringVersionTarget());
        response.setLlmModel(analysis.getLlmModel());
        response.setDependencyScope(analysis.getDependencyScope() != null ? analysis.getDependencyScope().name() : null);
        response.setStatus(analysis.getStatus() != null ? analysis.getStatus().name() : null);
        response.setTotalWorkpoints(extractTotalWorkpoints(analysis.getEffort()));
        response.setCreatedAt(analysis.getCreatedAt() != null ? analysis.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null);
        response.setChanges(mapChanges(analysis.getChanges()));
        response.setEffort(extractEffortMap(analysis.getEffort()));
        return response;
    }

    private static Integer extractTotalWorkpoints(Effort effort) {
        return effort != null ? effort.getTotalWorkpoints() : null;
    }

    private static DependencyScope resolveDependencyScope(String rawScope) {
        if (rawScope == null || rawScope.isBlank()) {
            return DependencyScope.ALL;
        }
        try {
            return DependencyScope.valueOf(rawScope);
        } catch (IllegalArgumentException ex) {
            return DependencyScope.ALL;
        }
    }

    private static Map<String, Integer> extractEffortMap(Effort effort) {
        return effort != null && effort.getWorkpointsByChange() != null ? effort.getWorkpointsByChange() : Collections.emptyMap();
    }

    private static List<ChangeResponse> mapChanges(List<Change> changes) {
        if (changes == null) {
            return Collections.emptyList();
        }
        return changes.stream().map(AnalysisDtoMapper::toChangeResponse).collect(Collectors.toList());
    }

    private static ChangeResponse toChangeResponse(Change change) {
        ChangeResponse response = new ChangeResponse();
        response.setId(change.getId());
        response.setType(change.getType());
        response.setSeverity(change.getSeverity());
        response.setTitle(change.getTitle());
        response.setDescription(change.getDescription());
        response.setFilePath(change.getFilePath());
        response.setSymbol(change.getSymbol());
        response.setWorkpoints(change.getWorkpoints());
        return response;
    }
}
