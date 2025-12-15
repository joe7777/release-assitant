package com.example.llmhost.api;

public record ToolCallTrace(
        String toolName,
        String argumentsSummary,
        long durationMs,
        boolean success,
        String errorMessage
) {
}
