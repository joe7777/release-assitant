package com.example.mcpserver.dto;

import java.util.List;

public record MethodologyRulesResponse(String version, List<String> rules) {
}
