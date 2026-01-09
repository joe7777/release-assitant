package com.example.llmhost.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.example.llmhost.api.ChatRequest;
import com.example.llmhost.api.ChatRunResponse;
import com.example.llmhost.api.GatingStats;
import com.example.llmhost.api.ToolCallTrace;
import com.example.llmhost.config.AppProperties;
import com.example.llmhost.config.AppProperties.ToolingProperties;
import com.example.llmhost.config.SystemPromptProvider;
import com.example.llmhost.model.UpgradeReport;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final UpgradeReportEvidenceGate evidenceGate;
    private final UpgradeReportSanitizer reportSanitizer;
    private final EvidenceEnricher evidenceEnricher;

    public ToolCallingChatService(ChatClient chatClient, SystemPromptProvider systemPromptProvider, AppProperties properties,
            List<ToolCallback> functionCallbacks, RagMultiPassUpgradeContext upgradeContextService,
            ObjectMapper objectMapper, MethodologyClient methodologyClient, EvidenceEnricher evidenceEnricher) {
        this.chatClient = chatClient;
        this.systemPromptProvider = systemPromptProvider;
        this.properties = properties;
        this.functionCallbacks = functionCallbacks;
        this.loggingAdvisor = new SimpleLoggerAdvisor();
        this.upgradeContextService = upgradeContextService;
        this.objectMapper = objectMapper;
        this.methodologyClient = methodologyClient;
        this.evidenceGate = new UpgradeReportEvidenceGate();
        this.reportSanitizer = new UpgradeReportSanitizer();
        this.evidenceEnricher = evidenceEnricher;
    }

    public ChatRunResponse run(ChatRequest request) {
        validatePrompt(request);
        List<ToolCallTrace> traces = new ArrayList<>();
        boolean guidedMode = isGuidedMode(request);
        List<ToolCallback> callbacks = guidedMode
                ? Collections.emptyList()
                : shouldUseTools(request) ? wrapCallbacks(traces) : Collections.emptyList();

        GuidedUpgradeResult guidedResult = null;
        String content;
        if (guidedMode) {
            guidedResult = runGuidedUpgrade(request);
            content = guidedResult.content();
        } else {
            content = chatClient.prompt()
                    .system(systemPromptProvider.buildSystemPrompt())
                    .user(request.prompt())
                    .advisors(loggingAdvisor)
                    .toolCallbacks(callbacks)
                    .call()
                    .content();
        }
        ValidationResult validation = validateAndRepairReport(content);
        UpgradeReport report = validation.report();
        String json = validation.json();
        String output = validation.content();
        GatingStats gating = null;
        if (report != null) {
            report = reportSanitizer.sanitize(report);
        }
        if (guidedMode && report != null && guidedResult != null) {
            EvidenceGateResult gatedResult = evidenceGate.applyWithReport(report, guidedResult.context().hits().size());
            report = gatedResult.report();
            gating = gatedResult.stats();
        } else if (guidedMode) {
            int sourceCount = guidedResult == null ? 0 : guidedResult.context().hits().size();
            gating = evidenceGate.applyWithReport(report, sourceCount).stats();
        }
        if (guidedMode && report != null && guidedResult != null) {
            report = evidenceEnricher.enrich(report, guidedResult.context());
        }
        if (report != null) {
            json = writeReportJson(report, json);
        }
        if (guidedMode && json != null) {
            output = json;
        }
        if (report != null) {
            LOGGER.debug("Sending report JSON to methodology client. evidenceDetails present={}",
                    json != null && json.contains("evidenceDetails"));
            Optional<String> formatted = methodologyClient.writeReport(json);
            if (formatted.isPresent()) {
                json = formatted.get();
                output = json;
            }
        }

        boolean toolsUsed = !guidedMode && shouldUseTools(request);
        return new ChatRunResponse(output, json, traces, toolsUsed, gating);
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

    private GuidedUpgradeResult runGuidedUpgrade(ChatRequest request) {
        requireGuidedFields(request);
        UpgradeContext context = upgradeContextService.retrieve(
                request.fromVersion(),
                request.toVersion(),
                request.workspaceId(),
                request.repoUrl(),
                request.moduleFocus()
        );
        String systemPrompt = systemPromptProvider.buildGuidedUpgradePrompt();
        String userPrompt = buildGuidedUserPrompt(request, context);
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(loggingAdvisor)
                .toolCallbacks(Collections.emptyList())
                .call()
                .content();
        return new GuidedUpgradeResult(content, context);
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

    private String buildGuidedUserPrompt(ChatRequest request, UpgradeContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Réponds uniquement avec un JSON valide conforme au contrat UpgradeReport.\n");
        builder.append("N'utilise jamais ```json``` ni markdown, retourne uniquement un objet JSON.\n");
        builder.append("Chaque impact doit inclure evidence=[S#] présents dans SOURCES.\n");
        builder.append("Inclure l'URL dans recommendation quand disponible (metadata.url).\n");
        builder.append("Ne pas utiliser de markdown.\n");
        builder.append(systemPromptProvider.upgradeReportContract()).append("\n");
        builder.append("SOURCES AUTORISÉES: ").append(buildAllowedSources(context.hits().size())).append("\n");
        builder.append("Project attendu: repoUrl=").append(request.repoUrl())
                .append(", workspaceId=").append(request.workspaceId())
                .append(", from=").append(request.fromVersion())
                .append(", to=").append(request.toVersion())
                .append("\n");
        if (request.moduleFocus() != null && !request.moduleFocus().isEmpty()) {
            builder.append("moduleFocus=").append(request.moduleFocus()).append("\n");
        }
        if (StringUtils.hasText(request.prompt())) {
            builder.append("\nQUESTION:\n").append(request.prompt()).append("\n");
        }
        builder.append("\n").append(context.contextText());
        return builder.toString();
    }

    private String buildAllowedSources(int sourceCount) {
        if (sourceCount <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= sourceCount; i++) {
            if (i > 1) {
                builder.append(',');
            }
            builder.append('S').append(i);
        }
        return builder.toString();
    }

    private String writeReportJson(UpgradeReport report, String fallback) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private List<ToolCallback> wrapCallbacks(List<ToolCallTrace> traces) {
        ToolingProperties tooling = properties.getTooling();
        AtomicInteger counter = new AtomicInteger();
        return functionCallbacks.stream()
                .map(delegate -> (ToolCallback) new LoggingToolCallback(delegate, counter, tooling.getMaxToolCalls(), traces))
                .toList();
    }

    private String extractJsonPayload(String content) {
        if (content == null) {
            LOGGER.debug("Detected JSON payload format: legacy (content=null)");
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            LOGGER.debug("Detected JSON payload format: legacy (content=empty)");
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.isObject()) {
                LOGGER.debug("Detected JSON payload format: object");
                return objectMapper.writeValueAsString(node);
            }
            if (node.isArray()) {
                if (node.size() > 0) {
                    JsonNode first = node.get(0);
                    JsonNode textNode = first.get("text");
                    if (textNode != null && textNode.isTextual()) {
                        try {
                            JsonNode innerNode = objectMapper.readTree(textNode.asText());
                            if (innerNode.isObject()) {
                                LOGGER.debug("Detected JSON payload format: array.text");
                                return objectMapper.writeValueAsString(innerNode);
                            }
                        } catch (Exception innerEx) {
                            LOGGER.debug("Failed to parse array.text JSON payload, falling back to array serialization");
                        }
                    }
                }
                LOGGER.debug("Detected JSON payload format: array");
                return objectMapper.writeValueAsString(node);
            }
        } catch (Exception ex) {
            LOGGER.debug("Failed to parse JSON payload directly, falling back to legacy extraction");
        }
        LOGGER.debug("Detected JSON payload format: legacy");
        return extractFirstJsonLegacy(content);
    }

    private String extractFirstJsonLegacy(String content) {
        if (content == null) {
            LOGGER.debug("No JSON object found in content length=0");
            return null;
        }
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (start < 0) {
                if (current == '{') {
                    start = i;
                    depth = 1;
                }
                continue;
            }
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (current == '\\') {
                    escape = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        LOGGER.debug("No JSON object found in content length={}", content.length());
        return null;
    }

    private ValidationResult validateAndRepairReport(String content) {
        String json = extractJsonPayload(content);
        UpgradeReport report = parseReport(json);
        if (report != null) {
            return new ValidationResult(content, json, report);
        }
        if (StringUtils.hasText(json)) {
            LOGGER.debug("Failed to parse UpgradeReport from extracted JSON (length={})", json.length());
        }
        String repairedContent = requestJsonRepair(content);
        String repairedJson = extractJsonPayload(repairedContent);
        UpgradeReport repairedReport = parseReport(repairedJson);
        if (repairedReport != null) {
            return new ValidationResult(repairedContent, repairedJson, repairedReport);
        }
        if (StringUtils.hasText(repairedJson)) {
            LOGGER.debug("Failed to parse UpgradeReport from repaired JSON (length={})", repairedJson.length());
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
                + "Retourne uniquement l'objet JSON corrigé, sans wrapper tableau ni champ \"text\". "
                + "N'utilise jamais ```json``` ni markdown. "
                + "Si l'entrée est un tableau [{\"text\":\"{...}\"}], extrais le JSON interne et retourne uniquement l'objet JSON.\n"
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

    private record ValidationResult(String content, String json, UpgradeReport report) {
    }

    private record GuidedUpgradeResult(String content, UpgradeContext context) {
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
