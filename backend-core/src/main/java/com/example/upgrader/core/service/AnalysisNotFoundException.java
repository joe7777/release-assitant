package com.example.upgrader.core.service;

public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(Long id) {
        super("Analysis not found: " + id);
    }
}
