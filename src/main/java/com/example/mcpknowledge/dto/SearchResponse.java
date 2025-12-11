package com.example.mcpknowledge.dto;

import java.util.List;

public record SearchResponse(List<SearchResultItem> results) {
}
