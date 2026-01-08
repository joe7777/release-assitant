package com.example.mcpserver.dto;

import java.util.List;

public record ApiChangeBatchRequest(List<String> symbols, String fromVersion, String toVersion,
        Integer topKPerSymbol, Integer maxSymbols, Boolean dedupe) {
}
