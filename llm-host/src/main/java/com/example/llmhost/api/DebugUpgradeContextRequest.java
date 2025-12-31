package com.example.llmhost.api;

import jakarta.validation.constraints.NotBlank;

public record DebugUpgradeContextRequest(
        @NotBlank(message = "workspaceId est requis")
        String workspaceId,
        String repoUrl,
        @NotBlank(message = "fromVersion est requis")
        String fromVersion,
        @NotBlank(message = "toVersion est requis")
        String toVersion
) {
}
