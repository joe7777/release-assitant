package com.example.mcpknowledgerag.ai;

import com.example.mcpknowledgerag.config.AppAiProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingOptions;

import java.util.List;

public class SpringAiEmbeddingGateway implements EmbeddingGateway {

    private final EmbeddingModel embeddingModel;
    private final AppAiProperties.Embedding embeddingProperties;

    public SpringAiEmbeddingGateway(EmbeddingModel embeddingModel, AppAiProperties.Embedding embeddingProperties) {
        this.embeddingModel = embeddingModel;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public List<Double> embed(String text) {
        EmbeddingOptions options = EmbeddingOptions.builder()
                .withModel(embeddingProperties.getModel())
                .build();

        EmbeddingResponse response = embeddingModel.embed(new EmbeddingRequest(List.of(text), options));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Embedding model returned no vectors");
        }

        return response.getResult().getOutput();
    }
}
