package com.example.upgrader.api.controller;

import com.example.upgrader.api.dto.AnalysisDetailResponse;
import com.example.upgrader.api.dto.AnalysisSummaryResponse;
import com.example.upgrader.api.dto.CreateAnalysisRequest;
import com.example.upgrader.api.mapper.AnalysisDtoMapper;
import com.example.upgrader.core.service.AnalysisNotFoundException;
import com.example.upgrader.core.service.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public AnalysisSummaryResponse createAnalysis(@RequestBody CreateAnalysisRequest request) {
        return AnalysisDtoMapper.toSummaryResponse(analysisService.createAnalysis(AnalysisDtoMapper.toCommand(request)));
    }

    @GetMapping("/{id}")
    public AnalysisDetailResponse getAnalysis(@PathVariable Long id) {
        try {
            return AnalysisDtoMapper.toDetailResponse(analysisService.getAnalysisDetail(id));
        } catch (AnalysisNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping
    public List<AnalysisSummaryResponse> listAnalyses() {
        return AnalysisDtoMapper.toSummaryList(analysisService.listAnalyses());
    }
}
