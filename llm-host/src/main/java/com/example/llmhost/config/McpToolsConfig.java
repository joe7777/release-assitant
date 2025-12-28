package com.example.llmhost.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

@Configuration
public class McpToolsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolsConfig.class);

    @Bean
    @Primary
    public List<ToolCallback> mcpToolCallbacks(List<io.modelcontextprotocol.client.McpSyncClient> clients) {
        if (clients == null || clients.isEmpty()) {
            LOGGER.warn("No MCP clients configured, MCP tool callbacks will be empty.");
            return Collections.emptyList();
        }
        List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(clients);
        List<String> names = toolNames(callbacks);
        LOGGER.info("Loaded {} MCP tools: {}", callbacks.size(), names);
        return callbacks;
    }

    @Bean
    public InfoContributor mcpToolsInfoContributor(List<ToolCallback> toolCallbacks) {
        List<String> names = toolNames(toolCallbacks);
        return builder -> builder.withDetail("mcpTools", Map.of(
                "count", names.size(),
                "names", names
        ));
    }

    public static List<String> toolNames(List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        return callbacks.stream()
                .map(ToolCallback::getToolDefinition)
                .map(ToolDefinition::name)
                .filter(StringUtils::hasText)
                .toList();
    }
}
