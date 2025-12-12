package com.example.upgrader.infra.repository;

import com.example.upgrader.infra.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByGitUrlAndBranch(String gitUrl, String branch);
}
