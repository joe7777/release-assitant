package com.example.llmhost.rag;

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
public class RagApiChangeBatchClient {

    static final String TOOL_NAME = "rag.findApiChangesBatch";

    private final List<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper;

    public RagApiChangeBatchClient(List<ToolCallback> toolCallbacks, ObjectMapper objectMapper) {
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
    }

    public ApiChangeBatchResponse findBatch(List<String> symbols, String fromVersion, String toVersion,
            int topKPerSymbol, int maxSymbols) {
        ToolCallback toolCallback = findToolCallback(TOOL_NAME)
                .orElseThrow(() -> new IllegalStateException("Tool introuvable: " + TOOL_NAME));
        String toolInput = buildToolInput(symbols, fromVersion, toVersion, topKPerSymbol, maxSymbols);
        String response = toolCallback.call(toolInput);
        return parseResponse(response);
    }

    private Optional<ToolCallback> findToolCallback(String name) {
        return toolCallbacks.stream()
                .filter(callback -> name.equals(resolveToolName(callback.getToolDefinition())))
                .findFirst();
    }

    private String buildToolInput(List<String> symbols, String fromVersion, String toVersion,
            int topKPerSymbol, int maxSymbols) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbols", symbols == null ? List.of() : symbols);
        payload.put("fromVersion", fromVersion);
        payload.put("toVersion", toVersion);
        payload.put("topKPerSymbol", topKPerSymbol);
        payload.put("maxSymbols", maxSymbols);
        payload.put("dedupe", true);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la requête rag.findApiChangesBatch", ex);
        }
    }

    private ApiChangeBatchResponse parseResponse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalStateException("rag.findApiChangesBatch response must be JSON object");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.isObject()) {
                throw new IllegalStateException("rag.findApiChangesBatch response must be JSON object");
            }
            return objectMapper.convertValue(root, ApiChangeBatchResponse.class);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("rag.findApiChangesBatch response must be JSON object", ex);
        }
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }
}
