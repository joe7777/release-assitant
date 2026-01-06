package com.example.llmhost.api;

import java.util.List;

public record GatingStats(
        List<String> allowedSources,
        int removedImpacts,
        int removedWorkpoints,
        int removedUnknowns,
        Integer totalImpactsBefore,
        Integer totalImpactsAfter,
        Integer totalWorkpointsBefore,
        Integer totalWorkpointsAfter,
        Integer totalUnknownsBefore,
        Integer totalUnknownsAfter,
        String reason
) {
}
