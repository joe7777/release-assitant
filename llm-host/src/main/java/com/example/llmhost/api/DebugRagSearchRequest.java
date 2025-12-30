package com.example.llmhost.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record DebugRagSearchRequest(
        @NotBlank(message = "query est requis")
        String query,
        Map<String, Object> filters,
        Integer topK
) {
}
