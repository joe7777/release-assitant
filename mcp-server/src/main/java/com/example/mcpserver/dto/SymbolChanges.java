package com.example.mcpserver.dto;

import java.util.List;

public record SymbolChanges(String symbol, List<RagSearchResult> hits) {
}
