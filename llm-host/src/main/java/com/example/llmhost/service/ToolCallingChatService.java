package com.example.llmhost.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.llmhost.api.ChatRequest;
import com.example.llmhost.api.ChatRunResponse;
import com.example.llmhost.api.ToolCallTrace;
import com.example.llmhost.config.AppProperties;
import com.example.llmhost.config.AppProperties.ToolingProperties;
import com.example.llmhost.config.SystemPromptProvider;
import com.example.llmhost.model.UpgradeReport;
import com.example.mcpmethodology.model.ChangeEffort;
import com.example.mcpmethodology.model.ChangeInput;
import com.example.mcpmethodology.model.EffortResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolCallingChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallingChatService.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*?\\}", Pattern.MULTILINE);
    private static final Pattern UPGRADE_PATTERN = Pattern.compile("\\bupgrade\\b", Pattern.CASE_INSENSITIVE);
    private static final String REPAIR_SYSTEM_PROMPT = "Tu es un réparateur JSON. Retourne uniquement un JSON valide "
            + "conforme au contrat UpgradeReport. Aucun texte hors JSON.";

    private final ChatClient chatClient;
    private final SystemPromptProvider systemPromptProvider;
    private final AppProperties properties;
    private final List<ToolCallback> functionCallbacks;
    private final CallAdvisor loggingAdvisor;
    private final RagMultiPassUpgradeContext upgradeContextService;
    private final ObjectMapper objectMapper;
    private final MethodologyClient methodologyClient;

    public ToolCallingChatService(ChatClient chatClient, SystemPromptProvider systemPromptProvider, AppProperties properties,
            List<ToolCallback> functionCallbacks, RagMultiPassUpgradeContext upgradeContextService,
            ObjectMapper objectMapper, MethodologyClient methodologyClient) {
        this.chatClient = chatClient;
        this.systemPromptProvider = systemPromptProvider;
        this.properties = properties;
        this.functionCallbacks = functionCallbacks;
        this.loggingAdvisor = new SimpleLoggerAdvisor();
        this.upgradeContextService = upgradeContextService;
        this.objectMapper = objectMapper;
        this.methodologyClient = methodologyClient;
    }

    public ChatRunResponse run(ChatRequest request) {
        validatePrompt(request);
        List<ToolCallTrace> traces = new ArrayList<>();
        boolean guidedMode = isGuidedMode(request);
        List<ToolCallback> callbacks = guidedMode
                ? Collections.emptyList()
                : shouldUseTools(request) ? wrapCallbacks(traces) : Collections.emptyList();

        String content = guidedMode
                ? runGuidedUpgrade(request)
                : chatClient.prompt()
                        .system(systemPromptProvider.buildSystemPrompt())
                        .user(request.prompt())
                        .advisors(loggingAdvisor)
                        .toolCallbacks(callbacks)
                        .call()
                        .content();
        ValidationResult validation = validateAndRepairReport(content);
        UpgradeReport report = validation.report();
        String json = validation.json();
        String output = validation.content();
        if (report != null) {
            UpgradeReport updated = applyMethodologyWorkpoints(report);
            json = writeReport(updated);
            output = json;
        }

        boolean toolsUsed = !guidedMode && shouldUseTools(request);
        return new ChatRunResponse(output, json, traces, toolsUsed);
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

    private String runGuidedUpgrade(ChatRequest request) {
        requireGuidedFields(request);
        UpgradeContext context = upgradeContextService.retrieve(
                request.fromVersion(),
                request.toVersion(),
                request.workspaceId(),
                request.repoUrl()
        );
        String systemPrompt = systemPromptProvider.buildGuidedUpgradePrompt();
        String userPrompt = buildGuidedUserPrompt(request, context.contextText());
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(loggingAdvisor)
                .toolCallbacks(Collections.emptyList())
                .call()
                .content();
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

    private String buildGuidedUserPrompt(ChatRequest request, String contextText) {
        StringBuilder builder = new StringBuilder();
        builder.append("Réponds uniquement avec un JSON valide conforme au contrat UpgradeReport.\n");
        builder.append(systemPromptProvider.upgradeReportContract()).append("\n");
        builder.append("Project attendu: repoUrl=").append(request.repoUrl())
                .append(", workspaceId=").append(request.workspaceId())
                .append(", from=").append(request.fromVersion())
                .append(", to=").append(request.toVersion())
                .append("\n");
        if (StringUtils.hasText(request.prompt())) {
            builder.append("\nQUESTION:\n").append(request.prompt()).append("\n");
        }
        builder.append("\n").append(contextText);
        return builder.toString();
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

    private ValidationResult validateAndRepairReport(String content) {
        String json = extractFirstJson(content);
        UpgradeReport report = parseReport(json);
        if (report != null) {
            return new ValidationResult(content, json, report);
        }
        String repairedContent = requestJsonRepair(content);
        String repairedJson = extractFirstJson(repairedContent);
        UpgradeReport repairedReport = parseReport(repairedJson);
        if (repairedReport != null) {
            return new ValidationResult(repairedContent, repairedJson, repairedReport);
        }
        return new ValidationResult(content, json, null);
    }

    private UpgradeReport parseReport(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, UpgradeReport.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String requestJsonRepair(String content) {
        String prompt = "Répare ce JSON afin qu'il soit valide et conforme au contrat UpgradeReport. "
                + "Retourne uniquement le JSON corrigé.\n"
                + systemPromptProvider.upgradeReportContract()
                + "\nJSON/texte:\n" + content;
        return chatClient.prompt()
                .system(REPAIR_SYSTEM_PROMPT)
                .user(prompt)
                .advisors(loggingAdvisor)
                .toolCallbacks(Collections.emptyList())
                .call()
                .content();
    }

    private UpgradeReport applyMethodologyWorkpoints(UpgradeReport report) {
        List<UpgradeReport.Impact> impacts = report.getImpacts();
        if (impacts == null || impacts.isEmpty()) {
            return report;
        }
        List<ChangeInput> inputs = impacts.stream()
                .map(this::toChangeInput)
                .toList();
        Optional<EffortResult> effortResult = methodologyClient.computeEffort(inputs);
        if (effortResult.isEmpty()) {
            return report;
        }
        Map<String, ChangeEffort> byId = new LinkedHashMap<>();
        for (ChangeEffort effort : effortResult.get().getByChange()) {
            if (effort.getChangeId() != null) {
                byId.put(effort.getChangeId(), effort);
            }
        }
        List<UpgradeReport.Workpoint> workpoints = new ArrayList<>();
        for (UpgradeReport.Impact impact : impacts) {
            if (impact == null || impact.getId() == null) {
                continue;
            }
            ChangeEffort effort = byId.get(impact.getId());
            if (effort == null) {
                continue;
            }
            workpoints.add(new UpgradeReport.Workpoint(
                    impact.getId(),
                    effort.getWorkpoints(),
                    effort.getReason(),
                    impact.getEvidence()
            ));
        }
        report.setWorkpoints(workpoints);
        return report;
    }

    private ChangeInput toChangeInput(UpgradeReport.Impact impact) {
        ChangeInput input = new ChangeInput();
        input.setId(impact.getId());
        input.setType(impact.getType());
        input.setSeverity(mapSeverity(impact.getType()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (impact.getAffectedAreas() != null && !impact.getAffectedAreas().isEmpty()) {
            metadata.put("impactedFiles", impact.getAffectedAreas().size());
        }
        if (impact.getEvidence() != null && !impact.getEvidence().isEmpty()) {
            metadata.put("occurrences", impact.getEvidence().size());
        }
        if (!metadata.isEmpty()) {
            input.setMetadata(metadata);
        }
        return input;
    }

    private String mapSeverity(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "BREAKING_CHANGE" -> "HIGH";
            case "DEPRECATION" -> "LOW";
            case "BEHAVIOR_CHANGE" -> "MEDIUM";
            case "DEPENDENCY_UPGRADE" -> "MEDIUM";
            default -> "MEDIUM";
        };
    }

    private String writeReport(UpgradeReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser le UpgradeReport", ex);
        }
    }

    private record ValidationResult(String content, String json, UpgradeReport report) {
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
