package com.example.llmhost.api;

import java.util.List;

import com.example.llmhost.rag.RagHit;

public record DebugProjectFactsResponse(
        List<RagHit> hits,
        String contextText
) {
}
