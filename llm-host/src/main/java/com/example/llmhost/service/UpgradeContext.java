package com.example.llmhost.service;

import java.util.List;

import com.example.llmhost.rag.RagHit;

public record UpgradeContext(List<RagHit> hits, String contextText) {
}
