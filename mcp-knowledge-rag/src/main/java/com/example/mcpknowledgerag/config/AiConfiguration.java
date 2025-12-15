package com.example.mcpknowledgerag.config;

import com.example.mcpknowledgerag.ai.ChatGateway;
import com.example.mcpknowledgerag.ai.EmbeddingGateway;
import com.example.mcpknowledgerag.ai.SpringAiChatGateway;
import com.example.mcpknowledgerag.ai.SpringAiEmbeddingGateway;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AppAiProperties.class)
public class AiConfiguration {

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean
    public EmbeddingGateway embeddingGateway(EmbeddingModel embeddingModel, AppAiProperties properties) {
        return new SpringAiEmbeddingGateway(embeddingModel, properties);
    }

    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean
    public ChatGateway chatGateway(ChatModel chatModel, AppAiProperties properties) {
        return new SpringAiChatGateway(chatModel, properties.getChat());
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingGateway.class)
    public EmbeddingGateway restEmbeddingGateway(RestClient.Builder restClientBuilder,
                                                 AppAiProperties properties,
                                                 @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
                                                 @Value("${spring.ai.openai.base-url:https://api.openai.com/v1}") String openAiBaseUrl,
                                                 @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        return switch (properties.getProvider()) {
            case OLLAMA -> new OllamaRestEmbeddingGateway(restClientBuilder, ollamaBaseUrl, properties.getEmbedding().getModel());
            case OPENAI -> {
                if (openAiApiKey == null || openAiApiKey.isBlank()) {
                    throw new IllegalStateException("OpenAI API key must be provided when app.ai.provider=openai");
                }
                yield new OpenAiRestEmbeddingGateway(restClientBuilder, openAiBaseUrl, openAiApiKey, properties.getEmbedding().getModel());
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public OpenAiProviderGuard openAiProviderGuard(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key must be provided when app.ai.provider=openai");
        }
        return new OpenAiProviderGuard();
    }

    public static class OpenAiProviderGuard {
    }

    private static class OllamaRestEmbeddingGateway implements EmbeddingGateway {

        private final RestClient restClient;
        private final String model;

        OllamaRestEmbeddingGateway(RestClient.Builder builder, String baseUrl, String model) {
            this.restClient = builder
                    .baseUrl(baseUrl)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();
            this.model = model;
        }

        @Override
        public List<Double> embed(String text) {
            OllamaEmbeddingResponse response = restClient.post()
                    .uri("/api/embeddings")
                    .body(new OllamaEmbeddingRequest(model, text))
                    .retrieve()
                    .body(OllamaEmbeddingResponse.class);

            if (response == null || response.embedding == null || response.embedding.isEmpty()) {
                throw new IllegalStateException("Ollama embedding request returned no vectors");
            }

            return response.embedding;
        }
    }

    private static class OpenAiRestEmbeddingGateway implements EmbeddingGateway {

        private final RestClient restClient;
        private final String model;

        OpenAiRestEmbeddingGateway(RestClient.Builder builder, String baseUrl, String apiKey, String model) {
            this.restClient = builder
                    .baseUrl(baseUrl)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
            this.model = model;
        }

        @Override
        public List<Double> embed(String text) {
            OpenAiEmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .body(new OpenAiEmbeddingRequest(model, text))
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);

            if (response == null || response.data == null || response.data.isEmpty()
                    || response.data.getFirst().embedding == null || response.data.getFirst().embedding.isEmpty()) {
                throw new IllegalStateException("OpenAI embedding request returned no vectors");
            }

            return response.data.getFirst().embedding;
        }
    }

    private record OllamaEmbeddingRequest(String model, String prompt) {
    }

    private record OllamaEmbeddingResponse(List<Double> embedding) {
    }

    private record OpenAiEmbeddingRequest(String model, String input) {
    }

    private record OpenAiEmbeddingResponse(List<Data> data) {
        private record Data(List<Double> embedding) {
        }
    }
}
