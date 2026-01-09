package com.example.llmhost.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpgradeReport {

    private Project project;
    private Map<String, Object> springUsageSummary;
    private List<Impact> impacts = new ArrayList<>();
    private List<Workpoint> workpoints = new ArrayList<>();
    private List<Unknown> unknowns = new ArrayList<>();

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Map<String, Object> getSpringUsageSummary() {
        return springUsageSummary;
    }

    public void setSpringUsageSummary(Map<String, Object> springUsageSummary) {
        this.springUsageSummary = springUsageSummary;
    }

    public List<Impact> getImpacts() {
        return impacts;
    }

    public void setImpacts(List<Impact> impacts) {
        if (impacts == null) {
            this.impacts = new ArrayList<>();
        } else {
            this.impacts = new ArrayList<>(impacts);
        }
    }

    public List<Workpoint> getWorkpoints() {
        return workpoints;
    }

    public void setWorkpoints(List<Workpoint> workpoints) {
        if (workpoints == null) {
            this.workpoints = new ArrayList<>();
        } else {
            this.workpoints = new ArrayList<>(workpoints);
        }
    }

    public List<Unknown> getUnknowns() {
        return unknowns;
    }

    public void setUnknowns(List<Unknown> unknowns) {
        if (unknowns == null) {
            this.unknowns = new ArrayList<>();
        } else {
            this.unknowns = new ArrayList<>(unknowns);
        }
    }

    public static class Project {
        private String repoUrl;
        private String workspaceId;
        private String from;
        private String to;

        public String getRepoUrl() {
            return repoUrl;
        }

        public void setRepoUrl(String repoUrl) {
            this.repoUrl = repoUrl;
        }

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }
    }

    public static class Impact {
        private String id;
        private String title;
        private String type;
        private List<String> affectedAreas = new ArrayList<>();
        private List<String> evidence = new ArrayList<>();
        private List<EvidenceDetail> evidenceDetails = new ArrayList<>();
        private String recommendation;

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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getAffectedAreas() {
            return affectedAreas;
        }

        public void setAffectedAreas(List<String> affectedAreas) {
            if (affectedAreas == null) {
                this.affectedAreas = new ArrayList<>();
            } else {
                this.affectedAreas = new ArrayList<>(affectedAreas);
            }
        }

        public List<String> getEvidence() {
            return evidence;
        }

        public void setEvidence(List<String> evidence) {
            if (evidence == null) {
                this.evidence = new ArrayList<>();
            } else {
                this.evidence = new ArrayList<>(evidence);
            }
        }

        public List<EvidenceDetail> getEvidenceDetails() {
            return evidenceDetails;
        }

        public void setEvidenceDetails(List<EvidenceDetail> evidenceDetails) {
            if (evidenceDetails == null) {
                this.evidenceDetails = new ArrayList<>();
            } else {
                this.evidenceDetails = new ArrayList<>(evidenceDetails);
            }
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }

    public static class Workpoint {
        private String impactId;
        private int points;
        private String rationale;
        private List<String> evidence = new ArrayList<>();
        private List<EvidenceDetail> evidenceDetails = new ArrayList<>();

        public Workpoint() {
        }

        public Workpoint(String impactId, int points, String rationale, List<String> evidence) {
            this.impactId = impactId;
            this.points = points;
            this.rationale = rationale;
            if (evidence != null) {
                this.evidence = new ArrayList<>(evidence);
            }
        }

        public String getImpactId() {
            return impactId;
        }

        public void setImpactId(String impactId) {
            this.impactId = impactId;
        }

        public int getPoints() {
            return points;
        }

        public void setPoints(int points) {
            this.points = points;
        }

        public String getRationale() {
            return rationale;
        }

        public void setRationale(String rationale) {
            this.rationale = rationale;
        }

        public List<String> getEvidence() {
            return evidence;
        }

        public void setEvidence(List<String> evidence) {
            if (evidence == null) {
                this.evidence = new ArrayList<>();
            } else {
                this.evidence = new ArrayList<>(evidence);
            }
        }

        public List<EvidenceDetail> getEvidenceDetails() {
            return evidenceDetails;
        }

        public void setEvidenceDetails(List<EvidenceDetail> evidenceDetails) {
            if (evidenceDetails == null) {
                this.evidenceDetails = new ArrayList<>();
            } else {
                this.evidenceDetails = new ArrayList<>(evidenceDetails);
            }
        }
    }

    public static class Unknown {
        private String question;
        private String why;
        private String nextStep;
        private List<String> evidence = new ArrayList<>();

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getWhy() {
            return why;
        }

        public void setWhy(String why) {
            this.why = why;
        }

        public String getNextStep() {
            return nextStep;
        }

        public void setNextStep(String nextStep) {
            this.nextStep = nextStep;
        }

        public List<String> getEvidence() {
            return evidence;
        }

        public void setEvidence(List<String> evidence) {
            if (evidence == null) {
                this.evidence = new ArrayList<>();
            } else {
                this.evidence = new ArrayList<>(evidence);
            }
        }
    }

    public static class EvidenceDetail {
        private String source;
        private String url;
        private String documentKey;
        private String version;
        private String library;
        private String filePath;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDocumentKey() {
            return documentKey;
        }

        public void setDocumentKey(String documentKey) {
            this.documentKey = documentKey;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getLibrary() {
            return library;
        }

        public void setLibrary(String library) {
            this.library = library;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }
}
