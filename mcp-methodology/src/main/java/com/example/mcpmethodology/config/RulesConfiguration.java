package com.example.mcpmethodology.config;

import java.util.HashMap;
import java.util.Map;

public class RulesConfiguration {

    private Map<String, Map<String, RuleDefinition>> rules = new HashMap<>();

    public Map<String, Map<String, RuleDefinition>> getRules() {
        return rules;
    }

    public void setRules(Map<String, Map<String, RuleDefinition>> rules) {
        this.rules = rules;
    }
}
