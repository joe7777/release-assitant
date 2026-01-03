package com.example.mcpserver.dto;

import java.util.List;

public record ProjectDependencyIngestionResponse(
        String workspaceId,
        String repoUrl,
        String commit,
        String springBootVersion,
        String javaVersion,
        List<String> springDependencies,
        List<String> thirdPartyDependencies,
        String documentKey,
        boolean ingested,
        long durationMs) {
}
