package com.example.mcpknowledgerag.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Knowledge RAG API",
                version = "v1",
                description = "Endpoints for ingesting and searching knowledge content via vector storage.",
                contact = @Contact(name = "Knowledge RAG"),
                license = @License(name = "Apache 2.0")
        ),
        servers = {
                @Server(url = "/", description = "Default server")
        }
)
public class OpenApiConfig {
}
