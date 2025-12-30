package com.example.llmhost.controller;

import java.util.List;
import java.util.Map;

import com.example.llmhost.api.DebugRagTestResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugRagControllerTest {

    @Test
    void computeMissingCitationsReturnsSourcesNotCoveredInAnswer() {
        List<DebugRagTestResponse.RagHit> hits = List.of(
                new DebugRagTestResponse.RagHit(0.9, "alpha", Map.of()),
                new DebugRagTestResponse.RagHit(0.8, "bravo", Map.of()),
                new DebugRagTestResponse.RagHit(0.7, "charlie", Map.of()),
                new DebugRagTestResponse.RagHit(0.6, "delta", Map.of()),
                new DebugRagTestResponse.RagHit(0.5, "echo", Map.of())
        );
        String answer = "1. Réponse [S2]\n2. Réponse [S4]\n3. Réponse [S5]";

        List<String> citationsFound = DebugRagController.extractCitationsFound(answer);
        List<String> missing = DebugRagController.computeMissingCitations(hits, citationsFound);

        assertEquals(List.of("S1", "S3"), missing);
    }
}
