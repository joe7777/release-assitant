package com.example.mcpmethodology.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Methodology Service API",
                version = "v1",
                description = "API documentation for computing methodology-based effort estimates.",
                contact = @Contact(name = "Methodology Service"),
                license = @License(name = "Apache 2.0")
        ),
        servers = {
                @Server(url = "/", description = "Default server")
        }
)
public class OpenApiConfig {
}
