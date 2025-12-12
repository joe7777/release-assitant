package com.example.upgrader.core.llm;

import com.example.upgrader.core.model.Analysis;

public interface LlmClient {
    LlmAnalysisResult runAnalysis(Analysis analysis);
}
