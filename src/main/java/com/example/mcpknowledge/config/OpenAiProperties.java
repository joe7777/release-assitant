package com.example.mcpknowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private String apiKey;
    private String embeddingsModel;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEmbeddingsModel() {
        return embeddingsModel;
    }

    public void setEmbeddingsModel(String embeddingsModel) {
        this.embeddingsModel = embeddingsModel;
    }
}
