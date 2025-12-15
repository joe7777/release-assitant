package com.example.mcpserver.dto;

public record IndexResponse(String workspaceId, int filesProcessed, int chunksStored, int chunksSkipped) {
}
