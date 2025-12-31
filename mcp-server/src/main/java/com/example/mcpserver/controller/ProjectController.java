package com.example.mcpserver.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.example.mcpserver.dto.ProjectSpringUsageScanRequest;
import com.example.mcpserver.dto.ProjectSpringUsageScanResponse;
import com.example.mcpserver.service.ProjectSpringUsageScannerService;

@RestController
@RequestMapping("/api/project")
public class ProjectController {

    private final ProjectSpringUsageScannerService projectSpringUsageScannerService;

    public ProjectController(ProjectSpringUsageScannerService projectSpringUsageScannerService) {
        this.projectSpringUsageScannerService = projectSpringUsageScannerService;
    }

    @PostMapping("/scanSpringUsage")
    public Mono<ResponseEntity<ProjectSpringUsageScanResponse>> scanSpringUsage(
            @RequestBody ProjectSpringUsageScanRequest request) {
        return Mono.fromCallable(() -> projectSpringUsageScannerService.scanSpringUsage(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }
}
