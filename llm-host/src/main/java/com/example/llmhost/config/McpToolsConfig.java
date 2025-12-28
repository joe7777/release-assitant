package com.example.llmhost.config;

import java.util.List;
import java.util.Map;

import com.example.llmhost.service.McpToolRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    @Bean
    public List<ToolCallback> mcpToolCallbacks(McpToolRegistry registry) {
        return registry.getToolCallbacks();
    }

    @Bean
    public InfoContributor mcpToolsInfoContributor(McpToolRegistry registry) {
        return builder -> builder.withDetail("mcpTools", Map.of(
                "count", registry.getToolNames().size(),
                "names", registry.getToolNames()
        ));
    }
}
