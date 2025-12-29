package com.example.llmhost.api;

import java.util.List;

public record DebugToolsResponse(int count, List<ToolInfo> tools) {

    public record ToolInfo(String name, String description) {
    }
}
