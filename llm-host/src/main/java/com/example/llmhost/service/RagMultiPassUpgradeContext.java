package com.example.llmhost.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.llmhost.rag.RagHit;
import com.example.llmhost.rag.RagSearchClient;
import org.springframework.stereotype.Service;

@Service
public class RagMultiPassUpgradeContext {

    private static final int PROJECT_FACT_TOP_K = 1;
    private static final int RELEASE_NOTES_TOP_K = 5;
    private static final int MAX_HITS = 10;

    private final RagSearchClient ragSearchClient;
    private final RagContextBuilder ragContextBuilder;

    public RagMultiPassUpgradeContext(RagSearchClient ragSearchClient, RagContextBuilder ragContextBuilder) {
        this.ragSearchClient = ragSearchClient;
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
        String query = "spring usage inventory workspaceId=" + workspaceId;
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "PROJECT_FACT");
        filters.put("workspaceId", workspaceId);
        return ragSearchClient.search(query, filters, PROJECT_FACT_TOP_K);
    }

    private List<RagHit> retrieveMigrationGuide(String fromVersion, String toVersion) {
        String query = "Spring Boot " + toVersion + " upgrading migration guide breaking changes from " + fromVersion;
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "SPRING_RELEASE_NOTE");
        filters.put("library", "spring-boot");
        filters.put("docKind", List.of("MIGRATION_GUIDE", "RELEASE_NOTES"));
        return ragSearchClient.search(query, filters, RELEASE_NOTES_TOP_K);
    }

    private List<RagHit> retrieveDeprecations(String fromVersion, String toVersion) {
        String query = "Spring Boot " + toVersion + " deprecated removed api changes from " + fromVersion;
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "SPRING_RELEASE_NOTE");
        filters.put("library", "spring-boot");
        filters.put("docKind", List.of("RELEASE_NOTES", "SOURCE_CODE"));
        return ragSearchClient.search(query, filters, RELEASE_NOTES_TOP_K);
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
