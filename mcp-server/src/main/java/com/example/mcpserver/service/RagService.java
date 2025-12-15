package com.example.mcpserver.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.mcpserver.dto.BaselineProposal;
import com.example.mcpserver.dto.RagIngestionResponse;
import com.example.mcpserver.dto.RagSearchResult;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final HashingService hashingService;
    private final HtmlTextExtractor htmlTextExtractor;
    private final IngestionLedger ingestionLedger;
    private final WebClient webClient;
    private final Set<String> allowlist;
    private final int maxContentLength;

    public RagService(VectorStore vectorStore, HashingService hashingService, HtmlTextExtractor htmlTextExtractor,
            IngestionLedger ingestionLedger, @Value("${mcp.rag.max-content-length:1048576}") int maxContentLength,
            @Value("${mcp.rag.allowlist:https://docs.spring.io,https://github.com}") List<String> allowlist) {
        this.vectorStore = vectorStore;
        this.hashingService = hashingService;
        this.htmlTextExtractor = htmlTextExtractor;
        this.ingestionLedger = ingestionLedger;
        this.maxContentLength = maxContentLength;
        this.allowlist = Set.copyOf(allowlist);
        this.webClient = WebClient.builder().build();
    }

    public RagIngestionResponse ingestFromHtml(String url, String sourceType, String library, String version,
            String docId, List<String> selectors) throws IOException {
        validateUrl(url);
        String html = fetch(url);
        String text = htmlTextExtractor.extract(html);
        return ingestText(sourceType, library, version, text, url, docId);
    }

    public RagIngestionResponse ingestText(String sourceType, String library, String version, String content, String url,
            String docId) {
        if (content.getBytes(StandardCharsets.UTF_8).length > maxContentLength) {
            return new RagIngestionResponse("", 0, 0, List.of("content-too-large"));
        }
        String documentKey = docId != null && !docId.isBlank() ? docId : url;
        String documentHash = hashingService.sha256(sourceType + library + version + content);
        if (ingestionLedger.alreadyIngested(documentHash)) {
            return new RagIngestionResponse(documentHash, 0, 1, List.of("duplicate"));
        }

        List<Document> docs = splitToDocuments(content, documentHash, documentKey, sourceType, library, version, url);
        vectorStore.add(docs);
        ingestionLedger.record(documentHash);
        return new RagIngestionResponse(documentHash, docs.size(), 0, List.of());
    }

    public List<RagSearchResult> search(String query, Map<String, Object> filters, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build()).stream()
                .map(doc -> new RagSearchResult(doc.getText(), doc.getScore(), doc.getMetadata()))
                .collect(Collectors.toList());
    }

    public BaselineProposal ensureBaselineIngested(String targetSpringVersion, List<String> libs) {
        List<String> missing = new ArrayList<>();
        for (String lib : libs) {
            String key = hashingService.sha256(lib + targetSpringVersion);
            if (!ingestionLedger.alreadyIngested(key)) {
                missing.add(lib);
            }
        }
        return new BaselineProposal(targetSpringVersion, missing);
    }

    private void validateUrl(String url) {
        boolean allowed = allowlist.stream().anyMatch(url::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("URL not in allowlist: " + url);
        }
    }

    private String fetch(String url) throws IOException {
        byte[] body = webClient.get().uri(URI.create(url)).retrieve().bodyToMono(byte[].class).block();
        if (body == null) {
            throw new IOException("Empty response from url " + url);
        }
        if (body.length > maxContentLength) {
            throw new IOException("Content too large");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private List<Document> splitToDocuments(String content, String docHash, String documentKey, String sourceType,
            String library, String version, String url) {
        List<Document> docs = new ArrayList<>();
        int chunkSize = 800;
        int overlap = 80;
        int step = Math.max(1, chunkSize - overlap);
        int start = 0;
        int idx = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            String chunk = content.substring(start, end);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentHash", docHash);
            metadata.put("chunkIndex", idx++);
            metadata.put("chunkHash", hashingService.sha256(docHash + chunk));
            metadata.put("documentKey", documentKey);
            metadata.put("sourceType", sourceType);
            metadata.put("library", library);
            metadata.put("version", version);
            metadata.put("url", url);
            docs.add(new Document(chunk, metadata));
            if (end >= content.length()) {
                break;
            }
            start = Math.min(content.length(), start + step);
        }
        return docs;
    }
}
