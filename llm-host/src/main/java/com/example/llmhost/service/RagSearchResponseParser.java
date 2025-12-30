package com.example.llmhost.service;

import java.util.List;

import com.example.llmhost.rag.RagHit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

public final class RagSearchResponseParser {

    private RagSearchResponseParser() {
    }

    public static List<RagHit> parse(String rawJson, ObjectMapper mapper) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(rawJson);
            if (root.isArray()) {
                return parseArray(root, mapper);
            }
            if (root.isObject() && root.has("results") && root.get("results").isArray()) {
                return mapper.convertValue(root.get("results"), new TypeReference<>() {
                });
            }
            throw new IllegalStateException("Invalid rag.search contract: " + root);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid rag.search contract: unreadable JSON", ex);
        }
    }

    private static List<RagHit> parseArray(JsonNode root, ObjectMapper mapper) {
        if (root.isEmpty()) {
            return List.of();
        }
        if (looksLikeHitArray(root)) {
            return mapper.convertValue(root, new TypeReference<>() {
            });
        }
        if (root.size() == 1 && root.get(0).has("text") && root.get(0).get("text").isTextual()) {
            String inner = root.get(0).get("text").asText().trim();
            if (inner.startsWith("[")) {
                try {
                    JsonNode innerNode = mapper.readTree(inner);
                    if (innerNode.isArray()) {
                        return mapper.convertValue(innerNode, new TypeReference<>() {
                        });
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException("rag.search returned envelope but inner text is not a JSON array",
                            ex);
                }
            }
            throw new IllegalStateException("rag.search returned envelope but inner text is not a JSON array");
        }
        throw new IllegalStateException(
                "Invalid rag.search contract: expected array of hits or envelope-with-text-array");
    }

    private static boolean looksLikeHitArray(JsonNode root) {
        JsonNode first = root.get(0);
        if (first == null || !first.isObject()) {
            return false;
        }
        if (first.has("metadata") && first.get("metadata").isNull()) {
            return false;
        }
        boolean hasMetadataObject = first.has("metadata") && first.get("metadata").isObject();
        boolean hasScoreAndText = first.has("score") && first.get("score").isNumber() && first.has("text");
        return hasMetadataObject || hasScoreAndText;
    }
}
