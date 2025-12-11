package com.example.upgrader.infra.repository;

import com.example.upgrader.infra.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
}
