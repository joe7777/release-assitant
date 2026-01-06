package com.example.llmhost.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagLookupClient {

    private static final String TOOL_NAME = "rag.lookup";
    private static final Logger LOGGER = LoggerFactory.getLogger(RagLookupClient.class);

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
        try {
            if (!StringUtils.hasText(response)) {
                throw new IllegalStateException("rag.lookup response must be JSON array");
            }
            JsonNode root = objectMapper.readTree(response);
            NormalizedResponse normalized = normalizeResponse(root);
            List<RagHit> hits = objectMapper.convertValue(normalized.hits(), new TypeReference<>() {
            });
            LOGGER.debug("rag.lookup response format={} hits={}", normalized.format(), hits.size());
            return hits;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("rag.lookup response must be JSON array", ex);
        }
    }

    private NormalizedResponse normalizeResponse(JsonNode root) {
        if (root.isArray()) {
            return new NormalizedResponse("array", root);
        }
        if (root.isObject()) {
            JsonNode results = root.get("results");
            if (results != null) {
                return normalizeContainer(results, "results");
            }
            JsonNode hits = root.get("hits");
            if (hits != null) {
                return normalizeContainer(hits, "hits");
            }
            JsonNode text = root.get("text");
            if (text != null && text.isTextual()) {
                return new NormalizedResponse("object.text", parseStringArray(text.asText()));
            }
        }
        if (root.isTextual()) {
            return new NormalizedResponse("text", parseStringArray(root.asText()));
        }
        throw new IllegalStateException("rag.lookup response must be JSON array");
    }

    private NormalizedResponse normalizeContainer(JsonNode container, String format) {
        if (container.isArray()) {
            return new NormalizedResponse(format, container);
        }
        if (container.isTextual()) {
            return new NormalizedResponse(format + ".text", parseStringArray(container.asText()));
        }
        throw new IllegalStateException("rag.lookup response must be JSON array");
    }

    private JsonNode parseStringArray(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalStateException("rag.lookup response must be JSON array");
        }
        try {
            JsonNode parsed = objectMapper.readTree(payload);
            if (!parsed.isArray()) {
                throw new IllegalStateException("rag.lookup response must be JSON array");
            }
            return parsed;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("rag.lookup response must be JSON array", ex);
        }
    }

    private record NormalizedResponse(String format, JsonNode hits) {
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }
}
