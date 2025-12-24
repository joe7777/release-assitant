package com.example.mcpserver.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.mcpserver.dto.ApiChangeResponse;
import com.example.mcpserver.dto.RagIngestionResponse;
import com.example.mcpserver.dto.RagSearchResult;
import com.example.mcpserver.dto.SpringSourceIngestionResponse;
import com.example.mcpserver.service.RagService;
import com.example.mcpserver.service.SpringApiChangeService;
import com.example.mcpserver.service.SpringSourceIngestionService;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final SpringSourceIngestionService springSourceIngestionService;
    private final SpringApiChangeService springApiChangeService;

    public RagController(RagService ragService, SpringSourceIngestionService springSourceIngestionService,
            SpringApiChangeService springApiChangeService) {
        this.ragService = ragService;
        this.springSourceIngestionService = springSourceIngestionService;
        this.springApiChangeService = springApiChangeService;
    }

    @PostMapping("/ingest/html")
    public ResponseEntity<RagIngestionResponse> ingestHtml(@RequestBody Map<String, Object> payload) throws IOException {
        RagIngestionResponse response = ragService.ingestFromHtml((String) payload.get("url"),
                (String) payload.get("sourceType"), (String) payload.get("library"), (String) payload.get("version"),
                (String) payload.getOrDefault("docId", ""), (List<String>) payload.getOrDefault("selectors", List.of()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ingest/text")
    public ResponseEntity<RagIngestionResponse> ingestText(@RequestBody Map<String, Object> payload) {
        RagIngestionResponse response = ragService.ingestText((String) payload.get("sourceType"),
                (String) payload.get("library"), (String) payload.get("version"), (String) payload.get("content"),
                (String) payload.get("url"), (String) payload.get("docId"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ingest/spring-source")
    public ResponseEntity<SpringSourceIngestionResponse> ingestSpringSource(@RequestBody Map<String, Object> payload)
            throws IOException {
        SpringSourceIngestionResponse response = springSourceIngestionService.ingestSpringSource(
                (String) payload.get("version"), (List<String>) payload.get("modules"),
                (String) payload.get("tagOrBranch"), (Boolean) payload.get("includeJavadoc"),
                (Integer) payload.get("maxFiles"), (Boolean) payload.get("force"),
                (Boolean) payload.get("includeTests"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search")
    public ResponseEntity<List<RagSearchResult>> search(@RequestBody Map<String, Object> payload) {
        String query = (String) payload.getOrDefault("query", "");
        int topK = ((Number) payload.getOrDefault("topK", 5)).intValue();
        Map<String, Object> filters = (Map<String, Object>) payload.getOrDefault("filters", Map.of());
        return ResponseEntity.ok(ragService.search(query, filters, topK));
    }

    @PostMapping("/api-changes")
    public ResponseEntity<ApiChangeResponse> findApiChanges(@RequestBody Map<String, Object> payload) {
        String symbol = (String) payload.get("symbol");
        String fromVersion = (String) payload.get("fromVersion");
        String toVersion = (String) payload.get("toVersion");
        int topK = ((Number) payload.getOrDefault("topK", 5)).intValue();
        return ResponseEntity.ok(springApiChangeService.findApiChanges(symbol, fromVersion, toVersion, topK));
    }
}
