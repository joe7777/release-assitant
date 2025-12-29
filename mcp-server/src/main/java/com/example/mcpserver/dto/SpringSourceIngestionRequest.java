package com.example.mcpserver.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpringSourceIngestionRequest(
        String version,
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
