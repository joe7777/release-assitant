package com.example.mcpserver.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.IndexRequestOptions;
import com.example.mcpserver.dto.IndexResponse;

@Service
public class CodeIndexer {

    private static final Logger logger = LoggerFactory.getLogger(CodeIndexer.class);

    private final VectorStore vectorStore;
    private final HashingService hashingService;
    private final int maxFileSizeBytes;
    private final Set<String> allowedExtensions;

    public CodeIndexer(VectorStore vectorStore, HashingService hashingService,
            @Value("${mcp.rag.max-content-length:1048576}") int maxFileSizeBytes) {
        this.vectorStore = vectorStore;
        this.hashingService = hashingService;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedExtensions = Set.of(".java", ".kt", ".kts", ".groovy", ".xml", ".yml", ".yaml", ".properties",
                ".md");
    }

    public IndexResponse indexWorkspace(Path workspace, IndexRequestOptions options, Map<String, String> metadataBase)
            throws IOException {
        List<Path> files = Files.walk(workspace)
                .filter(Files::isRegularFile)
                .filter(p -> !isBinary(p))
                .filter(p -> allowedExtensions.stream().anyMatch(ext -> p.toString().endsWith(ext)))
                .collect(Collectors.toList());

        int chunksStored = 0;
        int chunksSkipped = 0;

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (options.normalizeWhitespace()) {
                content = content.replaceAll("\\s+", " ").trim();
            }
            String documentHash = hashingService.sha256(content + file.toString());
            List<String> chunks = chunk(content, options.chunkSize(), options.chunkOverlap());

            for (String chunkText : chunks) {
                String chunkHash = hashingService.sha256(documentHash + chunkText);
                Document doc = new Document(chunkText, buildMetadata(metadataBase, file, documentHash, chunkHash));
                vectorStore.add(List.of(doc));
                chunksStored++;
            }
        }
        return new IndexResponse(workspace.getFileName().toString(), files.size(), chunksStored, chunksSkipped);
    }

    private boolean isBinary(Path p) {
        try {
            if (Files.size(p) > maxFileSizeBytes) {
                return true;
            }
            String probe = Files.probeContentType(p);
            if (probe != null && !probe.startsWith("text") && !probe.contains("xml")) {
                return true;
            }
            byte[] bytes = Files.readAllBytes(p);
            for (byte b : bytes) {
                if (b == 0) {
                    return true;
                }
            }
            return false;
        }
        catch (IOException e) {
            logger.warn("Unable to inspect file {}", p, e);
            return true;
        }
    }

    private Map<String, Object> buildMetadata(Map<String, String> base, Path file, String documentHash, String chunkHash) {
        Map<String, Object> metadata = new HashMap<>(base);
        metadata.put("filePath", file.toString());
        metadata.put("documentHash", documentHash);
        metadata.put("chunkHash", chunkHash);
        metadata.put("module", workspaceModule(file));
        metadata.put("language", languageFor(file));
        return metadata;
    }

    private String workspaceModule(Path file) {
        Path parent = file.getParent();
        return parent != null ? parent.getFileName().toString() : "";
    }

    private String languageFor(Path file) {
        String name = file.toString();
        if (name.endsWith(".kt") || name.endsWith(".kts")) {
            return "kotlin";
        }
        if (name.endsWith(".java")) {
            return "java";
        }
        if (name.endsWith(".xml")) {
            return "xml";
        }
        return "text";
    }

    List<String> chunk(String content, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, chunkSize - overlap);
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            result.add(content.substring(start, end));
            if (end >= content.length()) {
                break;
            }
            start = Math.min(content.length(), start + step);
        }
        return result;
    }
}
