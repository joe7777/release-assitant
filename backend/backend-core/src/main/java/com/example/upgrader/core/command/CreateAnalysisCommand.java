package com.example.upgrader.core.command;

public class CreateAnalysisCommand {
    private String projectGitUrl;
    private String projectName;
    private String branch;
    private String springVersionTarget;
    private String llmModel;
    private String gitTokenId;

    public String getProjectGitUrl() {
        return projectGitUrl;
    }

    public void setProjectGitUrl(String projectGitUrl) {
        this.projectGitUrl = projectGitUrl;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getSpringVersionTarget() {
        return springVersionTarget;
    }

    public void setSpringVersionTarget(String springVersionTarget) {
        this.springVersionTarget = springVersionTarget;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getGitTokenId() {
        return gitTokenId;
    }

    public void setGitTokenId(String gitTokenId) {
        this.gitTokenId = gitTokenId;
    }
}
