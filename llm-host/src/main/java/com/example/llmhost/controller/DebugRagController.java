package com.example.llmhost.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.example.llmhost.api.DebugRagTestRequest;
import com.example.llmhost.api.DebugRagTestResponse;
import com.example.llmhost.api.DebugToolsResponse;
import com.example.llmhost.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
public class DebugRagController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugRagController.class);
    private static final String TOOL_NAME = "rag.search";

    private final List<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final AppProperties appProperties;
    private final SimpleLoggerAdvisor loggingAdvisor;

    public DebugRagController(List<ToolCallback> toolCallbacks, ObjectMapper objectMapper, ChatClient chatClient,
            AppProperties appProperties) {
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
        this.appProperties = appProperties;
        this.loggingAdvisor = new SimpleLoggerAdvisor();
    }

    @GetMapping("/tools")
    public DebugToolsResponse listTools() {
        List<DebugToolsResponse.ToolInfo> tools = toolCallbacks.stream()
                .map(callback -> toToolInfo(callback.getToolDefinition()))
                .toList();
        return new DebugToolsResponse(tools.size(), tools);
    }

    @PostMapping("/ragTest")
    public DebugRagTestResponse ragTest(@Valid @RequestBody DebugRagTestRequest request) {
        int topK = resolveTopK(request.topK());
        int maxContextChars = resolveMaxContextChars(request.maxContextChars());
        boolean callLlm = Boolean.TRUE.equals(request.callLlm());
        if (callLlm && !StringUtils.hasText(request.llmQuestion())) {
            throw new IllegalArgumentException("llmQuestion est requis quand callLlm=true");
        }

        ToolCallback toolCallback = findToolCallback(TOOL_NAME)
                .orElseThrow(() -> new IllegalStateException("Tool introuvable: " + TOOL_NAME));

        String toolInput = buildToolInput(request.query(), request.filters(), topK);
        List<RagSearchResult> results = invokeSearch(toolCallback, toolInput);
        List<DebugRagTestResponse.Result> responseResults = results.stream()
                .map(result -> new DebugRagTestResponse.Result(result.score(), result.text(), result.metadata()))
                .toList();

        DebugRagTestResponse.Retrieval retrieval = new DebugRagTestResponse.Retrieval(topK, responseResults);
        DebugRagTestResponse.Llm llm = buildLlmResponse(callLlm, request, results, maxContextChars);

        return new DebugRagTestResponse(retrieval, llm);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    private int resolveTopK(Integer topK) {
        int resolved = topK == null ? appProperties.getSafety().getRagTopK() : topK;
        return resolved > 0 ? resolved : appProperties.getSafety().getRagTopK();
    }

    private int resolveMaxContextChars(Integer maxContextChars) {
        int resolved = maxContextChars == null ? 6000 : maxContextChars;
        return resolved > 0 ? resolved : 6000;
    }

    private Optional<ToolCallback> findToolCallback(String name) {
        return toolCallbacks.stream()
                .filter(callback -> name.equals(resolveToolName(callback.getToolDefinition())))
                .findFirst();
    }

    private String buildToolInput(String query, Map<String, Object> filters, int topK) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        if (filters != null && !filters.isEmpty()) {
            payload.put("filters", filters);
        }
        payload.put("topK", topK);
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la requête rag.search", ex);
        }
    }

    private List<RagSearchResult> invokeSearch(ToolCallback toolCallback, String toolInput) {
        String response = toolCallback.call(toolInput);
        if (!StringUtils.hasText(response)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(response, new TypeReference<>() {
            });
        }
        catch (Exception ex) {
            throw new IllegalStateException("Impossible de lire la réponse rag.search", ex);
        }
    }

    private DebugRagTestResponse.Llm buildLlmResponse(boolean callLlm, DebugRagTestRequest request,
            List<RagSearchResult> results, int maxContextChars) {
        if (!callLlm) {
            return new DebugRagTestResponse.Llm(false, null, List.of(), List.of());
        }

        String context = buildContext(results, maxContextChars);
        String systemPrompt = "Tu es un assistant rigoureux. "
                + "Tu dois répondre uniquement à partir du CONTEXT fourni. "
                + "N'invente aucune information. "
                + "Cite explicitement les documentKey utilisés dans ta réponse.";

        String userPrompt = request.llmQuestion();
        String fullUserPrompt = "CONTEXT:\n" + context + "\n\nQUESTION:\n" + userPrompt;

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(fullUserPrompt)
                .advisors(loggingAdvisor)
                .toolCallbacks(Collections.emptyList())
                .call()
                .content();

        List<String> documentKeys = results.stream()
                .map(result -> resolveMetadataString(result.metadata(), "documentKey"))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        List<String> citationsFound = documentKeys.stream()
                .filter(key -> answer != null && answer.contains(key))
                .toList();
        List<String> missingCitations = documentKeys.stream()
                .filter(key -> citationsFound.stream().noneMatch(found -> Objects.equals(found, key)))
                .toList();

        if (documentKeys.isEmpty() || citationsFound.isEmpty()) {
            LOGGER.warn("RAG debug: aucune citation documentKey détectée dans la réponse LLM.");
        }

        return new DebugRagTestResponse.Llm(true, answer, citationsFound, missingCitations);
    }

    private String buildContext(List<RagSearchResult> results, int maxContextChars) {
        if (results.isEmpty()) {
            return "Aucun chunk retourné.";
        }
        StringBuilder builder = new StringBuilder();
        for (RagSearchResult result : results) {
            String section = formatResult(result);
            if (builder.length() + section.length() > maxContextChars) {
                int remaining = maxContextChars - builder.length();
                if (remaining > 0) {
                    builder.append(section, 0, Math.min(section.length(), remaining));
                }
                break;
            }
            builder.append(section);
        }
        return builder.toString();
    }

    private String formatResult(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String citations = List.of(
                        formatMetadata(metadata, "documentKey"),
                        formatMetadata(metadata, "filePath"),
                        formatMetadata(metadata, "url"),
                        formatMetadata(metadata, "version")
                ).stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" | "));

        return "---\n" + citations + "\n" + result.text() + "\n\n";
    }

    private String formatMetadata(Map<String, Object> metadata, String key) {
        String value = resolveMetadataString(metadata, key);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return key + ": " + value;
    }

    private String resolveMetadataString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private DebugToolsResponse.ToolInfo toToolInfo(ToolDefinition definition) {
        String name = resolveToolName(definition);
        String description = definition == null ? null : definition.description();
        return new DebugToolsResponse.ToolInfo(name, description);
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }

    private record RagSearchResult(String text, double score, Map<String, Object> metadata) {
    }
}
