package com.example.mcpserver.tools;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.example.mcpserver.dto.BaselineProposal;
import com.example.mcpserver.dto.ApiChangeResponse;
import com.example.mcpserver.dto.RagIngestionResponse;
import com.example.mcpserver.dto.RagSearchResult;
import com.example.mcpserver.dto.SpringSourceIngestionRequest;
import com.example.mcpserver.dto.SpringSourceIngestionResponse;
import com.example.mcpserver.service.RagService;
import com.example.mcpserver.service.SpringApiChangeService;
import com.example.mcpserver.service.SpringBootSourceIngestionService;
import com.example.mcpserver.service.SpringSourceIngestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RagTools {

    private final RagService ragService;
    private final SpringSourceIngestionService springSourceIngestionService;
    private final SpringApiChangeService springApiChangeService;
    private final SpringBootSourceIngestionService springBootSourceIngestionService;
    private final ObjectMapper objectMapper;

    public RagTools(RagService ragService, SpringSourceIngestionService springSourceIngestionService,
            SpringApiChangeService springApiChangeService,
            SpringBootSourceIngestionService springBootSourceIngestionService, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.springSourceIngestionService = springSourceIngestionService;
        this.springApiChangeService = springApiChangeService;
        this.springBootSourceIngestionService = springBootSourceIngestionService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "rag.ingestFromHtml", description = "Ingère une page HTML dans le RAG")
    public RagIngestionResponse ingestFromHtml(String url, String sourceType, String library, String version,
            String docId, List<String> selectors) throws IOException {
        return ragService.ingestFromHtml(url, sourceType, library, version, docId, selectors);
    }

    @Tool(name = "rag.ingestText", description = "Ingère un texte brut")
    public RagIngestionResponse ingestText(String sourceType, String library, String version, String content, String url,
            String docId) {
        return ragService.ingestText(sourceType, library, version, content, url, docId);
    }

    @Tool(name = "rag.search", description = "Recherche des chunks dans Qdrant")
    public String search(String query, Map<String, Object> filters, int topK) {
        List<RagSearchResult> results = ragService.search(query, filters, topK);
        List<RagSearchResult> normalized = results.stream()
                .map(this::normalizeHit)
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de sérialiser les résultats rag.search", ex);
        }
    }

    @Tool(name = "rag.ensureBaselineIngested", description = "Vérifie les ingestions baseline")
    public BaselineProposal ensureBaselineIngested(String targetSpringVersion, List<String> libs) {
        return ragService.ensureBaselineIngested(targetSpringVersion, libs);
    }

    @Tool(name = "rag.ingestSpringSource", description = "Ingère le code source de Spring Framework")
    public SpringSourceIngestionResponse ingestSpringSource(SpringSourceIngestionRequest request)
            throws IOException, GitAPIException {
        return springSourceIngestionService.ingestSpringSource(request);
    }

    @Tool(name = "rag.ingestSpringBootSource", description = "Ingère le code source de Spring Boot")
    public SpringSourceIngestionResponse ingestSpringBootSource(SpringSourceIngestionRequest request)
            throws IOException, GitAPIException {
        return springBootSourceIngestionService.ingestSpringBootSource(request);
    }

    @Tool(name = "rag.findApiChanges", description = "Compare des changements API via RAG entre deux versions")
    public ApiChangeResponse findApiChanges(String symbol, String fromVersion, String toVersion, int topK) {
        return springApiChangeService.findApiChanges(symbol, fromVersion, toVersion, topK);
    }

    private RagSearchResult normalizeHit(RagSearchResult hit) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (hit.metadata() != null) {
            metadata.putAll(hit.metadata());
        }
        ensureMetadataKey(metadata, "sourceType");
        ensureMetadataKey(metadata, "library");
        ensureMetadataKey(metadata, "version");
        ensureMetadataKey(metadata, "documentKey");
        ensureMetadataKey(metadata, "chunkIndex");
        metadata.putIfAbsent("url", null);
        metadata.putIfAbsent("filePath", null);

        double score = normalizeScore(hit.score(), metadata);
        return new RagSearchResult(hit.text(), score, metadata);
    }

    private void ensureMetadataKey(Map<String, Object> metadata, String key) {
        metadata.putIfAbsent(key, null);
    }

    private double normalizeScore(double rawScore, Map<String, Object> metadata) {
        if (rawScore > 1.0) {
            metadata.putIfAbsent("distance", rawScore);
            return Math.max(0.0, 1.0 - rawScore);
        }
        return rawScore;
    }
}
