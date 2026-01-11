package com.example.llmhost.config;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AiConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    @Primary
    public ChatModel chatModel(AppProperties properties, ObjectProvider<OllamaChatModel> ollamaProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider,
            @Value("${spring.ai.openai.base-url:}") String openAiBaseUrl,
            @Value("${spring.ai.ollama.base-url:}") String ollamaBaseUrl) {
        AppProperties.Provider provider = properties.getAi().getProvider();
        String model = provider == AppProperties.Provider.OPENAI
                ? properties.getAi().getOpenai().getChatModel()
                : properties.getAi().getOllama().getChatModel();
        String baseUrl = provider == AppProperties.Provider.OPENAI ? openAiBaseUrl : ollamaBaseUrl;
        if (StringUtils.hasText(baseUrl)) {
            LOGGER.info("AI provider selected: {} model={} baseUrl={}", provider, model, baseUrl);
        } else {
            LOGGER.info("AI provider selected: {} model={}", provider, model);
        }
        if (provider == AppProperties.Provider.OPENAI) {
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
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public OpenAiGuard openAiGuard(@Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:}") String baseUrl) {
        if (requiresApiKey(apiKey, baseUrl)) {
            throw new IllegalStateException("OPENAI_API_KEY doit être fourni lorsque app.ai.provider=openai");
        }
        return new OpenAiGuard();
    }

    private boolean requiresApiKey(String apiKey, String baseUrl) {
        if (StringUtils.hasText(apiKey)) {
            return false;
        }
        if (!StringUtils.hasText(baseUrl)) {
            return true;
        }
        String normalizedBaseUrl = baseUrl.toLowerCase();
        return !(normalizedBaseUrl.contains("ollama") || normalizedBaseUrl.contains("11434"));
    }

    public static class OpenAiGuard {
    }
}
