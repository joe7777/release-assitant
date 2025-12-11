package com.example.mcpprojectanalyzer.api;

import java.util.List;

public record AnalyzeResponse(
        String springVersionCurrent,
        List<DependencyDto> dependencies,
        List<String> modules
) {
}
