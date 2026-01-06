package com.example.llmhost.service;

import java.util.List;

import com.example.llmhost.rag.RagHit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public final class RagSearchResponseParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagSearchResponseParser.class);

    private RagSearchResponseParser() {
    }

    public static List<RagHit> parse(String rawJson, ObjectMapper mapper) {
        int rawLength = rawJson == null ? 0 : rawJson.length();
        if (!StringUtils.hasText(rawJson)) {
            LOGGER.debug("rag.search response rawLength={} format={} selfHealApplied={} hits={}",
                    rawLength, "empty", false, 0);
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(rawJson);
            String format = "unknown";
            JsonNode hitsNode;
            if (root.isArray()) {
                format = "array";
                hitsNode = root;
            } else if (root.isObject()) {
                if (root.has("results")) {
                    format = "results";
                    hitsNode = root.get("results");
                } else if (root.has("hits")) {
                    format = "hits";
                    hitsNode = root.get("hits");
                } else if (root.has("text") && root.get("text").isTextual()) {
                    format = "object.text";
                    hitsNode = parseStringArray(root.get("text").asText(), mapper);
                } else {
                    throw new IllegalStateException("Invalid rag.search contract: " + payloadExcerpt(rawJson));
                }
            } else if (root.isTextual()) {
                format = "text";
                hitsNode = parseStringArray(root.asText(), mapper);
            } else {
                throw new IllegalStateException("Invalid rag.search contract: " + payloadExcerpt(rawJson));
            }
            if (!hitsNode.isArray()) {
                throw new IllegalStateException("Invalid rag.search contract: expected array. Payload excerpt: "
                        + payloadExcerpt(rawJson));
            }
            List<RagHit> hits = mapper.convertValue(hitsNode, new TypeReference<>() {
            });
            boolean selfHealApplied = false;
            JsonNode validationNode = hitsNode;
            String validationPayload = rawJson;
            if (hits.size() == 1 && hits.get(0).metadata() == null && looksLikeJsonArray(hits.get(0).text())) {
                String innerPayload = hits.get(0).text();
                JsonNode healedNode = parseStringArray(innerPayload, mapper);
                hits = mapper.convertValue(healedNode, new TypeReference<>() {
                });
                selfHealApplied = true;
                validationNode = healedNode;
                validationPayload = innerPayload;
            }
            validateHits(validationNode, validationPayload);
            LOGGER.debug("rag.search response rawLength={} format={} selfHealApplied={} hits={}",
                    rawLength, format, selfHealApplied, hits.size());
            return hits;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid rag.search contract: unreadable JSON", ex);
        }
    }

    private static JsonNode parseStringArray(String payload, ObjectMapper mapper) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalStateException("Invalid rag.search contract: expected JSON array. Payload excerpt: "
                    + payloadExcerpt(payload));
        }
        try {
            JsonNode parsed = mapper.readTree(payload);
            if (!parsed.isArray()) {
                throw new IllegalStateException("Invalid rag.search contract: expected JSON array. Payload excerpt: "
                        + payloadExcerpt(payload));
            }
            return parsed;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid rag.search contract: expected JSON array. Payload excerpt: "
                    + payloadExcerpt(payload), ex);
        }
    }

    private static void validateHits(JsonNode hitsNode, String payload) {
        if (!hitsNode.isArray()) {
            throw new IllegalStateException("Invalid rag.search contract: expected array. Payload excerpt: "
                    + payloadExcerpt(payload));
        }
        for (JsonNode hit : hitsNode) {
            if (!hit.isObject()) {
                throw new IllegalStateException("Invalid rag.search contract: hit must be object. Payload excerpt: "
                        + payloadExcerpt(payload));
            }
            JsonNode text = hit.get("text");
            JsonNode score = hit.get("score");
            JsonNode metadata = hit.get("metadata");
            if (text == null || !text.isTextual()
                    || score == null || !score.isNumber()
                    || metadata == null || !(metadata.isObject() || metadata.isNull())) {
                throw new IllegalStateException(
                        "Invalid rag.search contract: each hit requires text(string), score(number), metadata(object|null). Payload excerpt: "
                                + payloadExcerpt(payload));
            }
        }
    }

    private static boolean looksLikeJsonArray(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        String trimmed = payload.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private static String payloadExcerpt(String payload) {
        if (payload == null) {
            return "null";
        }
        String trimmed = payload.trim();
        int maxLength = 200;
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "...";
    }
}
