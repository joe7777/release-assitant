package com.example.upgrader.infra.repository;

import com.example.upgrader.infra.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    @EntityGraph(attributePaths = {"project", "changes", "effortSummary"})
    Optional<Analysis> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"project", "changes", "effortSummary"})
    List<Analysis> findAllWithDetails();
}
