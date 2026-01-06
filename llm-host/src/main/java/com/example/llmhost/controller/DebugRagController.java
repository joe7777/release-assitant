package com.example.llmhost.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.example.llmhost.api.DebugProjectFactsRequest;
import com.example.llmhost.api.DebugProjectFactsResponse;
import com.example.llmhost.api.DebugRagTestRequest;
import com.example.llmhost.api.DebugRagTestResponse;
import com.example.llmhost.api.DebugRagSearchRequest;
import com.example.llmhost.api.DebugRagSearchResponse;
import com.example.llmhost.api.DebugRagWithSourcesRequest;
import com.example.llmhost.api.DebugRagWithSourcesResponse;
import com.example.llmhost.api.DebugToolsResponse;
import com.example.llmhost.api.DebugUpgradeContextRequest;
import com.example.llmhost.api.DebugUpgradeContextResponse;
import com.example.llmhost.config.AppProperties;
import com.example.llmhost.config.SystemPromptProvider;
import com.example.llmhost.rag.CitationValidator;
import com.example.llmhost.rag.CitationValidator.CitationValidationResult;
import com.example.llmhost.rag.CitationValidator.RetryDirective;
import com.example.llmhost.rag.CitationValidator.RetryReason;
import com.example.llmhost.rag.RagHit;
import com.example.llmhost.rag.RagLookupClient;
import com.example.llmhost.rag.RagSearchClient;
import com.example.llmhost.service.RagMultiPassUpgradeContext;
import com.example.llmhost.service.RagContextBuilder;
import com.example.llmhost.service.UpgradeContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
public class DebugRagController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugRagController.class);

    private final List<ToolCallback> toolCallbacks;
    private final ChatClient chatClient;
    private final AppProperties appProperties;
    private final SimpleLoggerAdvisor loggingAdvisor;
    private final RagContextBuilder ragContextBuilder;
    private final RagSearchClient ragSearchClient;
    private final RagLookupClient ragLookupClient;
    private final CitationValidator citationValidator;
    private final SystemPromptProvider systemPromptProvider;
    private final RagMultiPassUpgradeContext upgradeContextService;

    public DebugRagController(List<ToolCallback> toolCallbacks, ChatClient chatClient, AppProperties appProperties,
            RagContextBuilder ragContextBuilder, RagSearchClient ragSearchClient, RagLookupClient ragLookupClient,
            CitationValidator citationValidator, SystemPromptProvider systemPromptProvider,
            RagMultiPassUpgradeContext upgradeContextService) {
        this.toolCallbacks = toolCallbacks;
        this.chatClient = chatClient;
        this.appProperties = appProperties;
        this.loggingAdvisor = new SimpleLoggerAdvisor();
        this.ragContextBuilder = ragContextBuilder;
        this.ragSearchClient = ragSearchClient;
        this.ragLookupClient = ragLookupClient;
        this.citationValidator = citationValidator;
        this.systemPromptProvider = systemPromptProvider;
        this.upgradeContextService = upgradeContextService;
    }

    @GetMapping("/tools")
    public DebugToolsResponse listTools() {
        List<DebugToolsResponse.ToolInfo> tools = toolCallbacks.stream()
                .map(callback -> toToolInfo(callback.getToolDefinition()))
                .toList();
        return new DebugToolsResponse(tools.size(), tools);
    }

    @PostMapping("/ragTest")
    public DebugRagTestResponse ragTest(@Valid @RequestBody DebugRagTestRequest request) {
        int topK = resolveTopK(request.topK());
        int maxContextChars = resolveMaxContextChars(request.maxContextChars());
        boolean callLlm = Boolean.TRUE.equals(request.callLlm());
        if (callLlm && !StringUtils.hasText(request.llmQuestion())) {
            throw new IllegalArgumentException("llmQuestion est requis quand callLlm=true");
        }

        List<RagHit> results = ragSearchClient.search(request.query(), request.filters(), topK);
        List<DebugRagTestResponse.RagHit> responseResults = results.stream()
                .map(result -> new DebugRagTestResponse.RagHit(result.score(), result.text(), result.metadata()))
                .toList();

        DebugRagTestResponse.Retrieval retrieval = new DebugRagTestResponse.Retrieval(
                topK,
                request.query(),
                request.filters(),
                responseResults
        );
        DebugRagTestResponse.Llm llm = buildLlmResponse(callLlm, request, results, maxContextChars);

        return new DebugRagTestResponse(retrieval, llm);
    }

    @PostMapping("/ragSearch")
    public DebugRagSearchResponse ragSearch(@Valid @RequestBody DebugRagSearchRequest request) {
        int topK = resolveTopK(request.topK());
        List<RagHit> results = ragSearchClient.search(request.query(), request.filters(), topK);
        return new DebugRagSearchResponse(results);
    }

    @PostMapping("/llmWithSources")
    public DebugRagWithSourcesResponse llmWithSources(@Valid @RequestBody DebugRagWithSourcesRequest request) {
        int maxContextChars = resolveMaxContextChars(request.maxContextChars());
        DebugRagTestResponse.Llm llm = buildLlmResponse(true, request.question(), request.hits(), maxContextChars);
        return new DebugRagWithSourcesResponse(llm);
    }

    @PostMapping("/upgradeContext")
    public DebugUpgradeContextResponse upgradeContext(@Valid @RequestBody DebugUpgradeContextRequest request) {
        UpgradeContext context = upgradeContextService.retrieve(
                request.fromVersion(),
                request.toVersion(),
                request.workspaceId(),
                request.repoUrl(),
                List.of()
        );
        return new DebugUpgradeContextResponse(context.hits(), context.contextText());
    }

    @PostMapping("/projectFacts")
    public DebugProjectFactsResponse projectFacts(@Valid @RequestBody DebugProjectFactsRequest request) {
        Map<String, Object> filters = new java.util.LinkedHashMap<>();
        filters.put("sourceType", "PROJECT_FACT");
        filters.put("workspaceId", request.workspaceId());
        filters.put("docKind", "PROJECT_FACT");
        List<RagHit> hits = ragLookupClient.lookup(filters, 5);
        String contextText = ragContextBuilder.buildContext(hits, 6000);
        return new DebugProjectFactsResponse(hits, contextText);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    private int resolveTopK(Integer topK) {
        int resolved = topK == null ? appProperties.getSafety().getRagTopK() : topK;
        return resolved > 0 ? resolved : appProperties.getSafety().getRagTopK();
    }

    private int resolveMaxContextChars(Integer maxContextChars) {
        int resolved = maxContextChars == null ? 6000 : maxContextChars;
        return resolved > 0 ? resolved : 6000;
    }

    private DebugRagTestResponse.Llm buildLlmResponse(boolean callLlm, DebugRagTestRequest request,
            List<RagHit> results, int maxContextChars) {
        return buildLlmResponse(callLlm, request.llmQuestion(), results, maxContextChars);
    }

    private DebugRagTestResponse.Llm buildLlmResponse(boolean callLlm, String question, List<RagHit> results,
            int maxContextChars) {
        List<DebugRagTestResponse.LlmEvidence> evidence = buildEvidence(results);
        if (!callLlm) {
            return new DebugRagTestResponse.Llm(false, null, List.of(), List.of(), 0.0, "SKIPPED", null, evidence);
        }

        String context = ragContextBuilder.buildContext(results, maxContextChars);
        int sourceCount = results.size();
        String basePrompt = systemPromptProvider.buildGuidedCitationsPrompt(sourceCount, false, false);
        String fullUserPrompt = buildUserPrompt(question, context);
        String answer = callLlm(basePrompt, fullUserPrompt);

        CitationValidationResult validation = citationValidator.validate(answer, sourceCount);
        RetryDirective directive = citationValidator.evaluateRetry(validation);
        if (directive.retry()) {
            String retryPrompt = buildRetryPrompt(sourceCount, directive.reason());
            answer = callLlm(retryPrompt, fullUserPrompt);
            validation = citationValidator.validate(answer, sourceCount);
        }

        if (!results.isEmpty() && validation.citationsFound().isEmpty()) {
            LOGGER.warn("RAG debug: aucune citation détectée dans la réponse LLM.");
        }

        String status = resolveStatus(validation);
        String warning = resolveWarning(validation, status);

        return new DebugRagTestResponse.Llm(true, answer, validation.citationsFound(), validation.missingSources(),
                validation.coverageRatio(), status, warning, evidence);
    }

    private String buildUserPrompt(String question, String context) {
        if (!StringUtils.hasText(question)) {
            return context;
        }
        return "QUESTION:\n" + question + "\n\n" + context;
    }

    private String callLlm(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(loggingAdvisor)
                .toolCallbacks(Collections.emptyList())
                .call()
                .content();
    }

    private String buildRetryPrompt(int sourceCount, RetryReason reason) {
        boolean forceCoverage = reason == RetryReason.LOW_COVERAGE;
        boolean forceAnyCitation = reason == RetryReason.NO_CITATIONS;
        return systemPromptProvider.buildGuidedCitationsPrompt(sourceCount, forceCoverage, forceAnyCitation);
    }

    private String resolveStatus(CitationValidationResult validation) {
        if (validation.providedSources() > 0 && validation.citationsFound().isEmpty()) {
            return "UNVERIFIED";
        }
        if (validation.providedSources() >= appProperties.getRag().getCitationMinSourcesForCoverage()
                && validation.coverageRatio() < appProperties.getRag().getCitationCoverageRatio()) {
            return "PARTIAL";
        }
        return "VERIFIED";
    }

    private String resolveWarning(CitationValidationResult validation, String status) {
        if ("UNVERIFIED".equals(status)) {
            return "Aucune citation détectée malgré des sources disponibles.";
        }
        if ("PARTIAL".equals(status)) {
            return "Couverture des citations insuffisante par rapport aux sources fournies.";
        }
        return null;
    }

    private List<DebugRagTestResponse.LlmEvidence> buildEvidence(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<DebugRagTestResponse.LlmEvidence> evidence = new java.util.ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            RagHit hit = hits.get(i);
            Map<String, Object> metadata = hit.metadata() == null ? Map.of() : hit.metadata();
            evidence.add(new DebugRagTestResponse.LlmEvidence(
                    "S" + (i + 1),
                    resolveMetadataString(metadata, "documentKey"),
                    resolveMetadataString(metadata, "url"),
                    resolveMetadataString(metadata, "version"),
                    resolveMetadataString(metadata, "library")
            ));
        }
        return List.copyOf(evidence);
    }

    private String resolveMetadataString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private DebugToolsResponse.ToolInfo toToolInfo(ToolDefinition definition) {
        String name = resolveToolName(definition);
        String description = definition == null ? null : definition.description();
        return new DebugToolsResponse.ToolInfo(name, description);
    }

    private String resolveToolName(ToolDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.name())) {
            return definition.name();
        }
        return "unknown";
    }
}
