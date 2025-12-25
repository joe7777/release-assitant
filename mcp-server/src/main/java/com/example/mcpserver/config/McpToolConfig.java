package com.example.mcpserver.config;

import org.springframework.ai.tool.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.mcpserver.tools.MethodologyTools;
import com.example.mcpserver.tools.ProjectTools;
import com.example.mcpserver.tools.RagTools;

@Configuration
public class McpToolConfig {

    @Bean
    public MethodToolCallbackProvider toolCallbackProvider(RagTools ragTools, ProjectTools projectTools,
            MethodologyTools methodologyTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ragTools, projectTools, methodologyTools)
                .build();
    }
}
