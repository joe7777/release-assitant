package com.example.llmhost.service;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.llmhost.rag.RagHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProjectFactsSummarizer {

    private static final Logger logger = LoggerFactory.getLogger(ProjectFactsSummarizer.class);
    private static final int TOP_IMPORT_LIMIT = 30;

    private final ObjectMapper objectMapper;

    public ProjectFactsSummarizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isProjectFact(RagHit hit) {
        if (hit == null || hit.metadata() == null) {
            return false;
        }
        Object docKind = hit.metadata().get("docKind");
        if (docKind != null && "PROJECT_FACT".equalsIgnoreCase(docKind.toString())) {
            return true;
        }
        Object sourceType = hit.metadata().get("sourceType");
        return sourceType != null && "PROJECT_FACT".equalsIgnoreCase(sourceType.toString());
    }

    public String summarize(RagHit hit, int maxChars) {
        if (hit == null) {
            return "";
        }
        String text = hit.text();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        List<ImportEntry> imports = extractImports(text);
        List<ImportEntry> topImports = imports.stream()
                .sorted(Comparator.comparingInt(ImportEntry::count).reversed())
                .limit(TOP_IMPORT_LIMIT)
                .toList();
        logImports(imports, topImports);
        if (imports.isEmpty()) {
            return normalizeSnippet(text, maxChars);
        }
        String summary = buildSummary(topImports);
        return normalizeSnippet(summary, maxChars);
    }

    private String buildSummary(List<ImportEntry> imports) {
        String joined = imports.stream()
                .map(entry -> entry.name() + " (" + entry.count() + ")")
                .collect(Collectors.joining(", "));
        return "Top imports Spring détectés: " + joined;
    }

    private List<ImportEntry> extractImports(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            Map<String, Integer> counts = new LinkedHashMap<>();
            collectImportsFromNode(root, counts);
            return counts.entrySet().stream()
                    .map(entry -> new ImportEntry(entry.getKey(), entry.getValue()))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void collectImportsFromNode(JsonNode node, Map<String, Integer> counts) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectImportsFromNode(item, counts);
            }
            return;
        }
        if (node.isObject()) {
            JsonNode nameNode = node.get("name");
            if (nameNode != null && nameNode.isTextual()) {
                String name = nameNode.asText();
                int count = node.path("count").asInt(1);
                addImport(counts, name, count);
                return;
            }
            node.fields().forEachRemaining(entry -> collectImportsFromNode(entry.getValue(), counts));
            return;
        }
        if (node.isTextual()) {
            String name = node.asText();
            addImport(counts, name, 1);
        }
    }

    private void addImport(Map<String, Integer> counts, String name, int count) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        String normalized = name.trim();
        if (!isRelevantImport(normalized)) {
            return;
        }
        counts.merge(normalized, count, Integer::sum);
    }

    private boolean isRelevantImport(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("org.springframework.")
                || lower.startsWith("org.springframework.boot.")
                || lower.startsWith("javax.")
                || lower.startsWith("jakarta.");
    }

    private String normalizeSnippet(String text, int maxChars) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private void logImports(List<ImportEntry> allImports, List<ImportEntry> topImports) {
        List<String> topFive = topImports.stream()
                .limit(5)
                .map(entry -> entry.name() + " (" + entry.count() + ")")
                .toList();
        logger.debug("PROJECT_FACT imports extracted={} top5={}", allImports.size(), topFive);
    }

    private record ImportEntry(String name, int count) {
    }
}
