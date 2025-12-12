package com.example.upgrader.core.repository;

import com.example.upgrader.core.model.Project;

import java.util.Optional;

public interface ProjectRepository {
    Optional<Project> findByGitUrlAndBranch(String gitUrl, String branch);

    Project save(Project project);
}
