package com.example.llmhost.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.llmhost.api.GatingStats;
import com.example.llmhost.model.UpgradeReport;

public class UpgradeReportEvidenceGate {

    private static final String REASON = "EVIDENCE_NOT_ALLOWED_OR_MISSING";

    public UpgradeReport apply(UpgradeReport report, int sourceCount) {
        return applyWithReport(report, sourceCount).report();
    }

    public EvidenceGateResult applyWithReport(UpgradeReport report, int sourceCount) {
        List<String> allowedSources = buildAllowedSources(sourceCount);
        if (report == null) {
            GatingStats stats = new GatingStats(
                    allowedSources,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    REASON
            );
            return new EvidenceGateResult(null, stats);
        }
        int impactsBefore = report.getImpacts().size();
        int workpointsBefore = report.getWorkpoints().size();
        int unknownsBefore = report.getUnknowns().size();
        filterImpacts(report, allowedSources);
        filterWorkpoints(report, allowedSources);
        filterUnknowns(report, allowedSources);
        if (report.getImpacts().isEmpty()) {
            report = buildNotFoundReport(report, allowedSources);
        }
        int impactsAfter = report.getImpacts().size();
        int workpointsAfter = report.getWorkpoints().size();
        int unknownsAfter = report.getUnknowns().size();
        GatingStats stats = new GatingStats(
                allowedSources,
                Math.max(0, impactsBefore - impactsAfter),
                Math.max(0, workpointsBefore - workpointsAfter),
                Math.max(0, unknownsBefore - unknownsAfter),
                impactsBefore,
                impactsAfter,
                workpointsBefore,
                workpointsAfter,
                unknownsBefore,
                unknownsAfter,
                REASON
        );
        return new EvidenceGateResult(report, stats);
    }

    private void filterImpacts(UpgradeReport report, List<String> allowedSources) {
        report.getImpacts().removeIf(impact -> !hasAllowedEvidence(impact == null ? null : impact.getEvidence(), allowedSources));
    }

    private void filterWorkpoints(UpgradeReport report, List<String> allowedSources) {
        Set<String> remainingImpactIds = new HashSet<>();
        report.getImpacts().forEach(impact -> {
            if (impact != null && hasText(impact.getId())) {
                remainingImpactIds.add(impact.getId());
            }
        });
        report.getWorkpoints().removeIf(workpoint -> {
            if (workpoint == null || !hasAllowedEvidence(workpoint.getEvidence(), allowedSources)) {
                return true;
            }
            if (hasText(workpoint.getImpactId())) {
                return !remainingImpactIds.contains(workpoint.getImpactId());
            }
            return false;
        });
    }

    private void filterUnknowns(UpgradeReport report, List<String> allowedSources) {
        report.getUnknowns().removeIf(unknown -> !hasAllowedEvidence(unknown == null ? null : unknown.getEvidence(), allowedSources));
    }

    private UpgradeReport buildNotFoundReport(UpgradeReport report, List<String> allowedSources) {
        UpgradeReport sanitized = new UpgradeReport();
        sanitized.setProject(report.getProject());
        sanitized.setSpringUsageSummary(report.getSpringUsageSummary());
        sanitized.setImpacts(List.of());
        sanitized.setWorkpoints(List.of());
        UpgradeReport.Unknown unknown = new UpgradeReport.Unknown();
        unknown.setQuestion("NON TROUVÉ");
        unknown.setWhy("Aucune source [S#] ne justifie des impacts fiables");
        unknown.setNextStep("Ajouter/ingérer des sources de migration Spring Boot 2.5->2.7 + relancer");
        if (allowedSources.isEmpty()) {
            unknown.setEvidence(List.of());
        } else {
            unknown.setEvidence(List.of("S1"));
        }
        sanitized.setUnknowns(List.of(unknown));
        return sanitized;
    }

    private boolean hasAllowedEvidence(List<String> evidence, List<String> allowedSources) {
        if (evidence == null || evidence.isEmpty() || allowedSources.isEmpty()) {
            return false;
        }
        for (String source : evidence) {
            if (allowedSources.contains(source)) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildAllowedSources(int sourceCount) {
        if (sourceCount <= 0) {
            return List.of();
        }
        List<String> allowed = new ArrayList<>();
        for (int i = 1; i <= sourceCount; i++) {
            allowed.add("S" + i);
        }
        return allowed;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
