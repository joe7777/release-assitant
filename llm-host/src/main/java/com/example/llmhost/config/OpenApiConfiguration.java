package com.example.llmhost.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "LLM Host API",
                version = "v1",
                description = "API for orchestrating LLM chats with MCP tool calling",
                license = @License(name = "Apache 2.0")
        ),
        servers = {@Server(url = "/", description = "Default server")}
)
public class OpenApiConfiguration {
}
