package com.example.mcpknowledgerag.config;

import com.example.mcpknowledgerag.ai.EmbeddingGateway;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("spring.main.web-application-type=none");

    @Test
    void defaultProviderIsOllama() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingGateway.class);
            AppAiProperties properties = context.getBean(AppAiProperties.class);
            assertThat(properties.getProvider()).isEqualTo(AppAiProperties.Provider.OLLAMA);
            assertThat(context.containsBean("openAiProviderGuard")).isFalse();
        });
    }

    @Test
    void openAiProviderRequiresApiKey() {
        ApplicationContextRunner runner = contextRunner.withPropertyValues(
                "app.ai.provider=openai",
                "spring.ai.openai.enabled=true",
                "spring.ai.ollama.enabled=false"
        );

        runner.run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("OpenAI API key must be provided"));
    }
}

@Configuration
@Import(AiConfiguration.class)
class TestConfig {

    @Bean
    AppAiProperties appAiProperties() {
        return new AppAiProperties();
    }

    @Bean
    ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return null;
            }
        };
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse embed(EmbeddingRequest request) {
                return null;
            }
        };
    }
}
