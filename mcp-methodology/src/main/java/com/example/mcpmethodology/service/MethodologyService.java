package com.example.mcpmethodology.service;

import com.example.mcpmethodology.config.MethodologyRulesLoader;
import com.example.mcpmethodology.config.RuleDefinition;
import com.example.mcpmethodology.model.ChangeEffort;
import com.example.mcpmethodology.model.ChangeInput;
import com.example.mcpmethodology.model.EffortResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MethodologyService {

    private final MethodologyRulesLoader rulesLoader;

    public MethodologyService(MethodologyRulesLoader rulesLoader) {
        this.rulesLoader = rulesLoader;
    }

    public EffortResult computeEffort(List<ChangeInput> changes) {
        if (changes == null) {
            return new EffortResult(0, List.of());
        }

        List<ChangeEffort> efforts = new ArrayList<>();
        int total = 0;

        for (ChangeInput change : changes) {
            ChangeEffort effort = evaluateChange(change);
            efforts.add(effort);
            total += effort.getWorkpoints();
        }

        return new EffortResult(total, efforts);
    }

    private ChangeEffort evaluateChange(ChangeInput change) {
        String changeId = change != null ? change.getId() : null;
        String type = change != null ? change.getType() : null;
        String severity = change != null ? change.getSeverity() : null;
        Map<String, Object> metadata = change != null ? change.getMetadata() : null;

        RuleDefinition rule = rulesLoader.getRule(normalize(type), normalize(severity));
        if (rule == null) {
            return new ChangeEffort(changeId, 0, "No matching rule for type/severity");
        }

        int workpoints = rule.getBaseWorkpoints();
        StringBuilder reason = new StringBuilder("Base workpoints: ").append(rule.getBaseWorkpoints());

        if (metadata != null) {
            for (Map.Entry<String, Integer> multiplier : rule.getMetadataMultipliers().entrySet()) {
                Object value = metadata.get(multiplier.getKey());
                if (value instanceof Number number) {
                    int contribution = multiplier.getValue() * number.intValue();
                    workpoints += contribution;
                    reason.append(", ").append(multiplier.getKey())
                            .append(" x ").append(multiplier.getValue())
                            .append(" = ").append(contribution);
                }
            }
        }

        return new ChangeEffort(changeId, workpoints, reason.toString());
    }

    private String normalize(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT).trim();
    }
}
