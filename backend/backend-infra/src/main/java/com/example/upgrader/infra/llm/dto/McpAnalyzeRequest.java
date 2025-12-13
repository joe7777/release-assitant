package com.example.upgrader.infra.llm.dto;

public class McpAnalyzeRequest {
    private String repoUrl;
    private String branch;
    private String gitToken;
    private String llmModel;
    private String dependencyScope;

    public McpAnalyzeRequest() {
    }

    public McpAnalyzeRequest(String repoUrl, String branch, String gitToken, String llmModel, String dependencyScope) {
        this.repoUrl = repoUrl;
        this.branch = branch;
        this.gitToken = gitToken;
        this.llmModel = llmModel;
        this.dependencyScope = dependencyScope;
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

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getDependencyScope() {
        return dependencyScope;
    }

    public void setDependencyScope(String dependencyScope) {
        this.dependencyScope = dependencyScope;
    }
}
