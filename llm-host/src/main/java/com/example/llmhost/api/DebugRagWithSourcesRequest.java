package com.example.llmhost.api;

import java.util.List;

import com.example.llmhost.rag.RagHit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record DebugRagWithSourcesRequest(
        @NotBlank(message = "question est requis")
        String question,
        @NotEmpty(message = "hits est requis")
        List<RagHit> hits,
        Integer maxContextChars
) {
}
