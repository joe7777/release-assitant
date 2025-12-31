package com.example.mcpserver.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private final Path workspaceRoot;
    private final Path workspaceMapPath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> workspaceMap = new HashMap<>();
    private final Object mapLock = new Object();

    public WorkspaceService(@Value("${mcp.workspace-root:workspaces}") String workspaceRoot) throws IOException {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath();
        Files.createDirectories(this.workspaceRoot);
        this.workspaceMapPath = this.workspaceRoot.resolve(".workspace-map.json");
        loadWorkspaceMap();
    }

    public Path resolveWorkspace(String workspaceId) {
        synchronized (mapLock) {
            String folder = workspaceMap.get(workspaceId);
            if (folder == null) {
                folder = allocateFolderName(workspaceId);
                workspaceMap.put(workspaceId, folder);
                persistWorkspaceMap();
            }
            return workspaceRoot.resolve(folder);
        }
    }

    public WorkspaceCloneResult cloneRepository(String repoUrl, String branch, String authRef, String workspaceId)
            throws GitAPIException, IOException {
        String resolvedWorkspaceId = workspaceId;
        if (resolvedWorkspaceId == null || resolvedWorkspaceId.isBlank()) {
            resolvedWorkspaceId = buildStableWorkspaceId(repoUrl, branch);
        }
        Path target = resolveWorkspace(resolvedWorkspaceId);
        Files.createDirectories(target);

        CloneCommand clone = Git.cloneRepository().setURI(repoUrl).setDirectory(target.toFile());
        if (branch != null && !branch.isBlank()) {
            clone.setBranch(branch);
        }
        if (authRef != null && !authRef.isBlank()) {
            clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(authRef, ""));
        }

        logger.info("Cloning repo {} to {}", repoUrl, target);
        try (Git git = clone.call()) {
            ObjectId head = git.getRepository().resolve("HEAD");
            String commitHash = head != null ? head.getName() : null;
            String resolvedBranch = git.getRepository().getBranch();
            return new WorkspaceCloneResult(resolvedWorkspaceId, repoUrl, resolvedBranch, commitHash, target);
        }
    }

    private void loadWorkspaceMap() throws IOException {
        if (!Files.exists(workspaceMapPath)) {
            return;
        }
        try {
            Map<String, String> loaded = objectMapper.readValue(workspaceMapPath.toFile(),
                    new TypeReference<Map<String, String>>() {
                    });
            workspaceMap.clear();
            workspaceMap.putAll(loaded);
        } catch (IOException ex) {
            logger.warn("Unable to read workspace map at {}", workspaceMapPath, ex);
        }
    }

    private void persistWorkspaceMap() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(workspaceMapPath.toFile(), workspaceMap);
        } catch (IOException ex) {
            logger.warn("Unable to persist workspace map at {}", workspaceMapPath, ex);
        }
    }

    private String allocateFolderName(String workspaceId) {
        String base = sanitizeFolderName(workspaceId);
        if (base.isBlank()) {
            base = "workspace";
        }
        Set<String> used = new HashSet<>(workspaceMap.values());
        String candidate = base;
        int suffix = 1;
        while (used.contains(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String sanitizeFolderName(String value) {
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]+", "-");
    }

    private String buildStableWorkspaceId(String repoUrl, String branch) {
        String repoName = extractRepoName(repoUrl);
        String fingerprint = repoUrl + ":" + (branch == null ? "" : branch);
        return repoName + "-" + shortHash(fingerprint);
    }

    private String extractRepoName(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return "repo";
        }
        String trimmed = repoUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String candidate;
        try {
            if (trimmed.contains("://")) {
                URI uri = URI.create(trimmed);
                String path = uri.getPath();
                candidate = path == null ? trimmed : path;
            } else if (trimmed.contains(":")) {
                candidate = trimmed.substring(trimmed.indexOf(':') + 1);
            } else {
                candidate = trimmed;
            }
        } catch (IllegalArgumentException ex) {
            candidate = trimmed;
        }
        if (candidate.contains("/")) {
            candidate = candidate.substring(candidate.lastIndexOf('/') + 1);
        }
        if (candidate.endsWith(".git")) {
            candidate = candidate.substring(0, candidate.length() - 4);
        }
        candidate = sanitizeFolderName(candidate);
        return candidate.isBlank() ? "repo" : candidate;
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            String hex = builder.toString();
            return hex.substring(0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 not available", ex);
        }
    }
}
