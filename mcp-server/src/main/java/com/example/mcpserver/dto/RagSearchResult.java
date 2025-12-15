package com.example.mcpserver.dto;

import java.util.List;
import java.util.Map;

public record RagSearchResult(String text, double score, Map<String, Object> metadata) {
}
