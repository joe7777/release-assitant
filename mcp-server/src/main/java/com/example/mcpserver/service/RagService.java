package com.example.mcpserver.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
            IngestionLedger ingestionLedger, @Value("${mcp.rag.max-content-length:5242880}") int maxContentLength,
            @Value("${mcp.rag.allowlist:https://docs.spring.io,https://github.com}") List<String> allowlist) {
        this.vectorStore = vectorStore;
        this.hashingService = hashingService;
        this.htmlTextExtractor = htmlTextExtractor;
        this.ingestionLedger = ingestionLedger;
        this.maxContentLength = maxContentLength;
        this.allowlist = Set.copyOf(allowlist);
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxContentLength))
                .build();
        this.webClient = WebClient.builder().exchangeStrategies(exchangeStrategies).build();
    }

    public RagIngestionResponse ingestFromHtml(String url, String sourceType, String library, String version,
            String docId, List<String> selectors) throws IOException {
        validateUrl(url);
        FetchResult fetchResult;
        try {
            fetchResult = fetch(url);
        } catch (ContentTooLargeException ex) {
            return new RagIngestionResponse("", 0, 0, List.of("content-too-large"));
        }
        SelectorConfig selectorConfig = SelectorConfig.from(selectors);
        String text = htmlTextExtractor.extract(fetchResult.html(), selectorConfig.contentCss(),
                selectorConfig.removeCss());
        RagIngestionResponse response = ingestText(sourceType, library, version, text, url, docId);
        if (fetchResult.truncated()) {
            List<String> warnings = new ArrayList<>(response.warnings());
            warnings.add("content-truncated");
            return new RagIngestionResponse(response.documentHash(), response.chunksStored(), response.chunksSkipped(),
                    warnings);
        }
        return response;
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
        int requestTopK = Math.max(1, topK * 4);
        List<Document> results = vectorStore
                .similaritySearch(SearchRequest.builder().query(query).topK(requestTopK).build());
        return results.stream().filter(doc -> matchesFilters(doc, filters)).limit(topK)
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

    private FetchResult fetch(String url) throws IOException {
        StringBuilder bodyBuilder = new StringBuilder();
        int[] bytesRead = new int[] { 0 };
        boolean[] truncated = new boolean[] { false };
        try {
            Iterable<DataBuffer> buffers = webClient.get().uri(URI.create(url)).retrieve()
                    .bodyToFlux(DataBuffer.class).toIterable();
            for (DataBuffer buffer : buffers) {
                int remaining = maxContentLength - bytesRead[0];
                if (remaining <= 0) {
                    truncated[0] = true;
                    DataBufferUtils.release(buffer);
                    continue;
                }
                int readable = buffer.readableByteCount();
                int toRead = Math.min(remaining, readable);
                byte[] chunk = new byte[toRead];
                buffer.read(chunk, 0, toRead);
                DataBufferUtils.release(buffer);
                bytesRead[0] += toRead;
                bodyBuilder.append(new String(chunk, StandardCharsets.UTF_8));
                if (toRead < readable) {
                    truncated[0] = true;
                }
            }
        } catch (WebClientResponseException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof DataBufferLimitException) {
                throw new ContentTooLargeException("Content exceeds max buffer size", ex);
            }
            throw ex;
        } catch (DataBufferLimitException ex) {
            throw new ContentTooLargeException("Content exceeds max buffer size", ex);
        }
        if (bytesRead[0] == 0) {
            throw new IOException("Empty response from url " + url);
        }
        return new FetchResult(bodyBuilder.toString(), truncated[0]);
    }

    private record FetchResult(String html, boolean truncated) {
    }

    private record SelectorConfig(String contentCss, List<String> removeCss) {
        static SelectorConfig from(List<String> selectors) {
            if (selectors == null || selectors.isEmpty()) {
                return new SelectorConfig("", List.of());
            }
            String contentCss = selectors.get(0);
            List<String> removeCss = selectors.size() > 1 ? selectors.subList(1, selectors.size()) : List.of();
            return new SelectorConfig(contentCss, removeCss);
        }
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

    private boolean matchesFilters(Document doc, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object value = doc.getMetadata().get(entry.getKey());
            if (!Objects.equals(value, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static class ContentTooLargeException extends IOException {
        ContentTooLargeException(String message) {
            super(message);
        }

        ContentTooLargeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
