package com.example.mcpanalyzer.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Project Analyzer API",
                version = "v1",
                description = "Endpoints for analyzing Spring Boot projects and returning upgrade insights.",
                contact = @Contact(name = "Project Analyzer"),
                license = @License(name = "Apache 2.0")
        ),
        servers = {
                @Server(url = "/", description = "Default server")
        }
)
public class OpenApiConfig {
}
