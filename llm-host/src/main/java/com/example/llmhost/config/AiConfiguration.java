package com.example.llmhost.config;

import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackContext;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AiConfiguration {

    @Bean
    @Primary
    public ChatModel chatModel(AppProperties properties, ObjectProvider<OllamaChatModel> ollamaProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider) {
        if (properties.getAi().getProvider() == AppProperties.Provider.OPENAI) {
            return Objects.requireNonNull(openAiProvider.getIfAvailable(), "OpenAI chat model is not configured");
        }
        return Objects.requireNonNull(ollamaProvider.getIfAvailable(), "Ollama chat model is not configured");
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolCallbackContext functionCallbackContext() {
        return new ToolCallbackContext();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public OpenAiGuard openAiGuard(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY doit être fourni lorsque app.ai.provider=openai");
        }
        return new OpenAiGuard();
    }

    public static class OpenAiGuard {
    }
}
