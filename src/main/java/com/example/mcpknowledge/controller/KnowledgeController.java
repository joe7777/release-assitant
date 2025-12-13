package com.example.mcpknowledge.controller;

import com.example.mcpknowledge.dto.IngestRequest;
import com.example.mcpknowledge.dto.SearchRequest;
import com.example.mcpknowledge.dto.SearchResponse;
import com.example.mcpknowledge.service.VectorStoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class KnowledgeController {

    private final VectorStoreService vectorStoreService;

    public KnowledgeController(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@Valid @RequestBody IngestRequest request) {
        vectorStoreService.ingest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        SearchResponse response = vectorStoreService.search(request);
        return ResponseEntity.ok(response);
    }
}
