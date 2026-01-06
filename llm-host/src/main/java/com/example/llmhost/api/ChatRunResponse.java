package com.example.llmhost.api;

import java.util.List;

public record ChatRunResponse(
        String output,
        String structuredJson,
        List<ToolCallTrace> toolCalls,
        boolean toolsUsed,
        GatingStats gating
) {
}
