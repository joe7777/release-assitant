package com.example.mcpknowledgerag.config;

import com.example.mcpknowledgerag.ai.ChatGateway;
import com.example.mcpknowledgerag.ai.EmbeddingGateway;
import com.example.mcpknowledgerag.ai.SpringAiChatGateway;
import com.example.mcpknowledgerag.ai.SpringAiEmbeddingGateway;
import com.example.mcpknowledgerag.config.AppAiProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppAiProperties.class)
public class AiConfiguration {

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean
    public EmbeddingGateway embeddingGateway(EmbeddingModel embeddingModel, AppAiProperties properties) {
        return new SpringAiEmbeddingGateway(embeddingModel, properties.getEmbedding());
    }

    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean
    public ChatGateway chatGateway(ChatModel chatModel, AppAiProperties properties) {
        return new SpringAiChatGateway(chatModel, properties.getChat());
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
}
