package com.example.mcpmethodology.web;

import com.example.mcpmethodology.model.ComputeEffortRequest;
import com.example.mcpmethodology.model.EffortResult;
import com.example.mcpmethodology.service.MethodologyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MethodologyController {

    private final MethodologyService methodologyService;

    public MethodologyController(MethodologyService methodologyService) {
        this.methodologyService = methodologyService;
    }

    @PostMapping(path = "/compute-effort", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public EffortResult computeEffort(@RequestBody ComputeEffortRequest request) {
        return methodologyService.computeEffort(request != null ? request.getChanges() : null);
    }
}
