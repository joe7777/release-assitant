package com.example.llmhost.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record DebugRagTestRequest(
        @NotBlank(message = "La requête est requise")
        String query,
        Map<String, Object> filters,
        Integer topK,
        Boolean callLlm,
        String llmQuestion,
        Integer maxContextChars
) {
}
