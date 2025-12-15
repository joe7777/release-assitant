package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private final Path workspaceRoot;

    public WorkspaceService(@Value("${mcp.workspace-root:workspaces}") String workspaceRoot) throws IOException {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath();
        Files.createDirectories(this.workspaceRoot);
    }

    public Path resolveWorkspace(String workspaceId) {
        return workspaceRoot.resolve(workspaceId);
    }

    public String cloneRepository(String repoUrl, String branch, String authRef) throws GitAPIException, IOException {
        String workspaceId = UUID.randomUUID().toString();
        Path target = resolveWorkspace(workspaceId);
        Files.createDirectories(target);

        CloneCommand clone = Git.cloneRepository().setURI(repoUrl).setDirectory(target.toFile());
        if (branch != null && !branch.isBlank()) {
            clone.setBranch(branch);
        }
        if (authRef != null && !authRef.isBlank()) {
            clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(authRef, ""));
        }

        logger.info("Cloning repo {} to {}", repoUrl, target);
        clone.call().close();
        return workspaceId;
    }
}
