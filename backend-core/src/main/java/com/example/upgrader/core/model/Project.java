package com.example.upgrader.core.model;

public class Project {
    private Long id;
    private String name;
    private String gitUrl;
    private String branch;
    private String gitTokenId;

    public Project() {
    }

    public Project(Long id, String name, String gitUrl, String branch, String gitTokenId) {
        this.id = id;
        this.name = name;
        this.gitUrl = gitUrl;
        this.branch = branch;
        this.gitTokenId = gitTokenId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getGitTokenId() {
        return gitTokenId;
    }

    public void setGitTokenId(String gitTokenId) {
        this.gitTokenId = gitTokenId;
    }
}
