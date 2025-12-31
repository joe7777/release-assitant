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

    public VectorStoreAddService(VectorStore vectorStore,
            @Value("${mcp.vector-add-timeout-ms:30000}") long addTimeoutMs) {
        this.vectorStore = vectorStore;
        this.addTimeoutMs = addTimeoutMs;
    }

    public void add(List<Document> documents) {
        if (addTimeoutMs <= 0) {
            vectorStore.add(documents);
            return;
        }
        try {
            CompletableFuture.runAsync(() -> vectorStore.add(documents))
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
}
