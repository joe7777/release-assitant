package com.example.llmhost.rag;

import java.util.Map;

public record RagHit(String text, double score, Map<String, Object> metadata) {
}
