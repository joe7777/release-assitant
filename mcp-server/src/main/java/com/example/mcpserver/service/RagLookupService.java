package com.example.mcpserver.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.mcpserver.dto.RagSearchResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagLookupService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final QdrantScrollClient qdrantScrollClient;
    private final ObjectMapper objectMapper;

    public RagLookupService(QdrantScrollClient qdrantScrollClient, ObjectMapper objectMapper) {
        this.qdrantScrollClient = qdrantScrollClient;
        this.objectMapper = objectMapper;
    }

    public List<RagSearchResult> lookup(Map<String, Object> filters, int limit) {
        int boundedLimit = Math.max(1, limit);
        boolean hasListFilters = hasListFilters(filters);
        Map<String, Object> filter = buildFilter(filters, true);
        JsonNode response;
        try {
            response = qdrantScrollClient.scroll(filter, boundedLimit);
        } catch (WebClientResponseException ex) {
            if (!hasListFilters || ex.getStatusCode().value() != 400) {
                throw ex;
            }
            Map<String, Object> fallback = buildFilter(filters, false);
            response = qdrantScrollClient.scroll(fallback, boundedLimit);
        }
        if (response == null) {
            throw new IllegalStateException("Réponse Qdrant vide pour rag.lookup");
        }
        JsonNode points = response.path("result").path("points");
        if (!points.isArray()) {
            throw new IllegalStateException("La réponse Qdrant ne contient pas un tableau de points");
        }
        List<RagSearchResult> results = new ArrayList<>();
        for (JsonNode point : points) {
            JsonNode payload = point.path("payload");
            Map<String, Object> metadata = payload.isObject()
                    ? objectMapper.convertValue(payload, MAP_TYPE)
                    : new LinkedHashMap<>();
            String text = extractText(payload);
            results.add(new RagSearchResult(text, 1.0, metadata));
        }
        return results;
    }

    private String extractText(JsonNode payload) {
        if (payload != null && payload.hasNonNull("doc_content")) {
            return payload.get("doc_content").asText("");
        }
        if (payload != null && payload.hasNonNull("text")) {
            return payload.get("text").asText("");
        }
        return "";
    }

    private Map<String, Object> buildFilter(Map<String, Object> filters, boolean useMatchAny) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> must = new ArrayList<>();
        List<Map<String, Object>> should = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof List<?> listValue) {
                if (listValue.isEmpty()) {
                    continue;
                }
                if (useMatchAny && listValue.stream().allMatch(item -> item instanceof String)) {
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("any", listValue);
                    must.add(matchCondition(key, match));
                } else {
                    for (Object item : listValue) {
                        if (item != null) {
                            should.add(matchCondition(key, Map.of("value", item)));
                        }
                    }
                }
            } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                must.add(matchCondition(key, Map.of("value", value)));
            }
        }
        if (must.isEmpty() && should.isEmpty()) {
            return null;
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        if (!must.isEmpty()) {
            filter.put("must", must);
        }
        if (!should.isEmpty()) {
            filter.put("should", should);
        }
        return filter;
    }

    private boolean hasListFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return false;
        }
        return filters.values().stream().anyMatch(value -> value instanceof List<?>);
    }

    private Map<String, Object> matchCondition(String key, Map<String, Object> match) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("key", key);
        condition.put("match", match);
        return condition;
    }
}
