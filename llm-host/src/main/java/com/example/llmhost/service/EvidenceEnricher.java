package com.example.llmhost.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.llmhost.model.UpgradeReport;
import com.example.llmhost.rag.RagHit;
import org.springframework.stereotype.Component;

@Component
public class EvidenceEnricher {

    public UpgradeReport enrich(UpgradeReport report, UpgradeContext context) {
        if (report == null) {
            return null;
        }
        Map<String, UpgradeReport.EvidenceDetail> evidenceBySource = buildEvidenceBySource(context);
        enrichImpacts(report.getImpacts(), evidenceBySource);
        enrichWorkpoints(report.getWorkpoints(), evidenceBySource);
        return report;
    }

    private Map<String, UpgradeReport.EvidenceDetail> buildEvidenceBySource(UpgradeContext context) {
        Map<String, UpgradeReport.EvidenceDetail> evidenceBySource = new HashMap<>();
        if (context == null || context.hits() == null) {
            return evidenceBySource;
        }
        List<RagHit> hits = context.hits();
        for (int i = 0; i < hits.size(); i++) {
            RagHit hit = hits.get(i);
            if (hit == null) {
                continue;
            }
            String source = "S" + (i + 1);
            UpgradeReport.EvidenceDetail detail = buildDetail(source, hit.metadata());
            evidenceBySource.put(source, detail);
        }
        return evidenceBySource;
    }

    private UpgradeReport.EvidenceDetail buildDetail(String source, Map<String, Object> metadata) {
        UpgradeReport.EvidenceDetail detail = new UpgradeReport.EvidenceDetail();
        detail.setSource(source);
        if (metadata == null) {
            return detail;
        }
        detail.setUrl(toText(metadata.get("url")));
        detail.setDocumentKey(toText(metadata.get("documentKey")));
        detail.setVersion(toText(metadata.get("version")));
        detail.setLibrary(toText(metadata.get("library")));
        detail.setFilePath(toText(metadata.get("filePath")));
        return detail;
    }

    private void enrichImpacts(List<UpgradeReport.Impact> impacts, Map<String, UpgradeReport.EvidenceDetail> evidenceBySource) {
        if (impacts == null) {
            return;
        }
        for (UpgradeReport.Impact impact : impacts) {
            if (impact == null) {
                continue;
            }
            impact.setEvidenceDetails(buildDetails(impact.getEvidence(), evidenceBySource));
        }
    }

    private void enrichWorkpoints(List<UpgradeReport.Workpoint> workpoints,
            Map<String, UpgradeReport.EvidenceDetail> evidenceBySource) {
        if (workpoints == null) {
            return;
        }
        for (UpgradeReport.Workpoint workpoint : workpoints) {
            if (workpoint == null) {
                continue;
            }
            workpoint.setEvidenceDetails(buildDetails(workpoint.getEvidence(), evidenceBySource));
        }
    }

    private List<UpgradeReport.EvidenceDetail> buildDetails(List<String> evidence,
            Map<String, UpgradeReport.EvidenceDetail> evidenceBySource) {
        if (evidence == null || evidence.isEmpty()) {
            return new ArrayList<>();
        }
        List<UpgradeReport.EvidenceDetail> details = new ArrayList<>();
        for (String source : evidence) {
            UpgradeReport.EvidenceDetail detail = evidenceBySource.get(source);
            if (detail != null) {
                details.add(copy(detail));
            }
        }
        return details;
    }

    private UpgradeReport.EvidenceDetail copy(UpgradeReport.EvidenceDetail detail) {
        UpgradeReport.EvidenceDetail cloned = new UpgradeReport.EvidenceDetail();
        cloned.setSource(detail.getSource());
        cloned.setUrl(detail.getUrl());
        cloned.setDocumentKey(detail.getDocumentKey());
        cloned.setVersion(detail.getVersion());
        cloned.setLibrary(detail.getLibrary());
        cloned.setFilePath(detail.getFilePath());
        return cloned;
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
