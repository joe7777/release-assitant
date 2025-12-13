package com.example.mcpknowledge.dto;

public record SearchResultItem(
        String content,
        SourceType sourceType,
        String version,
        String library,
        String url,
        double score
) {
}
