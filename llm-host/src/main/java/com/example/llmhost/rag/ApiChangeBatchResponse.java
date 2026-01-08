package com.example.llmhost.rag;

import java.util.List;

public record ApiChangeBatchResponse(String fromVersion,
                                     String toVersion,
                                     int requestedSymbols,
                                     int processedSymbols,
                                     boolean truncated,
                                     int maxSymbols,
                                     List<SymbolChanges> results) {
}
