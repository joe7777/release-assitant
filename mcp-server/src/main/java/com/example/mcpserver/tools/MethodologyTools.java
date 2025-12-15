package com.example.mcpserver.tools;

import java.io.IOException;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.example.mcpserver.dto.MethodologyRulesResponse;
import com.example.mcpserver.dto.WorkpointChange;
import com.example.mcpserver.dto.WorkpointComputationResult;
import com.example.mcpserver.service.MethodologyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class MethodologyTools {

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
}
