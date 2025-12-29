package com.example.mcpserver.dto;

import java.util.List;
import java.util.Map;

public record SpringSourceIngestionResponse(String version, List<String> modulesRequested, int filesScanned,
        int filesIngested, int filesSkipped, Map<String, Integer> skipReasons, long durationMs) {
}
