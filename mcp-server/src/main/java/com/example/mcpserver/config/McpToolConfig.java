package com.example.mcpserver.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.mcpserver.tools.MethodologyTools;
import com.example.mcpserver.tools.ProjectTools;
import com.example.mcpserver.tools.RagTools;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(RagTools ragTools, ProjectTools projectTools,
            MethodologyTools methodologyTools) {
        return ToolCallbackProvider.builder()
                .toolObjects(ragTools, projectTools, methodologyTools)
                .build();
    }
}
