package com.example.mcpknowledgerag.ai;

import java.util.List;

public interface EmbeddingGateway {

    List<Double> embed(String text);
}
