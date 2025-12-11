package com.example.upgrader.core.service;

import com.example.upgrader.core.command.CreateAnalysisCommand;
import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.model.AnalysisStatus;
import com.example.upgrader.core.model.Change;
import com.example.upgrader.core.model.Effort;
import com.example.upgrader.core.model.Project;
import com.example.upgrader.core.model.llm.LlmAnalysisResult;
import com.example.upgrader.core.repository.AnalysisRepository;
import com.example.upgrader.core.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;

    public AnalysisService(ProjectRepository projectRepository, AnalysisRepository analysisRepository) {
        this.projectRepository = projectRepository;
        this.analysisRepository = analysisRepository;
    }

    public Analysis createAnalysis(CreateAnalysisCommand command) {
        Project project = projectRepository.findByGitUrl(command.getProjectGitUrl())
                .orElseGet(() -> projectRepository.save(new Project(null, command.getProjectName(),
                        command.getProjectGitUrl(), command.getBranch(), command.getGitTokenId())));

        Analysis analysis = new Analysis();
        analysis.setProject(project);
        analysis.setSpringVersionTarget(command.getSpringVersionTarget());
        analysis.setStatus(AnalysisStatus.PENDING);
        Instant now = Instant.now();
        analysis.setCreatedAt(now);
        analysis.setUpdatedAt(now);
        return analysisRepository.save(analysis);
    }

    public Analysis getAnalysisSummary(Long id) {
        return analysisRepository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    public Analysis getAnalysisDetail(Long id) {
        return analysisRepository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    public List<Analysis> listAnalyses() {
        return analysisRepository.findAll();
    }

    public Analysis updateAnalysisFromLLMResult(Long id, LlmAnalysisResult result) {
        Analysis analysis = analysisRepository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
        analysis.setSpringVersionCurrent(result.getSpringVersionCurrent());
        analysis.setSpringVersionTarget(Optional.ofNullable(result.getSpringVersionTarget())
                .orElse(analysis.getSpringVersionTarget()));

        Map<String, Integer> workpointMap = mapWorkpoints(result.getEffort());
        List<Change> changes = new ArrayList<>();
        collectSpringChanges(result, workpointMap, changes);
        collectJavaChanges(result, workpointMap, changes);
        collectLibraryChanges(result, workpointMap, changes);
        collectCodeImpacts(result, workpointMap, changes);
        collectSecurityIssues(result, workpointMap, changes);

        analysis.setChanges(changes);
        analysis.setEffort(new Effort(result.getEffort() != null ? result.getEffort().getTotalWorkpoints() : null,
                workpointMap.isEmpty() ? null : workpointMap));
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setUpdatedAt(Instant.now());

        return analysisRepository.save(analysis);
    }

    private Map<String, Integer> mapWorkpoints(LlmAnalysisResult.Effort effort) {
        if (effort == null || effort.getByChange() == null) {
            return new HashMap<>();
        }
        return effort.getByChange().stream()
                .filter(entry -> entry.getChangeId() != null && entry.getWorkpoints() != null)
                .collect(Collectors.toMap(LlmAnalysisResult.EffortByChange::getChangeId,
                        LlmAnalysisResult.EffortByChange::getWorkpoints, (a, b) -> b));
    }

    private void collectSpringChanges(LlmAnalysisResult result, Map<String, Integer> workpointMap, List<Change> changes) {
        if (result.getSpringChanges() == null) {
            return;
        }
        for (LlmAnalysisResult.SpringChange springChange : result.getSpringChanges()) {
            if (springChange.getAffectedComponents() != null && !springChange.getAffectedComponents().isEmpty()) {
                int index = 1;
                for (LlmAnalysisResult.AffectedComponent component : springChange.getAffectedComponents()) {
                    String changeId = springChange.getId() + "-" + index++;
                    changes.add(new Change(changeId, "SPRING_CHANGE", springChange.getSeverity(), springChange.getTitle(),
                            component.getDetails(), component.getFilePath(), component.getSymbol(),
                            resolveWorkpoints(springChange.getId(), workpointMap)));
                }
            } else {
                changes.add(new Change(springChange.getId(), "SPRING_CHANGE", springChange.getSeverity(),
                        springChange.getTitle(), springChange.getDescription(), null, null,
                        resolveWorkpoints(springChange.getId(), workpointMap)));
            }
        }
    }

    private void collectJavaChanges(LlmAnalysisResult result, Map<String, Integer> workpointMap, List<Change> changes) {
        if (result.getJavaChanges() == null) {
            return;
        }
        for (LlmAnalysisResult.JavaChange javaChange : result.getJavaChanges()) {
            changes.add(new Change(javaChange.getId(), "JAVA_CHANGE", javaChange.getSeverity(), javaChange.getTitle(),
                    javaChange.getDescription(), null, null, resolveWorkpoints(javaChange.getId(), workpointMap)));
        }
    }

    private void collectLibraryChanges(LlmAnalysisResult result, Map<String, Integer> workpointMap, List<Change> changes) {
        if (result.getLibraryChanges() == null) {
            return;
        }
        for (LlmAnalysisResult.LibraryChange libraryChange : result.getLibraryChanges()) {
            String description = libraryChange.getDescription();
            if (libraryChange.getBreakingNotes() != null && !libraryChange.getBreakingNotes().isEmpty()) {
                description = description + " | Breaking: " + libraryChange.getBreakingNotes();
            }
            String symbol = libraryChange.getGroupId() + ":" + libraryChange.getArtifactId();
            changes.add(new Change(libraryChange.getId(), "LIBRARY_CHANGE", libraryChange.getSeverity(),
                    libraryChange.getRecommendedVersion(), description, null, symbol,
                    resolveWorkpoints(libraryChange.getId(), workpointMap)));
        }
    }

    private void collectCodeImpacts(LlmAnalysisResult result, Map<String, Integer> workpointMap, List<Change> changes) {
        if (result.getCodeImpacts() == null) {
            return;
        }
        for (LlmAnalysisResult.CodeImpact codeImpact : result.getCodeImpacts()) {
            changes.add(new Change(codeImpact.getId(), "CODE_IMPACT", codeImpact.getSeverity(),
                    Optional.ofNullable(codeImpact.getTitle()).orElse(codeImpact.getChangeType()),
                    codeImpact.getDescription(), codeImpact.getFilePath(), codeImpact.getSymbol(),
                    resolveWorkpoints(codeImpact.getId(), workpointMap)));
        }
    }

    private void collectSecurityIssues(LlmAnalysisResult result, Map<String, Integer> workpointMap, List<Change> changes) {
        if (result.getSecurityIssues() == null) {
            return;
        }
        for (LlmAnalysisResult.SecurityIssue issue : result.getSecurityIssues()) {
            String symbol = issue.getGroupId() + ":" + issue.getArtifactId() + ":" + issue.getVersion();
            changes.add(new Change(issue.getId(), "SECURITY_ISSUE", issue.getSeverity(), issue.getRecommendedVersion(),
                    issue.getDescription(), null, symbol, resolveWorkpoints(issue.getId(), workpointMap)));
        }
    }

    private Integer resolveWorkpoints(String changeId, Map<String, Integer> workpointMap) {
        if (changeId == null) {
            return null;
        }
        return workpointMap.get(changeId);
    }
}
