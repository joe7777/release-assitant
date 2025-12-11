package com.example.mcpprojectanalyzer.api;

public record AnalyzeRequest(
        String repoUrl,
        String branch,
        String gitToken
) {
}
