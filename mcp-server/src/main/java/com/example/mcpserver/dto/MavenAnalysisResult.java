package com.example.mcpserver.dto;

import java.util.List;

public record MavenAnalysisResult(String springBootVersionDetected, List<String> springDependencies,
        List<String> thirdPartyDependencies, String javaVersionDetected) {
}
