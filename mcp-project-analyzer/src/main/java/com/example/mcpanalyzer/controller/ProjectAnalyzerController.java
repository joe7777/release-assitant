package com.example.mcpanalyzer.controller;

import com.example.mcpanalyzer.api.dto.AnalyzeRequest;
import com.example.mcpanalyzer.api.dto.AnalyzeResponse;
import com.example.mcpanalyzer.service.ProjectAnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analyze")
public class ProjectAnalyzerController {

    private final ProjectAnalyzerService analyzerService;

    public ProjectAnalyzerController(ProjectAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @PostMapping
    public ResponseEntity<AnalyzeResponse> analyzeRepository(@RequestBody AnalyzeRequest request) {
        AnalyzeResponse response = analyzerService.analyze(request);
        return ResponseEntity.ok(response);
    }
}
