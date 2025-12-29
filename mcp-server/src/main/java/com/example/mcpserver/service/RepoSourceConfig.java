package com.example.mcpserver.service;

import java.util.List;

public record RepoSourceConfig(
        String repoUrl,
        String repoSlug,
        String library,
        String sourceType,
        String refPrefix,
        String documentKeyPrefix,
        List<String> defaultModules,
        List<String> defaultIncludeGlobs,
        List<String> defaultExcludeGlobs) {
}
