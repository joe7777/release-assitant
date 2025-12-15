package com.example.mcpserver.dto;

import java.util.List;

public record WorkpointComputationResult(int totalWorkpoints, List<WorkpointBreakdown> breakdown, String methodologyVersion) {
}
