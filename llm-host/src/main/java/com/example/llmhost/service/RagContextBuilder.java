package com.example.llmhost.service;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.example.llmhost.rag.RagHit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagContextBuilder {

    private static final int DEFAULT_SNIPPET_LIMIT = 600;
    private static final int DEFAULT_PROJECT_FACT_SNIPPET_LIMIT = 1500;

    private final ProjectFactsSummarizer projectFactsSummarizer;

    public RagContextBuilder(ProjectFactsSummarizer projectFactsSummarizer) {
        this.projectFactsSummarizer = projectFactsSummarizer;
    }

    public String buildContext(List<RagHit> hits, int maxChars) {
        return buildContext(hits, maxChars, DEFAULT_PROJECT_FACT_SNIPPET_LIMIT);
    }

    public String buildContext(List<RagHit> hits, int maxChars, int projectFactMaxChars) {
        if (hits == null || hits.isEmpty()) {
            return "SOURCES:\n(Aucun chunk retourné.)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("SOURCES (cite [S#] dans chaque point):\n");
        for (int i = 0; i < hits.size(); i++) {
            RagHit hit = hits.get(i);
            String entry = formatHit(i + 1, hit, projectFactMaxChars);
            if (builder.length() + entry.length() > maxChars) {
                int remaining = Math.max(0, maxChars - builder.length());
                if (remaining > 0) {
                    builder.append(entry, 0, Math.min(entry.length(), remaining));
                }
                break;
            }
            builder.append(entry);
        }
        return builder.toString();
    }

    private String formatHit(int index, RagHit hit, int projectFactMaxChars) {
        Map<String, Object> metadata = hit.metadata() == null ? Map.of() : hit.metadata();
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("score=" + hit.score());
        addMetadata(joiner, metadata, "documentKey");
        addMetadata(joiner, metadata, "url");
        addMetadata(joiner, metadata, "version");
        addMetadata(joiner, metadata, "library");
        addMetadata(joiner, metadata, "filePath");

        String snippet = projectFactsSummarizer.isProjectFact(hit)
                ? projectFactsSummarizer.summarize(hit, projectFactMaxChars)
                : cleanSnippet(hit.text(), DEFAULT_SNIPPET_LIMIT);
        return "[S" + index + "] " + joiner + "\n"
                + "     snippet=\"" + snippet + "\"\n";
    }

    private void addMetadata(StringJoiner joiner, Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return;
        }
        String text = value.toString();
        if (StringUtils.hasText(text)) {
            joiner.add(key + "=" + text);
        }
    }

    private String cleanSnippet(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 1)) + "…";
    }
}
