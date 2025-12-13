package com.example.mcpknowledge.dto;

public record SearchFilters(
        SourceType sourceType,
        String library,
        String fromVersion,
        String toVersion
) {
}
