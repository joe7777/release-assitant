package com.example.llmhost.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.example.llmhost.model.UpgradeReport;

public class UpgradeReportSanitizer {

    private static final String NO_IMPACTS_MARKER = "no impacts found";

    public UpgradeReport sanitize(UpgradeReport report) {
        if (report == null) {
            return null;
        }
        boolean hadEvidence = hasAnyEvidence(report);
        normalizeWorkpoints(report);
        removeNoImpactPlaceholders(report, hadEvidence);
        return report;
    }

    private void normalizeWorkpoints(UpgradeReport report) {
        for (UpgradeReport.Workpoint workpoint : report.getWorkpoints()) {
            if (workpoint != null && workpoint.getPoints() <= 0) {
                workpoint.setPoints(1);
            }
        }
    }

    private void removeNoImpactPlaceholders(UpgradeReport report, boolean hadEvidence) {
        Set<String> removedImpactIds = new HashSet<>();
        List<UpgradeReport.Impact> keptImpacts = new ArrayList<>();
        for (UpgradeReport.Impact impact : report.getImpacts()) {
            if (impact == null) {
                continue;
            }
            if (isNoImpactPlaceholder(impact)) {
                if (impact.getId() != null) {
                    removedImpactIds.add(impact.getId());
                }
            } else {
                keptImpacts.add(impact);
            }
        }
        if (keptImpacts.size() != report.getImpacts().size()) {
            report.setImpacts(keptImpacts);
            removeWorkpointsForImpacts(report, removedImpactIds);
            report.setUnknowns(appendUnknown(report.getUnknowns(), buildNoImpactUnknown(hadEvidence)));
        }
    }

    private void removeWorkpointsForImpacts(UpgradeReport report, Set<String> removedImpactIds) {
        if (removedImpactIds.isEmpty()) {
            return;
        }
        List<UpgradeReport.Workpoint> keptWorkpoints = new ArrayList<>();
        for (UpgradeReport.Workpoint workpoint : report.getWorkpoints()) {
            if (workpoint == null) {
                continue;
            }
            if (workpoint.getImpactId() != null && removedImpactIds.contains(workpoint.getImpactId())) {
                continue;
            }
            keptWorkpoints.add(workpoint);
        }
        report.setWorkpoints(keptWorkpoints);
    }

    private boolean isNoImpactPlaceholder(UpgradeReport.Impact impact) {
        return containsNoImpactsMarker(impact.getTitle())
                || containsNoImpactsMarker(impact.getRecommendation());
    }

    private boolean containsNoImpactsMarker(String value) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(NO_IMPACTS_MARKER);
    }

    private UpgradeReport.Unknown buildNoImpactUnknown(boolean hadEvidence) {
        UpgradeReport.Unknown unknown = new UpgradeReport.Unknown();
        unknown.setQuestion("NON TROUVÉ");
        unknown.setWhy("Les sources ne justifient pas d’impact concret pour ce focus");
        unknown.setNextStep("Ingérer davantage de sources (release notes, migration guides) ciblées ou élargir moduleFocus");
        if (hadEvidence) {
            unknown.setEvidence(List.of("S1"));
        } else {
            unknown.setEvidence(List.of());
        }
        return unknown;
    }

    private List<UpgradeReport.Unknown> appendUnknown(List<UpgradeReport.Unknown> existing, UpgradeReport.Unknown unknown) {
        List<UpgradeReport.Unknown> updated = new ArrayList<>();
        if (existing != null) {
            updated.addAll(existing);
        }
        updated.add(unknown);
        return updated;
    }

    private boolean hasAnyEvidence(UpgradeReport report) {
        for (UpgradeReport.Impact impact : report.getImpacts()) {
            if (impact != null && impact.getEvidence() != null && !impact.getEvidence().isEmpty()) {
                return true;
            }
        }
        for (UpgradeReport.Workpoint workpoint : report.getWorkpoints()) {
            if (workpoint != null && workpoint.getEvidence() != null && !workpoint.getEvidence().isEmpty()) {
                return true;
            }
        }
        for (UpgradeReport.Unknown unknown : report.getUnknowns()) {
            if (unknown != null && unknown.getEvidence() != null && !unknown.getEvidence().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
