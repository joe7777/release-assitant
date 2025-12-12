package com.example.mcpmethodology.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class MethodologyRulesLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodologyRulesLoader.class);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String rulesLocation;

    private Map<String, Map<String, RuleDefinition>> rules = new HashMap<>();

    public MethodologyRulesLoader(ResourceLoader resourceLoader,
                                  @Value("${methodology.rules.location:classpath:rules.yaml}") String rulesLocation) {
        this.resourceLoader = resourceLoader;
        this.rulesLocation = rulesLocation;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    @PostConstruct
    public void loadRules() {
        Resource resource = resourceLoader.getResource(rulesLocation);
        if (!resource.exists()) {
            LOGGER.warn("Rules file not found at {}. No rules will be loaded.", rulesLocation);
            this.rules = new HashMap<>();
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            RulesConfiguration configuration = objectMapper.readValue(inputStream, RulesConfiguration.class);
            if (configuration != null && configuration.getRules() != null) {
                this.rules = normalize(configuration.getRules());
                LOGGER.info("Loaded methodology rules from {}", rulesLocation);
            } else {
                this.rules = new HashMap<>();
                LOGGER.warn("Rules configuration is empty in {}", rulesLocation);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to read rules file {}", rulesLocation, e);
            this.rules = new HashMap<>();
        }
    }

    public Map<String, Map<String, RuleDefinition>> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    public RuleDefinition getRule(String type, String severity) {
        if (type == null || severity == null) {
            return null;
        }
        Map<String, RuleDefinition> bySeverity = rules.get(type.toUpperCase(Locale.ROOT));
        if (bySeverity == null) {
            return null;
        }
        return bySeverity.get(severity.toUpperCase(Locale.ROOT));
    }

    private Map<String, Map<String, RuleDefinition>> normalize(Map<String, Map<String, RuleDefinition>> source) {
        Map<String, Map<String, RuleDefinition>> normalized = new HashMap<>();
        source.forEach((type, severityMap) -> {
            Map<String, RuleDefinition> severityRules = new HashMap<>();
            if (severityMap != null) {
                severityMap.forEach((severity, rule) -> {
                    if (rule != null) {
                        severityRules.put(severity.toUpperCase(Locale.ROOT), rule);
                    }
                });
            }
            normalized.put(type.toUpperCase(Locale.ROOT), severityRules);
        });
        return normalized;
    }
}
