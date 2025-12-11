package com.example.mcpprojectanalyzer.web;

import com.example.mcpprojectanalyzer.api.AnalyzeRequest;
import com.example.mcpprojectanalyzer.api.AnalyzeResponse;
import com.example.mcpprojectanalyzer.service.ProjectAnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analyze")
public class ProjectAnalyzerController {

    private final ProjectAnalyzerService projectAnalyzerService;

    public ProjectAnalyzerController(ProjectAnalyzerService projectAnalyzerService) {
        this.projectAnalyzerService = projectAnalyzerService;
    }

    @PostMapping
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody AnalyzeRequest request) {
        AnalyzeResponse response = projectAnalyzerService.analyze(request);
        return ResponseEntity.ok(response);
    }
}
