package com.example.mcpserver.dto;

import java.util.List;

public record ProjectSpringUsageInventory(
        String repoUrl,
        String commit,
        String springBootVersionDetected,
        List<String> starters,
        List<ImportCount> topImports,
        List<String> springImports,
        List<String> annotations,
        List<String> packages,
        List<ProjectSpringUsageFile> files) {

    public record ImportCount(String name, int count) {
    }

    public record ProjectSpringUsageFile(String path, List<String> springImports, List<String> annotations) {
    }
}
