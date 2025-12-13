package com.example.upgrader.infra.llm.dto;

public class McpSearchRequest {
    private String query;
    private Filters filters = new Filters();
    private int topK = 10;
    private String llmModel;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Filters getFilters() {
        return filters;
    }

    public void setFilters(Filters filters) {
        this.filters = filters;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public static class Filters {
        private String sourceType;
        private String library;
        private String fromVersion;
        private String toVersion;

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getLibrary() {
            return library;
        }

        public void setLibrary(String library) {
            this.library = library;
        }

        public String getFromVersion() {
            return fromVersion;
        }

        public void setFromVersion(String fromVersion) {
            this.fromVersion = fromVersion;
        }

        public String getToVersion() {
            return toVersion;
        }

        public void setToVersion(String toVersion) {
            this.toVersion = toVersion;
        }
    }
}
