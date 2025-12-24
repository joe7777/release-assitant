package com.example.mcpserver.dto;

import java.util.List;

public record SpringSourceIngestionResponse(String version, String ref, String commit, int filesScanned,
        int filesIngested, int filesSkipped, int chunksStored, int chunksSkipped, List<String> warnings) {
}
