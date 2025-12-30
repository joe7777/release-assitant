package com.example.llmhost.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.llmhost.api.DebugRagTestRequest;
import com.example.llmhost.api.DebugRagTestResponse;
import com.example.llmhost.api.DebugToolsResponse;
import com.example.llmhost.config.AppProperties;
import com.example.llmhost.service.RagContextBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
    private static final Pattern SOURCE_CITATION_PATTERN = Pattern.compile("\\[(S\\d+)]");

    private final List<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final AppProperties appProperties;
    private final SimpleLoggerAdvisor loggingAdvisor;
    private final RagContextBuilder ragContextBuilder;

    public DebugRagController(List<ToolCallback> toolCallbacks, ObjectMapper objectMapper, ChatClient chatClient,
            AppProperties appProperties, RagContextBuilder ragContextBuilder) {
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
        this.appProperties = appProperties;
        this.loggingAdvisor = new SimpleLoggerAdvisor();
        this.ragContextBuilder = ragContextBuilder;
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
        List<DebugRagTestResponse.RagHit> responseResults = results.stream()
                .map(result -> new DebugRagTestResponse.RagHit(result.score(), result.text(), result.metadata()))
                .toList();

        DebugRagTestResponse.Retrieval retrieval = new DebugRagTestResponse.Retrieval(
                topK,
                request.query(),
                request.filters(),
                responseResults
        );
        DebugRagTestResponse.Llm llm = buildLlmResponse(callLlm, request, responseResults, maxContextChars);

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
            JsonNode node = objectMapper.readTree(response);
            List<RagSearchResult> results = parseSearchResults(node);
            return normalizeStringifiedResults(results);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Impossible de lire la réponse rag.search", ex);
        }
    }

    private DebugRagTestResponse.Llm buildLlmResponse(boolean callLlm, DebugRagTestRequest request,
            List<DebugRagTestResponse.RagHit> results, int maxContextChars) {
        List<DebugRagTestResponse.LlmEvidence> evidence = buildEvidence(results);
        if (!callLlm) {
            return new DebugRagTestResponse.Llm(false, null, List.of(), List.of(), evidence);
        }

        String context = ragContextBuilder.buildContext(results, maxContextChars);
        int sourceCount = results.size();
        String systemPrompt = "Tu dois répondre uniquement avec ce qui est dans les sources. "
                + "Tu dois produire exactement " + sourceCount + " points (N = nombre de sources). "
                + "Chaque point i doit citer [Si]. "
                + "Il est interdit d'écrire \"NON TROUVÉ\" si la source [Si] contient un snippet non vide. "
                + "Résume le snippet de [Si] et relie-le à la question.";

        String fullUserPrompt = "QUESTION:\n" + request.llmQuestion() + "\n\n" + context;

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(fullUserPrompt)
                .advisors(loggingAdvisor)
                .toolCallbacks(Collections.emptyList())
                .call()
                .content();

        List<String> citationsFound = extractCitationsFound(answer);
        List<String> missingCitations = computeMissingCitations(results, citationsFound);

        if (!results.isEmpty() && citationsFound.isEmpty()) {
            LOGGER.warn("RAG debug: aucune citation détectée dans la réponse LLM.");
        }

        return new DebugRagTestResponse.Llm(true, answer, citationsFound, missingCitations, evidence);
    }

    private List<RagSearchResult> parseSearchResults(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            return objectMapper.convertValue(node, new TypeReference<>() {
            });
        }
        if (node.has("results") && node.get("results").isArray()) {
            return objectMapper.convertValue(node.get("results"), new TypeReference<>() {
            });
        }
        if (node.has("text") && node.get("text").isTextual()) {
            // Ne jamais propager une liste JSON stringifiée dans "text": on perd la structure des chunks.
            return parseSearchResultsFromString(node.get("text").asText());
        }
        throw new IllegalStateException("Format de réponse rag.search inattendu: " + node);
    }

    private List<RagSearchResult> normalizeStringifiedResults(List<RagSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        if (results.size() == 1 && looksLikeJsonArray(results.get(0).text())) {
            List<RagSearchResult> parsed = parseSearchResultsFromString(results.get(0).text());
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        return results;
    }

    private List<RagSearchResult> parseSearchResultsFromString(String payload) {
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        String trimmed = payload.trim();
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node != null && node.isArray()) {
                return objectMapper.convertValue(node, new TypeReference<>() {
                });
            }
        }
        catch (Exception ex) {
            // Fall through to legacy handling below.
        }
        return List.of(new RagSearchResult(trimmed, 0.0, Map.of()));
    }

    private boolean looksLikeJsonArray(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        String trimmed = payload.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    static List<String> extractCitationsFound(String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = SOURCE_CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return List.copyOf(found);
    }

    static List<String> computeMissingCitations(List<DebugRagTestResponse.RagHit> hits, List<String> found) {
        Set<String> expected = new LinkedHashSet<>();
        for (int i = 0; i < hits.size(); i++) {
            expected.add("S" + (i + 1));
        }
        if (expected.isEmpty()) {
            return List.of();
        }
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(found);
        return List.copyOf(missing);
    }

    private List<DebugRagTestResponse.LlmEvidence> buildEvidence(List<DebugRagTestResponse.RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<DebugRagTestResponse.LlmEvidence> evidence = new java.util.ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            DebugRagTestResponse.RagHit hit = hits.get(i);
            Map<String, Object> metadata = hit.metadata() == null ? Map.of() : hit.metadata();
            evidence.add(new DebugRagTestResponse.LlmEvidence(
                    "S" + (i + 1),
                    resolveMetadataString(metadata, "documentKey"),
                    resolveMetadataString(metadata, "url"),
                    resolveMetadataString(metadata, "version"),
                    resolveMetadataString(metadata, "library")
            ));
        }
        return List.copyOf(evidence);
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
