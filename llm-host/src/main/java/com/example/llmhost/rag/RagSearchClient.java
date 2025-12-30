package com.example.llmhost.rag;

import com.example.llmhost.service.RagSearchResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagSearchClient {

    private static final String TOOL_NAME = "rag.search";

    private final List<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper;

    public RagSearchClient(List<ToolCallback> toolCallbacks, ObjectMapper objectMapper) {
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
    }

    public List<RagHit> search(String query, Map<String, Object> filters, int topK) {
        ToolCallback toolCallback = findToolCallback(TOOL_NAME)
                .orElseThrow(() -> new IllegalStateException("Tool introuvable: " + TOOL_NAME));
        String toolInput = buildToolInput(query, filters, topK);
        String response = toolCallback.call(toolInput);
        return parseResponse(response);
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
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la requête rag.search", ex);
        }
    }

    private List<RagHit> parseResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }
        return RagSearchResponseParser.parse(response, objectMapper);
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }
}
