package com.example.mcpserver.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class QdrantScrollClient {

    private final WebClient webClient;
    private final String collection;

    public QdrantScrollClient(
            @Value("${spring.ai.vectorstore.qdrant.host:localhost}") String host,
            @Value("${mcp.qdrant.http-port:6333}") int port,
            @Value("${spring.ai.vectorstore.qdrant.api-key:}") String apiKey,
            @Value("${mcp.rag.collection:mcp_documents}") String collection) {
        String baseUrl = "http://" + host + ":" + port;
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("api-key", apiKey);
        }
        this.webClient = builder.build();
        this.collection = collection;
    }

    public JsonNode scroll(Map<String, Object> filter, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", limit);
        body.put("with_payload", true);
        body.put("with_vector", false);
        if (filter != null && !filter.isEmpty()) {
            body.put("filter", filter);
        }
        return webClient.post()
                .uri("/collections/{collection}/points/scroll", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
