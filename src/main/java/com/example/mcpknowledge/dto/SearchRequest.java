package com.example.mcpknowledge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String query,
        @Valid SearchFilters filters,
        @Min(1) @Max(50) Integer topK
) {
    public int resolvedTopK() {
        return topK == null ? 10 : topK;
    }
}
