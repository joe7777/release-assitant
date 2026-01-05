package com.example.llmhost.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
public class RagLookupClient {

    private static final String TOOL_NAME = "rag.lookup";

    private final List<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper;

    public RagLookupClient(List<ToolCallback> toolCallbacks, ObjectMapper objectMapper) {
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
    }

    public List<RagHit> lookup(Map<String, Object> filters, int limit) {
        ToolCallback toolCallback = findToolCallback(TOOL_NAME)
                .orElseThrow(() -> new IllegalStateException("Tool introuvable: " + TOOL_NAME));
        String toolInput = buildToolInput(filters, limit);
        String response = toolCallback.call(toolInput);
        return parseResponse(response);
    }

    private Optional<ToolCallback> findToolCallback(String name) {
        return toolCallbacks.stream()
                .filter(callback -> name.equals(resolveToolName(callback.getToolDefinition())))
                .findFirst();
    }

    private String buildToolInput(Map<String, Object> filters, int limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filters", filters == null ? Map.of() : filters);
        payload.put("limit", limit);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la requête rag.lookup", ex);
        }
    }

    private List<RagHit> parseResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                throw new IllegalStateException("rag.lookup response must be JSON array");
            }
            return objectMapper.convertValue(root, new TypeReference<>() {
            });
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("rag.lookup response must be JSON array", ex);
        }
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }
}
