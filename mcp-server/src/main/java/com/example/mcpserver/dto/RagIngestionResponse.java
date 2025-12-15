package com.example.mcpserver.dto;

import java.util.List;

public record RagIngestionResponse(String documentHash, int chunksStored, int chunksSkipped, List<String> warnings) {
}
