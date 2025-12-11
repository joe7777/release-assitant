package com.example.upgrader.api.controller;

import com.example.upgrader.api.dto.AnalysisDetailResponse;
import com.example.upgrader.api.dto.AnalysisSummaryResponse;
import com.example.upgrader.api.dto.CreateAnalysisRequest;
import com.example.upgrader.api.mapper.AnalysisMapper;
import com.example.upgrader.core.command.CreateAnalysisCommand;
import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.service.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AnalysisMapper analysisMapper = new AnalysisMapper();

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisSummaryResponse createAnalysis(@RequestBody CreateAnalysisRequest request) {
        CreateAnalysisCommand command = new CreateAnalysisCommand(request.getProjectGitUrl(), request.getProjectName(),
                request.getBranch(), request.getSpringVersionTarget(), request.getGitTokenId());
        Analysis analysis = analysisService.createAnalysis(command);
        return analysisMapper.toSummary(analysis);
    }

    @GetMapping("/{id}")
    public AnalysisDetailResponse getAnalysis(@PathVariable("id") Long id) {
        Analysis analysis = analysisService.getAnalysisDetail(id);
        return analysisMapper.toDetail(analysis);
    }

    @GetMapping
    public List<AnalysisSummaryResponse> listAnalyses() {
        return analysisService.listAnalyses().stream()
                .map(analysisMapper::toSummary)
                .collect(Collectors.toList());
    }
}
