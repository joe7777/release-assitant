package com.example.upgrader.infra.llm.dto;

import java.util.ArrayList;
import java.util.List;

public class McpSearchResponse {
    private List<SearchResultItem> results = new ArrayList<>();

    public List<SearchResultItem> getResults() {
        return results;
    }

    public void setResults(List<SearchResultItem> results) {
        this.results = results;
    }
}
