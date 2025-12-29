package com.example.llmhost.api;

import java.util.List;
import java.util.Map;

public record DebugRagTestResponse(Retrieval retrieval, Llm llm) {

    public record Retrieval(int topK, List<Result> results) {
    }

    public record Result(double score, String text, Map<String, Object> meta) {
    }

    public record Llm(boolean used, String answer, List<String> citationsFound, List<String> missingCitations) {
    }
}
