package com.example.llmhost.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.llmhost.api.ChatRequest;
import com.example.llmhost.api.ChatRunResponse;
import com.example.llmhost.api.ToolCallTrace;
import com.example.llmhost.config.AppProperties;
import com.example.llmhost.config.AppProperties.ToolingProperties;
import com.example.llmhost.config.SystemPromptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallback;
import org.springframework.ai.model.tool.ToolCallbackContext;
import org.springframework.ai.model.tool.ToolResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolCallingChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallingChatService.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*?\\}", Pattern.MULTILINE);

    private final ChatClient chatClient;
    private final SystemPromptProvider systemPromptProvider;
    private final AppProperties properties;
    private final List<ToolCallback> functionCallbacks;

    public ToolCallingChatService(ChatClient chatClient, SystemPromptProvider systemPromptProvider, AppProperties properties,
            ToolCallbackContext functionCallbackContext) {
        this.chatClient = chatClient;
        this.systemPromptProvider = systemPromptProvider;
        this.properties = properties;
        this.functionCallbacks = new ArrayList<>(functionCallbackContext.getToolCallbacks());
    }

    public ChatRunResponse run(ChatRequest request) {
        validatePrompt(request);
        List<ToolCallTrace> traces = new ArrayList<>();
        List<ToolCallback> callbacks = shouldUseTools(request)
                ? wrapCallbacks(traces)
                : Collections.emptyList();

        var response = chatClient.prompt()
                .system(systemPromptProvider.buildSystemPrompt())
                .user(request.prompt())
                .tools(callbacks)
                .call();

        String content = response.content();
        String json = extractFirstJson(content);

        return new ChatRunResponse(content, json, traces, shouldUseTools(request));
    }

    private boolean shouldUseTools(ChatRequest request) {
        return !properties.getTooling().isDryRun() && !request.dryRun();
    }

    private void validatePrompt(ChatRequest request) {
        int maxLength = properties.getTooling().getMaxPromptLength();
        if (StringUtils.hasLength(request.prompt()) && request.prompt().length() > maxLength) {
            throw new IllegalArgumentException("Prompt too long. Limit is " + maxLength + " characters");
        }
    }

    private List<ToolCallback> wrapCallbacks(List<ToolCallTrace> traces) {
        ToolingProperties tooling = properties.getTooling();
        AtomicInteger counter = new AtomicInteger();
        return functionCallbacks.stream()
                .map(delegate -> new LoggingFunctionCallback(delegate, counter, tooling.getMaxToolCalls(), traces))
                .map(ToolCallback.class::cast)
                .toList();
    }

    private String extractFirstJson(String content) {
        Matcher matcher = JSON_BLOCK.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static class LoggingFunctionCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final AtomicInteger counter;
        private final int maxCalls;
        private final List<ToolCallTrace> traces;

        LoggingFunctionCallback(ToolCallback delegate, AtomicInteger counter, int maxCalls, List<ToolCallTrace> traces) {
            this.delegate = delegate;
            this.counter = counter;
            this.maxCalls = maxCalls;
            this.traces = traces;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public ToolResponse call(ToolCallbackContext context) {
            int current = counter.incrementAndGet();
            if (current > maxCalls) {
                throw new IllegalStateException("Maximum tool calls exceeded (" + maxCalls + ")");
            }
            Instant start = Instant.now();
            try {
                LOGGER.info("Tool call #{} - {}", current, delegate.getName());
                ToolResponse response = delegate.call(context);
                traces.add(new ToolCallTrace(delegate.getName(), context.toString(),
                        Duration.between(start, Instant.now()).toMillis(), true, null));
                return response;
            }
            catch (Exception ex) {
                traces.add(new ToolCallTrace(delegate.getName(), context.toString(),
                        Duration.between(start, Instant.now()).toMillis(), false, ex.getMessage()));
                throw ex;
            }
        }

        @Override
        public Object getInputTypeSchema() {
            return delegate.getInputTypeSchema();
        }
    }
}
