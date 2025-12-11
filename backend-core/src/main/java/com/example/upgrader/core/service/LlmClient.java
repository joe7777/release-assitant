package com.example.upgrader.core.service;

import com.example.upgrader.core.model.llm.LlmAnalysisResult;

public interface LlmClient {
    LlmAnalysisResult analyzeRepository(String repositoryUrl, String branch, String springVersionTarget, String gitTokenId);
}
