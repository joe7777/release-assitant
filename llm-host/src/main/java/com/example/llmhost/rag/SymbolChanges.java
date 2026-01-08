package com.example.llmhost.rag;

import java.util.List;

public record SymbolChanges(String symbol, List<RagHit> hits) {
}
