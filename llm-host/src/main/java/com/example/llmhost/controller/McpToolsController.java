package com.example.llmhost.controller;

import java.util.List;
import java.util.Map;

import com.example.llmhost.config.McpToolsConfig;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tools")
public class McpToolsController {

    private final List<ToolCallback> toolCallbacks;

    public McpToolsController(List<ToolCallback> toolCallbacks) {
        this.toolCallbacks = toolCallbacks;
    }

    @GetMapping
    public Map<String, Object> listTools() {
        List<String> names = McpToolsConfig.toolNames(toolCallbacks);
        return Map.of(
                "count", names.size(),
                "names", names
        );
    }
}
