package com.example.mcpknowledgerag.service;

import com.example.mcpknowledgerag.dto.SearchFilters;
import com.example.mcpknowledgerag.dto.SearchRequest;
import com.example.mcpknowledgerag.dto.SearchResultItem;
import com.example.mcpknowledgerag.dto.SourceType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VectorStoreService {

    private static final int VECTOR_SIZE = 1536;

    private final RestClient restClient;
    private final String collectionName;

    public VectorStoreService(RestClient.Builder builder,
                              @Value("${qdrant.url}") String qdrantUrl,
                              @Value("${qdrant.collection}") String collectionName) {
        this.restClient = builder.baseUrl(qdrantUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.collectionName = collectionName;
    }

    @PostConstruct
    public void ensureCollectionExists() {
        try {
            restClient.get()
                    .uri("/collections/{collection}", collectionName)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            createCollection();
        }
    }

    public boolean existsByDocumentHash(String documentHash) {
        Map<String, Object> filter = Map.of("must", List.of(
                Map.of("key", "documentHash", "match", Map.of("value", documentHash))
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("filter", filter);
        body.put("limit", 1);
        body.put("with_payload", false);
        body.put("with_vectors", false);

        ScrollResponse response = restClient.post()
                .uri("/collections/{collection}/points/scroll", collectionName)
                .body(body)
                .retrieve()
                .body(ScrollResponse.class);

        return response != null
                && response.result != null
                && response.result.points != null
                && !response.result.points.isEmpty();
    }

    public void upsertChunks(List<Point> points) {
        List<Map<String, Object>> mappedPoints = new ArrayList<>();
        for (Point point : points) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", UUID.randomUUID().toString());
            map.put("vector", point.vector());
            map.put("payload", point.payload());
            mappedPoints.add(map);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("points", mappedPoints);

        restClient.put()
                .uri("/collections/{collection}/points?wait=true", collectionName)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public List<SearchResultItem> search(List<Double> vector, SearchRequest request) {
        Map<String, Object> filter = buildFilter(request.getFilters());

        Map<String, Object> body = new HashMap<>();
        body.put("vector", vector);
        body.put("limit", request.getTopK() > 0 ? request.getTopK() : 10);
        body.put("with_payload", true);
        if (filter != null && !filter.isEmpty()) {
            body.put("filter", filter);
        }

        SearchResponse response = restClient.post()
                .uri("/collections/{collection}/points/search", collectionName)
                .body(body)
                .retrieve()
                .body(SearchResponse.class);

        List<SearchResultItem> results = new ArrayList<>();
        if (response != null && response.result != null) {
            for (SearchResponse.PointResult pointResult : response.result) {
                Map<String, Object> payload = pointResult.payload;
                String content = payload == null ? null : (String) payload.get("content");
                SourceType sourceType = payload != null && payload.get("sourceType") != null
                        ? SourceType.valueOf(payload.get("sourceType").toString())
                        : null;
                String version = payload == null ? null : (String) payload.get("version");
                String library = payload == null ? null : (String) payload.get("library");
                String url = payload == null ? null : (String) payload.get("url");

                results.add(new SearchResultItem(content, sourceType, version, library, url, pointResult.score));
            }
        }

        return results;
    }

    private Map<String, Object> buildFilter(SearchFilters filters) {
        if (filters == null) {
            return null;
        }
        List<Map<String, Object>> must = new ArrayList<>();

        if (filters.getSourceType() != null) {
            must.add(Map.of("key", "sourceType", "match", Map.of("value", filters.getSourceType().name())));
        }
        if (filters.getLibrary() != null && !filters.getLibrary().isBlank()) {
            must.add(Map.of("key", "library", "match", Map.of("value", filters.getLibrary())));
        }
        if (filters.getFromVersion() != null && !filters.getFromVersion().isBlank()) {
            must.add(Map.of("key", "version", "range", Map.of("gte", filters.getFromVersion())));
        }
        if (filters.getToVersion() != null && !filters.getToVersion().isBlank()) {
            must.add(Map.of("key", "version", "range", Map.of("lte", filters.getToVersion())));
        }

        if (must.isEmpty()) {
            return null;
        }
        return Map.of("must", must);
    }

    private void createCollection() {
        Map<String, Object> vectorsConfig = Map.of(
                "size", VECTOR_SIZE,
                "distance", "Cosine"
        );

        Map<String, Object> body = Map.of("vectors", vectorsConfig);

        restClient.put()
                .uri("/collections/{collection}", collectionName)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public record Point(List<Double> vector, Map<String, Object> payload) {
    }

    private static class ScrollResponse {
        private ScrollResult result;

        public ScrollResult getResult() {
            return result;
        }

        public void setResult(ScrollResult result) {
            this.result = result;
        }
    }

    private static class ScrollResult {
        private List<Map<String, Object>> points;

        public List<Map<String, Object>> getPoints() {
            return points;
        }

        public void setPoints(List<Map<String, Object>> points) {
            this.points = points;
        }
    }

    private static class SearchResponse {
        private List<PointResult> result;

        public List<PointResult> getResult() {
            return result;
        }

        public void setResult(List<PointResult> result) {
            this.result = result;
        }

        private static class PointResult {
            private double score;
            private Map<String, Object> payload;

            public double getScore() {
                return score;
            }

            public void setScore(double score) {
                this.score = score;
            }

            public Map<String, Object> getPayload() {
                return payload;
            }

            public void setPayload(Map<String, Object> payload) {
                this.payload = payload;
            }
        }
    }
}
