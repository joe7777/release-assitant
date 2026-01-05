package com.example.llmhost.api;

import jakarta.validation.constraints.NotBlank;

public record DebugProjectFactsRequest(
        @NotBlank(message = "workspaceId est requis")
        String workspaceId
) {
}
