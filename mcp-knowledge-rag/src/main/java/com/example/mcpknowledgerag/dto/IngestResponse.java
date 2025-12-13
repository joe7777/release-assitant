package com.example.mcpknowledgerag.dto;

public class IngestResponse {

    private String documentHash;
    private boolean ingested;
    private boolean skipped;
    private int chunksCreated;
    private String message;

    public IngestResponse() {
    }

    public IngestResponse(String documentHash, boolean ingested, boolean skipped, int chunksCreated, String message) {
        this.documentHash = documentHash;
        this.ingested = ingested;
        this.skipped = skipped;
        this.chunksCreated = chunksCreated;
        this.message = message;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public void setDocumentHash(String documentHash) {
        this.documentHash = documentHash;
    }

    public boolean isIngested() {
        return ingested;
    }

    public void setIngested(boolean ingested) {
        this.ingested = ingested;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public int getChunksCreated() {
        return chunksCreated;
    }

    public void setChunksCreated(int chunksCreated) {
        this.chunksCreated = chunksCreated;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
