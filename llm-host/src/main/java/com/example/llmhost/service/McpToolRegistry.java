package com.example.llmhost.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.McpClient;
import org.springframework.ai.mcp.client.tool.McpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.util.StringUtils;

@Service
public class McpToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolRegistry.class);

    private final ObjectProvider<McpClient> mcpClientProvider;
    private final String mcpServerUrl;
    private final CopyOnWriteArrayList<ToolCallback> toolCallbacks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> toolNames = new CopyOnWriteArrayList<>();

    public McpToolRegistry(ObjectProvider<McpClient> mcpClientProvider,
            @Value("${spring.ai.mcp.client.transport.streamable-http.url:}") String mcpServerUrl) {
        this.mcpClientProvider = mcpClientProvider;
        this.mcpServerUrl = mcpServerUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadToolsOnStartup() {
        reloadTools();
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    public List<String> getToolNames() {
        return Collections.unmodifiableList(toolNames);
    }

    public synchronized List<ToolCallback> reloadTools() {
        McpClient mcpClient = mcpClientProvider.getIfAvailable();
        if (mcpClient == null) {
            LOGGER.warn("MCP client not configured, skipping tool discovery.");
            toolCallbacks.clear();
            toolNames.clear();
            return toolCallbacks;
        }

        Instant start = Instant.now();
        try {
            List<ToolDefinition> definitions = mcpClient.listTools();
            List<ToolCallback> callbacks = definitions.stream()
                    .map(definition -> (ToolCallback) new McpToolCallback(mcpClient, definition))
                    .toList();
            List<String> names = definitions.stream()
                    .map(ToolDefinition::name)
                    .filter(StringUtils::hasText)
                    .toList();

            toolCallbacks.clear();
            toolCallbacks.addAll(callbacks);
            toolNames.clear();
            toolNames.addAll(names);

            LOGGER.info("Loaded {} MCP tools from {}: {} ({} ms)", callbacks.size(), mcpServerUrl, names,
                    Duration.between(start, Instant.now()).toMillis());
            return toolCallbacks;
        }
        catch (Exception ex) {
            toolCallbacks.clear();
            toolNames.clear();
            LOGGER.warn("Failed to load MCP tools from {}: {}", mcpServerUrl, ex.getMessage());
            return toolCallbacks;
        }
    }
}
