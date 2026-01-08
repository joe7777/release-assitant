package com.example.mcpserver.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.mcpserver.dto.ApiChangeBatchRequest;
import com.example.mcpserver.dto.ApiChangeBatchResponse;
import com.example.mcpserver.dto.BaselineProposal;
import com.example.mcpserver.dto.ApiChangeResponse;
import com.example.mcpserver.dto.RagIngestionResponse;
import com.example.mcpserver.dto.RagSearchResult;
import com.example.mcpserver.dto.SymbolChanges;
import com.example.mcpserver.dto.SpringSourceIngestionRequest;
import com.example.mcpserver.dto.SpringSourceIngestionResponse;
import com.example.mcpserver.service.RagLookupService;
import com.example.mcpserver.service.RagService;
import com.example.mcpserver.service.SpringApiChangeService;
import com.example.mcpserver.service.SpringBootSourceIngestionService;
import com.example.mcpserver.service.SpringSourceIngestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RagTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagTools.class);
    private final RagService ragService;
    private final RagLookupService ragLookupService;
    private final SpringSourceIngestionService springSourceIngestionService;
    private final SpringApiChangeService springApiChangeService;
    private final SpringBootSourceIngestionService springBootSourceIngestionService;
    private final ObjectMapper objectMapper;

    public RagTools(RagService ragService, RagLookupService ragLookupService,
            SpringSourceIngestionService springSourceIngestionService, SpringApiChangeService springApiChangeService,
            SpringBootSourceIngestionService springBootSourceIngestionService, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.ragLookupService = ragLookupService;
        this.springSourceIngestionService = springSourceIngestionService;
        this.springApiChangeService = springApiChangeService;
        this.springBootSourceIngestionService = springBootSourceIngestionService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "rag.ingestFromHtml", description = "Ingère une page HTML dans le RAG")
    public RagIngestionResponse ingestFromHtml(String url, String sourceType, String library, String version,
            String docId, String docKind, List<String> selectors) throws IOException {
        return ragService.ingestFromHtml(url, sourceType, library, version, docId, docKind, selectors);
    }

    @Tool(name = "rag.ingestText", description = "Ingère un texte brut")
    public RagIngestionResponse ingestText(String sourceType, String library, String version, String content, String url,
            String docId, String docKind) {
        return ragService.ingestText(sourceType, library, version, content, url, docId, docKind);
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

    @Tool(name = "rag.lookup", description = "Recherche déterministe dans Qdrant via filtres")
    public String lookup(Map<String, Object> filters, int limit) {
        List<RagSearchResult> results = ragLookupService.lookup(filters, limit);
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de sérialiser les résultats rag.lookup", ex);
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

    @Tool(name = "rag.findApiChangesBatch", description = "Compare des changements API via RAG entre deux versions pour une liste de symboles")
    public ApiChangeBatchResponse findApiChangesBatch(String requestPayload) {
        long startTime = System.nanoTime();
        int requestedSymbols = 0;
        int processedSymbols = 0;
        boolean truncated = false;
        try {
            ApiChangeBatchRequest request = parseBatchRequest(requestPayload);
            if (request.symbols() == null || request.symbols().isEmpty()) {
                throw new IllegalArgumentException("symbols must not be empty");
            }
            if (request.fromVersion() == null || request.fromVersion().isBlank()) {
                throw new IllegalArgumentException("fromVersion must not be empty");
            }
            if (request.toVersion() == null || request.toVersion().isBlank()) {
                throw new IllegalArgumentException("toVersion must not be empty");
            }
            requestedSymbols = request.symbols().size();

            int topKPerSymbol = request.topKPerSymbol() == null ? 3 : request.topKPerSymbol();
            int maxSymbols = request.maxSymbols() == null ? 500 : request.maxSymbols();
            boolean dedupe = request.dedupe() == null || request.dedupe();

            List<String> normalizedSymbols = new ArrayList<>(request.symbols().size());
            for (String symbol : request.symbols()) {
                if (symbol == null) {
                    throw new IllegalArgumentException("symbols must not contain null values");
                }
                String trimmed = symbol.trim();
                if (trimmed.isEmpty()) {
                    throw new IllegalArgumentException("symbols must not contain blank values");
                }
                normalizedSymbols.add(trimmed);
            }

            List<String> symbols = dedupe
                    ? new ArrayList<>(new LinkedHashSet<>(normalizedSymbols))
                    : normalizedSymbols;

            if (symbols.size() > maxSymbols) {
                symbols = symbols.subList(0, maxSymbols);
                truncated = true;
            }
            processedSymbols = symbols.size();

            List<SymbolChanges> results = new ArrayList<>(symbols.size());
            for (String symbol : symbols) {
                ApiChangeResponse response = springApiChangeService.findApiChanges(symbol, request.fromVersion(),
                        request.toVersion(), topKPerSymbol);
                List<RagSearchResult> hits = new ArrayList<>();
                if (response != null) {
                    if (response.fromMatches() != null) {
                        hits.addAll(response.fromMatches());
                    }
                    if (response.toMatches() != null) {
                        hits.addAll(response.toMatches());
                    }
                }
                results.add(new SymbolChanges(symbol, hits));
            }

            return new ApiChangeBatchResponse(request.fromVersion(), request.toVersion(), requestedSymbols,
                    processedSymbols, truncated, maxSymbols, results);
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            LOGGER.debug(
                    "rag.findApiChangesBatch requestedSymbols={}, processedSymbols={}, truncated={}, durationMs={}",
                    requestedSymbols, processedSymbols, truncated, durationMs);
        }
    }

    private ApiChangeBatchRequest parseBatchRequest(String requestPayload) {
        if (!StringUtils.hasText(requestPayload)) {
            throw new IllegalArgumentException("request is required");
        }
        try {
            ApiChangeBatchRequest request = objectMapper.readValue(requestPayload, ApiChangeBatchRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("request is required");
            }
            return request;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("request must be valid JSON", ex);
        }
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
