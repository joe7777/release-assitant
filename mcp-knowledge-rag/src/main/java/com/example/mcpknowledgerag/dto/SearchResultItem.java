package com.example.mcpknowledgerag.dto;

public class SearchResultItem {

    private String content;
    private SourceType sourceType;
    private String version;
    private String library;
    private String url;
    private double score;

    public SearchResultItem() {
    }

    public SearchResultItem(String content, SourceType sourceType, String version, String library, String url, double score) {
        this.content = content;
        this.sourceType = sourceType;
        this.version = version;
        this.library = library;
        this.url = url;
        this.score = score;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
