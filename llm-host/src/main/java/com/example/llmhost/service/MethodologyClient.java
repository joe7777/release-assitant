package com.example.llmhost.service;

import com.example.mcpmethodology.model.ChangeInput;
import com.example.mcpmethodology.model.ComputeEffortRequest;
import com.example.mcpmethodology.model.EffortResult;
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
public class MethodologyClient {

    private static final String TOOL_NAME = "methodology.computeWorkpoints";

    private final List<ToolCallback> toolCallbacks;
    private final ObjectMapper objectMapper;

    public MethodologyClient(List<ToolCallback> toolCallbacks, ObjectMapper objectMapper) {
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
    }

    public Optional<EffortResult> computeEffort(List<ChangeInput> changes) {
        Optional<ToolCallback> callback = findToolCallback(TOOL_NAME);
        if (callback.isEmpty()) {
            return Optional.empty();
        }
        String payload = buildToolInput(changes);
        String response = callback.get().call(payload);
        return parseResponse(response);
    }

    private Optional<ToolCallback> findToolCallback(String name) {
        return toolCallbacks.stream()
                .filter(callback -> name.equals(resolveToolName(callback.getToolDefinition())))
                .findFirst();
    }

    private String buildToolInput(List<ChangeInput> changes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("changes", changes);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de sérialiser la requête methodology.computeWorkpoints", ex);
        }
    }

    private Optional<EffortResult> parseResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(response, EffortResult.class));
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de parser la réponse methodology.computeWorkpoints", ex);
        }
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }
}
