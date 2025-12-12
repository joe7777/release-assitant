package com.example.upgrader.infra.llm;

import com.example.upgrader.core.llm.LlmAnalysisResult;
import com.example.upgrader.core.llm.LlmClient;
import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.model.ChangeType;
import com.example.upgrader.infra.llm.dto.McpAnalyzeRequest;
import com.example.upgrader.infra.llm.dto.McpAnalyzeResponse;
import com.example.upgrader.infra.llm.dto.McpComputeEffortRequest;
import com.example.upgrader.infra.llm.dto.McpComputeEffortResponse;
import com.example.upgrader.infra.llm.dto.McpSearchRequest;
import com.example.upgrader.infra.llm.dto.McpSearchResponse;
import com.example.upgrader.infra.llm.dto.SearchResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class McpLlmClient implements LlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpLlmClient.class);

    private final RestClient analyzerClient;
    private final RestClient knowledgeClient;
    private final RestClient methodologyClient;

    public McpLlmClient(RestClient.Builder builder, McpProperties properties) {
        this.analyzerClient = builder.baseUrl(properties.projectAnalyzerUrl()).build();
        this.knowledgeClient = builder.baseUrl(properties.knowledgeRagUrl()).build();
        this.methodologyClient = builder.baseUrl(properties.methodologyUrl()).build();
    }

    @Override
    public LlmAnalysisResult runAnalysis(Analysis analysis) {
        LlmAnalysisResult result = new LlmAnalysisResult();
        result.setAnalysisId(String.valueOf(analysis.getId()));
        result.setSpringVersionTarget(analysis.getSpringVersionTarget());

        McpAnalyzeResponse analyzeResponse = invokeProjectAnalyzer(analysis);
        if (analyzeResponse != null) {
            result.setSpringVersionCurrent(analyzeResponse.getSpringVersionCurrent());
        }

        List<LlmAnalysisResult.SpringChange> springChanges = searchReleaseNotes(result.getSpringVersionCurrent(),
                analysis.getSpringVersionTarget());
        result.setSpringChanges(springChanges);

        LlmAnalysisResult.EffortResult effortResult = computeEffort(springChanges);
        result.setEffort(effortResult);

        return result;
    }

    private McpAnalyzeResponse invokeProjectAnalyzer(Analysis analysis) {
        McpAnalyzeRequest payload = new McpAnalyzeRequest(
                analysis.getProject().getGitUrl(),
                analysis.getProject().getBranch(),
                analysis.getProject().getGitTokenId()
        );

        try {
            return analyzerClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(McpAnalyzeResponse.class);
        } catch (Exception ex) {
            LOGGER.warn("Project analyzer call failed", ex);
            return null;
        }
    }

    private List<LlmAnalysisResult.SpringChange> searchReleaseNotes(String currentVersion, String targetVersion) {
        McpSearchRequest request = new McpSearchRequest();
        request.setQuery(String.format("Spring Boot migration from %s to %s", Objects.toString(currentVersion, "unknown"),
                Objects.toString(targetVersion, "unknown")));
        request.setTopK(5);
        request.getFilters().setSourceType("SPRING_RELEASE_NOTE");
        request.getFilters().setFromVersion(currentVersion);
        request.getFilters().setToVersion(targetVersion);

        try {
            McpSearchResponse response = knowledgeClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(McpSearchResponse.class);
            return mapSearchResults(response);
        } catch (Exception ex) {
            LOGGER.warn("Knowledge RAG search failed", ex);
            return List.of();
        }
    }

    private List<LlmAnalysisResult.SpringChange> mapSearchResults(McpSearchResponse response) {
        if (response == null || response.getResults() == null) {
            return List.of();
        }
        List<LlmAnalysisResult.SpringChange> changes = new ArrayList<>();
        int index = 1;
        for (SearchResultItem item : response.getResults()) {
            LlmAnalysisResult.SpringChange change = new LlmAnalysisResult.SpringChange();
            change.setId("SPRING-" + index++);
            change.setTitle(item.getLibrary() != null ? item.getLibrary() : "Spring Boot change");
            change.setDescription(item.getContent());
            change.setSeverity(mapSeverity(item));
            changes.add(change);
        }
        return changes;
    }

    private String mapSeverity(SearchResultItem item) {
        double score = item != null ? item.getScore() : 0.0;
        if (score >= 0.75) {
            return "HIGH";
        }
        if (score >= 0.5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private LlmAnalysisResult.EffortResult computeEffort(List<LlmAnalysisResult.SpringChange> springChanges) {
        if (springChanges == null || springChanges.isEmpty()) {
            return null;
        }

        McpComputeEffortRequest request = new McpComputeEffortRequest(
                springChanges.stream()
                        .map(change -> new McpComputeEffortRequest.ChangeInput(change.getId(), ChangeType.SPRING.name(), change.getSeverity()))
                        .collect(Collectors.toList())
        );

        try {
            McpComputeEffortResponse response = methodologyClient.post()
                    .uri("/compute-effort")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(McpComputeEffortResponse.class);
            return mapEffortResponse(response);
        } catch (Exception ex) {
            LOGGER.warn("Methodology compute-effort failed", ex);
            return null;
        }
    }

    private LlmAnalysisResult.EffortResult mapEffortResponse(McpComputeEffortResponse response) {
        if (response == null) {
            return null;
        }
        LlmAnalysisResult.EffortResult effortResult = new LlmAnalysisResult.EffortResult();
        effortResult.setTotalWorkpoints(response.getTotalWorkpoints());
        effortResult.setByChange(response.getByChange().stream()
                .map(item -> new LlmAnalysisResult.Workpoint(item.getChangeId(), item.getWorkpoints()))
                .collect(Collectors.toList()));
        return effortResult;
    }
}
