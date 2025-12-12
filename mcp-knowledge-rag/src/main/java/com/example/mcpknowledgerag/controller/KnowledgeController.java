package com.example.mcpknowledgerag.controller;

import com.example.mcpknowledgerag.dto.IngestRequest;
import com.example.mcpknowledgerag.dto.SearchRequest;
import com.example.mcpknowledgerag.dto.SearchResponse;
import com.example.mcpknowledgerag.dto.SearchResultItem;
import com.example.mcpknowledgerag.service.EmbeddingService;
import com.example.mcpknowledgerag.service.VectorStoreService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class KnowledgeController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public KnowledgeController(EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@Valid @RequestBody IngestRequest request) {
        List<Double> embedding = embeddingService.embed(request.getContent());
        vectorStoreService.upsert(embedding, request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        List<Double> queryEmbedding = embeddingService.embed(request.getQuery());
        List<SearchResultItem> results = vectorStoreService.search(queryEmbedding, request);
        return ResponseEntity.ok(new SearchResponse(results));
    }
}
