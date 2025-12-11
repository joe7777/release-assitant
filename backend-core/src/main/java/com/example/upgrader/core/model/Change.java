package com.example.upgrader.core.model;

public class Change {
    private String id;
    private String type;
    private String severity;
    private String title;
    private String description;
    private String filePath;
    private String symbol;
    private Integer workpoints;

    public Change() {
    }

    public Change(String id, String type, String severity, String title, String description,
                  String filePath, String symbol, Integer workpoints) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.filePath = filePath;
        this.symbol = symbol;
        this.workpoints = workpoints;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
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

    public Integer getWorkpoints() {
        return workpoints;
    }

    public void setWorkpoints(Integer workpoints) {
        this.workpoints = workpoints;
    }
}
