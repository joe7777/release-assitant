package com.example.mcpserver.dto;

public record CloneResponse(String workspaceId, String repoUrl, String branch, String commitHash,
        String localPath) {
}
