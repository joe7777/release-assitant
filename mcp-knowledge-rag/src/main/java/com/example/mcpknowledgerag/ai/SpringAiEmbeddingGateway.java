package com.example.mcpknowledgerag.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

public class SpringAiEmbeddingGateway implements EmbeddingGateway {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingGateway(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Double> embed(String text) {
        EmbeddingResponse response = embeddingModel.embed(new EmbeddingRequest(text));
        if (response.getResults().isEmpty() || response.getResults().getFirst().getOutput() == null) {
            throw new IllegalStateException("Embedding model returned no vectors");
        }
        return response.getResults().getFirst().getOutput();
    }
}
