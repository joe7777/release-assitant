package com.example.llmhost.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.llmhost.config.AppProperties;
import com.example.llmhost.rag.ApiChangeBatchResponse;
import com.example.llmhost.rag.RagApiChangeBatchClient;
import com.example.llmhost.rag.RagHit;
import com.example.llmhost.rag.RagLookupClient;
import com.example.llmhost.rag.RagSearchClient;
import com.example.llmhost.rag.SymbolChanges;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RagMultiPassUpgradeContext {

    private static final Logger logger = LoggerFactory.getLogger(RagMultiPassUpgradeContext.class);
    private static final int RELEASE_NOTES_TOP_K = 5;
    private static final int FOCUSED_RELEASE_NOTES_TOP_K = 10;
    private static final int FOCUSED_SEARCH_MULTIPLIER = 3;
    private static final int MAX_HITS = 12;
    private static final int PROJECT_FACT_TOP_K = 5;
    private static final int FOCUSED_PROJECT_FACT_TOP_K = 8;
    private static final int MAX_PROJECT_FACT_CHARS = 1500;
    private static final int MAX_PROJECT_FACT_CHUNKS = 3;
    private static final int SPRING_SOURCE_IMPORT_LIMIT = 20;
    private static final int DEPRECATION_LOOKUP_LIMIT = 50;
    private static final int PROJECT_FACT_SYMBOL_LOOKUP_LIMIT = 100;
    private static final int API_CHANGES_TOP_K_PER_SYMBOL = 3;
    private static final int API_CHANGES_MAX_SYMBOLS = 500;
    private static final Pattern DEPRECATION_PATTERN =
            Pattern.compile("(?i)\\b(deprecat|deprecated|deprecation|removed|removal|breaking)\\b");
    private static final Pattern FQCN_PATTERN = Pattern.compile(
            "\\b[A-Za-z_][A-Za-z0-9_$.]*\\.[A-Za-z0-9_$.]+\\b");
    private static final List<String> WEB_SYMBOL_MARKERS = List.of(
            ".web.",
            ".webmvc.",
            ".webflux.",
            ".servlet.",
            ".tomcat.",
            ".web.bind."
    );

    private final RagSearchClient ragSearchClient;
    private final RagLookupClient ragLookupClient;
    private final RagApiChangeBatchClient ragApiChangeBatchClient;
    private final RagContextBuilder ragContextBuilder;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public RagMultiPassUpgradeContext(RagSearchClient ragSearchClient, RagLookupClient ragLookupClient,
            RagApiChangeBatchClient ragApiChangeBatchClient, RagContextBuilder ragContextBuilder,
            AppProperties appProperties, ObjectMapper objectMapper) {
        this.ragSearchClient = ragSearchClient;
        this.ragLookupClient = ragLookupClient;
        this.ragApiChangeBatchClient = ragApiChangeBatchClient;
        this.ragContextBuilder = ragContextBuilder;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public UpgradeContext retrieve(String fromVersion, String toVersion, String workspaceId, String repoUrl) {
        List<RagHit> projectFacts = retrieveProjectFacts(workspaceId, List.of());
        List<RagHit> migrationHits = retrieveMigrationGuide(fromVersion, toVersion, List.of());
        List<RagHit> deprecationHits = retrieveDeprecations(fromVersion, toVersion, List.of());
        List<RagHit> apiChangeHits = retrieveApiChangeBatchHits(fromVersion, toVersion, workspaceId, List.of());
        List<RagHit> sourceCodeHits = appProperties.getRag().isEnableSourceCodePass()
                ? retrieveSpringSourceSnippets(projectFacts, toVersion)
                : List.of();

        List<RagHit> merged = mergeHits(projectFacts, apiChangeHits, migrationHits, deprecationHits, sourceCodeHits);
        String contextText = ragContextBuilder.buildContext(merged, 6000, MAX_PROJECT_FACT_CHARS);
        return new UpgradeContext(merged, contextText);
    }

    public UpgradeContext retrieve(String fromVersion, String toVersion, String workspaceId, String repoUrl,
            List<String> moduleFocus) {
        List<String> normalizedFocus = FocusKeywords.normalize(moduleFocus);
        logger.debug("moduleFocus received={} normalized={}", moduleFocus, normalizedFocus);
        List<RagHit> projectFacts = retrieveProjectFacts(workspaceId, normalizedFocus);
        List<RagHit> migrationHits = retrieveMigrationGuide(fromVersion, toVersion, normalizedFocus);
        List<RagHit> deprecationHits = retrieveDeprecations(fromVersion, toVersion, normalizedFocus);
        List<RagHit> apiChangeHits = retrieveApiChangeBatchHits(fromVersion, toVersion, workspaceId, normalizedFocus);
        List<RagHit> sourceCodeHits = appProperties.getRag().isEnableSourceCodePass()
                ? retrieveSpringSourceSnippets(projectFacts, toVersion)
                : List.of();

        List<RagHit> merged = mergeHits(projectFacts, apiChangeHits, migrationHits, deprecationHits, sourceCodeHits);
        String contextText = ragContextBuilder.buildContext(merged, 6000, MAX_PROJECT_FACT_CHARS);
        return new UpgradeContext(merged, contextText);
    }

    private List<RagHit> retrieveProjectFacts(String workspaceId, List<String> moduleFocus) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "PROJECT_FACT");
        filters.put("workspaceId", workspaceId);
        filters.put("docKind", "PROJECT_FACT");
        int topK = resolveProjectFactTopK(moduleFocus);
        List<RagHit> hits = ragLookupClient.lookup(filters, topK);
        logger.debug("PROJECT_FACT hits retrieved={} for workspaceId={}", hits == null ? 0 : hits.size(), workspaceId);
        if (hits == null || hits.isEmpty()) {
            logger.info("Aucun PROJECT_FACT retourné pour workspaceId={}", workspaceId);
            return List.of();
        }
        if (moduleFocus == null || moduleFocus.isEmpty()) {
            return hits;
        }
        logger.debug("Project facts focus includeKeywords={} excludeKeywords={}",
                FocusKeywords.includeKeywords(moduleFocus), FocusKeywords.excludeKeywords(moduleFocus));
        List<RagHit> filtered = postFilterHits(hits, moduleFocus, topK, "projectFacts");
        return filtered.isEmpty() ? hits : filtered;
    }

    private List<RagHit> retrieveApiChangeBatchHits(String fromVersion, String toVersion, String workspaceId,
            List<String> moduleFocus) {
        List<RagHit> projectFacts = retrieveProjectFactsForSymbols(workspaceId);
        SymbolExtractionResult extraction = extractSymbols(projectFacts, moduleFocus, API_CHANGES_MAX_SYMBOLS);
        logger.debug("API change symbols unique={} maxSymbols={} truncated={}",
                extraction.symbols().size(), API_CHANGES_MAX_SYMBOLS, extraction.truncated());
        if (extraction.symbols().isEmpty()) {
            logger.info("Aucun symbole détecté pour l'appel batch rag.findApiChangesBatch.");
            return List.of();
        }
        ApiChangeBatchResponse response = ragApiChangeBatchClient.findBatch(
                extraction.symbols(),
                fromVersion,
                toVersion,
                API_CHANGES_TOP_K_PER_SYMBOL,
                API_CHANGES_MAX_SYMBOLS
        );
        List<RagHit> batchHits = flattenApiChangeHits(response);
        logger.debug("API change batch response processedSymbols={} truncated={} maxSymbols={} hitsTotal={}",
                response == null ? 0 : response.processedSymbols(),
                response != null && response.truncated(),
                response == null ? 0 : response.maxSymbols(),
                batchHits.size());
        return batchHits;
    }

    private List<RagHit> retrieveProjectFactsForSymbols(String workspaceId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "PROJECT_FACT");
        filters.put("workspaceId", workspaceId);
        filters.put("docKind", "PROJECT_FACT");
        List<RagHit> hits = ragLookupClient.lookup(filters, PROJECT_FACT_SYMBOL_LOOKUP_LIMIT);
        logger.debug("PROJECT_FACT hits retrieved for symbols={} for workspaceId={}",
                hits == null ? 0 : hits.size(), workspaceId);
        return hits == null ? List.of() : hits;
    }

    private SymbolExtractionResult extractSymbols(List<RagHit> projectFacts, List<String> moduleFocus,
            int maxSymbols) {
        Set<String> symbols = new LinkedHashSet<>();
        if (projectFacts != null) {
            for (RagHit hit : projectFacts) {
                if (hit == null || hit.text() == null) {
                    continue;
                }
                Matcher matcher = FQCN_PATTERN.matcher(hit.text());
                while (matcher.find()) {
                    String symbol = matcher.group(0);
                    if (symbol != null && !symbol.isBlank()) {
                        symbols.add(symbol);
                    }
                }
            }
        }
        List<String> orderedSymbols = new ArrayList<>(symbols);
        if (moduleFocus != null && moduleFocus.stream().anyMatch("web"::equalsIgnoreCase)) {
            orderedSymbols = orderedSymbols.stream()
                    .filter(this::isWebFocusedSymbol)
                    .toList();
        }
        boolean truncated = orderedSymbols.size() > maxSymbols;
        if (truncated) {
            orderedSymbols = orderedSymbols.subList(0, maxSymbols);
        }
        return new SymbolExtractionResult(orderedSymbols, truncated);
    }

    private boolean isWebFocusedSymbol(String symbol) {
        for (String marker : WEB_SYMBOL_MARKERS) {
            if (symbol.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private List<RagHit> flattenApiChangeHits(ApiChangeBatchResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return List.of();
        }
        List<RagHit> hits = new ArrayList<>();
        for (SymbolChanges changes : response.results()) {
            if (changes == null || changes.hits() == null) {
                continue;
            }
            for (RagHit hit : changes.hits()) {
                if (hit == null) {
                    continue;
                }
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (hit.metadata() != null) {
                    metadata.putAll(hit.metadata());
                }
                if (changes.symbol() != null) {
                    metadata.put("symbol", changes.symbol());
                }
                hits.add(new RagHit(hit.text(), hit.score(), metadata));
            }
        }
        return hits;
    }

    private List<RagHit> retrieveMigrationGuide(String fromVersion, String toVersion, List<String> moduleFocus) {
        String baseQuery = buildMigrationQuery(fromVersion, toVersion);
        int topK = resolveReleaseNotesTopK(moduleFocus);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceType", "SPRING_RELEASE_NOTE");
        filters.put("library", "spring-boot");
        filters.put("version", List.of("upgrading", "2.6.x", "2.7.x", "2.7.x-deps"));
        if (moduleFocus == null || moduleFocus.isEmpty()) {
            logger.debug("RAG search migration guide query='{}' filters={} topK={}", baseQuery, filters, topK);
            List<RagHit> hits = ragSearchClient.search(baseQuery, filters, topK);
            logger.debug("RAG search migration guide hits={}", hits == null ? 0 : hits.size());
            return hits;
        }
        List<String> includeKeywords = FocusKeywords.includeKeywords(moduleFocus);
        List<String> excludeKeywords = FocusKeywords.excludeKeywords(moduleFocus);
        logger.debug("Migration guide focus includeKeywords={} excludeKeywords={}", includeKeywords, excludeKeywords);
        String query = buildFocusedQuery(baseQuery, includeKeywords);
        int requestTopK = resolveFocusedRequestTopK(topK);
        logger.debug("RAG search migration guide query='{}' filters={} topK={}", query, filters, requestTopK);
        List<RagHit> hits = ragSearchClient.search(query, filters, requestTopK);
        logger.debug("RAG search migration guide hits={}", hits == null ? 0 : hits.size());
        List<RagHit> filtered = postFilterHits(hits, moduleFocus, topK, "migration");
        if (!filtered.isEmpty()) {
            return filtered;
        }
        logger.warn("focus produced zero hits, fallback to broad search for migration guide");
        List<RagHit> fallbackHits = ragSearchClient.search(baseQuery, filters, topK);
        logger.debug("RAG search migration guide fallback hits={}", fallbackHits == null ? 0 : fallbackHits.size());
        return fallbackHits;
    }

    private List<RagHit> retrieveDeprecations(String fromVersion, String toVersion, List<String> moduleFocus) {
        if (moduleFocus != null && !moduleFocus.isEmpty()) {
            List<RagHit> focusedHits = retrieveDeprecationsWithFocus(fromVersion, toVersion, moduleFocus);
            if (!focusedHits.isEmpty()) {
                return focusedHits;
            }
            logger.warn("focus produced zero hits, fallback to broad search for deprecations");
        }
        return retrieveDeprecationsWithoutFocus(fromVersion, toVersion);
    }

    private List<RagHit> retrieveDeprecationsWithFocus(String fromVersion, String toVersion, List<String> moduleFocus) {
        String baseQuery = buildDeprecationsQuery(fromVersion, toVersion);
        int topK = resolveReleaseNotesTopK(moduleFocus);
        List<String> versionFilters = List.of("upgrading", "2.6.x", "2.7.x", "2.7.x-deps");
        List<String> includeKeywords = FocusKeywords.includeKeywords(moduleFocus);
        List<String> excludeKeywords = FocusKeywords.excludeKeywords(moduleFocus);
        logger.debug("Deprecations focus includeKeywords={} excludeKeywords={}", includeKeywords, excludeKeywords);
        String query = buildFocusedQuery(baseQuery, includeKeywords);
        int requestTopK = resolveFocusedRequestTopK(topK);

        Map<String, Object> strictFilters = new LinkedHashMap<>();
        strictFilters.put("sourceType", "SPRING_RELEASE_NOTE");
        strictFilters.put("library", "spring-boot");
        strictFilters.put("version", versionFilters);
        strictFilters.put("docKind", "SPRING_RELEASE_NOTE");
        List<RagHit> hits = ragSearchClient.search(query, strictFilters, requestTopK);
        int hitCount = hits == null ? 0 : hits.size();
        logger.debug("RAG search deprecations passUsed=A query='{}' filters={} hits={}", query, strictFilters, hitCount);
        List<RagHit> filteredHits = postFilterHits(hits, moduleFocus, topK, "deprecations");
        if (!filteredHits.isEmpty()) {
            return filteredHits;
        }

        Map<String, Object> relaxedFilters = new LinkedHashMap<>();
        relaxedFilters.put("sourceType", "SPRING_RELEASE_NOTE");
        relaxedFilters.put("library", "spring-boot");
        List<RagHit> relaxedHits = ragSearchClient.search(query, relaxedFilters, requestTopK);
        int relaxedHitCount = relaxedHits == null ? 0 : relaxedHits.size();
        logger.debug("RAG search deprecations passUsed=B query='{}' filters={} hits={}", query, relaxedFilters,
                relaxedHitCount);
        List<RagHit> filteredRelaxed = postFilterHits(relaxedHits, moduleFocus, topK, "deprecations");
        if (!filteredRelaxed.isEmpty()) {
            return filteredRelaxed;
        }

        Map<String, Object> lookupFilters = new LinkedHashMap<>();
        lookupFilters.put("sourceType", "SPRING_RELEASE_NOTE");
        lookupFilters.put("library", "spring-boot");
        lookupFilters.put("version", versionFilters);
        List<RagHit> lookupHits = ragLookupClient.lookup(lookupFilters, DEPRECATION_LOOKUP_LIMIT);
        List<RagHit> filteredLookupHits = lookupHits == null
                ? List.of()
                : lookupHits.stream()
                        .filter(hit -> hit != null && hit.text() != null
                                && DEPRECATION_PATTERN.matcher(hit.text()).find())
                        .filter(hit -> shouldKeepHit(hit, moduleFocus))
                        .limit(topK)
                        .toList();
        logger.debug("RAG lookup deprecations passUsed=C query='lookup' filters={} hits={}", lookupFilters,
                filteredLookupHits.size());
        return filteredLookupHits;
    }

    private List<RagHit> retrieveDeprecationsWithoutFocus(String fromVersion, String toVersion) {
        String query = buildDeprecationsQuery(fromVersion, toVersion);
        int topK = resolveReleaseNotesTopK(List.of());
        List<String> versionFilters = List.of("upgrading", "2.6.x", "2.7.x", "2.7.x-deps");

        Map<String, Object> strictFilters = new LinkedHashMap<>();
        strictFilters.put("sourceType", "SPRING_RELEASE_NOTE");
        strictFilters.put("library", "spring-boot");
        strictFilters.put("version", versionFilters);
        strictFilters.put("docKind", "SPRING_RELEASE_NOTE");
        List<RagHit> hits = ragSearchClient.search(query, strictFilters, topK);
        int hitCount = hits == null ? 0 : hits.size();
        logger.debug("RAG search deprecations passUsed=A query='{}' filters={} hits={}", query, strictFilters, hitCount);
        if (hitCount > 0) {
            return hits;
        }

        Map<String, Object> relaxedFilters = new LinkedHashMap<>();
        relaxedFilters.put("sourceType", "SPRING_RELEASE_NOTE");
        relaxedFilters.put("library", "spring-boot");
        List<RagHit> relaxedHits = ragSearchClient.search(query, relaxedFilters, topK);
        int relaxedHitCount = relaxedHits == null ? 0 : relaxedHits.size();
        logger.debug("RAG search deprecations passUsed=B query='{}' filters={} hits={}", query, relaxedFilters,
                relaxedHitCount);
        if (relaxedHitCount > 0) {
            return relaxedHits;
        }

        Map<String, Object> lookupFilters = new LinkedHashMap<>();
        lookupFilters.put("sourceType", "SPRING_RELEASE_NOTE");
        lookupFilters.put("library", "spring-boot");
        lookupFilters.put("version", versionFilters);
        List<RagHit> lookupHits = ragLookupClient.lookup(lookupFilters, DEPRECATION_LOOKUP_LIMIT);
        List<RagHit> filteredHits = lookupHits == null
                ? List.of()
                : lookupHits.stream()
                        .filter(hit -> hit != null && hit.text() != null
                                && DEPRECATION_PATTERN.matcher(hit.text()).find())
                        .limit(topK)
                        .toList();
        logger.debug("RAG lookup deprecations passUsed=C query='lookup' filters={} hits={}", lookupFilters,
                filteredHits.size());
        return filteredHits;
    }

    private String buildMigrationQuery(String fromVersion, String toVersion) {
        return "Spring Boot " + toVersion + " upgrading migration guide breaking changes from " + fromVersion;
    }

    private String buildDeprecationsQuery(String fromVersion, String toVersion) {
        return "Spring Boot " + toVersion + " deprecated removed api changes from " + fromVersion;
    }

    private int resolveReleaseNotesTopK(List<String> moduleFocus) {
        if (moduleFocus == null || moduleFocus.isEmpty()) {
            return RELEASE_NOTES_TOP_K;
        }
        return FOCUSED_RELEASE_NOTES_TOP_K;
    }

    private int resolveProjectFactTopK(List<String> moduleFocus) {
        if (moduleFocus == null || moduleFocus.isEmpty()) {
            return PROJECT_FACT_TOP_K;
        }
        return FOCUSED_PROJECT_FACT_TOP_K;
    }

    private int resolveFocusedRequestTopK(int topK) {
        return Math.max(topK, topK * FOCUSED_SEARCH_MULTIPLIER);
    }

    private String buildFocusedQuery(String baseQuery, List<String> includeKeywords) {
        if (includeKeywords == null || includeKeywords.isEmpty()) {
            return baseQuery;
        }
        return baseQuery + " " + String.join(" ", includeKeywords);
    }

    private List<RagHit> postFilterHits(List<RagHit> hits, List<String> moduleFocus, int topK, String label) {
        if (hits == null) {
            return List.of();
        }
        if (moduleFocus == null || moduleFocus.isEmpty()) {
            return hits.size() > topK ? hits.subList(0, topK) : hits;
        }
        List<RagHit> filtered = hits.stream()
                .filter(hit -> shouldKeepHit(hit, moduleFocus))
                .limit(topK)
                .toList();
        logger.debug("Focus post-filter {} hits before={} after={}", label, hits.size(), filtered.size());
        return filtered;
    }

    private boolean shouldKeepHit(RagHit hit, List<String> moduleFocus) {
        if (hit == null || !StringUtils.hasText(hit.text())) {
            return false;
        }
        List<String> includeKeywords = FocusKeywords.includeKeywords(moduleFocus);
        List<String> excludeKeywords = FocusKeywords.excludeKeywords(moduleFocus);
        int includeScore = FocusKeywords.matchScore(hit.text(), includeKeywords);
        if (includeScore > 0) {
            return true;
        }
        if (!excludeKeywords.isEmpty()) {
            FocusKeywords.matchScore(hit.text(), excludeKeywords);
        }
        return false;
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

    private List<RagHit> mergeHits(List<RagHit> projectFacts, List<RagHit> apiChangeHits,
            List<RagHit> migrationHits, List<RagHit> deprecationHits, List<RagHit> sourceCodeHits) {
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
        appendUnique(remaining, apiChangeHits);
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

    private record SymbolExtractionResult(List<String> symbols, boolean truncated) {
    }
}
