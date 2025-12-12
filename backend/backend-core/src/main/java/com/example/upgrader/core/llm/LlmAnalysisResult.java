package com.example.upgrader.core.llm;

import java.util.List;

public class LlmAnalysisResult {
    private String analysisId;
    private String springVersionCurrent;
    private String springVersionTarget;
    private String summary;
    private List<SpringChange> springChanges;
    private List<JavaChange> javaChanges;
    private List<LibraryChange> libraryChanges;
    private List<CodeImpact> codeImpacts;
    private List<SecurityIssue> securityIssues;
    private EffortResult effort;

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<SpringChange> getSpringChanges() {
        return springChanges;
    }

    public void setSpringChanges(List<SpringChange> springChanges) {
        this.springChanges = springChanges;
    }

    public List<JavaChange> getJavaChanges() {
        return javaChanges;
    }

    public void setJavaChanges(List<JavaChange> javaChanges) {
        this.javaChanges = javaChanges;
    }

    public List<LibraryChange> getLibraryChanges() {
        return libraryChanges;
    }

    public void setLibraryChanges(List<LibraryChange> libraryChanges) {
        this.libraryChanges = libraryChanges;
    }

    public List<CodeImpact> getCodeImpacts() {
        return codeImpacts;
    }

    public void setCodeImpacts(List<CodeImpact> codeImpacts) {
        this.codeImpacts = codeImpacts;
    }

    public List<SecurityIssue> getSecurityIssues() {
        return securityIssues;
    }

    public void setSecurityIssues(List<SecurityIssue> securityIssues) {
        this.securityIssues = securityIssues;
    }

    public EffortResult getEffort() {
        return effort;
    }

    public void setEffort(EffortResult effort) {
        this.effort = effort;
    }

    public static class SpringChange {
        private String id;
        private String title;
        private String description;
        private String severity;
        private List<AffectedComponent> affectedComponents;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public List<AffectedComponent> getAffectedComponents() {
            return affectedComponents;
        }

        public void setAffectedComponents(List<AffectedComponent> affectedComponents) {
            this.affectedComponents = affectedComponents;
        }
    }

    public static class AffectedComponent {
        private String filePath;
        private String symbol;
        private String changeType;
        private String details;

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getChangeType() {
            return changeType;
        }

        public void setChangeType(String changeType) {
            this.changeType = changeType;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }
    }

    public static class JavaChange {
        private String id;
        private String title;
        private String description;
        private String severity;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }

    public static class LibraryChange {
        private String id;
        private String groupId;
        private String artifactId;
        private String currentVersion;
        private String recommendedVersion;
        private String description;
        private String breakingNotes;
        private String severity;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        public String getRecommendedVersion() {
            return recommendedVersion;
        }

        public void setRecommendedVersion(String recommendedVersion) {
            this.recommendedVersion = recommendedVersion;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getBreakingNotes() {
            return breakingNotes;
        }

        public void setBreakingNotes(String breakingNotes) {
            this.breakingNotes = breakingNotes;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }

    public static class CodeImpact {
        private String id;
        private String filePath;
        private String symbol;
        private String changeType;
        private String description;
        private String suggestedFix;
        private List<String> references;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getChangeType() {
            return changeType;
        }

        public void setChangeType(String changeType) {
            this.changeType = changeType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSuggestedFix() {
            return suggestedFix;
        }

        public void setSuggestedFix(String suggestedFix) {
            this.suggestedFix = suggestedFix;
        }

        public List<String> getReferences() {
            return references;
        }

        public void setReferences(List<String> references) {
            this.references = references;
        }
    }

    public static class SecurityIssue {
        private String id;
        private String groupId;
        private String artifactId;
        private String version;
        private List<String> cveIds;
        private String description;
        private String severity;
        private String recommendedVersion;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getCveIds() {
            return cveIds;
        }

        public void setCveIds(List<String> cveIds) {
            this.cveIds = cveIds;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getRecommendedVersion() {
            return recommendedVersion;
        }

        public void setRecommendedVersion(String recommendedVersion) {
            this.recommendedVersion = recommendedVersion;
        }
    }

    public static class EffortResult {
        private int totalWorkpoints;
        private List<Workpoint> byChange;

        public int getTotalWorkpoints() {
            return totalWorkpoints;
        }

        public void setTotalWorkpoints(int totalWorkpoints) {
            this.totalWorkpoints = totalWorkpoints;
        }

        public List<Workpoint> getByChange() {
            return byChange;
        }

        public void setByChange(List<Workpoint> byChange) {
            this.byChange = byChange;
        }
    }

    public static class Workpoint {
        private String changeId;
        private int workpoints;
        private String reason;

        public String getChangeId() {
            return changeId;
        }

        public void setChangeId(String changeId) {
            this.changeId = changeId;
        }

        public int getWorkpoints() {
            return workpoints;
        }

        public void setWorkpoints(int workpoints) {
            this.workpoints = workpoints;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
