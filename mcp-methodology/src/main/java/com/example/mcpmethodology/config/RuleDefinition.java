package com.example.mcpmethodology.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RuleDefinition {

    private int baseWorkpoints;
    private Map<String, Integer> metadataMultipliers = new HashMap<>();

    public int getBaseWorkpoints() {
        return baseWorkpoints;
    }

    public void setBaseWorkpoints(int baseWorkpoints) {
        this.baseWorkpoints = baseWorkpoints;
    }

    public Map<String, Integer> getMetadataMultipliers() {
        return Collections.unmodifiableMap(metadataMultipliers);
    }

    public void setMetadataMultipliers(Map<String, Integer> metadataMultipliers) {
        if (metadataMultipliers == null) {
            this.metadataMultipliers = new HashMap<>();
        } else {
            this.metadataMultipliers = new HashMap<>(metadataMultipliers);
        }
    }
}
