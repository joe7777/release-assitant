package com.example.mcpanalyzer.api.dto;

public class AnalyzeRequest {
    private String repoUrl;
    private String branch;
    private String gitToken;

    public AnalyzeRequest() {
    }

    public AnalyzeRequest(String repoUrl, String branch, String gitToken) {
        this.repoUrl = repoUrl;
        this.branch = branch;
        this.gitToken = gitToken;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getGitToken() {
        return gitToken;
    }

    public void setGitToken(String gitToken) {
        this.gitToken = gitToken;
    }
}
