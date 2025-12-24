package com.example.mcpserver.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.ApiChangeResponse;
import com.example.mcpserver.dto.RagSearchResult;

@Service
public class SpringApiChangeService {

    private static final String SOURCE_TYPE = "SPRING_SOURCE";
    private static final String LIBRARY = "spring-framework";

    private final VectorStore vectorStore;

    public SpringApiChangeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public ApiChangeResponse findApiChanges(String symbol, String fromVersion, String toVersion, int topK) {
        List<RagSearchResult> fromMatches = searchVersion(symbol, fromVersion, topK);
        List<RagSearchResult> toMatches = searchVersion(symbol, toVersion, topK);
        String summary = buildSummary(symbol, fromVersion, toVersion, fromMatches, toMatches);
        return new ApiChangeResponse(symbol, fromVersion, toVersion, summary, fromMatches, toMatches);
    }

    private List<RagSearchResult> searchVersion(String symbol, String version, int topK) {
        if (symbol == null || symbol.isBlank() || version == null || version.isBlank()) {
            return List.of();
        }
        int requestTopK = Math.max(1, topK * 4);
        List<Document> results = vectorStore
                .similaritySearch(SearchRequest.builder().query(symbol).topK(requestTopK).build());
        List<RagSearchResult> filtered = new ArrayList<>();
        for (Document doc : results) {
            Map<String, Object> metadata = doc.getMetadata();
            if (!SOURCE_TYPE.equals(metadata.get("sourceType"))) {
                continue;
            }
            if (!LIBRARY.equals(metadata.get("library"))) {
                continue;
            }
            if (!Objects.equals(version, metadata.get("version"))) {
                continue;
            }
            filtered.add(new RagSearchResult(doc.getText(), doc.getScore(), metadata));
            if (filtered.size() >= topK) {
                break;
            }
        }
        return filtered;
    }

    private String buildSummary(String symbol, String fromVersion, String toVersion, List<RagSearchResult> fromMatches,
            List<RagSearchResult> toMatches) {
        StringBuilder summary = new StringBuilder();
        summary.append("RAG comparison for symbol '").append(symbol).append("' between ").append(fromVersion)
                .append(" and ").append(toVersion).append(". ");
        summary.append("Matches in ").append(fromVersion).append(": ").append(fromMatches.size()).append(". ");
        summary.append("Matches in ").append(toVersion).append(": ").append(toMatches.size()).append(". ");
        if (fromMatches.isEmpty() && toMatches.isEmpty()) {
            summary.append("No matching Spring source snippets found.");
        }
        return summary.toString();
    }
}
