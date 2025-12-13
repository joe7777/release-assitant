package com.example.mcpknowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IngestRequest(
        @NotNull SourceType sourceType,
        @NotBlank String version,
        String library,
        @NotBlank String content,
        String url
) {
}
