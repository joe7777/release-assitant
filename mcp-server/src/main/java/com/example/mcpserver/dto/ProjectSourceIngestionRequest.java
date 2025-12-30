package com.example.mcpserver.dto;

import java.util.List;

public record ProjectSourceIngestionRequest(
        String repoUrl,
        String ref,
        String projectKey,
        String sourceType,
        List<String> modules,
        List<String> includeGlobs,
        List<String> excludeGlobs,
        Boolean includeTests,
        Boolean includeNonJava,
        Integer maxFiles,
        Integer maxFileBytes,
        Integer maxLinesPerFile,
        Boolean force,
        Integer chunkSize,
        Integer chunkOverlap,
        Boolean includeKotlin) {
}
