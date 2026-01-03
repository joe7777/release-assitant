package com.example.mcpserver.dto;

import java.util.List;

public record ProjectDependencyInventory(
        String repoUrl,
        String commit,
        String springBootVersion,
        String javaVersion,
        List<String> springDependencies,
        List<String> thirdPartyDependencies) {
}
