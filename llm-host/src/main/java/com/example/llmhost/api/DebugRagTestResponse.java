package com.example.llmhost.api;

import java.util.List;
import java.util.Map;

public record DebugRagTestResponse(Retrieval retrieval, Llm llm) {

    public record Retrieval(int topK, String query, Map<String, Object> filters, List<RagHit> results) {
    }

    public record RagHit(double score, String text, Map<String, Object> metadata) {
    }

    public record Llm(boolean used, String answer, List<String> citationsFound, List<String> missingSources,
                      double coverageRatio, String status, String warning, List<LlmEvidence> evidence) {
    }

    public record LlmEvidence(String source, String documentKey, String url, String version, String library) {
    }
}
