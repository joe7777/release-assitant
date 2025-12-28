package com.example.llmhost.controller;

import java.util.List;

import com.example.llmhost.service.McpToolRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tools")
public class McpToolsController {

    private final McpToolRegistry registry;

    public McpToolsController(McpToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<String> listTools() {
        return registry.getToolNames();
    }

    @PostMapping("/reload")
    @ResponseStatus(HttpStatus.OK)
    public List<String> reloadTools() {
        registry.reloadTools();
        return registry.getToolNames();
    }
}
