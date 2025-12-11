package com.example.upgrader.core.repository;

import com.example.upgrader.core.model.Project;

import java.util.Optional;

public interface ProjectRepository {
    Optional<Project> findByGitUrl(String gitUrl);

    Project save(Project project);
}
