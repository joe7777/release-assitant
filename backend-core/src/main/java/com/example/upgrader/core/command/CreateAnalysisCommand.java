package com.example.upgrader.core.command;

public class CreateAnalysisCommand {
    private String projectGitUrl;
    private String projectName;
    private String branch;
    private String springVersionTarget;
    private String gitTokenId;

    public CreateAnalysisCommand() {
    }

    public CreateAnalysisCommand(String projectGitUrl, String projectName, String branch,
                                 String springVersionTarget, String gitTokenId) {
        this.projectGitUrl = projectGitUrl;
        this.projectName = projectName;
        this.branch = branch;
        this.springVersionTarget = springVersionTarget;
        this.gitTokenId = gitTokenId;
    }

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

    public String getGitTokenId() {
        return gitTokenId;
    }

    public void setGitTokenId(String gitTokenId) {
        this.gitTokenId = gitTokenId;
    }
}
