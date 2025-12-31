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
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolCallingChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallingChatService.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*?\\}", Pattern.MULTILINE);
    private static final Pattern UPGRADE_PATTERN = Pattern.compile("\\bupgrade\\b", Pattern.CASE_INSENSITIVE);

    private final ChatClient chatClient;
    private final SystemPromptProvider systemPromptProvider;
    private final AppProperties properties;
    private final List<ToolCallback> functionCallbacks;
    private final CallAdvisor loggingAdvisor;
    private final RagMultiPassUpgradeContext upgradeContextService;

    public ToolCallingChatService(ChatClient chatClient, SystemPromptProvider systemPromptProvider, AppProperties properties,
            List<ToolCallback> functionCallbacks, RagMultiPassUpgradeContext upgradeContextService) {
        this.chatClient = chatClient;
        this.systemPromptProvider = systemPromptProvider;
        this.properties = properties;
        this.functionCallbacks = functionCallbacks;
        this.loggingAdvisor = new SimpleLoggerAdvisor();
        this.upgradeContextService = upgradeContextService;
    }

    public ChatRunResponse run(ChatRequest request) {
        validatePrompt(request);
        List<ToolCallTrace> traces = new ArrayList<>();
        boolean guidedMode = isGuidedMode(request);
        List<ToolCallback> callbacks = guidedMode
                ? Collections.emptyList()
                : shouldUseTools(request) ? wrapCallbacks(traces) : Collections.emptyList();

        var response = guidedMode
                ? runGuidedUpgrade(request)
                : chatClient.prompt()
                        .system(systemPromptProvider.buildSystemPrompt())
                        .user(request.prompt())
                        .advisors(loggingAdvisor)
                        .toolCallbacks(callbacks)
                        .call();

        String content = response.content();
        String json = extractFirstJson(content);

        boolean toolsUsed = !guidedMode && shouldUseTools(request);
        return new ChatRunResponse(content, json, traces, toolsUsed);
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

    private boolean isGuidedMode(ChatRequest request) {
        if (request.mode() == ChatRequest.Mode.GUIDED) {
            return true;
        }
        if (request.mode() == ChatRequest.Mode.AUTO) {
            return false;
        }
        return request.prompt() != null && UPGRADE_PATTERN.matcher(request.prompt()).find();
    }

    private ChatResponse runGuidedUpgrade(ChatRequest request) {
        requireGuidedFields(request);
        UpgradeContext context = upgradeContextService.retrieve(
                request.fromVersion(),
                request.toVersion(),
                request.workspaceId(),
                request.repoUrl()
        );
        String systemPrompt = systemPromptProvider.buildGuidedUpgradePrompt();
        String userPrompt = buildGuidedUserPrompt(request.prompt(), context.contextText());
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(loggingAdvisor)
                .toolCallbacks(Collections.emptyList())
                .call();
    }

    private void requireGuidedFields(ChatRequest request) {
        if (!StringUtils.hasText(request.workspaceId())) {
            throw new IllegalArgumentException("workspaceId est requis pour le mode GUIDED");
        }
        if (!StringUtils.hasText(request.fromVersion())) {
            throw new IllegalArgumentException("fromVersion est requis pour le mode GUIDED");
        }
        if (!StringUtils.hasText(request.toVersion())) {
            throw new IllegalArgumentException("toVersion est requis pour le mode GUIDED");
        }
    }

    private String buildGuidedUserPrompt(String question, String contextText) {
        if (!StringUtils.hasText(question)) {
            return contextText;
        }
        return "QUESTION:\n" + question + "\n\n" + contextText;
    }

    private List<ToolCallback> wrapCallbacks(List<ToolCallTrace> traces) {
        ToolingProperties tooling = properties.getTooling();
        AtomicInteger counter = new AtomicInteger();
        return functionCallbacks.stream()
                .map(delegate -> (ToolCallback) new LoggingToolCallback(delegate, counter, tooling.getMaxToolCalls(), traces))
                .toList();
    }

    private String extractFirstJson(String content) {
        Matcher matcher = JSON_BLOCK.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static class LoggingToolCallback implements ToolCallback {
        private final ToolCallback delegate;
        private final AtomicInteger counter;
        private final int maxCalls;
        private final List<ToolCallTrace> traces;
        private final String toolName;

        private LoggingToolCallback(ToolCallback delegate, AtomicInteger counter, int maxCalls,
                List<ToolCallTrace> traces) {
            this.delegate = delegate;
            this.counter = counter;
            this.maxCalls = maxCalls;
            this.traces = traces;
            this.toolName = resolveToolName();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            int current = counter.incrementAndGet();
            if (current > maxCalls) {
                throw new IllegalStateException("Maximum tool calls exceeded (" + maxCalls + ")");
            }
            Instant start = Instant.now();
            try {
                LOGGER.info("Tool call #{} - {}", current, toolName);
                String response = delegate.call(toolInput);
                traces.add(new ToolCallTrace(toolName, toolInput == null ? "" : toolInput,
                        Duration.between(start, Instant.now()).toMillis(), true, null));
                return response;
            }
            catch (Exception ex) {
                traces.add(new ToolCallTrace(toolName, toolInput == null ? "" : toolInput,
                        Duration.between(start, Instant.now()).toMillis(), false, ex.getMessage()));
                throw ex;
            }
        }

        private String resolveToolName() {
            ToolDefinition definition = delegate.getToolDefinition();
            if (definition != null && StringUtils.hasText(definition.name())) {
                return definition.name();
            }
            return delegate.getClass().getSimpleName();
        }
    }
}
