package com.example.mcpserver.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.MethodologyRulesResponse;
import com.example.mcpserver.dto.WorkpointBreakdown;
import com.example.mcpserver.dto.WorkpointChange;
import com.example.mcpserver.dto.WorkpointComputationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Service
public class MethodologyService {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final String defaultRulesPath = "methodology/v1.yml";

    public MethodologyRulesResponse loadRules() throws IOException {
        ClassPathResource resource = new ClassPathResource(defaultRulesPath);
        try (InputStream is = resource.getInputStream()) {
            var tree = yamlMapper.readTree(is);
            String version = tree.get("version").asText();
            List<String> rules = new ArrayList<>();
            tree.get("rules").forEach(node -> rules.add(node.asText()));
            return new MethodologyRulesResponse(version, rules);
        }
    }

    public WorkpointComputationResult compute(List<WorkpointChange> changes) throws IOException {
        MethodologyRulesResponse rules = loadRules();
        List<WorkpointBreakdown> breakdowns = new ArrayList<>();
        int total = 0;
        for (WorkpointChange change : changes) {
            int score = Math.max(1, change.impact() + change.complexity());
            total += score;
            breakdowns.add(new WorkpointBreakdown(change.id(), change.description(), score,
                    "Impact=" + change.impact() + " complexity=" + change.complexity()));
        }
        return new WorkpointComputationResult(total, breakdowns, rules.version());
    }
}
