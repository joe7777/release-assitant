package com.example.mcpserver.tools;

import java.io.IOException;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.mcpserver.dto.MethodologyRulesResponse;
import com.example.mcpserver.dto.UpgradeReport;
import com.example.mcpserver.dto.WorkpointChange;
import com.example.mcpserver.dto.WorkpointComputationResult;
import com.example.mcpserver.service.MethodologyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class MethodologyTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodologyTools.class);
    private final MethodologyService methodologyService;
    private final ObjectMapper objectMapper;

    public MethodologyTools(MethodologyService methodologyService, ObjectMapper objectMapper) {
        this.methodologyService = methodologyService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "methodology.getRules", description = "Retourne les règles de méthodologie")
    public MethodologyRulesResponse getRules() throws IOException {
        return methodologyService.loadRules();
    }

    @Tool(name = "methodology.computeWorkpoints", description = "Calcule les workpoints depuis une liste de changements")
    public WorkpointComputationResult computeWorkpoints(String changesJson) throws IOException {
        List<WorkpointChange> changes = objectMapper.readValue(changesJson, new TypeReference<>() {
        });
        return methodologyService.compute(changes);
    }

    @Tool(name = "methodology.writeReport", description = "Nettoie et reformate un UpgradeReport en JSON")
    public UpgradeReport writeReport(String content) throws IOException {
        String json = extractJson(content);
        if (json == null || json.isBlank()) {
            LOGGER.warn("methodology.writeReport: extracted JSON is empty.");
        }
        try {
            return objectMapper.readValue(json, UpgradeReport.class);
        } catch (IOException exception) {
            LOGGER.warn("methodology.writeReport: failed to parse UpgradeReport JSON.", exception);
            throw exception;
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            return "";
        }
        int start = content.indexOf('{');
        if (start < 0) {
            return content.trim();
        }
        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            char current = content.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return content.substring(start).trim();
    }
}
