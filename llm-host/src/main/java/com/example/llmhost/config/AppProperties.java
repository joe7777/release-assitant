package com.example.llmhost.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final AiProperties ai = new AiProperties();
    private final ToolingProperties tooling = new ToolingProperties();
    private final SafetyProperties safety = new SafetyProperties();
    private final RagProperties rag = new RagProperties();
    private String systemPrompt;

    public AiProperties getAi() {
        return ai;
    }

    public ToolingProperties getTooling() {
        return tooling;
    }

    public SafetyProperties getSafety() {
        return safety;
    }

    public RagProperties getRag() {
        return rag;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public static class AiProperties {

        private Provider provider = Provider.OLLAMA;
        private final OllamaProperties ollama = new OllamaProperties();
        private final OpenAiProperties openai = new OpenAiProperties();

        public Provider getProvider() {
            return provider;
        }

        public void setProvider(Provider provider) {
            this.provider = provider;
        }

        public OllamaProperties getOllama() {
            return ollama;
        }

        public OpenAiProperties getOpenai() {
            return openai;
        }
    }

    public enum Provider {
        OLLAMA, OPENAI
    }

    public static class OllamaProperties {

        private String chatModel = "llama3.1:8b";
        private String embeddingModel = "nomic-embed-text";

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }

    public static class OpenAiProperties {

        private String chatModel = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }

    public static class ToolingProperties {

        private int maxToolCalls = 6;
        private int toolTimeoutSeconds = 90;
        private boolean dryRun = false;
        private int maxPromptLength = 4000;

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        public int getToolTimeoutSeconds() {
            return toolTimeoutSeconds;
        }

        public void setToolTimeoutSeconds(int toolTimeoutSeconds) {
            this.toolTimeoutSeconds = toolTimeoutSeconds;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public int getMaxPromptLength() {
            return maxPromptLength;
        }

        public void setMaxPromptLength(int maxPromptLength) {
            this.maxPromptLength = maxPromptLength;
        }
    }

    public static class SafetyProperties {

        private final List<String> allowlist = new ArrayList<>(List.of("https://docs.spring.io", "https://github.com"));
        private int ragTopK = 6;

        public List<String> getAllowlist() {
            return allowlist;
        }

        public int getRagTopK() {
            return ragTopK;
        }

        public void setRagTopK(int ragTopK) {
            this.ragTopK = ragTopK;
        }
    }

    public static class RagProperties {

        private double citationCoverageRatio = 0.5;
        private int citationMinSourcesForCoverage = 4;
        private int citationMinSourcesRequired = 1;
        private boolean enableSourceCodePass = true;

        public double getCitationCoverageRatio() {
            return citationCoverageRatio;
        }

        public void setCitationCoverageRatio(double citationCoverageRatio) {
            this.citationCoverageRatio = citationCoverageRatio;
        }

        public int getCitationMinSourcesForCoverage() {
            return citationMinSourcesForCoverage;
        }

        public void setCitationMinSourcesForCoverage(int citationMinSourcesForCoverage) {
            this.citationMinSourcesForCoverage = citationMinSourcesForCoverage;
        }

        public int getCitationMinSourcesRequired() {
            return citationMinSourcesRequired;
        }

        public void setCitationMinSourcesRequired(int citationMinSourcesRequired) {
            this.citationMinSourcesRequired = citationMinSourcesRequired;
        }

        public boolean isEnableSourceCodePass() {
            return enableSourceCodePass;
        }

        public void setEnableSourceCodePass(boolean enableSourceCodePass) {
            this.enableSourceCodePass = enableSourceCodePass;
        }
    }
}
