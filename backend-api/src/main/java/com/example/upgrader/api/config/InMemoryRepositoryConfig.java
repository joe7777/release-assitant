package com.example.upgrader.api.config;

import com.example.upgrader.core.model.Analysis;
import com.example.upgrader.core.model.Project;
import com.example.upgrader.core.repository.AnalysisRepository;
import com.example.upgrader.core.repository.ProjectRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class InMemoryRepositoryConfig {

    @Bean
    public ProjectRepository projectRepository() {
        return new InMemoryProjectRepository();
    }

    @Bean
    public AnalysisRepository analysisRepository(ProjectRepository projectRepository) {
        return new InMemoryAnalysisRepository();
    }

    private static class InMemoryProjectRepository implements ProjectRepository {
        private final AtomicLong sequence = new AtomicLong(1);
        private final Map<String, Project> byUrl = new HashMap<>();

        @Override
        public Optional<Project> findByGitUrl(String gitUrl) {
            return Optional.ofNullable(byUrl.get(gitUrl));
        }

        @Override
        public Project save(Project project) {
            if (project.getId() == null) {
                project.setId(sequence.getAndIncrement());
            }
            byUrl.put(project.getGitUrl(), project);
            return project;
        }
    }

    private static class InMemoryAnalysisRepository implements AnalysisRepository {
        private final AtomicLong sequence = new AtomicLong(1);
        private final Map<Long, Analysis> store = new HashMap<>();

        @Override
        public Analysis save(Analysis analysis) {
            if (analysis.getId() == null) {
                analysis.setId(sequence.getAndIncrement());
            }
            if (analysis.getCreatedAt() == null) {
                analysis.setCreatedAt(Instant.now());
            }
            store.put(analysis.getId(), analysis);
            return analysis;
        }

        @Override
        public Optional<Analysis> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Analysis> findAll() {
            return new ArrayList<>(store.values());
        }
    }
}
