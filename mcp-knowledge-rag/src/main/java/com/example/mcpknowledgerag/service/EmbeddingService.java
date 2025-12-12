package com.example.mcpknowledgerag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class EmbeddingService {

    private final RestClient restClient;
    private final String embeddingModel;

    public EmbeddingService(RestClient.Builder builder,
                             @Value("${openai.api.base-url}") String baseUrl,
                             @Value("${openai.api.key}") String apiKey,
                             @Value("${openai.api.embedding-model}") String embeddingModel) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.embeddingModel = embeddingModel;
    }

    public List<Double> embed(String text) {
        EmbeddingRequest payload = new EmbeddingRequest(text, embeddingModel);
        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .body(payload)
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data == null || response.data.isEmpty()) {
            throw new IllegalStateException("Unable to compute embeddings from OpenAI response");
        }

        return response.data.getFirst().embedding;
    }

    private record EmbeddingRequest(String input, String model) {
    }

    private static class EmbeddingResponse {
        private List<EmbeddingData> data;

        public List<EmbeddingData> getData() {
            return data;
        }

        public void setData(List<EmbeddingData> data) {
            this.data = data;
        }
    }

    private static class EmbeddingData {
        private List<Double> embedding;

        public List<Double> getEmbedding() {
            return embedding;
        }

        public void setEmbedding(List<Double> embedding) {
            this.embedding = embedding;
        }
    }
}
