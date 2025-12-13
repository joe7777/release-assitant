package com.example.mcpknowledgerag.ai;

import com.example.mcpknowledgerag.config.AppAiProperties;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptOptions;

import java.util.List;
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
        ChatOptions chatOptions = new DefaultChatOptionsBuilder()
                .model(chatProperties.getModel())
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxOutputTokens())
                .build();

        Prompt chatPrompt = new Prompt(
                List.of(new UserMessage(Objects.requireNonNullElse(prompt, ""))),
                chatOptions
        );

        ChatResponse response = chatModel.call(chatPrompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("No response returned from chat model");
        }

        return response.getResult().getOutput().getContent();
    }
}
