package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.MavenAnalysisResult;
import com.example.mcpserver.dto.ProjectDependencyIngestionRequest;
import com.example.mcpserver.dto.ProjectDependencyIngestionResponse;
import com.example.mcpserver.dto.ProjectDependencyInventory;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProjectDependencyIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectDependencyIngestionService.class);

    private final WorkspaceService workspaceService;
    private final MavenAnalyzerService mavenAnalyzerService;
    private final HashingService hashingService;
    private final IngestionLedger ingestionLedger;
    private final VectorStoreAddService vectorStoreAddService;
    private final ObjectMapper objectMapper;

    public ProjectDependencyIngestionService(WorkspaceService workspaceService, MavenAnalyzerService mavenAnalyzerService,
            HashingService hashingService, IngestionLedger ingestionLedger, VectorStoreAddService vectorStoreAddService,
            ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.mavenAnalyzerService = mavenAnalyzerService;
        this.hashingService = hashingService;
        this.ingestionLedger = ingestionLedger;
        this.vectorStoreAddService = vectorStoreAddService;
        this.objectMapper = objectMapper;
    }

    public ProjectDependencyIngestionResponse ingestDependencies(ProjectDependencyIngestionRequest request)
            throws IOException {
        if (request == null || request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        long start = System.nanoTime();
        Path workspace = workspaceService.resolveWorkspace(request.workspaceId());
        if (!Files.exists(workspace)) {
            throw new IllegalArgumentException("workspaceId does not exist: " + request.workspaceId());
        }
        boolean force = Boolean.TRUE.equals(request.force());

        MavenAnalysisResult analysis = mavenAnalyzerService.analyze(workspace);
        GitInfo gitInfo = resolveGitInfo(workspace);

        String commitOrHash = !"unknown".equals(gitInfo.commit())
                ? gitInfo.commit()
                : hashingService.sha256(request.workspaceId());
        String documentKey = "PROJECT_FACT/" + request.workspaceId() + "/" + commitOrHash + "/maven-dependencies";

        ProjectDependencyInventory inventory = new ProjectDependencyInventory(
                gitInfo.repoUrl(),
                gitInfo.commit(),
                analysis.springBootVersionDetected(),
                analysis.javaVersionDetected(),
                analysis.springDependencies(),
                analysis.thirdPartyDependencies());

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(inventory);
        String documentHash = hashingService.sha256(documentKey + json);
        boolean ingested = true;
        if (!force && ingestionLedger.alreadyIngested(documentHash)) {
            ingested = false;
        } else {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceType", "PROJECT_FACT");
            metadata.put("docKind", "PROJECT_FACT");
            metadata.put("library", "project");
            metadata.put("version", analysis.springBootVersionDetected());
            metadata.put("workspaceId", request.workspaceId());
            metadata.put("repoUrl", gitInfo.repoUrl());
            metadata.put("commit", gitInfo.commit());
            metadata.put("documentKey", documentKey);
            metadata.put("javaVersion", analysis.javaVersionDetected());
            vectorStoreAddService.add(List.of(new Document(json, metadata)));
            ingestionLedger.record(documentHash);
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new ProjectDependencyIngestionResponse(
                request.workspaceId(),
                gitInfo.repoUrl(),
                gitInfo.commit(),
                analysis.springBootVersionDetected(),
                analysis.javaVersionDetected(),
                analysis.springDependencies(),
                analysis.thirdPartyDependencies(),
                documentKey,
                ingested,
                durationMs);
    }

    private GitInfo resolveGitInfo(Path workspace) {
        if (Files.isDirectory(workspace.resolve(".git"))) {
            try (Git git = Git.open(workspace.toFile())) {
                StoredConfig config = git.getRepository().getConfig();
                String repoUrl = config.getString("remote", "origin", "url");
                String commit = git.getRepository().resolve("HEAD").name();
                return new GitInfo(repoUrl != null ? repoUrl : "unknown", commit != null ? commit : "unknown");
            } catch (Exception ex) {
                logger.warn("Unable to resolve Git metadata for {}", workspace, ex);
            }
        }
        return new GitInfo("unknown", "unknown");
    }

    private record GitInfo(String repoUrl, String commit) {
    }
}
