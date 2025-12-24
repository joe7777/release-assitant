package com.example.mcpserver.dto;

import java.util.List;

public record ApiChangeResponse(String symbol, String fromVersion, String toVersion, String summary,
        List<RagSearchResult> fromMatches, List<RagSearchResult> toMatches) {
}
