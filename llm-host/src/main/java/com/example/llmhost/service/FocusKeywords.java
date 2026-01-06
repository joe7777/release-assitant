package com.example.llmhost.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FocusKeywords {

    private static final Map<String, List<String>> MODULE_KEYWORDS = Map.of(
            "web", List.of("webmvc", "controller", "webflux", "servlet", "tomcat", "jetty", "undertow",
                    "spring-web", "spring-webmvc", "spring-webflux", "spring-boot-starter-web"),
            "security", List.of("spring-security", "oauth2", "csrf", "filterchain", "securityfilterchain",
                    "authorization", "authentication", "saml", "jwt"),
            "data", List.of("spring-data", "jdbc", "jpa", "hibernate", "transaction", "@Transactional", "datasource",
                    "r2dbc", "flyway", "liquibase")
    );

    private FocusKeywords() {
    }

    public static List<String> normalize(List<String> moduleFocus) {
        if (moduleFocus == null || moduleFocus.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String focus : moduleFocus) {
            if (focus == null) {
                continue;
            }
            String trimmed = focus.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    public static List<String> includeKeywords(List<String> moduleFocus) {
        List<String> normalized = normalize(moduleFocus);
        if (normalized.isEmpty()) {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String focus : normalized) {
            List<String> focusKeywords = MODULE_KEYWORDS.get(focus);
            if (focusKeywords != null) {
                keywords.addAll(focusKeywords);
            }
        }
        return List.copyOf(keywords);
    }

    public static List<String> excludeKeywords(List<String> moduleFocus) {
        List<String> normalized = normalize(moduleFocus);
        if (normalized.size() != 1) {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : MODULE_KEYWORDS.entrySet()) {
            if (!normalized.contains(entry.getKey())) {
                keywords.addAll(entry.getValue());
            }
        }
        return List.copyOf(keywords);
    }

    public static int matchScore(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

}
