package com.example.llmhost.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.llmhost.rag.RagHit;

public record UpgradeContext(List<RagHit> hits, String contextText) {

    public UpgradeContext {
        List<RagHit> safeHits = hits == null ? List.of() : hits.stream()
                .filter(Objects::nonNull)
                .map(hit -> hit.metadata() == null ? new RagHit(hit.text(), hit.score(), Map.of()) : hit)
                .toList();
        hits = safeHits;
    }
}
