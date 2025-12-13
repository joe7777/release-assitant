package com.example.upgrader.core.service;

import com.example.upgrader.core.command.CreateAnalysisCommand;
import com.example.upgrader.core.llm.LlmAnalysisResult;
import com.example.upgrader.core.llm.LlmClient;
import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.model.AnalysisStatus;
import com.example.upgrader.core.model.Change;
import com.example.upgrader.core.model.Effort;
import com.example.upgrader.core.model.Project;
import com.example.upgrader.core.repository.AnalysisRepository;
import com.example.upgrader.core.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final ProjectRepository projectRepository;
    private final LlmClient llmClient;

    public AnalysisService(AnalysisRepository analysisRepository, ProjectRepository projectRepository, LlmClient llmClient) {
        this.analysisRepository = analysisRepository;
        this.projectRepository = projectRepository;
        this.llmClient = llmClient;
    }

    public Analysis createAnalysis(CreateAnalysisCommand command) {
        Project project = findOrCreateProject(command);

        Analysis analysis = new Analysis();
        analysis.setProject(project);
        analysis.setSpringVersionTarget(command.getSpringVersionTarget());
        analysis.setLlmModel(command.getLlmModel());
        analysis.setStatus(AnalysisStatus.PENDING);
        analysis.setCreatedAt(LocalDateTime.now());

        Analysis saved = analysisRepository.save(analysis);

        LlmAnalysisResult result = llmClient.runAnalysis(saved);
        return updateAnalysisFromLLMResult(saved.getId(), result);
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
        if (result.getSpringVersionTarget() != null) {
            analysis.setSpringVersionTarget(result.getSpringVersionTarget());
        }
        if (result.getLlmModel() != null) {
            analysis.setLlmModel(result.getLlmModel());
        }

        Map<String, Integer> workpointsByChange = extractWorkpoints(result.getEffort());
        analysis.setChanges(mapChanges(result, workpointsByChange));
        analysis.setEffort(mapEffort(result.getEffort()));
        analysis.setStatus(AnalysisStatus.COMPLETED);

        return analysisRepository.save(analysis);
    }

    private Project findOrCreateProject(CreateAnalysisCommand command) {
        Optional<Project> existingProject = projectRepository.findByGitUrlAndBranch(command.getProjectGitUrl(), command.getBranch());
        if (existingProject.isPresent()) {
            return existingProject.get();
        }

        Project project = new Project();
        project.setGitUrl(command.getProjectGitUrl());
        project.setName(command.getProjectName());
        project.setBranch(command.getBranch());
        project.setGitTokenId(command.getGitTokenId());
        return projectRepository.save(project);
    }

    private Map<String, Integer> extractWorkpoints(LlmAnalysisResult.EffortResult effortResult) {
        if (effortResult == null || effortResult.getByChange() == null) {
            return new HashMap<>();
        }
        return effortResult.getByChange().stream()
                .collect(Collectors.toMap(LlmAnalysisResult.Workpoint::getChangeId, LlmAnalysisResult.Workpoint::getWorkpoints, (left, right) -> left));
    }

    private Effort mapEffort(LlmAnalysisResult.EffortResult effortResult) {
        if (effortResult == null) {
            return null;
        }
        Effort effort = new Effort();
        effort.setTotalWorkpoints(effortResult.getTotalWorkpoints());
        effort.setWorkpointsByChange(extractWorkpoints(effortResult));
        return effort;
    }

    private List<Change> mapChanges(LlmAnalysisResult result, Map<String, Integer> workpointsByChange) {
        List<Change> changes = new ArrayList<>();

        if (result.getSpringChanges() != null) {
            for (LlmAnalysisResult.SpringChange springChange : result.getSpringChanges()) {
                if (springChange.getAffectedComponents() != null && !springChange.getAffectedComponents().isEmpty()) {
                    for (LlmAnalysisResult.AffectedComponent component : springChange.getAffectedComponents()) {
                        changes.add(buildChangeFromSpringChange(springChange, component, workpointsByChange));
                    }
                } else {
                    changes.add(buildChange("SPRING", springChange.getSeverity(), springChange.getTitle(), springChange.getDescription(), null, null, springChange.getId(), workpointsByChange));
                }
            }
        }

        if (result.getJavaChanges() != null) {
            for (LlmAnalysisResult.JavaChange javaChange : result.getJavaChanges()) {
                changes.add(buildChange("JAVA", javaChange.getSeverity(), javaChange.getTitle(), javaChange.getDescription(), null, null, javaChange.getId(), workpointsByChange));
            }
        }

        if (result.getLibraryChanges() != null) {
            for (LlmAnalysisResult.LibraryChange libraryChange : result.getLibraryChanges()) {
                String description = String.format("%s (current: %s, recommended: %s)%s", libraryChange.getDescription(), libraryChange.getCurrentVersion(), libraryChange.getRecommendedVersion(),
                        libraryChange.getBreakingNotes() != null ? " - Notes: " + libraryChange.getBreakingNotes() : "");
                changes.add(buildChange("LIBRARY", libraryChange.getSeverity(), libraryChange.getTitle(), description, null, null, libraryChange.getId(), workpointsByChange));
            }
        }

        if (result.getCodeImpacts() != null) {
            for (LlmAnalysisResult.CodeImpact codeImpact : result.getCodeImpacts()) {
                changes.add(buildChange("CODE_IMPACT", null, codeImpact.getDescription(), codeImpact.getSuggestedFix(), codeImpact.getFilePath(), codeImpact.getSymbol(), codeImpact.getId(), workpointsByChange));
            }
        }

        if (result.getSecurityIssues() != null) {
            for (LlmAnalysisResult.SecurityIssue securityIssue : result.getSecurityIssues()) {
                String description = String.format("%s (CVE: %s)", securityIssue.getDescription(), securityIssue.getCveIds() != null ? String.join(",", securityIssue.getCveIds()) : "none");
                changes.add(buildChange("SECURITY", securityIssue.getSeverity(), securityIssue.getId(), description, null, null, securityIssue.getId(), workpointsByChange));
            }
        }

        return changes;
    }

    private Change buildChangeFromSpringChange(LlmAnalysisResult.SpringChange springChange, LlmAnalysisResult.AffectedComponent component, Map<String, Integer> workpointsByChange) {
        String composedDescription = springChange.getDescription();
        if (component.getDetails() != null && !component.getDetails().isEmpty()) {
            composedDescription = composedDescription + " - " + component.getDetails();
        }
        return buildChange("SPRING", springChange.getSeverity(), springChange.getTitle(), composedDescription, component.getFilePath(), component.getSymbol(), springChange.getId(), workpointsByChange);
    }

    private Change buildChange(String type, String severity, String title, String description, String filePath, String symbol, String changeId, Map<String, Integer> workpointsByChange) {
        Change change = new Change();
        change.setId(changeId);
        change.setType(type);
        change.setSeverity(severity);
        change.setTitle(title);
        change.setDescription(description);
        change.setFilePath(filePath);
        change.setSymbol(symbol);
        change.setWorkpoints(workpointsByChange.get(changeId));
        return change;
    }
}
