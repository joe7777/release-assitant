package com.example.mcpknowledgerag.service;

import com.example.mcpknowledgerag.dto.IngestRequest;
import com.example.mcpknowledgerag.dto.IngestResponse;
import com.example.mcpknowledgerag.util.HashingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class IngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionService.class);
    private static final int MAX_CHUNK_SIZE = 1000;

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ConcurrentMap<String, ReentrantLock> documentLocks = new ConcurrentHashMap<>();

    public IngestionService(EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public IngestResponse ingest(IngestRequest request) {
        String normalizedContent = HashingUtils.normalizeText(request.getContent());
        String documentHash = HashingUtils.sha256Hex(buildDocumentIdentity(request, normalizedContent));

        ReentrantLock lock = documentLocks.computeIfAbsent(documentHash, key -> new ReentrantLock());
        lock.lock();
        try {
            if (vectorStoreService.existsByDocumentHash(documentHash)) {
                LOGGER.info("Ingestion skipped for existing document hash {}", documentHash);
                return new IngestResponse(documentHash, false, true, 0, "Document already ingested");
            }

            List<String> chunks = chunkContent(normalizedContent);
            List<VectorStoreService.Point> points = new ArrayList<>();
            int chunkIndex = 0;
            for (String chunk : chunks) {
                if (chunk.isBlank()) {
                    continue;
                }
                List<Double> embedding = embeddingService.embed(chunk);
                Map<String, Object> payload = buildPayload(request, documentHash, chunk, chunkIndex);
                points.add(new VectorStoreService.Point(embedding, payload));
                chunkIndex++;
            }

            if (!points.isEmpty()) {
                vectorStoreService.upsertChunks(points);
            }

            LOGGER.info("Ingestion completed for document hash {} with {} chunks", documentHash, points.size());
            return new IngestResponse(documentHash, true, false, points.size(), "Document ingested successfully");
        } finally {
            lock.unlock();
            documentLocks.remove(documentHash, lock);
        }
    }

    private String buildDocumentIdentity(IngestRequest request, String normalizedContent) {
        String libraryPart = request.getLibrary() == null ? "" : request.getLibrary();
        return request.getSourceType().name() + "|" + libraryPart + "|" + request.getVersion() + "|" + normalizedContent;
    }

    private Map<String, Object> buildPayload(IngestRequest request, String documentHash, String chunk, int chunkIndex) {
        String chunkHash = HashingUtils.sha256Hex(HashingUtils.normalizeText(chunk));
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("content", chunk);
        payload.put("documentHash", documentHash);
        payload.put("sourceType", request.getSourceType().name());
        if (request.getLibrary() != null && !request.getLibrary().isBlank()) {
            payload.put("library", request.getLibrary());
        }
        payload.put("version", request.getVersion());
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            payload.put("url", request.getUrl());
        }
        payload.put("chunkIndex", chunkIndex);
        payload.put("chunkTextHash", chunkHash);
        payload.put("ingestedAt", Instant.now().toString());
        return payload;
    }

    private List<String> chunkContent(String content) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + MAX_CHUNK_SIZE);
            int split = findSplitPosition(content, start, end);
            String chunk = content.substring(start, split).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = split;
        }
        if (chunks.isEmpty() && !content.isBlank()) {
            chunks.add(content);
        }
        return chunks;
    }

    private int findSplitPosition(String content, int start, int preferredEnd) {
        if (preferredEnd >= content.length()) {
            return content.length();
        }
        int newlineSplit = content.lastIndexOf('\n', preferredEnd);
        if (newlineSplit > start) {
            return newlineSplit + 1;
        }
        int spaceSplit = content.lastIndexOf(' ', preferredEnd);
        if (spaceSplit > start) {
            return spaceSplit + 1;
        }
        return preferredEnd;
    }
}
