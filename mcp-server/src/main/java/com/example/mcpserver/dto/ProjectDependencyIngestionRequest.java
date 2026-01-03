package com.example.mcpserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDependencyIngestionRequest(
        String workspaceId,
        Boolean force) {
}
