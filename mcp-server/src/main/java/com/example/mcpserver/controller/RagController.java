package com.example.mcpserver.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.example.mcpserver.dto.ApiChangeResponse;
import com.example.mcpserver.dto.RagIngestionResponse;
import com.example.mcpserver.dto.RagSearchResult;
import com.example.mcpserver.dto.SpringSourceIngestionRequest;
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
    public Mono<ResponseEntity<RagIngestionResponse>> ingestHtml(@RequestBody Map<String, Object> payload) {
        return Mono.fromCallable(() -> ragService.ingestFromHtml((String) payload.get("url"),
                        (String) payload.get("sourceType"), (String) payload.get("library"),
                        (String) payload.get("version"), (String) payload.getOrDefault("docId", ""),
                        (List<String>) payload.getOrDefault("selectors", List.of())))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/ingest/text")
    public Mono<ResponseEntity<RagIngestionResponse>> ingestText(@RequestBody Map<String, Object> payload) {
        return Mono.fromCallable(() -> ragService.ingestText((String) payload.get("sourceType"),
                        (String) payload.get("library"), (String) payload.get("version"),
                        (String) payload.get("content"), (String) payload.get("url"), (String) payload.get("docId")))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/ingest/spring-source")
    public Mono<ResponseEntity<SpringSourceIngestionResponse>> ingestSpringSource(
            @RequestBody SpringSourceIngestionRequest request) {
        return Mono.fromCallable(() -> springSourceIngestionService.ingestSpringSource(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/search")
    public Mono<ResponseEntity<List<RagSearchResult>>> search(@RequestBody Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
                    String query = (String) payload.getOrDefault("query", "");
                    int topK = ((Number) payload.getOrDefault("topK", 5)).intValue();
                    Map<String, Object> filters = (Map<String, Object>) payload.getOrDefault("filters", Map.of());
                    return ragService.search(query, filters, topK);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/api-changes")
    public Mono<ResponseEntity<ApiChangeResponse>> findApiChanges(@RequestBody Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
                    String symbol = (String) payload.get("symbol");
                    String fromVersion = (String) payload.get("fromVersion");
                    String toVersion = (String) payload.get("toVersion");
                    int topK = ((Number) payload.getOrDefault("topK", 5)).intValue();
                    return springApiChangeService.findApiChanges(symbol, fromVersion, toVersion, topK);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }
}
