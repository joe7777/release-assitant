package com.example.llmhost.api;

import java.util.List;

import com.example.llmhost.rag.RagHit;

public record DebugRagSearchResponse(List<RagHit> results) {
}
