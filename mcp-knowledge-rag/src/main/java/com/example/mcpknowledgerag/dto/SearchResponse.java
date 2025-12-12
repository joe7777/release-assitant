package com.example.mcpknowledgerag.dto;

import java.util.List;

public class SearchResponse {

    private List<SearchResultItem> results;

    public SearchResponse() {
    }

    public SearchResponse(List<SearchResultItem> results) {
        this.results = results;
    }

    public List<SearchResultItem> getResults() {
        return results;
    }

    public void setResults(List<SearchResultItem> results) {
        this.results = results;
    }
}
