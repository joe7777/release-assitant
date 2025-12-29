package com.example.mcpserver.tools;

import java.io.IOException;
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

@Component
public class RagTools {

    private final RagService ragService;
    private final SpringSourceIngestionService springSourceIngestionService;
    private final SpringApiChangeService springApiChangeService;
    private final SpringBootSourceIngestionService springBootSourceIngestionService;

    public RagTools(RagService ragService, SpringSourceIngestionService springSourceIngestionService,
            SpringApiChangeService springApiChangeService,
            SpringBootSourceIngestionService springBootSourceIngestionService) {
        this.ragService = ragService;
        this.springSourceIngestionService = springSourceIngestionService;
        this.springApiChangeService = springApiChangeService;
        this.springBootSourceIngestionService = springBootSourceIngestionService;
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
    public List<RagSearchResult> search(String query, Map<String, Object> filters, int topK) {
        return ragService.search(query, filters, topK);
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
}
