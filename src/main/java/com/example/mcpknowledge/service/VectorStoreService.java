package com.example.mcpknowledge.service;

import com.example.mcpknowledge.config.QdrantProperties;
import com.example.mcpknowledge.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);
    private final WebClient webClient;
    private final EmbeddingService embeddingService;
    private final QdrantProperties properties;

    public VectorStoreService(WebClient.Builder builder, EmbeddingService embeddingService, QdrantProperties properties) {
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.webClient = builder
                .baseUrl(properties.getUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void ingest(IngestRequest request) {
        List<Double> embedding = embeddingService.embed(request.content());
        ensureCollection(embedding.size());

        Map<String, Object> payload = new HashMap<>();
        payload.put("content", request.content());
        payload.put("sourceType", request.sourceType().name());
        payload.put("version", request.version());
        if (request.library() != null) {
            payload.put("library", request.library());
        }
        if (request.url() != null) {
            payload.put("url", request.url());
        }

        Map<String, Object> point = Map.of(
                "id", UUID.randomUUID().toString(),
                "vector", embedding,
                "payload", payload
        );

        webClient.put()
                .uri("/collections/{collection}/points", properties.getCollectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("points", List.of(point)))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("Failed to upsert point into Qdrant", error))
                .block();
    }

    public SearchResponse search(SearchRequest request) {
        ensureCollectionExists();
        List<Double> embedding = embeddingService.embed(request.query());

        Map<String, Object> filter = buildFilter(request.filters());
        Map<String, Object> body = new HashMap<>();
        body.put("vector", embedding);
        body.put("limit", request.resolvedTopK());
        body.put("with_payload", true);
        body.put("with_vector", false);
        if (filter != null) {
            body.put("filter", filter);
        }

        QdrantSearchResponse response = webClient.post()
                .uri("/collections/{collection}/points/search", properties.getCollectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(QdrantSearchResponse.class)
                .doOnError(error -> log.error("Failed to search Qdrant", error))
                .blockOptional()
                .orElse(new QdrantSearchResponse(Collections.emptyList()));

        List<SearchResultItem> results = response.result().stream()
                .map(point -> new SearchResultItem(
                        (String) point.payload().get("content"),
                        point.payload().containsKey("sourceType") ? SourceType.valueOf((String) point.payload().get("sourceType")) : null,
                        (String) point.payload().get("version"),
                        (String) point.payload().get("library"),
                        (String) point.payload().get("url"),
                        point.score()
                ))
                .toList();

        return new SearchResponse(results);
    }

    private Map<String, Object> buildFilter(SearchFilters filters) {
        if (filters == null) {
            return null;
        }
        List<Map<String, Object>> must = new ArrayList<>();
        if (filters.sourceType() != null) {
            must.add(Map.of(
                    "key", "sourceType",
                    "match", Map.of("value", filters.sourceType().name())
            ));
        }
        if (filters.library() != null && !filters.library().isBlank()) {
            must.add(Map.of(
                    "key", "library",
                    "match", Map.of("value", filters.library())
            ));
        }
        if (filters.fromVersion() != null && !filters.fromVersion().isBlank()) {
            must.add(Map.of(
                    "key", "version",
                    "range", Map.of("gte", filters.fromVersion())
            ));
        }
        if (filters.toVersion() != null && !filters.toVersion().isBlank()) {
            must.add(Map.of(
                    "key", "version",
                    "range", Map.of("lte", filters.toVersion())
            ));
        }
        if (must.isEmpty()) {
            return null;
        }
        return Map.of("must", must);
    }

    private void ensureCollection(int vectorSize) {
        if (collectionExists()) {
            return;
        }
        createCollection(vectorSize);
    }

    private void ensureCollectionExists() {
        if (!collectionExists()) {
            throw new IllegalStateException("Qdrant collection does not exist: " + properties.getCollectionName());
        }
    }

    private boolean collectionExists() {
        return webClient.get()
                .uri("/collections/{collection}", properties.getCollectionName())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorResume(throwable -> Mono.just(false))
                .blockOptional()
                .orElse(false);
    }

    private void createCollection(int vectorSize) {
        Map<String, Object> vectors = Map.of(
                "size", vectorSize,
                "distance", "Cosine"
        );
        Map<String, Object> request = Map.of("vectors", vectors);

        webClient.put()
                .uri("/collections/{collection}", properties.getCollectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("Failed to create Qdrant collection", error))
                .block();
    }

    private record QdrantSearchResponse(List<QdrantPoint> result) {
    }

    private record QdrantPoint(double score, Map<String, Object> payload) {
    }
}
