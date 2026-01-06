package com.example.llmhost.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.llmhost.config.AppProperties;
import com.example.llmhost.rag.RagHit;
import com.example.llmhost.rag.RagLookupClient;
import com.example.llmhost.rag.RagSearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagMultiPassUpgradeContext {

    private static final Logger logger = LoggerFactory.getLogger(RagMultiPassUpgradeContext.class);
    private static final int RELEASE_NOTES_TOP_K = 5;
    private static final int MAX_HITS = 10;
    private static final int PROJECT_FACT_TOP_K = 3;
    private static final int MAX_PROJECT_FACT_CHARS = 1500;
    private static final int MAX_PROJECT_FACT_CHUNKS = 3;
    private static final int SPRING_SOURCE_IMPORT_LIMIT = 20;

    private final RagSearchClient ragSearchClient;
    private final RagLookupClient ragLookupClient;
    private final RagContextBuilder ragContextBuilder;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public RagMultiPassUpgradeContext(RagSearchClient ragSearchClient, RagLookupClient ragLookupClient,
            RagContextBuilder ragContextBuilder, AppProperties appProperties, ObjectMapper objectMapper) {
        this.ragSearchClient = ragSearchClient;
        this.ragLookupClient = ragLookupClient;
        this.ragContextBuilder = ragContextBuilder;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public UpgradeContext retrieve(String fromVersion, String toVersion, String workspaceId, String repoUrl) {
        List<RagHit> projectFacts = retrieveProjectFacts(workspaceId);
        List<RagHit> migrationHits = retrieveMigrationGuide(fromVersion, toVersion);
        List<RagHit> deprecationHits = retrieveDeprecations(fromVersion, toVersion);
        List<RagHit> sourceCodeHits = appProperties.getRag().isEnableSourceCodePass()
                ? retrieveSpringSourceSnippets(projectFacts, toVersion)
                : List.of();

        List<RagHit> merged = mergeHits(projectFacts, migrationHits, deprecationHits, sourceCodeHits);
        String contextText = ragContextBuilder.buildContext(merged, 6000, MAX_PROJECT_FACT_CHARS);
        return new UpgradeContext(merged, contextText);
    }

    private List<RagHit> retrieveProjectFacts(String workspaceId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "PROJECT_FACT");
        filters.put("workspaceId", workspaceId);
        filters.put("docKind", "PROJECT_FACT");
        List<RagHit> hits = ragLookupClient.lookup(filters, PROJECT_FACT_TOP_K);
        logger.debug("PROJECT_FACT hits retrieved={} for workspaceId={}", hits == null ? 0 : hits.size(), workspaceId);
        if (hits.isEmpty()) {
            logger.info("Aucun PROJECT_FACT retourné pour workspaceId={}", workspaceId);
            return List.of();
        }
        return hits;
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

    private List<RagHit> retrieveSpringSourceSnippets(List<RagHit> projectFacts, String toVersion) {
        Optional<RagHit> inventory = Optional.empty();
        if (projectFacts != null) {
            inventory = projectFacts.stream()
                    .filter(hit -> hit != null && hit.metadata() != null)
                    .filter(hit -> {
                        Object documentKey = hit.metadata().get("documentKey");
                        return documentKey != null && documentKey.toString().contains("/spring-usage-inventory");
                    })
                    .findFirst();
            if (inventory.isEmpty()) {
                inventory = projectFacts.stream().findFirst();
            }
        }
        if (inventory.isEmpty()) {
            logger.info("Aucun PROJECT_FACT spring-usage-inventory pour récupérer les imports Spring.");
            return List.of();
        }
        String json = inventory.get().text();
        if (json == null || json.isBlank()) {
            logger.info("PROJECT_FACT spring-usage-inventory vide, aucun import Spring récupérable.");
            return List.of();
        }
        List<String> springImports = extractSpringImports(json);
        if (springImports.isEmpty()) {
            logger.info("Aucun import Spring trouvé dans le PROJECT_FACT.");
            return List.of();
        }
        Map<String, RagHit> unique = new LinkedHashMap<>();
        for (String springImport : springImports) {
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("sourceType", List.of("SPRING_SOURCE", "SPRING_BOOT_SOURCE"));
            filters.put("version", toVersion);
            logger.debug("RAG search spring source query='{}' filters={} topK=1", springImport, filters);
            List<RagHit> hits = ragSearchClient.search(springImport, filters, 1);
            appendUnique(unique, hits);
            if (unique.size() >= MAX_HITS) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<String> extractSpringImports(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<String> imports = new ArrayList<>();
            JsonNode topImports = root.path("topImports");
            if (topImports.isArray()) {
                for (JsonNode node : topImports) {
                    String name = node.path("name").asText(null);
                    if (name != null && !name.isBlank()) {
                        imports.add(name);
                    }
                }
            }
            if (imports.isEmpty()) {
                JsonNode springImports = root.path("springImports");
                if (springImports.isArray()) {
                    for (JsonNode node : springImports) {
                        String name = node.asText(null);
                        if (name != null && !name.isBlank()) {
                            imports.add(name);
                        }
                    }
                }
            }
            return imports.stream()
                    .filter(name -> name.contains("org.springframework."))
                    .distinct()
                    .limit(SPRING_SOURCE_IMPORT_LIMIT)
                    .toList();
        } catch (IOException ex) {
            logger.warn("Impossible de parser le PROJECT_FACT spring-usage-inventory.", ex);
            return List.of();
        }
    }

    private List<RagHit> mergeHits(List<RagHit> projectFacts, List<RagHit> migrationHits,
            List<RagHit> deprecationHits, List<RagHit> sourceCodeHits) {
        List<RagHit> merged = new ArrayList<>();
        Map<String, RagHit> unique = new LinkedHashMap<>();
        if (projectFacts != null && !projectFacts.isEmpty()) {
            int projectFactLimit = Math.min(projectFacts.size(), MAX_PROJECT_FACT_CHUNKS);
            for (int i = 0; i < projectFactLimit; i++) {
                RagHit hit = projectFacts.get(i);
                String dedupeKey = buildDedupeKey(hit);
                if (!unique.containsKey(dedupeKey)) {
                    unique.put(dedupeKey, hit);
                    merged.add(hit);
                }
            }
        }
        Map<String, RagHit> remaining = new LinkedHashMap<>();
        appendUnique(remaining, migrationHits);
        appendUnique(remaining, deprecationHits);
        appendUnique(remaining, sourceCodeHits);
        for (RagHit hit : remaining.values()) {
            String dedupeKey = buildDedupeKey(hit);
            if (unique.containsKey(dedupeKey)) {
                continue;
            }
            unique.put(dedupeKey, hit);
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
