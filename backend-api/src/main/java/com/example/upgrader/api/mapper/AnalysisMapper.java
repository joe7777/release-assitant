package com.example.upgrader.api.mapper;

import com.example.upgrader.api.dto.AnalysisDetailResponse;
import com.example.upgrader.api.dto.AnalysisSummaryResponse;
import com.example.upgrader.api.dto.ChangeResponse;
import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.model.Change;
import com.example.upgrader.core.model.Effort;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnalysisMapper {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public AnalysisSummaryResponse toSummary(Analysis analysis) {
        AnalysisSummaryResponse response = new AnalysisSummaryResponse();
        response.setId(analysis.getId());
        response.setProjectName(analysis.getProject() != null ? analysis.getProject().getName() : null);
        response.setSpringVersionCurrent(analysis.getSpringVersionCurrent());
        response.setSpringVersionTarget(analysis.getSpringVersionTarget());
        response.setStatus(analysis.getStatus() != null ? analysis.getStatus().name() : null);
        response.setTotalWorkpoints(extractTotalWorkpoints(analysis.getEffort()));
        response.setCreatedAt(formatInstant(analysis.getCreatedAt()));
        return response;
    }

    public AnalysisDetailResponse toDetail(Analysis analysis) {
        AnalysisDetailResponse response = new AnalysisDetailResponse();
        response.setId(analysis.getId());
        response.setProjectName(analysis.getProject() != null ? analysis.getProject().getName() : null);
        response.setSpringVersionCurrent(analysis.getSpringVersionCurrent());
        response.setSpringVersionTarget(analysis.getSpringVersionTarget());
        response.setStatus(analysis.getStatus() != null ? analysis.getStatus().name() : null);
        response.setTotalWorkpoints(extractTotalWorkpoints(analysis.getEffort()));
        response.setCreatedAt(formatInstant(analysis.getCreatedAt()));
        response.setEffortByChange(extractEffortByChange(analysis.getEffort()));
        if (analysis.getChanges() != null) {
            response.setChanges(analysis.getChanges().stream().map(this::toChange).collect(Collectors.toList()));
        }
        return response;
    }

    private ChangeResponse toChange(Change change) {
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

    private Integer extractTotalWorkpoints(Effort effort) {
        return effort != null ? effort.getTotalWorkpoints() : null;
    }

    private Map<String, Integer> extractEffortByChange(Effort effort) {
        return effort != null ? effort.getWorkpointsByChangeId() : null;
    }

    private String formatInstant(java.time.Instant instant) {
        return instant != null ? ISO_FORMATTER.format(instant) : null;
    }
}
