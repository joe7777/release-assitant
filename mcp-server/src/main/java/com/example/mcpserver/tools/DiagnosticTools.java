package com.example.mcpserver.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticTools.class);

    @Tool(name = "diagnostic.ping", description = "Returns pong:<input>")
    public String ping(String input) {
        LOGGER.info("diagnostic.ping input={}", input);
        return "pong:" + input;
    }
}
