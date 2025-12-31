package com.example.mcpserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectSpringUsageScanRequest(
        String workspaceId,
        Boolean includeTests,
        Integer maxFiles,
        Integer maxFileBytes,
        Boolean force) {
}
