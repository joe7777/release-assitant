package com.example.mcpserver.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

@Configuration
public class EmbeddingConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel hashEmbeddingModel() {
        return new HashEmbeddingModel();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return new SimpleVectorStore(embeddingModel);
    }

    static class HashEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            if (request == null || CollectionUtils.isEmpty(request.getInstructions())) {
                return new EmbeddingResponse(List.of());
            }

            List<Embedding> embeddings = new ArrayList<>();
            int index = 0;
            for (String input : request.getInstructions()) {
                embeddings.add(new Embedding(index++, hashToVector(input)));
            }
            return new EmbeddingResponse(embeddings);
        }

        private List<Double> hashToVector(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                List<Double> vector = new ArrayList<>(hash.length);
                for (byte b : hash) {
                    vector.add((b & 0xFF) / 255.0d);
                }
                return vector;
            }
            catch (NoSuchAlgorithmException ex) {
                logger.warn("Unable to compute hash embedding", ex);
                List<Double> fallback = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    fallback.add(0.0d);
                }
                return fallback;
            }
        }
    }
}
