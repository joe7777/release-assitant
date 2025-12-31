package com.example.mcpserver.dto;

import java.util.List;

public record ProjectSpringUsageScanResponse(
        String workspaceId,
        int filesScanned,
        int javaFilesParsed,
        int importsCount,
        int springImportsCount,
        int annotationsCount,
        List<String> springModulesGuessed,
        List<String> bootStartersDetected,
        String documentKey,
        boolean ingested,
        long durationMs) {
}
