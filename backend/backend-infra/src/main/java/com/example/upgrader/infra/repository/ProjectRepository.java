package com.example.upgrader.infra.repository;

import com.example.upgrader.infra.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
