package com.example.llmhost.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Le prompt est requis")
        @Size(min = 4, max = 4000, message = "Le prompt doit contenir entre 4 et 4000 caractères")
        String prompt,
        boolean dryRun,
        Mode mode,
        String workspaceId,
        String repoUrl,
        String fromVersion,
        String toVersion
) {

    public enum Mode {
        GUIDED,
        AUTO
    }
}
