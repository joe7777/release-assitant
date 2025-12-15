package com.example.mcpserver.tools;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.server.annotation.McpTool;
import org.springframework.stereotype.Component;

import com.example.mcpserver.dto.BaselineProposal;
import com.example.mcpserver.dto.RagIngestionResponse;
import com.example.mcpserver.dto.RagSearchResult;
import com.example.mcpserver.service.RagService;

@Component
public class RagTools {

    private final RagService ragService;

    public RagTools(RagService ragService) {
        this.ragService = ragService;
    }

    @McpTool(name = "rag.ingestFromHtml", description = "Ingère une page HTML dans le RAG")
    public RagIngestionResponse ingestFromHtml(String url, String sourceType, String library, String version,
            String docId, List<String> selectors) throws IOException {
        return ragService.ingestFromHtml(url, sourceType, library, version, docId, selectors);
    }

    @McpTool(name = "rag.ingestText", description = "Ingère un texte brut")
    public RagIngestionResponse ingestText(String sourceType, String library, String version, String content, String url,
            String docId) {
        return ragService.ingestText(sourceType, library, version, content, url, docId);
    }

    @McpTool(name = "rag.search", description = "Recherche des chunks dans Qdrant")
    public List<RagSearchResult> search(String query, Map<String, Object> filters, int topK) {
        return ragService.search(query, filters, topK);
    }

    @McpTool(name = "rag.ensureBaselineIngested", description = "Vérifie les ingestions baseline")
    public BaselineProposal ensureBaselineIngested(String targetSpringVersion, List<String> libs) {
        return ragService.ensureBaselineIngested(targetSpringVersion, libs);
    }
}
