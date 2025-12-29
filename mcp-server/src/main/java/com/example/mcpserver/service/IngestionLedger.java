package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class IngestionLedger {

    private static final Logger logger = LoggerFactory.getLogger(IngestionLedger.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path ledgerPath;
    private final Set<String> documentHashes;

    public IngestionLedger() throws IOException {
        this(Path.of("mcp-ingestion-ledger.json"));
    }

    public IngestionLedger(Path ledgerPath) throws IOException {
        this.ledgerPath = ledgerPath.toAbsolutePath();
        Files.createDirectories(this.ledgerPath.getParent() == null ? Path.of(".") : this.ledgerPath.getParent());
        this.documentHashes = new HashSet<>(readExisting());
    }

    public synchronized boolean alreadyIngested(String documentHash) {
        return documentHashes.contains(documentHash);
    }

    public synchronized void record(String documentHash) {
        documentHashes.add(documentHash);
        persist();
    }

    public synchronized boolean alreadyIngestedChunk(String chunkHash) {
        return documentHashes.contains(chunkKey(chunkHash));
    }

    public synchronized void recordChunk(String chunkHash) {
        documentHashes.add(chunkKey(chunkHash));
        persist();
    }

    private String chunkKey(String chunkHash) {
        return "chunk:" + chunkHash;
    }

    private Set<String> readExisting() throws IOException {
        if (!Files.exists(ledgerPath)) {
            return Set.of();
        }
        return objectMapper.readValue(ledgerPath.toFile(), new TypeReference<>() {
        });
    }

    private void persist() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(ledgerPath.toFile(), documentHashes);
        }
        catch (IOException e) {
            logger.warn("Unable to persist ingestion ledger", e);
        }
    }
}
