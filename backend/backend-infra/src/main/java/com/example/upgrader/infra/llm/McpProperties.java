package com.example.upgrader.infra.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class McpProperties {

    private final String projectAnalyzerUrl;
    private final String knowledgeRagUrl;
    private final String methodologyUrl;

    public McpProperties(
            @Value("${mcp.project-analyzer-url:http://mcp-project-analyzer:8080}") String projectAnalyzerUrl,
            @Value("${mcp.knowledge-rag-url:http://mcp-knowledge-rag:8080}") String knowledgeRagUrl,
            @Value("${mcp.methodology-url:http://mcp-methodology:8080}") String methodologyUrl) {
        this.projectAnalyzerUrl = projectAnalyzerUrl;
        this.knowledgeRagUrl = knowledgeRagUrl;
        this.methodologyUrl = methodologyUrl;
    }

    public String projectAnalyzerUrl() {
        return projectAnalyzerUrl;
    }

    public String knowledgeRagUrl() {
        return knowledgeRagUrl;
    }

    public String methodologyUrl() {
        return methodologyUrl;
    }
}
