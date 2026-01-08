package com.example.mcpserver.dto;

import java.util.List;

public record ApiChangeBatchResponse(String fromVersion, String toVersion, int requestedSymbols, int processedSymbols,
        boolean truncated, int maxSymbols, List<SymbolChanges> results) {
}
