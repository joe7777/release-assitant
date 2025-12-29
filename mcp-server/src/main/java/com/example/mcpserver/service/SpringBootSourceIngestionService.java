package com.example.mcpserver.service;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import com.example.mcpserver.dto.SpringSourceIngestionRequest;
import com.example.mcpserver.dto.SpringSourceIngestionResponse;

@Service
public class SpringBootSourceIngestionService {

    private static final RepoSourceConfig SPRING_BOOT_CONFIG = new RepoSourceConfig(
            "https://github.com/spring-projects/spring-boot",
            "spring-boot",
            "spring-boot",
            "SPRING_BOOT_SOURCE",
            "",
            "SPRING_SOURCE",
            List.of(
                    "spring-boot-project/spring-boot-autoconfigure/**",
                    "spring-boot-project/spring-boot-starters/**"),
            List.of("**/*.java"),
            List.of(
                    "**/src/test/**",
                    "**/*Test.java",
                    "**/build/**",
                    "**/target/**",
                    "**/.git/**",
                    "**/spring-boot-samples/**",
                    "**/package-info.java",
                    "**/module-info.java"));

    private final RepoSourceIngestionService repoSourceIngestionService;

    public SpringBootSourceIngestionService(RepoSourceIngestionService repoSourceIngestionService) {
        this.repoSourceIngestionService = repoSourceIngestionService;
    }

    public SpringSourceIngestionResponse ingestSpringBootSource(SpringSourceIngestionRequest request)
            throws IOException, GitAPIException {
        return repoSourceIngestionService.ingest(request, SPRING_BOOT_CONFIG);
    }
}
