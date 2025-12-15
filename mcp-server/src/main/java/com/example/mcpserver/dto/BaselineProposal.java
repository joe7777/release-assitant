package com.example.mcpserver.dto;

import java.util.List;

public record BaselineProposal(String targetSpringVersion, List<String> missingDocuments) {
}
