package com.example.mcpknowledgerag.ai;

import com.example.mcpknowledgerag.config.AppAiProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Objects;

public class SpringAiChatGateway implements ChatGateway {

    private final ChatModel chatModel;
    private final AppAiProperties.Chat chatProperties;

    public SpringAiChatGateway(ChatModel chatModel, AppAiProperties.Chat chatProperties) {
        this.chatModel = chatModel;
        this.chatProperties = chatProperties;
    }

    @Override
    public String generateAnalysisJson(String prompt) {
        Prompt chatPrompt = new Prompt(
                new UserMessage(Objects.requireNonNullElse(prompt, "")),
                ChatOptions.builder()
                        .withModel(chatProperties.getModel())
                        .withTemperature(chatProperties.getTemperature())
                        .withMaxTokens(chatProperties.getMaxOutputTokens())
                        .build()
        );

        ChatResponse response = chatModel.call(chatPrompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("No response returned from chat model");
        }

        return response.getResult().getOutput().getContent();
    }
}
