package com.example.mcpprojectanalyzer.api;

public record DependencyDto(
        String groupId,
        String artifactId,
        String version,
        String scope
) {
}
