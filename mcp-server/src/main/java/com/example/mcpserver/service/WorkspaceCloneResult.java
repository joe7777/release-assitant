package com.example.mcpserver.service;

import java.nio.file.Path;

public record WorkspaceCloneResult(String workspaceId, String repoUrl, String branch, String commitHash,
        Path localPath) {
}
