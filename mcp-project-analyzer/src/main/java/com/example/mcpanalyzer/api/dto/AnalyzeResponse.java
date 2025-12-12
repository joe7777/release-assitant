package com.example.mcpanalyzer.api.dto;

import java.util.ArrayList;
import java.util.List;

public class AnalyzeResponse {
    private String springVersionCurrent;
    private List<DependencyDto> dependencies = new ArrayList<>();
    private List<String> modules = new ArrayList<>();

    public AnalyzeResponse() {
    }

    public AnalyzeResponse(String springVersionCurrent, List<DependencyDto> dependencies, List<String> modules) {
        this.springVersionCurrent = springVersionCurrent;
        this.dependencies = dependencies;
        this.modules = modules;
    }

    public String getSpringVersionCurrent() {
        return springVersionCurrent;
    }

    public void setSpringVersionCurrent(String springVersionCurrent) {
        this.springVersionCurrent = springVersionCurrent;
    }

    public List<DependencyDto> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyDto> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }
}
