package com.example.llmhost.service;

import com.example.llmhost.api.GatingStats;
import com.example.llmhost.model.UpgradeReport;

public record EvidenceGateResult(
        UpgradeReport report,
        GatingStats stats
) {
}
