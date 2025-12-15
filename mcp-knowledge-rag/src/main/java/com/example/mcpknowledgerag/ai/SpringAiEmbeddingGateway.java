package com.example.mcpknowledgerag.ai;

import com.example.mcpknowledgerag.config.AppAiProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

import java.util.ArrayList;
import java.util.List;

public class SpringAiEmbeddingGateway implements EmbeddingGateway {

    private final EmbeddingModel embeddingModel;
    private final AppAiProperties.Provider provider;
    private final AppAiProperties.Embedding embeddingProperties;

    public SpringAiEmbeddingGateway(EmbeddingModel embeddingModel, AppAiProperties appAiProperties) {
        this.embeddingModel = embeddingModel;
        this.provider = appAiProperties.getProvider();
        this.embeddingProperties = appAiProperties.getEmbedding();
    }

    @Override
    public List<Double> embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(
                new EmbeddingRequest(List.of(text), buildOptions()));

        if (response == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("Embedding model returned no vectors");
        }

        float[] embedding = response.getResults().getFirst().getOutput();
        if (embedding != null && embedding.length > 0) {
            List<Double> converted = new ArrayList<>(embedding.length);
            for (float value : embedding) {
                converted.add((double) value);
            }
            return converted;
        }

        throw new IllegalStateException("Embedding model returned no vectors");
    }

    private EmbeddingOptions buildOptions() {
        return switch (provider) {
            case OPENAI -> OpenAiEmbeddingOptions.builder()
                    .model(embeddingProperties.getModel())
                    .build();
            case OLLAMA -> OllamaEmbeddingOptions.builder()
                    .model(embeddingProperties.getModel())
                    .build();
        };
    }
}
