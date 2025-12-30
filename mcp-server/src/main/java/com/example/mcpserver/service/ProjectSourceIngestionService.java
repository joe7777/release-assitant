package com.example.mcpserver.service;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.ProjectSourceIngestionRequest;
import com.example.mcpserver.dto.ProjectSourceIngestionResponse;
import com.example.mcpserver.dto.SpringSourceIngestionRequest;
import com.example.mcpserver.dto.SpringSourceIngestionResponse;

@Service
public class ProjectSourceIngestionService {

    private static final String DEFAULT_SOURCE_TYPE = "PROJECT_CODE";
    private static final String DOCUMENT_KEY_PREFIX = "PROJECT_SOURCE";
    private static final List<String> DEFAULT_INCLUDE_GLOBS = List.of("**/*.java");
    private static final List<String> DEFAULT_EXCLUDE_GLOBS = List.of(
            "**/src/test/**",
            "**/src/it/**",
            "**/src/integrationTest/**",
            "**/*Test.java",
            "**/*Tests.java",
            "**/target/**",
            "**/build/**",
            "**/.git/**",
            "**/package-info.java",
            "**/module-info.java");

    private final RepoSourceIngestionService repoSourceIngestionService;

    public ProjectSourceIngestionService(RepoSourceIngestionService repoSourceIngestionService) {
        this.repoSourceIngestionService = repoSourceIngestionService;
    }

    public ProjectSourceIngestionResponse ingestProject(ProjectSourceIngestionRequest request)
            throws IOException, GitAPIException {
        if (request == null || request.repoUrl() == null || request.repoUrl().isBlank()) {
            throw new IllegalArgumentException("repoUrl is required");
        }
        if (request.ref() == null || request.ref().isBlank()) {
            throw new IllegalArgumentException("ref is required");
        }
        if (request.projectKey() == null || request.projectKey().isBlank()) {
            throw new IllegalArgumentException("projectKey is required");
        }
        String sourceType = request.sourceType() == null || request.sourceType().isBlank()
                ? DEFAULT_SOURCE_TYPE
                : request.sourceType().trim();
        String slug = slugify(request.repoUrl());
        RepoSourceConfig config = new RepoSourceConfig(
                request.repoUrl(),
                slug,
                request.projectKey(),
                sourceType,
                "",
                DOCUMENT_KEY_PREFIX,
                List.of(),
                DEFAULT_INCLUDE_GLOBS,
                DEFAULT_EXCLUDE_GLOBS);

        SpringSourceIngestionRequest sourceRequest = new SpringSourceIngestionRequest(
                request.ref(),
                request.modules(),
                request.includeGlobs(),
                request.excludeGlobs(),
                request.includeTests(),
                request.includeNonJava(),
                request.maxFiles(),
                request.maxFileBytes(),
                request.maxLinesPerFile(),
                request.force(),
                request.chunkSize(),
                request.chunkOverlap(),
                request.includeKotlin());

        SpringSourceIngestionResponse response = repoSourceIngestionService.ingest(sourceRequest, config);
        return new ProjectSourceIngestionResponse(
                request.projectKey(),
                request.repoUrl(),
                request.ref(),
                response.modulesRequested(),
                response.filesScanned(),
                response.filesIngested(),
                response.filesSkipped(),
                response.skipReasons(),
                response.durationMs());
    }

    private String slugify(String repoUrl) {
        String value = repoUrl.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(value);
            if (uri.getPath() != null && !uri.getPath().isBlank()) {
                String path = uri.getPath().replaceAll("^/+", "").replaceAll("/+$", "");
                if (!path.isBlank()) {
                    return path.replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase(Locale.ROOT);
                }
            }
        }
        catch (IllegalArgumentException ignored) {
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase(Locale.ROOT);
    }
}
