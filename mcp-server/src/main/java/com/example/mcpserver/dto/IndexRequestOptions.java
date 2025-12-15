package com.example.mcpserver.dto;

public record IndexRequestOptions(int chunkSize, int chunkOverlap, boolean normalizeWhitespace) {
    public IndexRequestOptions {
        if (chunkSize <= 0) {
            chunkSize = 800;
        }
        if (chunkOverlap < 0) {
            chunkOverlap = 80;
        }
    }
}
