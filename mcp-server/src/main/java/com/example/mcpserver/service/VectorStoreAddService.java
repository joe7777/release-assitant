package com.example.mcpserver.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.netty.handler.timeout.ReadTimeoutException;

@Service
public class VectorStoreAddService {

    private final VectorStore vectorStore;
    private final long addTimeoutMs;
    private final int addBatchSize;
    private final int maxRetries;
    private final long backoffMs;

    public VectorStoreAddService(VectorStore vectorStore,
            @Value("${mcp.vector-add-timeout-ms:30000}") long addTimeoutMs,
            @Value("${mcp.vector-add-batch-size:24}") int addBatchSize,
            @Value("${mcp.vector-add-max-retries:3}") int maxRetries,
            @Value("${mcp.vector-add-backoff-ms:500}") long backoffMs) {
        this.vectorStore = vectorStore;
        this.addTimeoutMs = addTimeoutMs;
        this.addBatchSize = Math.max(1, addBatchSize);
        this.maxRetries = Math.max(0, maxRetries);
        this.backoffMs = Math.max(0, backoffMs);
    }

    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        int index = 0;
        while (index < documents.size()) {
            int end = Math.min(documents.size(), index + addBatchSize);
            List<Document> batch = documents.subList(index, end);
            addBatchWithRetry(batch, maxRetries);
            index = end;
        }
    }

    private void addBatchWithRetry(List<Document> batch, int retriesLeft) {
        try {
            addWithTimeout(batch);
        } catch (RuntimeException ex) {
            if (!isReadTimeout(ex)) {
                throw ex;
            }
            if (batch.size() > 1) {
                int mid = batch.size() / 2;
                addBatchWithRetry(batch.subList(0, mid), maxRetries);
                addBatchWithRetry(batch.subList(mid, batch.size()), maxRetries);
                return;
            }
            if (retriesLeft > 0) {
                waitBeforeRetry(maxRetries - retriesLeft + 1);
                addBatchWithRetry(batch, retriesLeft - 1);
                return;
            }
            throw ex;
        }
    }

    private void addWithTimeout(List<Document> batch) {
        if (addTimeoutMs <= 0) {
            vectorStore.add(batch);
            return;
        }
        try {
            CompletableFuture.runAsync(() -> vectorStore.add(batch))
                    .get(addTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new RuntimeException(ReadTimeoutException.INSTANCE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

    private void waitBeforeRetry(int attempt) {
        if (backoffMs <= 0) {
            return;
        }
        long delayMs = backoffMs * Math.max(1, attempt);
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isReadTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof io.netty.handler.timeout.ReadTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
