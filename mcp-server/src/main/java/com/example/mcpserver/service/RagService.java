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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final VectorStoreAddService vectorStoreAddService;
    private final HashingService hashingService;
    private final HtmlTextExtractor htmlTextExtractor;
    private final IngestionLedger ingestionLedger;
    private final WebClient webClient;
    private final Set<String> allowlist;
    private final int maxContentLength;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int embeddingBatchSize;
    private final int embeddingMaxRetries;
    private final long embeddingBackoffMs;

    public RagService(VectorStore vectorStore, VectorStoreAddService vectorStoreAddService,
            HashingService hashingService, HtmlTextExtractor htmlTextExtractor, IngestionLedger ingestionLedger,
            @Value("${mcp.rag.max-content-length:5242880}") int maxContentLength,
            @Value("${mcp.rag.chunk-size:800}") int chunkSize,
            @Value("${mcp.rag.chunk-overlap:80}") int chunkOverlap,
            @Value("${mcp.rag.embedding-batch-size:24}") int embeddingBatchSize,
            @Value("${mcp.rag.embedding-max-retries:3}") int embeddingMaxRetries,
            @Value("${mcp.rag.embedding-backoff-ms:500}") long embeddingBackoffMs,
            @Value("${mcp.rag.allowlist:https://docs.spring.io,https://github.com}") List<String> allowlist) {
        this.vectorStore = vectorStore;
        this.vectorStoreAddService = vectorStoreAddService;
        this.hashingService = hashingService;
        this.htmlTextExtractor = htmlTextExtractor;
        this.ingestionLedger = ingestionLedger;
        this.maxContentLength = maxContentLength;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.embeddingBatchSize = Math.max(1, embeddingBatchSize);
        this.embeddingMaxRetries = Math.max(0, embeddingMaxRetries);
        this.embeddingBackoffMs = Math.max(0, embeddingBackoffMs);
        this.allowlist = Set.copyOf(allowlist);
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxContentLength))
                .build();
        this.webClient = WebClient.builder().exchangeStrategies(exchangeStrategies).build();
    }

    public RagIngestionResponse ingestFromHtml(String url, String sourceType, String library, String version,
            String docId, String docKind, List<String> selectors) throws IOException {
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
        RagIngestionResponse response = ingestText(sourceType, library, version, text, url, docId, docKind);
        if (fetchResult.truncated()) {
            List<String> warnings = new ArrayList<>(response.warnings());
            warnings.add("content-truncated");
            return new RagIngestionResponse(response.documentHash(), response.chunksStored(), response.chunksSkipped(),
                    warnings);
        }
        return response;
    }

    public RagIngestionResponse ingestText(String sourceType, String library, String version, String content, String url,
            String docId, String docKind) {
        if (content.getBytes(StandardCharsets.UTF_8).length > maxContentLength) {
            return new RagIngestionResponse("", 0, 0, List.of("content-too-large"));
        }
        String resolvedDocKind = resolveDocKind(sourceType, docKind);
        String documentKey = docId != null && !docId.isBlank() ? docId : url;
        String documentHash = hashingService.sha256(sourceType + library + version + content);
        if (ingestionLedger.alreadyIngested(documentHash)) {
            return new RagIngestionResponse(documentHash, 0, 1, List.of("duplicate"));
        }

        List<Document> docs = splitToDocuments(content, documentHash, documentKey, sourceType, library, version, url,
                resolvedDocKind);
        IngestionResult ingestionResult = addDocumentsWithRetries(docs);
        if (ingestionResult.chunksSkipped() == 0) {
            ingestionLedger.record(documentHash);
        }
        return new RagIngestionResponse(documentHash, ingestionResult.chunksStored(), ingestionResult.chunksSkipped(),
                ingestionResult.warnings());
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
            String library, String version, String url, String docKind) {
        List<Document> docs = new ArrayList<>();
        int step = Math.max(1, chunkSize - chunkOverlap);
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
            metadata.put("docKind", docKind);
            docs.add(new Document(chunk, metadata));
            if (end >= content.length()) {
                break;
            }
            start = Math.min(content.length(), start + step);
        }
        return docs;
    }

    private String resolveDocKind(String sourceType, String docKind) {
        if (docKind != null && !docKind.isBlank()) {
            return docKind;
        }
        if ("SPRING_RELEASE_NOTE".equalsIgnoreCase(sourceType)) {
            return "RELEASE_NOTES";
        }
        return "DOC";
    }

    private IngestionResult addDocumentsWithRetries(List<Document> docs) {
        IngestionResult result = new IngestionResult();
        int index = 0;
        while (index < docs.size()) {
            int end = Math.min(docs.size(), index + embeddingBatchSize);
            List<Document> batch = docs.subList(index, end);
            addBatchWithRetry(batch, result, embeddingMaxRetries);
            index = end;
        }
        return result;
    }

    private void addBatchWithRetry(List<Document> batch, IngestionResult result, int retriesLeft) {
        try {
            vectorStoreAddService.add(batch);
            result.addStored(batch.size());
        } catch (RuntimeException ex) {
            if (!isReadTimeout(ex)) {
                throw ex;
            }
            if (batch.size() > 1) {
                int mid = batch.size() / 2;
                addBatchWithRetry(batch.subList(0, mid), result, embeddingMaxRetries);
                addBatchWithRetry(batch.subList(mid, batch.size()), result, embeddingMaxRetries);
                return;
            }
            if (retriesLeft > 0) {
                waitBeforeRetry(embeddingMaxRetries - retriesLeft + 1);
                addBatchWithRetry(batch, result, retriesLeft - 1);
                return;
            }
            logger.warn("Embedding timeout for chunk {}, skipping.", batch.get(0).getMetadata().get("chunkIndex"),
                    ex);
            result.addSkipped(1);
            result.addWarning("embedding-timeout");
        }
    }

    private void waitBeforeRetry(int attempt) {
        if (embeddingBackoffMs <= 0) {
            return;
        }
        long delayMs = embeddingBackoffMs * Math.max(1, attempt);
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

    private static class IngestionResult {
        private int chunksStored;
        private int chunksSkipped;
        private final List<String> warnings = new ArrayList<>();

        void addStored(int count) {
            chunksStored += count;
        }

        void addSkipped(int count) {
            chunksSkipped += count;
        }

        void addWarning(String warning) {
            warnings.add(warning);
        }

        int chunksStored() {
            return chunksStored;
        }

        int chunksSkipped() {
            return chunksSkipped;
        }

        List<String> warnings() {
            return warnings;
        }
    }
}
