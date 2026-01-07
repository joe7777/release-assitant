package com.example.llmhost.service;

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
public class MethodologyClient {

    private static final String TOOL_NAME = "methodology.writeReport";
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodologyClient.class);

    private final List<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper;

    public MethodologyClient(List<ToolCallback> toolCallbacks, ObjectMapper objectMapper) {
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
    }

    public Optional<String> writeReport(String content) {
        Optional<ToolCallback> callback = findToolCallback(TOOL_NAME);
        if (callback.isEmpty()) {
            return Optional.empty();
        }
        String payload = buildToolInput(content);
        String response = callback.get().call(payload);
        if (!StringUtils.hasText(response)) {
            return Optional.empty();
        }
        return extractJsonObject(response);
    }

    private Optional<ToolCallback> findToolCallback(String name) {
        return toolCallbacks.stream()
                .filter(callback -> name.equals(resolveToolName(callback.getToolDefinition())))
                .findFirst();
    }

    private String buildToolInput(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la requête methodology.writeReport", ex);
        }
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }

    private Optional<String> extractJsonObject(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.isObject()) {
                if (node.hasNonNull("text") && node.get("text").isTextual()) {
                    Optional<String> extracted = parseJsonObject(node.get("text").asText(), "object.text");
                    if (extracted.isPresent()) {
                        return extracted;
                    }
                    logFormat("unknown");
                    logUnknown(response);
                    return Optional.empty();
                }
                logFormat("object");
                return Optional.of(writeJson(node));
            }
            if (node.isArray()) {
                Optional<String> extracted = extractFromArray(node);
                if (extracted.isPresent()) {
                    return extracted;
                }
                logFormat("unknown");
                logUnknown(response);
                return Optional.empty();
            }
            if (node.isTextual()) {
                Optional<String> extracted = parseJsonObject(node.asText(), "text");
                if (extracted.isPresent()) {
                    return extracted;
                }
                logFormat("unknown");
                logUnknown(response);
                return Optional.empty();
            }
        } catch (Exception ex) {
            logFormat("unknown");
            LOGGER.warn("Impossible d'interpréter la réponse methodology.writeReport", ex);
            return Optional.empty();
        }
        logFormat("unknown");
        logUnknown(response);
        return Optional.empty();
    }

    private Optional<String> extractFromArray(JsonNode node) {
        for (JsonNode element : node) {
            if (element != null && element.isObject() && element.hasNonNull("text") && element.get("text").isTextual()) {
                Optional<String> extracted = parseJsonObject(element.get("text").asText(), "array.text");
                if (extracted.isPresent()) {
                    return extracted;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> parseJsonObject(String value, String format) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed.isObject()) {
                logFormat(format);
                return Optional.of(writeJson(parsed));
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la réponse methodology.writeReport", ex);
        }
    }

    private void logFormat(String format) {
        LOGGER.debug("Detected methodology.writeReport format: {}", format);
    }

    private void logUnknown(String response) {
        LOGGER.warn("Réponse methodology.writeReport non reconnue: {}", response);
    }
}
