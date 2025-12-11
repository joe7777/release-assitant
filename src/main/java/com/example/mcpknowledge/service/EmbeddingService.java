package com.example.mcpknowledge.service;

import com.example.mcpknowledge.config.OpenAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private final WebClient webClient;
    private final OpenAiProperties properties;

    public EmbeddingService(WebClient.Builder builder, OpenAiProperties properties) {
        this.properties = properties;
        this.webClient = builder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    public List<Double> embed(String text) {
        Map<String, Object> request = Map.of(
                "input", text,
                "model", properties.getEmbeddingsModel()
        );

        EmbeddingResponse response = webClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .doOnError(error -> log.error("Failed to call OpenAI embeddings API", error))
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve embedding from OpenAI"));

        if (response.data == null || response.data.isEmpty()) {
            throw new IllegalStateException("OpenAI embeddings response is empty");
        }

        return response.data.getFirst().embedding;
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding) {
    }
}
