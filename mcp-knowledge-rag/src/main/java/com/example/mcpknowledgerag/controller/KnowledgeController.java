package com.example.mcpknowledgerag.controller;

import com.example.mcpknowledgerag.ai.EmbeddingGateway;
import com.example.mcpknowledgerag.dto.IngestRequest;
import com.example.mcpknowledgerag.dto.IngestResponse;
import com.example.mcpknowledgerag.dto.SearchRequest;
import com.example.mcpknowledgerag.dto.SearchResponse;
import com.example.mcpknowledgerag.dto.SearchResultItem;
import com.example.mcpknowledgerag.service.IngestionService;
import com.example.mcpknowledgerag.service.VectorStoreService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class KnowledgeController {

    private final EmbeddingGateway embeddingService;
    private final VectorStoreService vectorStoreService;
    private final IngestionService ingestionService;

    public KnowledgeController(EmbeddingGateway embeddingService, VectorStoreService vectorStoreService, IngestionService ingestionService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        IngestResponse response = ingestionService.ingest(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        List<Double> queryEmbedding = embeddingService.embed(request.getQuery());
        List<SearchResultItem> results = vectorStoreService.search(queryEmbedding, request);
        return ResponseEntity.ok(new SearchResponse(results));
    }
}
