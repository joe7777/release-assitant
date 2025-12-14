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
                EmbeddingRequest.builder(List.of(text))
                        .withOptions(buildOptions())
                        .build());

        if (response == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("Embedding model returned no vectors");
        }

        List<Double> embedding = response.getResults().getFirst().getOutput();
        if (embedding != null && !embedding.isEmpty()) {
            return embedding;
        }

        float[] floatOutput = response.getResults().getFirst().getOutputArray();
        if (floatOutput != null && floatOutput.length > 0) {
            List<Double> converted = new ArrayList<>(floatOutput.length);
            for (float value : floatOutput) {
                converted.add((double) value);
            }
            return converted;
        }

        throw new IllegalStateException("Embedding model returned no vectors");
    }

    private EmbeddingOptions buildOptions() {
        return switch (provider) {
            case OPENAI -> OpenAiEmbeddingOptions.builder()
                    .withModel(embeddingProperties.getModel())
                    .build();
            case OLLAMA -> OllamaEmbeddingOptions.builder()
                    .withModel(embeddingProperties.getModel())
                    .build();
        };
    }
}
