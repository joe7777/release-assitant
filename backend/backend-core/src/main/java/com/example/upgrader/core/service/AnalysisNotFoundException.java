package com.example.upgrader.core.service;

public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(Long id) {
        super("Analysis with id " + id + " not found");
    }
}
