package com.example.mcpserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.mcpserver.tools.DiagnosticTools;
import com.example.mcpserver.tools.MethodologyTools;
import com.example.mcpserver.tools.ProjectTools;
import com.example.mcpserver.tools.RagTools;

@Configuration
public class McpToolConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolConfig.class);

    @Bean
    public MethodToolCallbackProvider toolCallbackProvider(RagTools ragTools, ProjectTools projectTools,
            MethodologyTools methodologyTools, DiagnosticTools diagnosticTools) {
        LOGGER.info("Registering MCP tools: rag, project, methodology, diagnostic");
        return MethodToolCallbackProvider.builder()
                .toolObjects(ragTools, projectTools, methodologyTools, diagnosticTools)
                .build();
    }
}
