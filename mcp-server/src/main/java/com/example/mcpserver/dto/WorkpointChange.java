package com.example.mcpserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record WorkpointChange(
        @NotBlank String id,
        @NotBlank String description,
        @PositiveOrZero int impact,
        @PositiveOrZero int complexity,
        String category) {
}
