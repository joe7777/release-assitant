package com.example.llmhost.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.llmhost.rag.RagHit;
import com.example.llmhost.rag.RagLookupClient;
import com.example.llmhost.rag.RagSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagMultiPassUpgradeContext {

    private static final Logger logger = LoggerFactory.getLogger(RagMultiPassUpgradeContext.class);
    private static final int RELEASE_NOTES_TOP_K = 5;
    private static final int MAX_HITS = 10;
    private static final int PROJECT_FACT_TOP_K = 3;

    private final RagSearchClient ragSearchClient;
    private final RagLookupClient ragLookupClient;
    private final RagContextBuilder ragContextBuilder;

    public RagMultiPassUpgradeContext(RagSearchClient ragSearchClient, RagLookupClient ragLookupClient,
            RagContextBuilder ragContextBuilder) {
        this.ragSearchClient = ragSearchClient;
        this.ragLookupClient = ragLookupClient;
        this.ragContextBuilder = ragContextBuilder;
    }

    public UpgradeContext retrieve(String fromVersion, String toVersion, String workspaceId, String repoUrl) {
        List<RagHit> projectFacts = retrieveProjectFacts(workspaceId);
        List<RagHit> migrationHits = retrieveMigrationGuide(fromVersion, toVersion);
        List<RagHit> deprecationHits = retrieveDeprecations(fromVersion, toVersion);

        List<RagHit> merged = mergeHits(projectFacts, migrationHits, deprecationHits);
        String contextText = ragContextBuilder.buildContext(merged, 6000);
        return new UpgradeContext(merged, contextText);
    }

    private List<RagHit> retrieveProjectFacts(String workspaceId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "PROJECT_FACT");
        filters.put("workspaceId", workspaceId);
        filters.put("docKind", "PROJECT_FACT");
        List<RagHit> hits = ragLookupClient.lookup(filters, PROJECT_FACT_TOP_K);
        if (hits.isEmpty()) {
            logger.info("Aucun PROJECT_FACT retourné pour workspaceId={}", workspaceId);
            return List.of();
        }
        RagHit selected = selectProjectFact(hits);
        return selected == null ? List.of() : List.of(selected);
    }

    private RagHit selectProjectFact(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        for (RagHit hit : hits) {
            if (hit == null || hit.metadata() == null) {
                continue;
            }
            Object documentKey = hit.metadata().get("documentKey");
            if (documentKey != null && documentKey.toString().contains("/spring-usage-inventory")) {
                return hit;
            }
        }
        return hits.getFirst();
    }

    private List<RagHit> retrieveMigrationGuide(String fromVersion, String toVersion) {
        String query = "Spring Boot " + toVersion + " upgrading migration guide breaking changes from " + fromVersion;
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "SPRING_RELEASE_NOTE");
        filters.put("library", "spring-boot");
        filters.put("version", List.of("upgrading", "2.6.x", "2.7.x", "2.7.x-deps"));
        logger.debug("RAG search migration guide query='{}' filters={} topK={}", query, filters, RELEASE_NOTES_TOP_K);
        List<RagHit> hits = ragSearchClient.search(query, filters, RELEASE_NOTES_TOP_K);
        logger.debug("RAG search migration guide hits={}", hits == null ? 0 : hits.size());
        return hits;
    }

    private List<RagHit> retrieveDeprecations(String fromVersion, String toVersion) {
        String query = "Spring Boot " + toVersion + " deprecated removed api changes from " + fromVersion;
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "SPRING_RELEASE_NOTE");
        filters.put("library", "spring-boot");
        filters.put("version", List.of("upgrading", "2.6.x", "2.7.x", "2.7.x-deps"));
        logger.debug("RAG search deprecations query='{}' filters={} topK={}", query, filters, RELEASE_NOTES_TOP_K);
        List<RagHit> hits = ragSearchClient.search(query, filters, RELEASE_NOTES_TOP_K);
        logger.debug("RAG search deprecations hits={}", hits == null ? 0 : hits.size());
        return hits;
    }

    private List<RagHit> mergeHits(List<RagHit> projectFacts, List<RagHit> migrationHits, List<RagHit> deprecationHits) {
        List<RagHit> merged = new ArrayList<>();
        if (projectFacts != null && !projectFacts.isEmpty()) {
            merged.add(projectFacts.getFirst());
        }
        Map<String, RagHit> unique = new LinkedHashMap<>();
        appendUnique(unique, migrationHits);
        appendUnique(unique, deprecationHits);
        for (RagHit hit : unique.values()) {
            if (merged.size() >= MAX_HITS) {
                break;
            }
            merged.add(hit);
        }
        return merged;
    }

    private void appendUnique(Map<String, RagHit> unique, List<RagHit> hits) {
        if (hits == null) {
            return;
        }
        for (RagHit hit : hits) {
            String dedupeKey = buildDedupeKey(hit);
            if (!unique.containsKey(dedupeKey)) {
                unique.put(dedupeKey, hit);
            }
        }
    }

    private String buildDedupeKey(RagHit hit) {
        if (hit == null || hit.metadata() == null) {
            return String.valueOf(hit);
        }
        Object documentKey = hit.metadata().get("documentKey");
        Object chunkIndex = hit.metadata().get("chunkIndex");
        if (documentKey == null && chunkIndex == null) {
            return String.valueOf(hit);
        }
        return String.valueOf(documentKey) + "::" + String.valueOf(chunkIndex);
    }
}
